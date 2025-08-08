use std::path::PathBuf;
use crate::core::lunar_error::{LunarError, LunarResult};

pub async fn copy(
    src: &PathBuf,
    dst: &PathBuf,
    overwrite: bool,
) -> LunarResult<()> {
    // Check if source exists
    if !tokio::fs::metadata(src).await.is_ok() {
        return Err(LunarError::FileOrFolderNotFound);
    }
    // Check if destination exists
    if tokio::fs::metadata(dst).await.is_ok() && !overwrite {
        return Err(LunarError::FileOrFolderAlreadyExists);
    }

    // Perform copy
    tokio::fs::copy(src, dst).await?;
    Ok(())
}

pub async fn move_file(
    src: &PathBuf,
    dst: &PathBuf,
    overwrite: bool,
) -> LunarResult<()> {
    // Check if source exists
    if !tokio::fs::metadata(src).await.is_ok() {
        return Err(LunarError::FileOrFolderNotFound);
    }
    // Check if destination exists
    if tokio::fs::metadata(dst).await.is_ok() && !overwrite {
        return Err(LunarError::FileOrFolderAlreadyExists);
    }

    // Attempt rename (move)
    match tokio::fs::rename(src, dst).await {
        Ok(_) => Ok(()),
        Err(e) => {
            // On failure (e.g. cross-device rename), fallback to copy + delete
            tokio::fs::copy(src, dst).await?;
            tokio::fs::remove_file(src).await?;
            Ok(())
        }
    }
}
