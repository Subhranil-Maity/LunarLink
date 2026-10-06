pub mod jwt;
pub mod login_guard;

use crate::config::UserConfig;

/// Finds the user and checks the password in constant time.
/// Passwords are plain text for now; hashing is planned for a later release.
pub fn verify_credentials<'a>(
    users: &'a [UserConfig],
    username: &str,
    password: &str,
) -> Option<&'a UserConfig> {
    let user = users.iter().find(|u| u.username == username);
    // Compare against a dummy when the user doesn't exist so timing is similar.
    let expected = user.map_or("\0no-such-user\0".as_bytes(), |u| u.password.as_bytes());
    if constant_time_eq(expected, password.as_bytes()) {
        user
    } else {
        None
    }
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.iter().zip(b).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::permission::Permissions;

    fn users() -> Vec<UserConfig> {
        vec![UserConfig {
            username: "alice".into(),
            password: "secret".into(),
            permissions: Permissions::ALL,
        }]
    }

    #[test]
    fn verifies_credentials() {
        let users = users();
        assert!(verify_credentials(&users, "alice", "secret").is_some());
        assert!(verify_credentials(&users, "alice", "Secret").is_none());
        assert!(verify_credentials(&users, "alice", "secret ").is_none());
        assert!(verify_credentials(&users, "bob", "secret").is_none());
        assert!(verify_credentials(&users, "bob", "\0no-such-user\0").is_none());
    }
}
