// This file is part of the `core` module, which provides filesystem operations.
// It includes submodules for reading, creating, writing, copying/moving,
mod read;
pub use read::*;
pub mod create;
pub use create::*;
mod write;
pub use write::*;
pub mod copy_move;
pub use copy_move::*;
mod delete;
pub use delete::*;

pub mod metadata;
pub use metadata::*;
