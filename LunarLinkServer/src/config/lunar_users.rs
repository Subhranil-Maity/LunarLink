use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct LunarUsersConfig {
    pub users: Vec<LunarUser>,
}

pub type UserPermissions = Vec<UserPermission>;
#[derive(Debug, Deserialize, Serialize, Default, Clone)]
pub struct LunarUser {
    pub username: String,
    pub password: String,
    pub permissions: UserPermissions,
}

#[derive(Debug, Deserialize, Serialize, Clone, PartialEq, Hash, Eq)]
pub enum UserPermission {
    Read,
    Write,
    Delete,
    Execute,
}

impl Default for LunarUsersConfig {
    fn default() -> Self {
        let power_default_user = LunarUser {
            username: "power".to_string(),
            password: "power".to_string(),
            permissions: vec![
                UserPermission::Read,
                UserPermission::Write,
                UserPermission::Delete,
            ],
        };
        let default_user = LunarUser {
            username: "default".to_string(),
            password: "default".to_string(),
            permissions: vec![],
        };
        Self {
            users: vec![default_user, power_default_user],
        }
    }
}
