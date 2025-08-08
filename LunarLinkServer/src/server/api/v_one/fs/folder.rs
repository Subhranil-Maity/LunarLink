use std::path::PathBuf;
use axum::{Extension, Json};
use crate::auth::permission_manager::have_enough_permissions;
use crate::config::UserPermission;
use crate::core::fs::read_folder;
use crate::core::lunar_error::LunarResult;
use crate::core::types::{FolderData, LunarMetadata};
use crate::server::context::auth_context::AuthContext;

#[derive(Debug, serde::Deserialize, serde::Serialize)]
pub struct LsRequest {
    pub path: String,
}

pub async fn get_ls(
    Extension(auth_context): Extension<AuthContext>,
    Json(ls_result): Json<LsRequest>,
) -> LunarResult<Json<FolderData>> {
    have_enough_permissions(&vec![UserPermission::Read], &auth_context.permissions)?;
    let path_buff = &PathBuf::from(ls_result.path);
    let data = read_folder(path_buff).await?;
    Ok(Json(data))
}