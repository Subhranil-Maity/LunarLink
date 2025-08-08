use std::path::PathBuf;
use crate::core::lunar_error::LunarResult;
use crate::core::types::LunarMetadata;

pub async fn touch(path: &PathBuf) -> LunarResult<LunarMetadata>{
    // Create the file if it doesn't exist, or update its last modified time
    tokio::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
        .await?;

    // Get the metadata of the file
    let metadata = crate::core::fs::metadata::get_metadata(path).await?;

    Ok(metadata)
}

pub async fn mkdir(path: &PathBuf) -> LunarResult<LunarMetadata> {
    // Create the directory if it doesn't exist
    tokio::fs::create_dir_all(path).await?;

    // Get the metadata of the directory
    let metadata = crate::core::fs::metadata::get_metadata(path).await?;

    Ok(metadata)
}