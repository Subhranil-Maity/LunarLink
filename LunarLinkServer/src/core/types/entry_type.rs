use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum EntryType {
    File,
    Directory,
    Symlink,
    Drive,
    Other, // pipes, sockets, etc.
}
