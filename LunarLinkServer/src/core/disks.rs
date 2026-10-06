use serde::Serialize;
use std::path::{Path, PathBuf};
use sysinfo::{DiskKind as SysDiskKind, Disks};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum DiskKind {
    Hdd,
    Ssd,
    Unknown,
}

#[derive(Debug, Clone, Serialize)]
pub struct DiskInfo {
    pub name: String,
    pub mount_point: PathBuf,
    pub fs_type: String,
    pub disk_kind: DiskKind,
    pub removable: bool,
    pub total_bytes: u64,
    pub available_bytes: u64,
}

pub fn list_disks() -> Vec<DiskInfo> {
    Disks::new_with_refreshed_list()
        .iter()
        .map(|d| DiskInfo {
            name: d.name().to_string_lossy().into_owned(),
            mount_point: d.mount_point().to_path_buf(),
            fs_type: d.file_system().to_string_lossy().into_owned(),
            disk_kind: match d.kind() {
                SysDiskKind::HDD => DiskKind::Hdd,
                SysDiskKind::SSD => DiskKind::Ssd,
                SysDiskKind::Unknown(_) => DiskKind::Unknown,
            },
            removable: d.is_removable(),
            total_bytes: d.total_space(),
            available_bytes: d.available_space(),
        })
        .collect()
}

/// The disk whose mount point is the longest prefix of `path`.
pub fn disk_for<'a>(path: &Path, disks: &'a [DiskInfo]) -> Option<&'a DiskInfo> {
    disks
        .iter()
        .filter(|d| path.starts_with(&d.mount_point))
        .max_by_key(|d| d.mount_point.components().count())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn disk(mount: &str) -> DiskInfo {
        DiskInfo {
            name: mount.into(),
            mount_point: mount.into(),
            fs_type: "ext4".into(),
            disk_kind: DiskKind::Ssd,
            removable: false,
            total_bytes: 0,
            available_bytes: 0,
        }
    }

    #[test]
    fn picks_deepest_mount_point() {
        let disks = [disk("/"), disk("/home"), disk("/home2")];
        assert_eq!(
            disk_for(Path::new("/home/me/x"), &disks).unwrap().name,
            "/home"
        );
        assert_eq!(disk_for(Path::new("/etc"), &disks).unwrap().name, "/");
        assert_eq!(
            disk_for(Path::new("/home2"), &disks).unwrap().name,
            "/home2"
        );
    }
}
