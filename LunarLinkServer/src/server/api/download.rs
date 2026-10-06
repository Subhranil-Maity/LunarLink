use crate::core::error::{LunarError, LunarResult};
use crate::core::path::Leaf;
use crate::core::permission::Permission;
use crate::server::api::fs::{require, resolve};
use crate::server::extract::ApiQuery;
use crate::server::middleware::AuthUser;
use crate::server::state::AppState;
use axum::extract::State;
use axum::http::{header, HeaderMap, HeaderValue};
use axum::Extension;
use axum_extra::headers::Range;
use axum_extra::TypedHeader;
use axum_range::{KnownSize, Ranged};
use chrono::{DateTime, Utc};
use serde::Deserialize;
use std::path::Path;
use std::sync::Arc;
use tokio::fs::File;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DownloadQuery {
    path: String,
}

/// Streams a file, honouring `Range` requests.
pub async fn download(
    State(state): State<Arc<AppState>>,
    Extension(user): Extension<AuthUser>,
    ApiQuery(query): ApiQuery<DownloadQuery>,
    range: Option<TypedHeader<Range>>,
) -> LunarResult<(HeaderMap, Ranged<KnownSize<File>>)> {
    let resolved = resolve(&state, &query.path, Leaf::Follow)?;
    require(&user, &resolved, &[Permission::Read])?;

    let file = File::open(&resolved.path).await?;
    let meta = file.metadata().await?;
    if meta.is_dir() {
        return Err(LunarError::IsADirectory);
    }

    let mut headers = HeaderMap::new();
    let mime = mime_guess::from_path(&resolved.path).first_or_octet_stream();
    if let Ok(value) = HeaderValue::from_str(mime.essence_str()) {
        headers.insert(header::CONTENT_TYPE, value);
    }
    if let Ok(modified) = meta.modified() {
        let http_date = DateTime::<Utc>::from(modified)
            .format("%a, %d %b %Y %H:%M:%S GMT")
            .to_string();
        if let Ok(value) = HeaderValue::from_str(&http_date) {
            headers.insert(header::LAST_MODIFIED, value);
        }
    }
    if let Ok(value) = HeaderValue::from_str(&content_disposition(&resolved.path)) {
        headers.insert(header::CONTENT_DISPOSITION, value);
    }

    let body = KnownSize::file(file).await?;
    Ok((headers, Ranged::new(range.map(|TypedHeader(r)| r), body)))
}

/// `attachment; filename*=UTF-8''<percent-encoded name>` (RFC 6266 / RFC 5987).
fn content_disposition(path: &Path) -> String {
    let name = path
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| "download".into());
    let mut encoded = String::with_capacity(name.len());
    for byte in name.bytes() {
        if byte.is_ascii_alphanumeric() || b"!#$&+-.^_`|~".contains(&byte) {
            encoded.push(byte as char);
        } else {
            encoded.push_str(&format!("%{byte:02X}"));
        }
    }
    format!("attachment; filename*=UTF-8''{encoded}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encodes_file_names() {
        assert_eq!(
            content_disposition(Path::new("/a/report v1.pdf")),
            "attachment; filename*=UTF-8''report%20v1.pdf"
        );
        assert_eq!(
            content_disposition(Path::new("/a/ñ\"x")),
            "attachment; filename*=UTF-8''%C3%B1%22x"
        );
    }
}
