use crate::core::error::{LunarError, LunarResult};
use crate::core::path::Leaf;
use crate::core::permission::Permission;
use crate::core::thumbs;
use crate::server::api::blocking;
use crate::server::api::fs::{require, resolve};
use crate::server::extract::ApiQuery;
use crate::server::middleware::AuthUser;
use crate::server::state::AppState;
use axum::extract::State;
use axum::http::header;
use axum::response::{IntoResponse, Response};
use axum::Extension;
use serde::Deserialize;
use std::sync::Arc;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ThumbnailQuery {
    path: String,
    size: Option<u32>,
}

/// A small JPEG (or PNG, for transparent images) preview of an image file.
pub async fn thumbnail(
    State(state): State<Arc<AppState>>,
    Extension(user): Extension<AuthUser>,
    ApiQuery(query): ApiQuery<ThumbnailQuery>,
) -> LunarResult<Response> {
    let thumbnailer = state
        .thumbnails
        .clone()
        .ok_or_else(|| LunarError::ThumbnailUnavailable("thumbnails are turned off".into()))?;
    let resolved = resolve(&state, &query.path, Leaf::Follow)?;
    require(&user, &resolved, &[Permission::Read])?;
    let _permit = state
        .thumbnail_permits
        .acquire()
        .await
        .map_err(|e| LunarError::Internal(e.to_string()))?;
    let size = query.size.unwrap_or(thumbs::DEFAULT_SIZE);
    let path = resolved.path;
    let thumb = blocking(move || thumbnailer.thumbnail(&path, size)).await?;
    Ok((
        [
            (header::CONTENT_TYPE, thumb.content_type),
            (header::CACHE_CONTROL, "private, max-age=604800"),
        ],
        thumb.bytes,
    )
        .into_response())
}
