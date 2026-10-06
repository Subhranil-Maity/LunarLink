pub mod auth;
pub mod download;
pub mod fs;
pub mod info;

use crate::core::error::{LunarError, LunarResult};

/// Runs blocking filesystem work off the async runtime.
pub(crate) async fn blocking<T, F>(f: F) -> LunarResult<T>
where
    T: Send + 'static,
    F: FnOnce() -> LunarResult<T> + Send + 'static,
{
    tokio::task::spawn_blocking(f)
        .await
        .map_err(|e| LunarError::Internal(e.to_string()))?
}
