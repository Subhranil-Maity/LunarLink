use crate::core::types::LunarMetadata;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct FolderData {
    pub metadata: LunarMetadata,     // always comes with metadata
    pub entries: Vec<LunarMetadata>, // contents of the folder
}
