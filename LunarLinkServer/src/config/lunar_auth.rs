use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct LunarAuth {
    pub enable_auth: bool,
    pub token_expiry_minutes: u16, // in minutes
    pub jwt_secret: String,        // Optional JWT secret for token generation
}

impl Default for LunarAuth {
    fn default() -> Self {
        Self {
            enable_auth: true,         // Default to enabled for security
            token_expiry_minutes: 120, // Default token expiry time
            jwt_secret: crate::server::utils::generate_random_string(256),
        }
    }
}
