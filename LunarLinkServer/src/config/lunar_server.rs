use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct LunarServerConfig {
    pub port: u16,
    pub host: String,
    pub max_connections: usize,
    pub enable_https: bool,
    pub cert_path: Option<String>,
    pub key_path: Option<String>,
}
impl Default for LunarServerConfig {
    fn default() -> Self {
        Self {
            port: 8080,
            host: "127.0.0.1".to_string(),
            max_connections: 100,
            enable_https: false,
            cert_path: None,
            key_path: None,
        }
    }
}
