use crate::core::fs::metadata::get_metadata;
use crate::core::lunar_error::LunarResult;
use crate::core::types::LunarMetadata;
use std::path::PathBuf;
use tokio::io::AsyncWriteExt;

/// Asynchronously writes data to a file at the specified path.
///
/// If the file exists and `should_override` is `false`, the function returns an error.
/// Otherwise, it creates the file if it does not exist or truncates it if overriding.
///
/// # Arguments
///
/// * [`path`] - A reference to a [`PathBuf`] representing the file path to write to.
/// * `data` - A byte slice containing the data to write.
/// * `should_override` - A boolean indicating whether to overwrite the file if it exists.
///
/// # Returns
///
/// Returns a [`LunarResult<LunarMetadata>`] containing the metadata of the written file.
///
/// # Errors
///
/// This function may return an error if:
/// - The file exists and `should_override` is `false`.
/// - The file cannot be created or opened.
/// - Writing or flushing the data fails.
/// - Retrieving the metadata of the file fails.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::write_file;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let path = PathBuf::from("output.txt");
/// let data = b"Hello, world!";
/// let metadata = write_file(&path, data, true).await?;
/// println!("Wrote {} bytes", metadata.size);
/// # Ok(())
/// # }
/// ```

pub async fn write_file(
    path: &PathBuf,
    data: &[u8],
    should_override: bool,
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
