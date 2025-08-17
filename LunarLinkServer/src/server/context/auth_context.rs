use crate::config::UserPermissions;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct AuthContext {
    pub user_name: String,
    pub permissions: UserPermissions,
}
