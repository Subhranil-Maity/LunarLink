use serde::{Deserialize, Serialize};
use sysinfo::{DiskKind, Disks};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Drive {
    pub name: String,
    pub used_size: usize,
    pub total_size: usize,
    pub disk_type: DriveType,
    pub mount_point: String,
    pub fs_type: String,
}

pub type Drives = Vec<Drive>;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum DriveType {
    HDD,
    SSD,
    NVME,
    REMOVABLE,
    UNKNOWN,
}

impl Drive {
    pub fn new(
        name: String,
        used_size: usize,
        total_size: usize,
        disk_type: DriveType,
        mount_point: String,
        fs_type: String,
    ) -> Self {
        Self {
            name,
            used_size,
            total_size,
            disk_type,
            mount_point,
            fs_type,
        }
    }
}

pub fn get_drives() -> Drives {
    let disk = Disks::new_with_refreshed_list();
    return disk
        .iter()
        .map(|d| {
            let disk_type = if d.is_removable() {
                DriveType::REMOVABLE
            } else {
                match d.kind() {
                    DiskKind::HDD => DriveType::HDD,
                    DiskKind::SSD => DriveType::SSD,
                    DiskKind::Unknown(_) => DriveType::UNKNOWN,
                }
            };
            Drive::new(
                d.name().to_string_lossy().into_owned(),
                (d.total_space() - d.available_space()) as usize,
                d.total_space() as usize,
                disk_type,
                d.mount_point().to_string_lossy().into_owned(),
                d.file_system().to_string_lossy().into_owned(),
            )
        })
        .collect();
}
