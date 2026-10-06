//! Turns client-supplied paths into canonical absolute paths and maps them to a root.
//!
//! Every filesystem handler must get its paths from [`RootTable::resolve`]; nothing else
//! should build a `PathBuf` from request input.
//!
//! Known limitation: a symlink could be swapped between resolving a path and using it.
//! Closing that race fully needs `openat`-style APIs and is out of scope.

use crate::config::RootsConfig;
use crate::core::disks::DiskInfo;
use crate::core::error::{LunarError, LunarResult};
use crate::core::permission::Permissions;
use serde::Serialize;
use std::io;
use std::path::{Component, Path, PathBuf};
use std::sync::Arc;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum RootKind {
    Mount,
    Drive,
}

#[derive(Debug)]
pub struct Root {
    pub name: String,
    /// Canonical absolute path.
    pub path: PathBuf,
    pub kind: RootKind,
    pub permissions: Permissions,
}

/// How the final path component is treated.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Leaf {
    /// Resolve symlinks in the whole path (reading, listing, downloading).
    Follow,
    /// Resolve symlinks in the parent only, so the operation acts on a link itself
    /// (delete, move, stat without `follow`).
    NoFollow,
}

#[derive(Debug, Clone)]
pub struct ResolvedPath {
    /// Canonical absolute path.
    pub path: PathBuf,
    pub root: Arc<Root>,
}

impl ResolvedPath {
    pub fn is_root(&self) -> bool {
        self.path == self.root.path
    }
}

#[derive(Debug, Default)]
pub struct RootTable {
    roots: Vec<Arc<Root>>,
}

impl RootTable {
    /// `roots` must already have canonical paths.
    pub fn new(roots: Vec<Root>) -> Self {
        Self {
            roots: roots.into_iter().map(Arc::new).collect(),
        }
    }

    /// Builds the table from config. Mounts that don't exist are skipped with a warning.
    pub fn from_config(config: &RootsConfig, disks: &[DiskInfo]) -> Self {
        let mut roots: Vec<Root> = Vec::new();
        for mount in &config.mounts {
            match dunce::canonicalize(&mount.path) {
                Ok(path) if path.is_dir() => roots.push(Root {
                    name: mount.name.clone(),
                    path,
                    kind: RootKind::Mount,
                    permissions: mount.permissions,
                }),
                Ok(_) => {
                    tracing::warn!(mount = %mount.name, path = %mount.path, "mount is not a folder, skipping")
                }
                Err(e) => {
                    tracing::warn!(mount = %mount.name, path = %mount.path, error = %e, "mount not found, skipping")
                }
            }
        }
        if config.expose_drives {
            for disk in disks {
                let Ok(path) = dunce::canonicalize(&disk.mount_point) else {
                    continue;
                };
                if roots
                    .iter()
                    .any(|r| r.kind == RootKind::Drive && r.path == path)
                {
                    continue;
                }
                roots.push(Root {
                    name: drive_name(disk, &path),
                    path,
                    kind: RootKind::Drive,
                    permissions: config.drive_permissions,
                });
            }
        }
        Self::new(roots)
    }

    pub fn roots(&self) -> &[Arc<Root>] {
        &self.roots
    }

    /// The deepest root containing `path`. Comparison is per component, so
    /// `/home/user2` is not inside `/home/user`.
    pub fn root_for(&self, path: &Path) -> Option<Arc<Root>> {
        self.roots
            .iter()
            .filter(|r| path.starts_with(&r.path))
            .max_by_key(|r| r.path.components().count())
            .cloned()
    }

    pub fn resolve(&self, raw: &str, leaf: Leaf) -> LunarResult<ResolvedPath> {
        let normalized = normalize(raw)?;
        let canonical = match leaf {
            Leaf::Follow => canonicalize_lenient(&normalized),
            Leaf::NoFollow => match (normalized.parent(), normalized.file_name()) {
                (Some(parent), Some(name)) => canonicalize_lenient(parent).map(|p| p.join(name)),
                _ => canonicalize_lenient(&normalized),
            },
        };
        let canonical = match canonical {
            Ok(path) => path,
            // Don't reveal anything about paths outside the roots through OS errors.
            Err(_) if self.root_for(&normalized).is_none() => {
                return Err(LunarError::PathOutsideRoots)
            }
            Err(e) => return Err(e),
        };
        let root = self
            .root_for(&canonical)
            .ok_or(LunarError::PathOutsideRoots)?;
        Ok(ResolvedPath {
            path: canonical,
            root,
        })
    }
}

