use std::fs;
use std::os::windows::fs::MetadataExt;
use std::path::PathBuf;
use std::time::UNIX_EPOCH;
use tokio::fs::metadata;
use crate::core::fs::types::{EntryType, LunarMetadata};
use crate::core::lunar_error::{LunarError, LunarResult};

pub async fn get_metadata(path: &PathBuf) -> LunarResult<LunarMetadata>{
    let fs_metadata = metadata(path).await?;
    let is_symlink = fs_metadata.file_type().is_symlink();


    let absolute_path = path.to_string_lossy().to_string();
    let last_modified = fs_metadata
        .modified()?
        .duration_since(UNIX_EPOCH)
        .map_err(|e| LunarError::KnownUnknownError(e.to_string()))?
        .as_secs();
    let symlink_location = if is_symlink {
        match fs::read(path) {
            Ok(link_data) => Some(String::from_utf8_lossy(&link_data).to_string()),
            Err(_) => None, // If reading symlink fails, we just return None
        }
    }else {
        None
    };
    let entry_type = if is_symlink {
        EntryType::Symlink
    } else if fs_metadata.file_type().is_dir() {
        EntryType::Directory
    } else if fs_metadata.file_type().is_file() {
        EntryType::File
    } else {
        EntryType::Other
    };
    let size = if entry_type == EntryType::File{
        fs_metadata.file_size()
    }else {
        0
    };
    let file_name = path.file_name()
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