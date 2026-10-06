use crate::core::entry::{entry_for, Entry, EntryKind};
use crate::core::error::{LunarError, LunarResult};
use serde::{Deserialize, Serialize};
use std::cmp::Ordering;
use std::fs;
use std::path::Path;

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SortKey {
    #[default]
    Name,
    Modified,
    Size,
    Kind,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SortOrder {
    #[default]
    Asc,
    Desc,
}

#[derive(Debug, Clone)]
pub struct ListOptions {
    pub offset: usize,
    pub limit: Option<usize>,
    pub sort: SortKey,
    pub order: SortOrder,
    pub dirs_first: bool,
    pub show_hidden: bool,
}

impl Default for ListOptions {
    fn default() -> Self {
        Self {
            offset: 0,
            limit: None,
            sort: SortKey::Name,
            order: SortOrder::Asc,
            dirs_first: true,
            show_hidden: true,
        }
    }
}

#[derive(Debug, Serialize, Deserialize)]
pub struct Listing {
    pub entry: Entry,
    pub items: Vec<Entry>,
    pub total: usize,
    pub offset: usize,
    pub limit: Option<usize>,
}

/// Lists a folder. Entries that can't be read are skipped and logged.
pub fn list_dir(path: &Path, options: &ListOptions) -> LunarResult<Listing> {
    let entry = entry_for(path)?;
    if entry.kind != EntryKind::Dir {
        return Err(LunarError::NotADirectory);
    }
    let mut items: Vec<Entry> = Vec::new();
    for dir_entry in fs::read_dir(path)? {
        let child = match dir_entry {
            Ok(d) => d.path(),
            Err(e) => {
                tracing::warn!(error = %e, folder = %path.display(), "skipping unreadable entry");
                continue;
            }
        };
        match entry_for(&child) {
            Ok(e) if options.show_hidden || !e.hidden => items.push(e),
            Ok(_) => {}
            Err(e) => {
                tracing::warn!(error = %e, path = %child.display(), "skipping unreadable entry")
            }
        }
    }
    items.sort_by(|a, b| compare(a, b, options));
    let total = items.len();
    let page: Vec<Entry> = items
        .into_iter()
        .skip(options.offset)
        .take(options.limit.unwrap_or(usize::MAX))
        .collect();
    Ok(Listing {
        entry,
        items: page,
        total,
        offset: options.offset,
        limit: options.limit,
    })
}

fn compare(a: &Entry, b: &Entry, options: &ListOptions) -> Ordering {
    if options.dirs_first {
        let dirs = b.is_dir_like().cmp(&a.is_dir_like());
        if dirs != Ordering::Equal {
            return dirs;
        }
    }
    let by_name = || {
        a.name
            .to_lowercase()
            .cmp(&b.name.to_lowercase())
            .then_with(|| a.name.cmp(&b.name))
    };
    let ordering = match options.sort {
        SortKey::Name => by_name(),
        SortKey::Modified => a.modified.cmp(&b.modified).then_with(by_name),
        SortKey::Size => a.size.cmp(&b.size).then_with(by_name),
        SortKey::Kind => kind_rank(a).cmp(&kind_rank(b)).then_with(by_name),
    };
    match options.order {
        SortOrder::Asc => ordering,
        SortOrder::Desc => ordering.reverse(),
    }
}

fn kind_rank(e: &Entry) -> u8 {
    match e.kind {
        EntryKind::Dir => 0,
        EntryKind::Symlink => 1,
        EntryKind::File => 2,
        EntryKind::Other => 3,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn setup() -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        let p = dir.path();
        fs::write(p.join("b.txt"), "12345").unwrap();
        fs::write(p.join("A.txt"), "1").unwrap();
        fs::write(p.join(".hidden"), "123").unwrap();
        fs::create_dir(p.join("zdir")).unwrap();
        dir
    }

    fn names(l: &Listing) -> Vec<&str> {
        l.items.iter().map(|e| e.name.as_str()).collect()
    }

    #[test]
    fn default_sort_dirs_first_case_insensitive() {
        let dir = setup();
        let l = list_dir(dir.path(), &ListOptions::default()).unwrap();
        assert_eq!(names(&l), ["zdir", ".hidden", "A.txt", "b.txt"]);
        assert_eq!(l.total, 4);
        assert_eq!(l.entry.kind, EntryKind::Dir);
    }

    #[test]
    fn hidden_filter_and_pagination() {
        let dir = setup();
        let options = ListOptions {
            show_hidden: false,
            offset: 1,
            limit: Some(1),
            ..ListOptions::default()
        };
        let l = list_dir(dir.path(), &options).unwrap();
        assert_eq!(l.total, 3);
        assert_eq!(names(&l), ["A.txt"]);
        assert_eq!(l.limit, Some(1));
    }

    #[test]
    fn sort_by_size_desc_without_dirs_first() {
        let dir = setup();
        let options = ListOptions {
            sort: SortKey::Size,
            order: SortOrder::Desc,
            dirs_first: false,
            ..ListOptions::default()
        };
        let l = list_dir(dir.path(), &options).unwrap();
        assert_eq!(names(&l), ["b.txt", ".hidden", "A.txt", "zdir"]);
    }

    #[test]
    fn listing_a_file_fails() {
        let dir = setup();
        assert!(matches!(
            list_dir(&dir.path().join("b.txt"), &ListOptions::default()),
            Err(LunarError::NotADirectory)
        ));
    }
}
