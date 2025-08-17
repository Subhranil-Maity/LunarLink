use crate::auth::permission_manager::have_enough_permissions;
use crate::config::{LunarConfig, UserPermission};
use crate::core::lunar_error::{LunarError, LunarResult};
use crate::core::types::{FileData, LunarMetadata};
use crate::server::api::utils::QueryParams;
use crate::server::context::auth_context::AuthContext;
use axum::body::Body;
use axum::extract::{Multipart, Query};
use axum::response::{IntoResponse, Response};
use axum::{Extension, Json};
use http::StatusCode;
use serde::Deserialize;
use std::io::{ErrorKind, SeekFrom};
use std::path::{absolute, PathBuf};
use axum_extra::headers::Range;
use axum_extra::TypedHeader;
use axum_range::{KnownSize, Ranged};
use tokio::fs::{create_dir_all, File};
use tokio::io::{AsyncReadExt, AsyncSeekExt, AsyncWriteExt};
use tracing::{trace, Metadata};
use crate::core::lunar_error::LunarError::PathNotProvided;
use crate::server::utils::{assemble_file, is_upload_complete};



pub async fn get_read_full_file(
    Query(request): Query<QueryParams>,
    Extension(auth_context): Extension<AuthContext>,
) -> LunarResult<Json<FileData>> {
    let path = request.get_path()?;
    have_enough_permissions(&vec![UserPermission::Read], &auth_context.permissions)?;
    let path_buff = PathBuf::from(path);
    let file_data = crate::core::fs::read_file(&path_buff).await?;
    Ok(Json(file_data))
}

pub async fn get_chunked_file(
    Query(params): Query<QueryParams>,
    range: Option<TypedHeader<Range>>,
    Extension(auth_context): Extension<AuthContext>,
) -> LunarResult<Ranged<KnownSize<File>>> {
    have_enough_permissions(&vec![UserPermission::Read], &auth_context.permissions)?;
    let path = PathBuf::from(params.get_path()?);
    // Open file
    let file = match File::open(&path).await {
        Ok(f) => f,
        Err(e) => return match e.kind() {
            ErrorKind::NotFound => {
                Err(LunarError::FileOrFolderNotFound)
            }
            ErrorKind::PermissionDenied => {
                Err(LunarError::PermissionDenied)
            }
            _ => {
                Err(LunarError::FailedToReadFile)
            }
        },
    };
    let body = KnownSize::file(file).await?;
    let range = range.map(|TypedHeader(range)| range);
    Ok(Ranged::new(range, body))
}


#[deprecated]
pub async fn post_upload_chunk(
    Extension(auth_context): Extension<AuthContext>,
    mut multipart: Multipart,
) -> LunarResult<impl IntoResponse> {
    have_enough_permissions(&vec![UserPermission::Write], &auth_context.permissions)?;
    let mut file_name = String::new();
    let mut chunk_number = 0;
    let mut total_chunks = 0;
    let mut chunk_data = Vec::new();

    while let Some(field) = match multipart.next_field().await {
        Ok(f) => f,
        Err(err) => {
            tracing::error!("Error reading multipart field: {:?}", err);
            return Ok(StatusCode::BAD_REQUEST);
        }
    } {
        let field_name = field.name().unwrap_or_default().to_string();
        // if file_name == "chunk" {
        //     println!("chunk {:#?}", field.bytes().await?);
        // }
        let c = field.text().await?;
        println!("Field name: {}; Data = {:#?}", field_name, c);
        // match field_name.as_str() {
        //     "fileName" => file_name = field.text().await?,
        //     "chunkNumber" => chunk_number = field.text().await.unwrap_or_default().parse().unwrap_or(0),
        //     "totalChunks" => total_chunks = field.text().await.unwrap_or_default().parse().unwrap_or(0),
        //     "chunk" => chunk_data = field.bytes().await.unwrap_or_else(|_| Vec::new().into()).to_vec(),
        //     _ => {}
        // }
    }

    if file_name.is_empty() || chunk_data.is_empty() {
        tracing::error!("File name or chunk data is empty");
        println!("File name: {}, Chunk data length: {}", file_name, chunk_data.len());
        return Ok(StatusCode::BAD_REQUEST);
    }

    let temp_dir = format!("./temp/{}", file_name);
    create_dir_all(&temp_dir).await?;
    let chunk_path = format!("{}/chunk_{}", temp_dir, chunk_number);
    let mut file = File::create(&chunk_path).await?;
    file.write_all(&chunk_data).await?;

    if is_upload_complete(&temp_dir, total_chunks).await {
        assemble_file(&temp_dir, &file_name, total_chunks).await?;
    }

    Ok(StatusCode::OK)
}

pub async fn patch_rename_file(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<StatusCode> {
    have_enough_permissions(&vec![UserPermission::Write], &auth_context.permissions)?;
    let path = request.get_path()?;
    let new_name = request.get_new_path()?;
    let should_overwrite = request.should_override();
    let old_path = PathBuf::from(path);
    let new_path = PathBuf::from(new_name);

    if old_path == new_path {
        return Ok(StatusCode::OK);
    }
    crate::core::fs::move_file(&old_path, &new_path, should_overwrite).await?;
    Ok(StatusCode::OK)
}
pub async fn patch_move_file(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<StatusCode> {
    have_enough_permissions(&vec![UserPermission::Write, UserPermission::Delete], &auth_context.permissions)?;
    let path = request.get_path()?;
    let new_name = request.get_new_path()?;
    let should_overwrite = request.should_override();
    let old_path = PathBuf::from(path);
    let new_path = PathBuf::from(new_name);

    if old_path == new_path {
        return Ok(StatusCode::OK);
    }
    crate::core::fs::move_file(&old_path, &new_path, should_overwrite).await?;
    Ok(StatusCode::OK)
}

pub async fn post_copy(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<StatusCode> {
    have_enough_permissions(&vec![UserPermission::Write, UserPermission::Read], &auth_context.permissions)?;
    let path = request.get_path()?;
    let new_name = request.get_new_path()?;
    let should_overwrite = request.should_override();
    let old_path = PathBuf::from(path);
    let new_path = PathBuf::from(new_name);

    if old_path == new_path {
        return Ok(StatusCode::OK);
    }
    crate::core::fs::copy(&old_path, &new_path, should_overwrite).await?;
    Ok(StatusCode::OK)
}

pub async fn delete_delete(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<StatusCode> {
    have_enough_permissions(&vec![UserPermission::Delete], &auth_context.permissions)?;
    let path = request.get_path()?;
    let permanent = request.is_permanent();
    let file_path = PathBuf::from(path);
    crate::core::fs::delete(&file_path, permanent).await?;
    Ok(StatusCode::OK)
}


pub async fn post_touch(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<Json<LunarMetadata>> {
    have_enough_permissions(&vec![UserPermission::Write], &auth_context.permissions)?;
    let path = request.get_path()?;
    let file_path = PathBuf::from(path);
    
    let data = crate::core::fs::touch(&file_path).await?;
    Ok(Json(data))
}


