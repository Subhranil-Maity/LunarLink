use crate::core::lunar_error::{LunarError, LunarResult};
use crate::core::to_absolute_path;
use crate::core::types::{EntryType, LunarMetadata};
use std::fs;
// use std::os::windows::fs::MetadataExt;
use std::path::PathBuf;
use std::time::UNIX_EPOCH;
use tokio::fs::{metadata, symlink_metadata};

/// Asynchronously retrieves metadata for the file or folder at the specified path.
///
/// # Arguments
///
/// * [`path`] - A reference to a [`PathBuf`] representing the file or folder to get metadata for.
///
/// # Returns
///
/// Returns a [`LunarResult<LunarMetadata>`] containing the metadata information.
///
/// # Errors
///
/// This function may return an error if:
/// - The file or folder does not exist.
/// - Retrieving metadata fails due to permissions or other I/O errors.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::metadata::get_metadata;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let path = PathBuf::from("example.txt");
/// let metadata = get_metadata(&path).await?;
/// println!("File size: {}", metadata.size);
/// # Ok(())
/// # }
/// ```

pub async fn get_metadata(path: &PathBuf) -> LunarResult<LunarMetadata> {
    let fs_metadata = tokio::fs::symlink_metadata(path).await?;
    let is_symlink = fs_metadata.file_type().is_symlink();
    let absolute_path = to_absolute_path(path)?.to_string_lossy().to_string();
    let last_modified = fs_metadata
        .modified()?
        .duration_since(UNIX_EPOCH)
        .map_err(|e| LunarError::KnownUnknownError(e.to_string()))?
        .as_secs();
    let symlink_location = if is_symlink {
        match std::fs::read_link(path) {
            Ok(link_path) => Some(link_path.to_string_lossy().to_string()),
            Err(_) => None,
        }
    } else {
        None
    };
    let entry_type = if is_symlink {
        EntryType::Symlink
    } else if fs_metadata.is_dir() {
        EntryType::Directory
    } else if fs_metadata.is_file() {
        EntryType::File
    } else {
        EntryType::Other
    };
    let size = if entry_type == EntryType::File {
        fs_metadata.len()
    } else {
        0
    };
    let file_name = path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("unknown")
        .to_string();
    let lunar_metadata = LunarMetadata {
        name: file_name,
        size,
        entry_type,
        symlink_location,
        absolute_path,
        last_modified,
        mime_type: None,
    };
    Ok(lunar_metadata)
}
