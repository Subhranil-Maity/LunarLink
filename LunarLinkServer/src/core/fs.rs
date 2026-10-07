//! Synchronous filesystem operations. Callers run them on a blocking thread and pass
//! paths that were already resolved by [`crate::core::path::RootTable::resolve`].

use crate::core::error::{LunarError, LunarResult};
use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};

/// Files at least this big are copied in chunks so progress and cancellation work mid-file.
/// Smaller files use `fs::copy`, which can use fast OS paths (reflinks, `copy_file_range`).
pub(crate) const CHUNKED_COPY_MIN: u64 = 64 * 1024 * 1024;
const CHUNK: usize = 1024 * 1024;

/// Follows a long copy or move, and can stop it.
pub trait Tracker: Sync {
    /// `false` skips progress reporting entirely, so plain copies keep the fastest OS paths.
    fn wants_progress(&self) -> bool {
        true
    }
    fn add_bytes(&self, _n: u64) {}
    fn file_started(&self, _name: &str) {}
    fn file_done(&self) {}
    fn is_cancelled(&self) -> bool {
        false
    }
}

/// For callers that don't need progress.
pub struct NoTracker;

impl Tracker for NoTracker {
    fn wants_progress(&self) -> bool {
        false
    }
}

fn check_cancelled(tracker: &dyn Tracker) -> LunarResult<()> {
    if tracker.is_cancelled() {
        Err(LunarError::Cancelled)
    } else {
        Ok(())
    }
}

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
    copy_any(from, to, &NoTracker)
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
            copy_any(from, to, &NoTracker)?;
            remove_any(from)
        }
        Err(e) => Err(e.into()),
    }
}

/// The checks a copy or move does before touching anything, so they can be reported right away
/// when the work itself runs in the background.
pub fn check_transfer_start(from: &Path, to: &Path, overwrite: bool) -> LunarResult<()> {
    if from == to {
        // Moving a path onto itself is a no-op; copying onto itself is rejected below.
        fs::symlink_metadata(from)?;
    } else {
        check_transfer(from, to)?;
    }
    if !overwrite && from != to && exists_no_follow(to) {
        return Err(LunarError::AlreadyExists);
    }
    Ok(())
}

/// Total bytes of files and the number of files below `path` (or of `path` itself). Symlinks count
/// as files of size 0 because they are copied as links.
pub fn measure(path: &Path, tracker: &dyn Tracker) -> LunarResult<(u64, u64)> {
    let (mut bytes, mut files) = (0, 0);
    for entry in walkdir::WalkDir::new(path).follow_links(false) {
        check_cancelled(tracker)?;
        let Ok(entry) = entry else { continue };
        if entry.file_type().is_dir() {
            continue;
        }
        files += 1;
        if entry.file_type().is_file() {
            bytes += entry.metadata().map(|m| m.len()).unwrap_or(0);
        }
    }
    Ok((bytes, files))
}

/// Like [`copy`], with progress and cancellation. The copy is written next to `to` under a temporary
/// name and only renamed into place when complete, so a cancelled or failed copy leaves no partial
/// result, and with `overwrite` the old target survives until the new one is ready.
pub fn copy_tracked(
    from: &Path,
    to: &Path,
    overwrite: bool,
    tracker: &dyn Tracker,
) -> LunarResult<()> {
    check_transfer_start(from, to, overwrite)?;
    if from == to {
        return Err(LunarError::InvalidOperation(
            "The source and destination are the same.",
        ));
    }
    let temp = temp_sibling(to)?;
    if let Err(e) = copy_any(from, &temp, tracker) {
        let _ = remove_any(&temp);
        return Err(e);
    }
    replace_with(&temp, to, overwrite)
}

