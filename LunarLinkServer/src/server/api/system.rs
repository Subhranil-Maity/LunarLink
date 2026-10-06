use serde::{Deserialize, Serialize};
use sysinfo::System;

#[derive(Debug, Serialize, Deserialize)]
pub struct PersonalDevice {
    device_name: String,
    os_version: String,
    os_type: String,
}

impl PersonalDevice {
    pub fn new(device_name: &str, os_version: &str, os_type: &str) -> Self {
        Self {
            device_name: device_name.to_string(),
            os_version: os_version.to_string(),
            os_type: os_type.to_string(),
        }
    }

    /// Detects device info (basic example, platform dependent)
    pub fn detect() -> Self {
        // OS type
        let os_type = std::env::consts::OS.to_string(); // "windows", "linux", "macos", etc.
        let os_version = System::os_version().unwrap_or("Unknown".to_string());
        // let device_name = System::name().unwrap_or("Unknown".to_string());
        let device_name = System::host_name().unwrap_or("Unknown".to_string());

        Self {
            device_name,
            os_version,
            os_type,
        }
    }
}
