use std::path::PathBuf;
use axum::{Extension, Json};
use axum::extract::Query;
use serde::{Deserialize, Serialize};
use crate::auth::permission_manager::have_enough_permissions;
use crate::config::{LunarConfig, UserPermission};
use crate::core::lunar_error::{LunarError, LunarResult};
use crate::core::types::LunarMetadata;
use crate::server::api::utils::QueryParams;
use crate::server::context::auth_context::AuthContext;

pub async fn get_metadata(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<Json<LunarMetadata>>{
    let path = request.get_path()?;
    have_enough_permissions(&vec![UserPermission::Read], &auth_context.permissions)?;
    let path = &PathBuf::from(path);
    let metadata = crate::core::fs::get_metadata(path).await?;
    Ok(Json(metadata))
}