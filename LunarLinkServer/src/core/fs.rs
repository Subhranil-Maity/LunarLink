//! Synchronous filesystem operations. Callers run them on a blocking thread and pass
//! paths that were already resolved by [`crate::core::path::RootTable::resolve`].

use crate::core::error::{LunarError, LunarResult};
use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::path::Path;

/// Creates a new empty file. Fails with `AlreadyExists` if anything exists at `path`.
pub fn create_file(path: &Path) -> LunarResult<()> {
    OpenOptions::new().write(true).create_new(true).open(path)?;
    Ok(())
}

/// Creates a folder. With `parents`, missing parent folders are created too.
pub fn create_dir(path: &Path, parents: bool) -> LunarResult<()> {
    if parents {
        if exists_no_follow(path) {
            return Err(LunarError::AlreadyExists);
        }
        fs::create_dir_all(path)?;
    } else {
        fs::create_dir(path)?;
    }
    Ok(())
}

/// Writes `data` to `path`. Without `overwrite`, fails if the file already exists.
pub fn write_file(path: &Path, data: &[u8], overwrite: bool) -> LunarResult<()> {
    let mut options = OpenOptions::new();
    options.write(true);
    if overwrite {
        options.create(true).truncate(true);
    } else {
        options.create_new(true);
    }
    let mut file = options.open(path)?;
    file.write_all(data)?;
    file.flush()?;
    Ok(())
}

/// Copies a file, symlink or folder (recursively) to `to`.
/// With `overwrite`, an existing target is removed first (folders are replaced, not merged).
pub fn copy(from: &Path, to: &Path, overwrite: bool) -> LunarResult<()> {
    check_transfer(from, to)?;
    prepare_destination(to, overwrite)?;
    copy_any(from, to)
}

/// Moves (or renames) a file, symlink or folder. Falls back to copy + delete
/// only when the target is on a different device.
pub fn move_entry(from: &Path, to: &Path, overwrite: bool) -> LunarResult<()> {
    if from == to {
        return Ok(());
    }
    check_transfer(from, to)?;
    prepare_destination(to, overwrite)?;
    match fs::rename(from, to) {
        Ok(()) => Ok(()),
        Err(e) if e.kind() == io::ErrorKind::CrossesDevices => {
            copy_any(from, to)?;
            remove_any(from)
        }
        Err(e) => Err(e.into()),
    }
}

/// Deletes `path`. Non-empty folders need `recursive`. Symlinks are removed, never followed.
pub fn delete(path: &Path, permanent: bool, recursive: bool) -> LunarResult<()> {
    let meta = fs::symlink_metadata(path)?;
    if meta.is_dir() && !recursive && fs::read_dir(path)?.next().is_some() {
        return Err(LunarError::DirectoryNotEmpty);
    }
    if permanent {
        remove_any(path)
    } else {
        trash::delete(path).map_err(|e| LunarError::TrashFailed(e.to_string()))
    }
}

fn exists_no_follow(path: &Path) -> bool {
    fs::symlink_metadata(path).is_ok()
}

fn check_transfer(from: &Path, to: &Path) -> LunarResult<()> {
    let meta = fs::symlink_metadata(from)?;
    if from == to {
        return Err(LunarError::InvalidOperation(
            "The source and destination are the same.",
        ));
    }
    if meta.is_dir() && to.starts_with(from) {
        return Err(LunarError::InvalidOperation(
            "A folder cannot be copied or moved into itself.",
        ));
    }
    if from.starts_with(to) {
        return Err(LunarError::InvalidOperation(
            "The destination contains the source.",
        ));
    }
    Ok(())
}

fn prepare_destination(to: &Path, overwrite: bool) -> LunarResult<()> {
    if exists_no_follow(to) {
        if !overwrite {
            return Err(LunarError::AlreadyExists);
        }
        remove_any(to)?;
    }
    Ok(())
}

fn copy_any(from: &Path, to: &Path) -> LunarResult<()> {
    let meta = fs::symlink_metadata(from)?;
    if meta.is_symlink() {
        copy_symlink(from, to)
    } else if meta.is_dir() {
        copy_dir(from, to)
    } else {
        fs::copy(from, to)?;
        Ok(())
    }
}

