use crate::config::UserPermissions;
use crate::core::lunar_error::{LunarError, LunarResult};
use std::collections::HashSet;

pub fn have_enough_permissions(
    required_permissions: &UserPermissions,
    user_permissions: &UserPermissions,
) -> LunarResult<()> {
    if required_permissions.is_empty() && !user_permissions.is_empty() {
        return Err(LunarError::PermissionDenied);
    }

    let user_perm_set: HashSet<_> = user_permissions.iter().collect();

    if required_permissions
        .iter()
        .all(|perm| user_perm_set.contains(perm))
    {
        Ok(())
    } else {
        Err(LunarError::PermissionDenied)
    }
}
