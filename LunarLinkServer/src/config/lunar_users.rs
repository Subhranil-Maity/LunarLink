use std::fs::Permissions;
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

#[derive(Debug, Deserialize, Serialize, Clone)]
pub enum UserPermission {
    Read,
    Write,
    Delete,
    Execute,
}

impl Default for LunarUsersConfig {
    fn default() -> Self {
        let default_user = LunarUser{
            username: "default".to_string(),
            password: "default".to_string(),
            permissions: vec![],
        };
        Self {
            users: vec![default_user],
        }
    }
}