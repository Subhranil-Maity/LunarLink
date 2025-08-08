use std::fs::Permissions;
use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
pub struct LunarUsersConfig {
    pub users: Vec<LunarUser>,
}

#[derive(Debug, Deserialize, Serialize, Default)]
pub struct LunarUser {
    pub username: String,
    pub password: String,
    pub permissions: Vec<UserPermissions>,
}

#[derive(Debug, Deserialize, Serialize)]
pub enum UserPermissions {
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