use std::io;

pub type LunarResult<T> = Result<T, LunarError>;

/// Every error the server can return. `code()` is the stable, machine-readable
/// identifier sent to clients; `message()` is the public, human-readable text.
/// Internal details (OS errors, paths) are only kept for server-side logging.
#[derive(Debug, thiserror::Error)]
pub enum LunarError {
    #[error("bad request: {0}")]
    BadRequest(String),
    #[error("invalid path")]
    InvalidPath,
    #[error("not a directory")]
    NotADirectory,
    #[error("is a directory")]
    IsADirectory,
    #[error("invalid operation: {0}")]
    InvalidOperation(&'static str),
    #[error("missing authorization")]
    Unauthenticated,
    #[error("invalid token")]
    InvalidToken,
    #[error("token expired")]
    TokenExpired,
    #[error("login failed")]
    LoginFailed,
    #[error("permission denied")]
    PermissionDenied,
    #[error("path outside roots")]
    PathOutsideRoots,
    #[error("access denied by the operating system")]
    SystemAccessDenied,
    #[error("not found")]
    NotFound,
    #[error("already exists")]
    AlreadyExists,
    #[error("directory not empty")]
    DirectoryNotEmpty,
    #[error("too many attempts, retry after {retry_after_secs}s")]
    TooManyAttempts { retry_after_secs: u64 },
    #[error("trash failed: {0}")]
    TrashFailed(String),
    #[error("internal error: {0}")]
    Internal(String),
}

impl LunarError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::BadRequest(_) => "BAD_REQUEST",
            Self::InvalidPath => "INVALID_PATH",
            Self::NotADirectory => "NOT_A_DIRECTORY",
            Self::IsADirectory => "IS_A_DIRECTORY",
            Self::InvalidOperation(_) => "INVALID_OPERATION",
            Self::Unauthenticated => "UNAUTHENTICATED",
            Self::InvalidToken => "INVALID_TOKEN",
            Self::TokenExpired => "TOKEN_EXPIRED",
            Self::LoginFailed => "LOGIN_FAILED",
            Self::PermissionDenied => "PERMISSION_DENIED",
            Self::PathOutsideRoots => "PATH_OUTSIDE_ROOTS",
            Self::SystemAccessDenied => "SYSTEM_ACCESS_DENIED",
            Self::NotFound => "NOT_FOUND",
            Self::AlreadyExists => "ALREADY_EXISTS",
            Self::DirectoryNotEmpty => "DIRECTORY_NOT_EMPTY",
            Self::TooManyAttempts { .. } => "TOO_MANY_ATTEMPTS",
            Self::TrashFailed(_) => "TRASH_FAILED",
            Self::Internal(_) => "INTERNAL",
        }
    }

    /// Text that is safe to show to clients.
    pub fn message(&self) -> String {
        match self {
            Self::BadRequest(detail) => detail.clone(),
            Self::InvalidPath => "The path is invalid. Paths must be absolute.".into(),
            Self::NotADirectory => "The path is not a folder.".into(),
            Self::IsADirectory => "The path is a folder.".into(),
            Self::InvalidOperation(reason) => (*reason).into(),
            Self::Unauthenticated => "Authentication required.".into(),
            Self::InvalidToken => "The token is invalid.".into(),
            Self::TokenExpired => "The token has expired. Log in again.".into(),
            Self::LoginFailed => "Wrong username or password.".into(),
            Self::PermissionDenied => "You do not have permission to do this.".into(),
            Self::PathOutsideRoots => "The path is outside the shared folders.".into(),
            Self::SystemAccessDenied => "The operating system denied access.".into(),
            Self::NotFound => "File or folder not found.".into(),
            Self::AlreadyExists => "File or folder already exists.".into(),
            Self::DirectoryNotEmpty => "The folder is not empty.".into(),
            Self::TooManyAttempts { .. } => {
                "Too many failed login attempts. Try again later.".into()
            }
            Self::TrashFailed(_) => "Could not move to trash. Try deleting permanently.".into(),
            Self::Internal(_) => "An internal error occurred.".into(),
        }
    }
}

impl From<io::Error> for LunarError {
    fn from(err: io::Error) -> Self {
        use io::ErrorKind as K;
        match err.kind() {
            K::NotFound => Self::NotFound,
            K::PermissionDenied => Self::SystemAccessDenied,
            K::AlreadyExists => Self::AlreadyExists,
            K::NotADirectory => Self::NotADirectory,
            K::IsADirectory => Self::IsADirectory,
            K::DirectoryNotEmpty => Self::DirectoryNotEmpty,
            K::InvalidInput | K::InvalidFilename => Self::InvalidPath,
            _ => Self::Internal(err.to_string()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn io_errors_map_to_public_variants() {
        let cases = [
            (io::ErrorKind::NotFound, "NOT_FOUND"),
            (io::ErrorKind::PermissionDenied, "SYSTEM_ACCESS_DENIED"),
            (io::ErrorKind::AlreadyExists, "ALREADY_EXISTS"),
            (io::ErrorKind::DirectoryNotEmpty, "DIRECTORY_NOT_EMPTY"),
            (io::ErrorKind::Other, "INTERNAL"),
        ];
        for (kind, code) in cases {
            assert_eq!(LunarError::from(io::Error::from(kind)).code(), code);
        }
    }

    #[test]
    fn internal_details_are_not_in_public_message() {
        let err = LunarError::Internal("/secret/path exploded".into());
        assert!(!err.message().contains("/secret"));
    }
}