fn copy_dir(from: &Path, to: &Path) -> LunarResult<()> {
    for entry in walkdir::WalkDir::new(from).follow_links(false) {
        let entry = entry.map_err(|e| match e.into_io_error() {
            Some(io) => LunarError::from(io),
            None => LunarError::Internal("filesystem loop while copying".into()),
        })?;
        let relative = entry
            .path()
            .strip_prefix(from)
            .map_err(|e| LunarError::Internal(e.to_string()))?;
        let target = to.join(relative);
        let file_type = entry.file_type();
        if file_type.is_symlink() {
            copy_symlink(entry.path(), &target)?;
        } else if file_type.is_dir() {
            fs::create_dir(&target)?;
        } else {
            fs::copy(entry.path(), &target)?;
        }
    }
    Ok(())
}

fn copy_symlink(from: &Path, to: &Path) -> LunarResult<()> {
    let target = fs::read_link(from)?;
    #[cfg(unix)]
    std::os::unix::fs::symlink(&target, to)?;
    #[cfg(windows)]
    {
        if fs::metadata(from).map(|m| m.is_dir()).unwrap_or(false) {
            std::os::windows::fs::symlink_dir(&target, to)?;
        } else {
            std::os::windows::fs::symlink_file(&target, to)?;
        }
    }
    Ok(())
}

