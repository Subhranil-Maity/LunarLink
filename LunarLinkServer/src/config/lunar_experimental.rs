use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
pub struct LunarExperimental {
    pub enable_execution_features: bool,
}

impl Default for LunarExperimental {
    fn default() -> Self {
        Self {
            enable_execution_features: false, // Default to disabled
        }
    }
}