/// Like [`move_entry`], with progress and cancellation. A move within one device is a rename and
/// finishes at once. Across devices the data is copied to a temporary name next to `to`, put in
/// place, and only then is the source removed.
pub fn move_tracked(
    from: &Path,
    to: &Path,
    overwrite: bool,
    tracker: &dyn Tracker,
) -> LunarResult<()> {
    check_transfer_start(from, to, overwrite)?;
    if from == to {
        return Ok(());
    }
    let temp = temp_sibling(to)?;
    match fs::rename(from, &temp) {
        Ok(()) => {
            let (bytes, files) = measure(&temp, &NoTracker)?;
            tracker.add_bytes(bytes);
            for _ in 0..files {
                tracker.file_done();
            }
            if let Err(e) = replace_with(&temp, to, overwrite) {
                // Put the source back where it was.
                let _ = fs::rename(&temp, from);
                return Err(e);
            }
            Ok(())
        }
        Err(e) if e.kind() == io::ErrorKind::CrossesDevices => {
            if let Err(e) = copy_any(from, &temp, tracker) {
                let _ = remove_any(&temp);
                return Err(e);
            }
            replace_with(&temp, to, overwrite)?;
            remove_any(from)
        }
        Err(e) => Err(e.into()),
    }
}

/// A free hidden name in `path`'s folder, e.g. `.photo.jpg.lunarpart-1a2b3c4d`.
fn temp_sibling(path: &Path) -> LunarResult<PathBuf> {
    let name = path
        .file_name()
        .ok_or(LunarError::InvalidPath)?
        .to_string_lossy();
    loop {
        let candidate =
            path.with_file_name(format!(".{name}.lunarpart-{:08x}", rand::random::<u32>()));
        if !exists_no_follow(&candidate) {
            return Ok(candidate);
        }
    }
}

/// Moves a finished `temp` onto `to`, removing an existing `to` first when `overwrite` allows it.
fn replace_with(temp: &Path, to: &Path, overwrite: bool) -> LunarResult<()> {
    if exists_no_follow(to) {
        if !overwrite {
            // Something appeared at the destination while we were copying.
            let _ = remove_any(temp);
            return Err(LunarError::AlreadyExists);
        }
        if let Err(e) = remove_any(to) {
            let _ = remove_any(temp);
            return Err(e);
        }
    }
    fs::rename(temp, to).map_err(|e| {
        let _ = remove_any(temp);
        e.into()
    })
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

fn copy_any(from: &Path, to: &Path, tracker: &dyn Tracker) -> LunarResult<()> {
    let meta = fs::symlink_metadata(from)?;
    if meta.is_symlink() {
        copy_symlink(from, to)?;
        tracker.file_done();
        Ok(())
    } else if meta.is_dir() {
        copy_dir(from, to, tracker)
    } else {
        copy_file(from, to, meta.len(), tracker)
    }
}

fn copy_file(from: &Path, to: &Path, len: u64, tracker: &dyn Tracker) -> LunarResult<()> {
    check_cancelled(tracker)?;
    if let Some(name) = from.file_name() {
        tracker.file_started(&name.to_string_lossy());
    }
    if !tracker.wants_progress() || len < CHUNKED_COPY_MIN {
        fs::copy(from, to)?;
        tracker.add_bytes(len);
    } else {
        let mut input = File::open(from)?;
        let mut output = File::create(to)?;
        let mut buffer = vec![0; CHUNK];
        loop {
            check_cancelled(tracker)?;
            let n = input.read(&mut buffer)?;
            if n == 0 {
                break;
            }
            output.write_all(&buffer[..n])?;
            tracker.add_bytes(n as u64);
        }
        output.flush()?;
        // Keep the permissions, as fs::copy does.
        fs::set_permissions(to, input.metadata()?.permissions())?;
    }
    tracker.file_done();
    Ok(())
}

fn copy_dir(from: &Path, to: &Path, tracker: &dyn Tracker) -> LunarResult<()> {
    for entry in walkdir::WalkDir::new(from).follow_links(false) {
        check_cancelled(tracker)?;
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
            tracker.file_done();
        } else if file_type.is_dir() {
            fs::create_dir(&target)?;
        } else {
            let len = entry.metadata().map(|m| m.len()).unwrap_or(0);
            copy_file(entry.path(), &target, len, tracker)?;
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
