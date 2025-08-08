use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
pub struct LunarMounts{
    pub show_default_mounts: bool,
    pub custom_mounts: Vec<CustomMounts>,
}

#[derive(Debug, Deserialize, Serialize, Default)]
pub struct CustomMounts{
    pub name: String,
    pub path: String,
    pub mount_permissions: Vec<MountPermissions>,
}
#[derive(Debug, Deserialize, Serialize)]
pub enum MountPermissions{
    Read,
    Write,
    Delete,
    Execute,
}
impl Default for LunarMounts {
    fn default() -> Self {
        let default_mount = CustomMounts {
            name: "Server Source".to_string(),
            path: "D:\\code\\LunarLink\\LunarLinkServer".to_string(),
            mount_permissions: vec![],
        };
        Self {
            show_default_mounts: true,
            custom_mounts: vec![default_mount],
        }
    }
}

impl Default for MountPermissions {
    fn default() -> Self {
        MountPermissions::Read
    }
}