fn remove_any(path: &Path) -> LunarResult<()> {
    let meta = fs::symlink_metadata(path)?;
    if meta.is_dir() {
        fs::remove_dir_all(path)?;
    } else if let Err(e) = fs::remove_file(path) {
        // Directory symlinks on Windows must be removed with remove_dir.
        if cfg!(windows) && meta.is_symlink() {
            fs::remove_dir(path)?;
        } else {
            return Err(e.into());
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn tmp() -> (tempfile::TempDir, PathBuf) {
        let dir = tempfile::tempdir().unwrap();
        let base = dunce::canonicalize(dir.path()).unwrap();
        (dir, base)
    }

    fn tree(base: &Path) -> PathBuf {
        let src = base.join("src");
        fs::create_dir_all(src.join("a/b")).unwrap();
        fs::write(src.join("top.txt"), "top").unwrap();
        fs::write(src.join("a/b/deep.txt"), "deep").unwrap();
        src
    }

    #[test]
    fn create_file_and_dir() {
        let (_d, base) = tmp();
        let file = base.join("f.txt");
        create_file(&file).unwrap();
        assert!(matches!(create_file(&file), Err(LunarError::AlreadyExists)));

        assert!(matches!(
            create_dir(&base.join("x/y"), false),
            Err(LunarError::NotFound)
        ));
        create_dir(&base.join("x/y"), true).unwrap();
        assert!(base.join("x/y").is_dir());
        assert!(matches!(
            create_dir(&base.join("x/y"), true),
            Err(LunarError::AlreadyExists)
        ));
        assert!(matches!(
            create_dir(&base.join("x"), false),
            Err(LunarError::AlreadyExists)
        ));
    }

    #[test]
    fn write_file_creates_and_overwrites() {
        let (_d, base) = tmp();
        let file = base.join("w.txt");
        write_file(&file, b"first version", false).unwrap();
        assert!(matches!(
            write_file(&file, b"x", false),
            Err(LunarError::AlreadyExists)
        ));
        write_file(&file, b"second", true).unwrap();
        assert_eq!(fs::read_to_string(&file).unwrap(), "second");
    }

    #[test]
    fn copy_file_and_folder() {
        let (_d, base) = tmp();
        let src = tree(&base);
        copy(&src.join("top.txt"), &base.join("copy.txt"), false).unwrap();
        assert_eq!(fs::read_to_string(base.join("copy.txt")).unwrap(), "top");

        copy(&src, &base.join("dst"), false).unwrap();
        assert_eq!(
            fs::read_to_string(base.join("dst/a/b/deep.txt")).unwrap(),
            "deep"
        );
        assert!(src.join("a/b/deep.txt").exists());
    }

    #[test]
    fn copy_overwrite_replaces_folder() {
        let (_d, base) = tmp();
        let src = tree(&base);
        let dst = base.join("dst");
        fs::create_dir(&dst).unwrap();
        fs::write(dst.join("stale.txt"), "old").unwrap();

        assert!(matches!(
            copy(&src, &dst, false),
            Err(LunarError::AlreadyExists)
        ));
        copy(&src, &dst, true).unwrap();
        assert!(!dst.join("stale.txt").exists());
        assert!(dst.join("top.txt").exists());
    }

    #[test]
    fn copy_rejects_self_nesting() {
        let (_d, base) = tmp();
        let src = tree(&base);
        assert!(matches!(
            copy(&src, &src.join("a/inside"), false),
            Err(LunarError::InvalidOperation(_))
        ));
        assert!(matches!(
            copy(&src, &src, true),
            Err(LunarError::InvalidOperation(_))
        ));
        // Overwriting a parent with its own child would delete the source.
        assert!(matches!(
            copy(&src.join("a"), &src, true),
            Err(LunarError::InvalidOperation(_))
        ));
        assert!(src.join("a/b/deep.txt").exists());
    }

    #[test]
    fn copy_missing_source() {
        let (_d, base) = tmp();
        assert!(matches!(
            copy(&base.join("nope"), &base.join("x"), false),
            Err(LunarError::NotFound)
        ));
    }

    #[cfg(unix)]
    #[test]
    fn copy_keeps_symlinks_as_links() {
        let (_d, base) = tmp();
        let src = tree(&base);
        std::os::unix::fs::symlink("/etc/passwd", src.join("link")).unwrap();
        copy(&src, &base.join("dst"), false).unwrap();
        let copied = base.join("dst/link");
        assert!(fs::symlink_metadata(&copied).unwrap().is_symlink());
        assert_eq!(
            fs::read_link(&copied).unwrap(),
            PathBuf::from("/etc/passwd")
        );
    }

    #[test]
    fn move_and_rename() {
        let (_d, base) = tmp();
        let src = tree(&base);
        move_entry(&src.join("top.txt"), &base.join("moved.txt"), false).unwrap();
        assert!(!src.join("top.txt").exists());
        assert!(base.join("moved.txt").exists());

        move_entry(&src, &base.join("renamed"), false).unwrap();
        assert!(base.join("renamed/a/b/deep.txt").exists());
        assert!(!src.exists());

        // Same path is a no-op.
        move_entry(&base.join("moved.txt"), &base.join("moved.txt"), false).unwrap();
        assert!(base.join("moved.txt").exists());
    }

    #[test]
    fn move_respects_overwrite_and_nesting() {
        let (_d, base) = tmp();
        let src = tree(&base);
        fs::write(base.join("other.txt"), "other").unwrap();
        assert!(matches!(
            move_entry(&base.join("other.txt"), &src.join("top.txt"), false),
            Err(LunarError::AlreadyExists)
        ));
        move_entry(&base.join("other.txt"), &src.join("top.txt"), true).unwrap();
        assert_eq!(fs::read_to_string(src.join("top.txt")).unwrap(), "other");
        assert!(matches!(
            move_entry(&src, &src.join("a/x"), false),
            Err(LunarError::InvalidOperation(_))
        ));
    }

    #[test]
    fn delete_requires_recursive_for_non_empty_folder() {
        let (_d, base) = tmp();
        let src = tree(&base);
        assert!(matches!(
            delete(&src, true, false),
            Err(LunarError::DirectoryNotEmpty)
        ));
        fs::create_dir(base.join("empty")).unwrap();
        delete(&base.join("empty"), true, false).unwrap();
        delete(&src.join("top.txt"), true, false).unwrap();
        delete(&src, true, true).unwrap();
        assert!(!src.exists());
        assert!(matches!(
            delete(&src, true, true),
            Err(LunarError::NotFound)
        ));
    }

    #[cfg(unix)]
    #[test]
    fn delete_symlink_keeps_target() {
        let (_d, base) = tmp();
        let src = tree(&base);
        let link = base.join("link");
        std::os::unix::fs::symlink(&src, &link).unwrap();
        delete(&link, true, false).unwrap();
        assert!(!exists_no_follow(&link));
        assert!(src.join("top.txt").exists());
    }
}
