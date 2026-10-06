use regex::{Captures, Regex};
use std::sync::LazyLock;

static VAR_RE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)|%([A-Za-z_][A-Za-z0-9_()]*)%",
    )
    .expect("valid regex")
});

/// Expands a leading `~` and `$VAR`, `${VAR}` or `%VAR%` references.
/// Unknown variables are left untouched.
pub fn expand_path(raw: &str) -> String {
    let with_home = expand_tilde(raw);
    VAR_RE
        .replace_all(&with_home, |caps: &Captures| {
            let name = caps
                .get(1)
                .or_else(|| caps.get(2))
                .or_else(|| caps.get(3))
                .map(|m| m.as_str())
                .unwrap_or_default();
            std::env::var(name).unwrap_or_else(|_| caps[0].to_string())
        })
        .into_owned()
}

fn expand_tilde(raw: &str) -> String {
    let rest = match raw.strip_prefix('~') {
        Some(rest) if rest.is_empty() || rest.starts_with('/') || rest.starts_with('\\') => rest,
        _ => return raw.to_string(),
    };
    match std::env::home_dir() {
        Some(home) => format!("{}{}", home.display(), rest),
        None => raw.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn expands_tilde() {
        let home = std::env::home_dir().unwrap().display().to_string();
        assert_eq!(expand_path("~"), home);
        assert_eq!(expand_path("~/docs"), format!("{home}/docs"));
        assert_eq!(expand_path("/a/~b"), "/a/~b");
        assert_eq!(expand_path("~user/x"), "~user/x");
    }

    #[test]
    fn expands_variables() {
        let path = std::env::var("PATH").unwrap();
        assert_eq!(expand_path("$PATH/x"), format!("{path}/x"));
        assert_eq!(expand_path("${PATH}x"), format!("{path}x"));
        assert_eq!(expand_path("%PATH%\\x"), format!("{path}\\x"));
        assert_eq!(
            expand_path("$LUNAR_DOES_NOT_EXIST/a"),
            "$LUNAR_DOES_NOT_EXIST/a"
        );
    }
}
