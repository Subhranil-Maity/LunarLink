mod expand;

pub use expand::expand_path;

use crate::core::permission::Permissions;
use anyhow::{bail, Context};
use rand::distr::Alphanumeric;
use rand::Rng;
use serde::Deserialize;
use std::collections::HashSet;
use std::path::Path;

const MIN_SECRET_LEN: usize = 32;

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LunarConfig {
    #[serde(default)]
    pub server: ServerConfig,
    pub auth: AuthConfig,
    #[serde(default)]
    pub users: Vec<UserConfig>,
    #[serde(default)]
    pub roots: RootsConfig,
    #[serde(default)]
    pub thumbnails: ThumbnailsConfig,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
    pub max_concurrent_requests: usize,
    pub display_name: Option<String>,
    pub show_qr: bool,
    /// Announce the server on the local network so apps can find it.
    pub discoverable: bool,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            host: "0.0.0.0".into(),
            port: 1234,
            max_concurrent_requests: 100,
            display_name: None,
            show_qr: false,
            discoverable: true,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AuthConfig {
    #[serde(default = "default_true")]
    pub enabled: bool,
    #[serde(default = "default_token_ttl")]
    pub token_ttl_minutes: u32,
    pub jwt_secret: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UserConfig {
    pub username: String,
    pub password: String,
    pub permissions: Permissions,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct RootsConfig {
    pub expose_drives: bool,
    pub drive_permissions: Permissions,
    pub mounts: Vec<MountConfig>,
}

impl Default for RootsConfig {
    fn default() -> Self {
        Self {
            expose_drives: false,
            drive_permissions: Permissions::READ,
            mounts: vec![],
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct ThumbnailsConfig {
    pub enabled: bool,
    /// Images bigger than this many megabytes are not previewed.
    pub max_source_mb: u64,
    /// Where generated previews are kept. Defaults to the system cache folder.
    pub cache_dir: Option<String>,
}

impl Default for ThumbnailsConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            max_source_mb: 64,
            cache_dir: None,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct MountConfig {
    pub name: String,
    pub path: String,
    #[serde(default = "read_only")]
    pub permissions: Permissions,
}

fn default_true() -> bool {
    true
}

fn default_token_ttl() -> u32 {
    120
}

fn read_only() -> Permissions {
    Permissions::READ
}

impl LunarConfig {
    pub fn load(path: &Path) -> anyhow::Result<Self> {
        let text = std::fs::read_to_string(path)
            .with_context(|| format!("could not read {}", path.display()))?;
        Self::from_toml_str(&text).with_context(|| format!("invalid config {}", path.display()))
    }

    /// Parses, expands mount paths and validates a config.
    pub fn from_toml_str(text: &str) -> anyhow::Result<Self> {
        let mut config: LunarConfig = toml::from_str(text).context(
            "could not parse config. If it was created by an older LunarLink version, \
             rename or delete it to generate a new one (see config.example.toml)",
        )?;
        for mount in &mut config.roots.mounts {
            mount.path = expand_path(&mount.path);
        }
        if let Some(dir) = &mut config.thumbnails.cache_dir {
            *dir = expand_path(dir);
        }
        config.validate()?;
        Ok(config)
    }

    fn validate(&self) -> anyhow::Result<()> {
        if self.auth.jwt_secret.len() < MIN_SECRET_LEN {
            bail!("auth.jwt_secret must be at least {MIN_SECRET_LEN} characters");
        }
        if self.auth.token_ttl_minutes == 0 {
            bail!("auth.token_ttl_minutes must be at least 1");
        }
        if self.server.max_concurrent_requests == 0 {
            bail!("server.max_concurrent_requests must be at least 1");
        }
        let mut seen = HashSet::new();
        for user in &self.users {
            if user.username.is_empty() {
                bail!("a user has an empty username");
            }
            if !seen.insert(user.username.as_str()) {
                bail!("duplicate user `{}`", user.username);
            }
        }
        for mount in &self.roots.mounts {
            if mount.name.is_empty() || mount.path.is_empty() {
                bail!("every mount needs a name and a path");
            }
        }
        Ok(())
    }

    pub fn user(&self, username: &str) -> Option<&UserConfig> {
        self.users.iter().find(|u| u.username == username)
    }
}

pub fn random_string(len: usize) -> String {
    rand::rng()
        .sample_iter(&Alphanumeric)
        .take(len)
        .map(char::from)
        .collect()
}

/// A freshly generated config file. The admin password is returned so it can be shown once.
pub struct GeneratedConfig {
    pub toml: String,
    pub admin_password: String,
}

pub fn generate_default_config() -> GeneratedConfig {
    let admin_password = random_string(16);
    let toml = default_config_toml(&random_string(64), &admin_password);
    GeneratedConfig {
        toml,
        admin_password,
    }
}

pub fn default_config_toml(jwt_secret: &str, admin_password: &str) -> String {
    format!(
        r#"# LunarLink server configuration. See API.md for details.

[server]
host = "0.0.0.0"
port = 1234
max_concurrent_requests = 100
# display_name = "My Desktop"   # shown to clients before login. Defaults to the hostname
show_qr = false                 # print a QR code with the server URL at startup
discoverable = true             # let apps on the local network find this server

[auth]
enabled = true
token_ttl_minutes = 120
jwt_secret = "{jwt_secret}"

# Passwords are stored as plain text for now.
# Permissions: "read", "write", "delete"
[[users]]
username = "admin"
password = "{admin_password}"
permissions = ["read", "write", "delete"]

[thumbnails]
enabled = true                  # small previews of images, shown in the app
max_source_mb = 64              # images bigger than this are not previewed
# cache_dir = "~/.cache/lunarlink/thumbs"   # defaults to the system cache folder

[roots]
expose_drives = false           # share every disk mount point as a root
drive_permissions = ["read"]    # permissions for drive roots

# Paths support ~, $VAR, ${{VAR}} and %VAR%.
# permissions defaults to ["read"]. An empty list hides the mount.
[[roots.mounts]]
name = "Home"
path = "~"
permissions = ["read", "write", "delete"]
"#
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::permission::Permission;

    const SECRET: &str = "0123456789abcdef0123456789abcdef";

    fn minimal(extra: &str) -> String {
        format!("[auth]\njwt_secret = \"{SECRET}\"\n{extra}")
    }

    #[test]
    fn default_template_parses() {
        let generated = generate_default_config();
        let config = LunarConfig::from_toml_str(&generated.toml).unwrap();
        assert_eq!(config.users.len(), 1);
        assert_eq!(config.users[0].password, generated.admin_password);
        assert_eq!(config.users[0].permissions, Permissions::ALL);
        assert!(!config.roots.expose_drives);
        assert!(!config.server.show_qr);
        assert!(!config.roots.mounts[0].path.starts_with('~'));
    }

    #[test]
    fn example_config_matches_template() {
        let example = include_str!("../../config.example.toml");
        let expected = default_config_toml(
            "CHANGE-ME-use-a-long-random-string-of-at-least-32-characters",
            "change-me",
        );
        assert_eq!(example, expected, "regenerate config.example.toml");
        LunarConfig::from_toml_str(example).unwrap();
    }

    #[test]
    fn defaults_apply() {
        let config = LunarConfig::from_toml_str(&minimal(
            "[[roots.mounts]]\nname = \"x\"\npath = \"/tmp\"\n",
        ))
        .unwrap();
        assert!(config.auth.enabled);
        assert_eq!(config.auth.token_ttl_minutes, 120);
        assert_eq!(config.server.port, 1234);
        assert_eq!(config.roots.drive_permissions, Permissions::READ);
        assert_eq!(config.roots.mounts[0].permissions, Permissions::READ);
    }

    #[test]
    fn empty_mount_permissions_mean_none() {
        let config = LunarConfig::from_toml_str(&minimal(
            "[[roots.mounts]]\nname = \"x\"\npath = \"/tmp\"\npermissions = []\n",
        ))
        .unwrap();
        assert!(config.roots.mounts[0].permissions.is_empty());
    }

    #[test]
    fn rejects_old_format_and_unknown_keys() {
        let old =
            minimal("[[users.users]]\nusername = \"a\"\npassword = \"b\"\npermissions = []\n");
        assert!(LunarConfig::from_toml_str(&old).is_err());
        assert!(LunarConfig::from_toml_str(&minimal(
            "[experiments]\nenable_execution_features = false\n"
        ))
        .is_err());
        assert!(LunarConfig::from_toml_str(&minimal("[server]\nenable_https = true\n")).is_err());
    }

    #[test]
    fn rejects_invalid_values() {
        assert!(LunarConfig::from_toml_str("[auth]\njwt_secret = \"short\"\n").is_err());
        assert!(LunarConfig::from_toml_str(&minimal("token_ttl_minutes = 0\n")).is_err());
        let dup = "[[users]]\nusername = \"a\"\npassword = \"b\"\npermissions = []\n";
        assert!(LunarConfig::from_toml_str(&minimal(&format!("{dup}{dup}"))).is_err());
        assert!(LunarConfig::from_toml_str(&minimal(
            "[[users]]\nusername = \"a\"\npassword = \"b\"\npermissions = [\"Execute\"]\n"
        ))
        .is_err());
    }

    #[test]
    fn user_lookup() {
        let config = LunarConfig::from_toml_str(&minimal(
            "[[users]]\nusername = \"a\"\npassword = \"b\"\npermissions = [\"read\"]\n",
        ))
        .unwrap();
        assert!(config
            .user("a")
            .unwrap()
            .permissions
            .contains(Permission::Read));
        assert!(config.user("b").is_none());
    }
}
