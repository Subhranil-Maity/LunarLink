use crate::auth::permission_manager::have_enough_permissions;
use crate::config::UserPermission;
use crate::core::lunar_error::{LunarError, LunarResult};
use crate::core::types::{FileData, LunarMetadata};
use crate::server::api::utils::QueryParams;
use crate::server::context::auth_context::AuthContext;
use axum::extract::Query;
use axum::{Extension, Json};
use axum_extra::headers::Range;
use axum_extra::TypedHeader;
use axum_range::{KnownSize, Ranged};
use axum::http::StatusCode;
use std::io::ErrorKind;
use std::path::PathBuf;
use tokio::fs::File;
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
        Err(e) => {
            return match e.kind() {
                ErrorKind::NotFound => Err(LunarError::FileOrFolderNotFound),
                ErrorKind::PermissionDenied => Err(LunarError::PermissionDenied),
                _ => Err(LunarError::FailedToReadFile),
            }
        }
    };
    let body = KnownSize::file(file).await?;
    let range = range.map(|TypedHeader(range)| range);
    Ok(Ranged::new(range, body))
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
    have_enough_permissions(
        &vec![UserPermission::Write, UserPermission::Delete],
        &auth_context.permissions,
    )?;
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
    have_enough_permissions(
        &vec![UserPermission::Write, UserPermission::Read],
        &auth_context.permissions,
    )?;
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
