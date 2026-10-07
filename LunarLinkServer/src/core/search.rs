//! Recursive name search below a folder.

use crate::core::entry::{entry_for, has_hidden_attribute, Entry};
use crate::core::error::{LunarError, LunarResult};
use crate::core::list::natural_cmp;
use serde::Serialize;
use std::collections::VecDeque;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};

pub const DEFAULT_LIMIT: usize = 200;
pub const MAX_LIMIT: usize = 1000;

#[derive(Debug, Clone)]
pub struct SearchOptions {
    pub limit: usize,
    pub show_hidden: bool,
    /// Searching stops (with `truncated: true`) once this much time has passed.
    pub time_budget: Duration,
}

impl Default for SearchOptions {
    fn default() -> Self {
        Self {
            limit: DEFAULT_LIMIT,
            show_hidden: true,
            time_budget: Duration::from_secs(10),
        }
    }
}

#[derive(Debug, Serialize)]
pub struct SearchResult {
    pub items: Vec<Entry>,
    /// `true` when the search stopped early (limit or time budget), so more matches may exist.
    pub truncated: bool,
}

/// Finds entries below `dir` whose name contains `query` (case-insensitive).
///
/// The walk is breadth-first, so matches closest to `dir` come first and a capped result still
/// holds the nearest hits. Symlinks are listed but never followed. `can_enter` is asked before
/// descending into each subfolder, so callers can keep the walk inside folders the user may read.
/// Setting `cancel` stops the walk at the next entry.
pub fn search(
    dir: &Path,
    query: &str,
    options: &SearchOptions,
    cancel: &AtomicBool,
    can_enter: impl Fn(&Path) -> bool,
) -> LunarResult<SearchResult> {
    let needle = query.trim().to_lowercase();
    if needle.is_empty() {
        return Err(LunarError::BadRequest("The search query is empty.".into()));
    }
    if !fs::metadata(dir)?.is_dir() {
        return Err(LunarError::NotADirectory);
    }

    let deadline = Instant::now() + options.time_budget;
    let mut items = Vec::new();
    let mut queue = VecDeque::from([dir.to_path_buf()]);
    while let Some(folder) = queue.pop_front() {
        let mut children: Vec<(String, PathBuf, bool)> = match fs::read_dir(&folder) {
            Ok(read) => read
                .filter_map(|e| e.ok())
                .filter_map(|e| {
                    let name = e.file_name().to_string_lossy().into_owned();
                    // DirEntry metadata does not follow symlinks.
                    let meta = e.metadata().ok()?;
                    if !options.show_hidden
                        && (name.starts_with('.') || has_hidden_attribute(&meta))
                    {
                        return None;
                    }
                    Some((name, e.path(), meta.is_dir()))
                })
                .collect(),
            Err(e) => {
                tracing::debug!(error = %e, folder = %folder.display(), "search: skipping unreadable folder");
                continue;
            }
        };
        children.sort_by(|a, b| natural_cmp(&a.0, &b.0));

        for (name, path, is_dir) in children {
            if cancel.load(Ordering::Relaxed) || Instant::now() >= deadline {
                return Ok(SearchResult {
                    items,
                    truncated: true,
                });
            }
            if name.to_lowercase().contains(&needle) {
                if items.len() == options.limit {
                    return Ok(SearchResult {
                        items,
                        truncated: true,
                    });
                }
                if let Ok(entry) = entry_for(&path) {
                    items.push(entry);
                }
            }
            if is_dir && can_enter(&path) {
                queue.push_back(path);
            }
        }
    }
    Ok(SearchResult {
        items,
        truncated: false,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tree() -> (tempfile::TempDir, PathBuf) {
        let dir = tempfile::tempdir().unwrap();
        let base = dunce::canonicalize(dir.path()).unwrap();
        fs::create_dir_all(base.join("a/b/c")).unwrap();
        fs::create_dir_all(base.join(".secret")).unwrap();
        fs::write(base.join("Report.txt"), "").unwrap();
        fs::write(base.join("a/report-2.txt"), "").unwrap();
        fs::write(base.join("a/b/c/deep-report.md"), "").unwrap();
        fs::write(base.join(".secret/report.key"), "").unwrap();
        fs::write(base.join("other.txt"), "").unwrap();
        (dir, base)
    }

    fn names(result: &SearchResult) -> Vec<&str> {
        result.items.iter().map(|e| e.name.as_str()).collect()
    }

    fn run(base: &Path, query: &str, options: &SearchOptions) -> SearchResult {
        search(base, query, options, &AtomicBool::new(false), |_| true).unwrap()
    }

    #[test]
    fn finds_matches_breadth_first_case_insensitive() {
        let (_d, base) = tree();
        let result = run(&base, "REPORT", &SearchOptions::default());
        assert_eq!(
            names(&result),
            ["Report.txt", "report.key", "report-2.txt", "deep-report.md"]
        );
        assert!(!result.truncated);
    }

    #[test]
    fn hidden_entries_and_folders_can_be_skipped() {
        let (_d, base) = tree();
        let options = SearchOptions {
            show_hidden: false,
            ..Default::default()
        };
        assert_eq!(
            names(&run(&base, "report", &options)),
            ["Report.txt", "report-2.txt", "deep-report.md"]
        );
    }

    #[test]
    fn limit_truncates() {
        let (_d, base) = tree();
        let options = SearchOptions {
            limit: 2,
            ..Default::default()
        };
        let result = run(&base, "report", &options);
        assert_eq!(result.items.len(), 2);
        assert!(result.truncated);
    }

    #[test]
    fn can_enter_and_cancel_stop_the_walk() {
        let (_d, base) = tree();
        let blocked = base.join("a");
        let result = search(
            &base,
            "report",
            &SearchOptions::default(),
            &AtomicBool::new(false),
            |p| p != blocked,
        )
        .unwrap();
        assert_eq!(names(&result), ["Report.txt", "report.key"]);

        let cancelled = search(
            &base,
            "report",
            &SearchOptions::default(),
            &AtomicBool::new(true),
            |_| true,
        )
        .unwrap();
        assert!(cancelled.items.is_empty());
        assert!(cancelled.truncated);
    }

    #[test]
    fn rejects_empty_query_and_files() {
        let (_d, base) = tree();
        let never = AtomicBool::new(false);
        assert!(matches!(
            search(&base, "  ", &SearchOptions::default(), &never, |_| true),
            Err(LunarError::BadRequest(_))
        ));
        assert!(matches!(
            search(
                &base.join("other.txt"),
                "x",
                &SearchOptions::default(),
                &never,
                |_| true
            ),
            Err(LunarError::NotADirectory)
        ));
    }

    #[cfg(unix)]
    #[test]
    fn symlinked_folders_are_not_followed() {
        let (_d, base) = tree();
        std::os::unix::fs::symlink(base.join("a"), base.join("link-to-a")).unwrap();
        let result = run(&base, "report-2", &SearchOptions::default());
        assert_eq!(names(&result), ["report-2.txt"]);
    }
}
