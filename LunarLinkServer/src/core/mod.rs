use std::env;
use std::path::{Path, PathBuf};

pub mod lunar_error;
pub mod fs;
pub mod types;

fn to_absolute_path<P: AsRef<Path>>(path: P) -> std::io::Result<PathBuf> {
    let path = path.as_ref();
    if path.is_absolute() {
        Ok(path.to_path_buf())
    } else {
        let current_dir = env::current_dir()?;
        Ok(current_dir.join(path).canonicalize()?)
    }
}