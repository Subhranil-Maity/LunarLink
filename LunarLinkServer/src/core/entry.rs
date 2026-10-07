use serde::{Deserialize, Serialize};
use std::fs::{self, FileType, Metadata};
use std::io;
use std::path::Path;
use std::time::UNIX_EPOCH;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum EntryKind {
    File,
    Dir,
    Symlink,
    Other,
}

impl EntryKind {
    fn of(file_type: &FileType) -> Self {
        if file_type.is_symlink() {
            Self::Symlink
        } else if file_type.is_dir() {
            Self::Dir
        } else if file_type.is_file() {
            Self::File
        } else {
            Self::Other
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Entry {
    pub name: String,
    pub path: String,
    pub kind: EntryKind,
    pub size: u64,
    pub modified: Option<u64>,
    pub mime_type: Option<String>,
    pub hidden: bool,
    pub symlink_target: Option<String>,
    pub target_kind: Option<EntryKind>,
}

impl Entry {
    /// A folder, or a symlink that points to a folder.
    pub fn is_dir_like(&self) -> bool {
        self.kind == EntryKind::Dir || self.target_kind == Some(EntryKind::Dir)
    }
}

/// Describes `path` itself. Symlinks are not followed, but their target is reported.
pub fn entry_for(path: &Path) -> io::Result<Entry> {
    let meta = fs::symlink_metadata(path)?;
    let kind = EntryKind::of(&meta.file_type());
    let (symlink_target, target_kind) = if kind == EntryKind::Symlink {
        (
            fs::read_link(path)
                .ok()
                .map(|t| t.to_string_lossy().into_owned()),
            fs::metadata(path)
                .ok()
                .map(|m| EntryKind::of(&m.file_type())),
        )
    } else {
        (None, None)
    };
    let name = path
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| path.to_string_lossy().into_owned());
    let mime_type = if kind == EntryKind::File || target_kind == Some(EntryKind::File) {
        mime_guess::from_path(path).first_raw().map(str::to_string)
    } else {
        None
    };
    Ok(Entry {
        hidden: name.starts_with('.') || has_hidden_attribute(&meta),
        name,
        path: path.to_string_lossy().into_owned(),
        kind,
        size: if kind == EntryKind::File {
            meta.len()
        } else {
            0
        },
        modified: meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map(|d| d.as_secs()),
        mime_type,
        symlink_target,
        target_kind,
    })
}

#[cfg(windows)]
pub(crate) fn has_hidden_attribute(meta: &Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_HIDDEN: u32 = 0x2;
    meta.file_attributes() & FILE_ATTRIBUTE_HIDDEN != 0
}

#[cfg(not(windows))]
pub(crate) fn has_hidden_attribute(_meta: &Metadata) -> bool {
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn describes_files_and_dirs() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("notes.txt");
        fs::write(&file, b"hello").unwrap();
        fs::create_dir(dir.path().join(".hidden")).unwrap();

        let e = entry_for(&file).unwrap();
        assert_eq!(e.kind, EntryKind::File);
        assert_eq!(e.size, 5);
        assert_eq!(e.name, "notes.txt");
        assert_eq!(e.mime_type.as_deref(), Some("text/plain"));
        assert!(!e.hidden);
        assert!(e.modified.is_some());

        let d = entry_for(&dir.path().join(".hidden")).unwrap();
        assert_eq!(d.kind, EntryKind::Dir);
        assert_eq!(d.size, 0);
        assert!(d.hidden);
        assert!(d.mime_type.is_none());
    }

    #[cfg(unix)]
    #[test]
    fn describes_symlinks() {
        let dir = tempfile::tempdir().unwrap();
        std::os::unix::fs::symlink(dir.path(), dir.path().join("to_dir")).unwrap();
        std::os::unix::fs::symlink("/nope/missing", dir.path().join("broken")).unwrap();

        let link = entry_for(&dir.path().join("to_dir")).unwrap();
        assert_eq!(link.kind, EntryKind::Symlink);
        assert_eq!(link.target_kind, Some(EntryKind::Dir));
        assert!(link.is_dir_like());

        let broken = entry_for(&dir.path().join("broken")).unwrap();
        assert_eq!(broken.symlink_target.as_deref(), Some("/nope/missing"));
        assert_eq!(broken.target_kind, None);
    }
}
