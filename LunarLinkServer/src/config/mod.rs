
mod lunar_server;

use serde::{Deserialize, Serialize};
pub use lunar_server::*;
mod lunar_auth;
pub use lunar_auth::*;
mod lunar_mounts;
pub use lunar_mounts::*;
mod lunar_experimental;
pub use lunar_experimental::*;
mod lunar_users;
pub use lunar_users::*;

pub mod read_write;

#[derive(Debug, Deserialize, Serialize, Default)]
pub struct LunarConfig{
    server: LunarServerConfig,
    auth: LunarAuth,
    users: LunarUsersConfig,
    mounts: LunarMounts,
    experiments: LunarExperimental
}




