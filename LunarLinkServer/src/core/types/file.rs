use crate::core::types::LunarMetadata;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct FileData {
    pub metadata: LunarMetadata, // always comes with metadata
    pub data: Vec<u8>,           // actual file bytes
}
