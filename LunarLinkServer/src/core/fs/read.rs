use crate::core::fs::metadata::get_metadata;
use crate::core::lunar_error::LunarResult;
use crate::core::types::{FileData, FolderData};
use std::path::PathBuf;


/// Asynchronously reads the contents of a file at the specified path and retrieves its metadata.
///
/// # Arguments
///
/// * `path` - A reference to a `PathBuf` representing the file path to read.
///
/// # Returns
///
/// Returns a [`LunarResult<FileData>`], which on success contains:
/// - [`FileData`] struct holding the file's raw bytes (`data`) and its metadata (`metadata`).
///
/// # Errors
///
/// This function may return an error if:
/// - The file does not exist or cannot be opened/read.
/// - The metadata retrieval fails.
/// - Any underlying I/O error occurs during reading or metadata fetching.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::read_file;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let path = PathBuf::from("example.txt");
/// let file_data = read_file(&path).await?;
/// println!("Read {} bytes from file", file_data.data.len());
/// # Ok(())
/// # }
/// ```

pub async fn read_file(path: &PathBuf) -> LunarResult<FileData>{
    let data = read_file_bytes(path).await?;
    let metadata = get_metadata(path).await?;
    Ok(FileData{metadata, data})
}

pub async fn read_file_bytes(path: &PathBuf) -> LunarResult<Vec<u8>> {
    let data = tokio::fs::read(path).await?;
    Ok(data)
}


/// Asynchronously reads the contents of a folder at the specified path and retrieves metadata for the folder and its entries.
///
/// # Arguments
///
/// * [`path`] - A reference to a [`PathBuf`] representing the folder path to read.
///
/// # Returns
///
/// Returns a [`LunarResult<FolderData>`], which on success contains:
/// - [`FolderData`] struct holding the folder’s metadata and a list of metadata for each entry inside the folder.
///
/// # Errors
///
/// This function may return an error if:
/// - The folder does not exist or cannot be opened/read.
/// - Retrieving metadata for the folder or any of its entries fails.
/// - Any underlying I/O error occurs during directory reading or metadata fetching.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::read_folder;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let folder_path = PathBuf::from("my_folder");
/// let folder_data = read_folder(&folder_path).await?;
/// println!("Folder contains {} entries", folder_data.entries.len());
/// # Ok(())
/// # }
/// ```

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