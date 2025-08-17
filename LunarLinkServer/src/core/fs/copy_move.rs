use crate::core::lunar_error::{LunarError, LunarResult};
use std::path::PathBuf;

/// Asynchronously copies a file or folder from the source path to the destination path.
///
/// # Arguments
///
/// * [`src`] - A reference to a [`PathBuf`] representing the source file or folder to copy.
/// * [`dst`] - A reference to a [`PathBuf`] representing the destination path.
/// * `overwrite` - A boolean indicating whether to overwrite the destination if it already exists.
///
/// # Returns
///
/// Returns a [`LunarResult<()>`] indicating success or failure.
///
/// # Errors
///
/// This function may return an error if:
/// - The source file or folder does not exist.
/// - The destination already exists and `overwrite` is `false`.
/// - Any underlying I/O error occurs during the copy operation.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::copy_move::copy;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let src = PathBuf::from("source.txt");
/// let dst = PathBuf::from("destination.txt");
/// copy(&src, &dst, false).await?;
/// println!("File copied successfully");
/// # Ok(())
/// # }
/// ```

pub async fn copy(src: &PathBuf, dst: &PathBuf, overwrite: bool) -> LunarResult<()> {
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

/// Asynchronously moves a file from the source path to the destination path.
///
/// This function attempts to rename the file directly. If the rename fails
/// (e.g., due to cross-device move restrictions), it falls back to copying
/// the file to the destination and deleting the original.
///
/// # Arguments
///
/// * [`src`] - A reference to a [`PathBuf`] representing the source file to move.
/// * [`dst`] - A reference to a [`PathBuf`] representing the destination path.
/// * `overwrite` - A boolean indicating whether to overwrite the destination if it already exists.
///
/// # Returns
///
/// Returns a [`LunarResult<()>`] indicating success or failure.
///
/// # Errors
///
/// This function may return an error if:
/// - The source file does not exist.
/// - The destination already exists and `overwrite` is `false`.
/// - Any underlying I/O error occurs during the rename, copy, or delete operations.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::copy_move::move_file;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let src = PathBuf::from("old_location.txt");
/// let dst = PathBuf::from("new_location.txt");
/// move_file(&src, &dst, true).await?;
/// println!("File moved successfully");
/// # Ok(())
/// # }
/// ```

pub async fn move_file(src: &PathBuf, dst: &PathBuf, overwrite: bool) -> LunarResult<()> {
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
        Err(_) => {
            // On failure (e.g. cross-device rename), fallback to copy + delete
            tokio::fs::copy(src, dst).await?;
            tokio::fs::remove_file(src).await?;
            Ok(())
        }
    }
}
