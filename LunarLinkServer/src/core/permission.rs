use crate::core::error::{LunarError, LunarResult};
use serde::{Deserialize, Deserializer, Serialize, Serializer};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Permission {
    Read,
    Write,
    Delete,
}

impl Permission {
    pub const ALL: [Permission; 3] = [Permission::Read, Permission::Write, Permission::Delete];

    fn bit(self) -> u8 {
        match self {
            Permission::Read => 0b001,
            Permission::Write => 0b010,
            Permission::Delete => 0b100,
        }
    }
}

/// A set of permissions. Serialized as a list, e.g. `["read", "write"]`.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct Permissions(u8);

impl Permissions {
    pub const NONE: Self = Self(0);
    pub const ALL: Self = Self(0b111);
    pub const READ: Self = Self(0b001);

    pub fn of(perms: &[Permission]) -> Self {
        Self(perms.iter().fold(0, |acc, p| acc | p.bit()))
    }

    pub fn contains(self, perm: Permission) -> bool {
        self.0 & perm.bit() != 0
    }

    pub fn intersect(self, other: Self) -> Self {
        Self(self.0 & other.0)
    }

    pub fn is_empty(self) -> bool {
        self.0 == 0
    }

    pub fn iter(self) -> impl Iterator<Item = Permission> {
        Permission::ALL
            .into_iter()
            .filter(move |p| self.contains(*p))
    }

    /// Fails with `PermissionDenied` unless every permission in `required` is present.
    pub fn require(self, required: &[Permission]) -> LunarResult<()> {
        if required.iter().all(|p| self.contains(*p)) {
            Ok(())
        } else {
            Err(LunarError::PermissionDenied)
        }
    }
}

impl Serialize for Permissions {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.collect_seq(self.iter())
    }
}

impl<'de> Deserialize<'de> for Permissions {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let list = Vec::<Permission>::deserialize(deserializer)?;
        Ok(Self::of(&list))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use Permission::*;

    #[test]
    fn intersect_and_require() {
        let user = Permissions::of(&[Read, Write, Delete]);
        let root = Permissions::of(&[Read]);
        let effective = user.intersect(root);
        assert!(effective.require(&[Read]).is_ok());
        assert!(matches!(
            effective.require(&[Read, Write]),
            Err(LunarError::PermissionDenied)
        ));
        assert!(Permissions::NONE.require(&[Read]).is_err());
        assert!(Permissions::NONE.require(&[]).is_ok());
    }

    #[test]
    fn serde_round_trip_is_lowercase_list() {
        let perms = Permissions::of(&[Delete, Read]);
        let json = serde_json::to_string(&perms).unwrap();
        assert_eq!(json, r#"["read","delete"]"#);
        let back: Permissions = serde_json::from_str(&json).unwrap();
        assert_eq!(back, perms);
    }

    #[test]
    fn unknown_permission_is_rejected() {
        assert!(serde_json::from_str::<Permissions>(r#"["execute"]"#).is_err());
        assert!(serde_json::from_str::<Permissions>(r#"["Read"]"#).is_err());
    }
}
