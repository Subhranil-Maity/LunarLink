use crate::auth::permission_manager::have_enough_permissions;
use crate::config::UserPermission;
use crate::core::lunar_error::LunarResult;
use crate::core::types::LunarMetadata;
use crate::server::api::utils::QueryParams;
use crate::server::context::auth_context::AuthContext;
use axum::extract::Query;
use axum::{Extension, Json};
use std::path::PathBuf;

pub async fn get_metadata(
    Extension(auth_context): Extension<AuthContext>,
    Query(request): Query<QueryParams>,
) -> LunarResult<Json<LunarMetadata>> {
    let path = request.get_path()?;
    have_enough_permissions(&vec![UserPermission::Read], &auth_context.permissions)?;
    let path = &PathBuf::from(path);
    let metadata = crate::core::fs::get_metadata(path).await?;
    Ok(Json(metadata))
}
