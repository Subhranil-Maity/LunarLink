use crate::core::lunar_error::{LunarError, LunarResult};
use std::path::PathBuf;

pub async fn delete(path: &PathBuf, permanently: bool) -> LunarResult<()> {
    if permanently {
        let metadata = tokio::fs::metadata(path).await?;
        if metadata.is_dir() {
            tokio::fs::remove_dir_all(path).await?;
        } else {
            tokio::fs::remove_file(path).await?;
        }
    } else {
        // Run blocking trash delete in a blocking task
        let path_clone = path.clone();
        tokio::task::spawn_blocking(move || {
            trash::delete(&path_clone)
                .map_err(|e| LunarError::KnownUnknownError(e.to_string()))
        })
            .await
            .map_err(|e| LunarError::KnownUnknownError(e.to_string()))??;
    }

    Ok(())
}
