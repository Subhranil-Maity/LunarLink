use crate::core::types::{EntryType, FileData, FolderData, LunarMetadata};
use crate::core::lunar_error::{LunarError, LunarResult};
use std::os::windows::fs::MetadataExt;
use std::path::PathBuf;
use std::time::UNIX_EPOCH;
use crate::core::fs::metadata::get_metadata;

pub async fn read_file(path: &PathBuf) -> LunarResult<FileData>{
    let data = tokio::fs::read(path).await?;
    let metadata = get_metadata(path).await?;
    Ok(FileData{metadata, data})
}

pub async fn read_folder(path: &PathBuf) -> LunarResult<FolderData>{
    let mut entries = Vec::new();
    let mut dir = tokio::fs::read_dir(path).await?;
    while let Some(entry) = dir.next_entry().await? {
        entries.push(get_metadata(&entry.path()).await?);
    }
    let metadata = get_metadata(path).await?;
    let folder_data = FolderData{
        metadata,
        entries,
    };

    Ok(folder_data)
}