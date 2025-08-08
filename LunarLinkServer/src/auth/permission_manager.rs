use crate::config::UserPermissions;
use crate::core::lunar_error::{LunarError, LunarResult};

pub fn have_enough_permissions(
    required_permissions: &UserPermissions,
    user_permissions: &UserPermissions,
) -> LunarResult<()> {
    if required_permissions.is_empty() && !user_permissions.is_empty() {
        return Err(LunarError::PermissionDenied);
    }

    let user_perm_set: HashSet<_> = user_permissions.iter().collect();

    if required_permissions.iter().all(|perm| user_perm_set.contains(perm)) {
        Ok(())
    } else {
        Err(LunarError::PermissionDenied)
    }
}
fn is_subset<T: PartialEq>(subset: &[T], superset: &[T]) -> bool {
    subset.iter().all(|item| superset.contains(item))
}
use std::collections::HashSet;
use std::hash::Hash;

pub fn is_subset_with_exception<T: Eq + Hash>(
    subset: &[T],
    superset: &[T]
) -> bool {
    // Special exception:
    // If subset is empty but superset is not empty, return false
    if subset.is_empty() && !superset.is_empty() {
        return false;
    }

    let superset_set: HashSet<_> = superset.iter().collect();

    subset.iter().all(|item| superset_set.contains(item))
}
