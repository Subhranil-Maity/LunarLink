use crate::core::lunar_error::{LunarError, LunarResult};
use std::path::PathBuf;

/// Asynchronously deletes a file or folder at the specified path.
///
/// If `permanently` is `true`, deletes the file or folder permanently.
/// If `permanently` is `false`, attempts to move the file or folder to the system trash/recycle bin.
///
/// # Arguments
///
/// * [`path`] - A reference to a [`PathBuf`] representing the file or folder to delete.
/// * `permanently` - A boolean indicating whether to delete permanently (`true`) or move to trash (`false`).
///
/// # Returns
///
/// Returns a [`LunarResult<()>`] indicating success or failure.
///
/// # Errors
///
/// This function may return an error if:
/// - The file or folder does not exist.
/// - Deletion fails due to permissions or other I/O errors.
/// - The trash operation fails when `permanently` is `false`.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::delete::delete;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let path = PathBuf::from("old_file.txt");
/// // Move to trash
/// delete(&path, false).await?;
/// // Or delete permanently
/// // delete(&path, true).await?;
/// println!("Deleted file");
/// # Ok(())
/// # }
/// ```

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
