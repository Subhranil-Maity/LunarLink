use crate::core::types::drive::Drives;
use crate::core::types::LunarMetadata;
use serde::{Deserialize, Serialize};

#[derive(Deserialize, Serialize, Debug, Clone)]
pub struct MountPoints {
    pub drives: Drives,
    pub mounts: Vec<LunarMetadata>,
}
