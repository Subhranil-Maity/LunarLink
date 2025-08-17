use crate::config::{LunarUsersConfig, UserPermissions};
pub mod permission_manager;
pub fn is_creds_valid(
    username: &str,
    password: &str,
    user_config: &LunarUsersConfig,
) -> Option<UserPermissions> {
    tracing::warn!("The Credentials are in plain text, this is not secure!");
    for user in &user_config.users {
        if user.username == username && user.password == password {
            return Some(user.permissions.clone());
        }
    }
    None
}
