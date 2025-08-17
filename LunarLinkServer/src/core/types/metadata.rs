use crate::core::types::EntryType;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LunarMetadata {
    pub name: String,                     // base name, e.g. "foo.txt"
    pub size: u64,                        // file size in bytes
    pub entry_type: EntryType,            // file, dir, symlink, etc.
    pub symlink_location: Option<String>, // target if symlink
    pub absolute_path: String,            // full path on disk
    pub last_modified: u64,               // useful for sorting/sync
    pub mime_type: Option<String>,        // optional hint for clients
}
