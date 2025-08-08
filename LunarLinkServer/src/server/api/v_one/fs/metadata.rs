use std::path::PathBuf;
use axum::{Extension, Json};
use serde::{Deserialize, Serialize};
use crate::auth::permission_manager::have_enough_permissions;
use crate::config::{LunarConfig, UserPermission};
use crate::core::lunar_error::LunarResult;
use crate::core::types::LunarMetadata;
use crate::server::context::auth_context::AuthContext;

#[derive(Debug, Deserialize, Serialize, Clone, PartialEq)]
pub struct MetadataRequest {
    pub path: String,
}

pub async fn get_metadata(
    Extension(auth_context): Extension<AuthContext>,
    Json(request): Json<MetadataRequest>,
) -> LunarResult<Json<LunarMetadata>>{
    have_enough_permissions(&vec![UserPermission::Read], &auth_context.permissions)?;
    let path = &PathBuf::from(&request.path);
    let metadata = crate::core::fs::get_metadata(path).await?;
    Ok(Json(metadata))
}