fn drive_name(disk: &DiskInfo, path: &Path) -> String {
    if disk.name.is_empty() || disk.name.starts_with("/dev/") {
        path.display().to_string()
    } else {
        disk.name.clone()
    }
}

/// Rejects empty, relative or NUL-containing paths and removes `.` and `..` lexically.
fn normalize(raw: &str) -> LunarResult<PathBuf> {
    if raw.is_empty() || raw.contains('\0') {
        return Err(LunarError::InvalidPath);
    }
    let path = Path::new(raw);
    if !path.is_absolute() {
        return Err(LunarError::InvalidPath);
    }
    let mut out = PathBuf::new();
    let mut depth = 0usize;
    for component in path.components() {
        match component {
            Component::Prefix(_) | Component::RootDir => out.push(component.as_os_str()),
            Component::CurDir => {}
            Component::ParentDir => {
                if depth == 0 {
                    return Err(LunarError::InvalidPath);
                }
                out.pop();
                depth -= 1;
            }
            Component::Normal(name) => {
                out.push(name);
                depth += 1;
            }
        }
    }
    Ok(out)
}

/// Canonicalizes the deepest existing ancestor of `path` and re-appends the rest,
/// so paths that don't exist yet (create/copy/move targets) can be resolved.
fn canonicalize_lenient(path: &Path) -> LunarResult<PathBuf> {
    let mut existing = path.to_path_buf();
    let mut missing = Vec::new();
    loop {
        match dunce::canonicalize(&existing) {
            Ok(mut canonical) => {
                for name in missing.iter().rev() {
                    canonical.push(name);
                }
                return Ok(canonical);
            }
            Err(e) if e.kind() == io::ErrorKind::NotFound => {
                let name = existing
                    .file_name()
                    .ok_or(LunarError::InvalidPath)?
                    .to_owned();
                missing.push(name);
                if !existing.pop() {
                    return Err(LunarError::NotFound);
                }
            }
            Err(e) => return Err(e.into()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::permission::Permissions;
    use std::fs;

    struct Fixture {
        _dir: tempfile::TempDir,
        base: PathBuf,
        table: RootTable,
    }

    /// base/
    ///   share/        <- root "share"
    ///     sub/file.txt
    ///     inner/      <- nested root "inner"
    ///   share2/       <- sibling with a shared prefix, not a root
    ///   outside.txt
    fn fixture() -> Fixture {
        let dir = tempfile::tempdir().unwrap();
        let base = dunce::canonicalize(dir.path()).unwrap();
        fs::create_dir_all(base.join("share/sub")).unwrap();
        fs::create_dir_all(base.join("share/inner")).unwrap();
        fs::create_dir_all(base.join("share2")).unwrap();
        fs::write(base.join("share/sub/file.txt"), "x").unwrap();
        fs::write(base.join("outside.txt"), "secret").unwrap();
        let table = RootTable::new(vec![
            Root {
                name: "share".into(),
                path: base.join("share"),
                kind: RootKind::Mount,
                permissions: Permissions::ALL,
            },
            Root {
                name: "inner".into(),
                path: base.join("share/inner"),
                kind: RootKind::Mount,
                permissions: Permissions::READ,
            },
        ]);
        Fixture {
            _dir: dir,
            base,
            table,
        }
    }

    fn s(p: &Path) -> String {
        p.to_string_lossy().into_owned()
    }

    #[test]
    fn normalize_rules() {
        assert!(matches!(normalize(""), Err(LunarError::InvalidPath)));
        assert!(matches!(
            normalize("relative/x"),
            Err(LunarError::InvalidPath)
        ));
        assert!(matches!(normalize("/a\0b"), Err(LunarError::InvalidPath)));
        assert!(matches!(normalize("/.."), Err(LunarError::InvalidPath)));
        assert_eq!(normalize("/a/./b/../c").unwrap(), PathBuf::from("/a/c"));
    }

    #[test]
    fn resolves_inside_root() {
        let f = fixture();
        let r = f
            .table
            .resolve(&s(&f.base.join("share/sub/file.txt")), Leaf::Follow)
            .unwrap();
        assert_eq!(r.path, f.base.join("share/sub/file.txt"));
        assert_eq!(r.root.name, "share");
        assert!(!r.is_root());
        assert!(f
            .table
            .resolve(&s(&f.base.join("share")), Leaf::Follow)
            .unwrap()
            .is_root());
    }

    #[test]
    fn dot_dot_cannot_escape() {
        let f = fixture();
        let raw = format!("{}/share/sub/../../outside.txt", s(&f.base));
        assert!(matches!(
            f.table.resolve(&raw, Leaf::Follow),
            Err(LunarError::PathOutsideRoots)
        ));
        assert!(matches!(
            f.table.resolve("/etc/passwd", Leaf::Follow),
            Err(LunarError::PathOutsideRoots)
        ));
    }

    #[test]
    fn sibling_prefix_is_not_inside() {
        let f = fixture();
        assert!(matches!(
            f.table.resolve(&s(&f.base.join("share2")), Leaf::Follow),
            Err(LunarError::PathOutsideRoots)
        ));
    }

    #[test]
    fn deepest_root_wins() {
        let f = fixture();
        let r = f
            .table
            .resolve(&s(&f.base.join("share/inner/new.txt")), Leaf::NoFollow)
            .unwrap();
        assert_eq!(r.root.name, "inner");
    }

    #[test]
    fn missing_paths_resolve_through_existing_parent() {
        let f = fixture();
        let r = f
            .table
            .resolve(&s(&f.base.join("share/a/b/c")), Leaf::Follow)
            .unwrap();
        assert_eq!(r.path, f.base.join("share/a/b/c"));
    }

    #[test]
    fn errors_outside_roots_do_not_leak() {
        let f = fixture();
        // `outside.txt/child` would be NotADirectory; it must be reported as outside instead.
        let raw = s(&f.base.join("outside.txt/child"));
        assert!(matches!(
            f.table.resolve(&raw, Leaf::Follow),
            Err(LunarError::PathOutsideRoots)
        ));
    }

    #[cfg(unix)]
    #[test]
    fn symlink_escape_is_blocked_but_link_itself_is_addressable() {
        use std::os::unix::fs::symlink;
        let f = fixture();
        symlink(f.base.join("outside.txt"), f.base.join("share/escape")).unwrap();
        symlink(f.base.clone(), f.base.join("share/escape_dir")).unwrap();
        let link = s(&f.base.join("share/escape"));

        assert!(matches!(
            f.table.resolve(&link, Leaf::Follow),
            Err(LunarError::PathOutsideRoots)
        ));
        let through_dir = s(&f.base.join("share/escape_dir/outside.txt"));
        assert!(matches!(
            f.table.resolve(&through_dir, Leaf::Follow),
            Err(LunarError::PathOutsideRoots)
        ));
        let r = f.table.resolve(&link, Leaf::NoFollow).unwrap();
        assert_eq!(r.path, f.base.join("share/escape"));
        assert_eq!(r.root.name, "share");
    }

    #[cfg(unix)]
    #[test]
    fn symlink_inside_root_is_followed() {
        use std::os::unix::fs::symlink;
        let f = fixture();
        symlink(f.base.join("share/sub"), f.base.join("share/sub_link")).unwrap();
        let r = f
            .table
            .resolve(&s(&f.base.join("share/sub_link/file.txt")), Leaf::Follow)
            .unwrap();
        assert_eq!(r.path, f.base.join("share/sub/file.txt"));
    }

    #[test]
    fn from_config_skips_missing_mounts() {
        let f = fixture();
        let config = RootsConfig {
            expose_drives: false,
            drive_permissions: Permissions::READ,
            mounts: vec![
                crate::config::MountConfig {
                    name: "ok".into(),
                    path: s(&f.base.join("share")),
                    permissions: Permissions::ALL,
                },
                crate::config::MountConfig {
                    name: "missing".into(),
                    path: s(&f.base.join("nope")),
                    permissions: Permissions::ALL,
                },
            ],
        };
        let table = RootTable::from_config(&config, &[]);
        assert_eq!(table.roots().len(), 1);
        assert_eq!(table.roots()[0].name, "ok");
    }
}
