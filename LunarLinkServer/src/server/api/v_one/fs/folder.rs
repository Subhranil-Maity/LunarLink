use crate::auth::permission_manager::have_enough_permissions;
use crate::config::UserPermission;
use crate::core::fs::read_folder;
use crate::core::lunar_error::LunarResult;
use crate::core::types::{FolderData, LunarMetadata};
use crate::server::api::utils::QueryParams;
use crate::server::context::auth_context::AuthContext;
use axum::extract::Query;
use axum::{Extension, Json};
use std::path::PathBuf;

pub async fn get_ls(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<Json<FolderData>> {
    let path = request.get_path()?;
    have_enough_permissions(&vec![UserPermission::Read], &auth_context.permissions)?;
    let path_buff = &PathBuf::from(path);
    let data = read_folder(path_buff).await?;
    Ok(Json(data))
}

pub async fn post_mkdir(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<Json<LunarMetadata>> {
    have_enough_permissions(&vec![UserPermission::Write], &auth_context.permissions)?;
    let path = request.get_path()?;
    let path_buff = PathBuf::from(path);
    let metadata = crate::core::fs::mkdir(&path_buff).await?;
    Ok(Json(metadata))
}
