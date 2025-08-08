use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
pub struct LunarAuth {
    pub enable_auth: bool,
    pub token_expiry_minutes: u16, // in minutes
}

impl Default for LunarAuth {
    fn default() -> Self {
        Self {
            enable_auth: true, // Default to enabled for security
            token_expiry_minutes: 120, // Default token expiry time
        }
    }
}