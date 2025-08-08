use serde::{Deserialize, Serialize};
use crate::config::UserPermissions;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct AuthContext {
    pub user_name: String,
    pub permissions: UserPermissions,
}