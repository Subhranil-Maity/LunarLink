use serde::{Deserialize, Serialize};
use crate::core::types::LunarMetadata;

#[derive(Debug, Serialize, Deserialize)]
pub struct FolderData {
    pub metadata: LunarMetadata, // always comes with metadata
    pub entries: Vec<LunarMetadata>, // contents of the folder
}
