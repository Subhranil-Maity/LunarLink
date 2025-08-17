use crate::core::lunar_error::LunarResult;
use crate::core::types::LunarMetadata;
use std::path::PathBuf;

/// Asynchronously creates a file at the specified path if it does not exist,
/// or updates its last modified time if it does.
///
/// # Arguments
///
/// * [`path`] - A reference to a [`PathBuf`] representing the file path to touch.
///
/// # Returns
///
/// Returns a [`LunarResult<LunarMetadata>`] containing the metadata of the file after the operation.
///
/// # Errors
///
/// This function may return an error if:
/// - The file cannot be created or opened.
/// - Retrieving the metadata of the file fails.
/// - Any underlying I/O error occurs.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::create::touch;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let path = PathBuf::from("example.txt");
/// let metadata = touch(&path).await?;
/// println!("Touched file, size: {}", metadata.size);
/// # Ok(())
/// # }
/// ```

pub async fn touch(path: &PathBuf) -> LunarResult<LunarMetadata> {
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

/// Asynchronously creates a directory and all of its parent components if they do not exist.
///
/// # Arguments
///
/// * [`path`] - A reference to a [`PathBuf`] representing the directory path to create.
///
/// # Returns
///
/// Returns a [`LunarResult<LunarMetadata>`] containing the metadata of the directory after creation.
///
/// # Errors
///
/// This function may return an error if:
/// - The directory cannot be created (due to permissions or other I/O errors).
/// - Retrieving the metadata of the directory fails.
///
/// # Examples
///
/// ```rust,no_run
/// # use std::path::PathBuf;
/// use lunar_lib::core::fs::mkdir;
/// # async fn example() -> Result<(), Box<dyn std::error::Error>> {
/// let dir_path = PathBuf::from("new_folder/subfolder");
/// let metadata = mkdir(&dir_path).await?;
/// println!("Created directory: {:#?}", metadata);
/// # Ok(())
/// # }
/// ```

pub async fn mkdir(path: &PathBuf) -> LunarResult<LunarMetadata> {
    // Create the directory if it doesn't exist
    tokio::fs::create_dir_all(path).await?;

    // Get the metadata of the directory
    let metadata = crate::core::fs::metadata::get_metadata(path).await?;

    Ok(metadata)
}
