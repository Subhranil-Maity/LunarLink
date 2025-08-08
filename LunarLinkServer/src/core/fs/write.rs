use std::path::PathBuf;
use tokio::io::AsyncWriteExt;
use crate::core::fs::metadata::get_metadata;
use crate::core::types::LunarMetadata;
use crate::core::lunar_error::LunarResult;

pub async fn write_file(
    path: &PathBuf,
    data: &[u8],
    should_override: bool
) -> LunarResult<LunarMetadata> {
    let exists = tokio::fs::metadata(path).await?.is_file();
    if exists && should_override == false {
        return Err(crate::core::lunar_error::LunarError::FileOrFolderAlreadyExists);
    }
    let mut file = tokio::fs::OpenOptions::new()
        .create(true)
        .truncate(should_override)
        .open(path)
        .await?;
    file.write(data).await?;
    file.flush().await?;

    Ok(get_metadata(path).await?)
}