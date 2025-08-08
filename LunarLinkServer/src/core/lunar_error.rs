pub type LunarResult<T> = Result<T, LunarError>;

#[derive(Debug)]
pub enum LunarError {
    LoginFail,
    Unauthorized,              // No valid auth
    PermissionDenied,          // User lacks permission for this action
    AccessDeniedBySystem,      // OS-level block (filesystem, etc.)

    FileOrFolderNotFound,
    FileOrFolderAlreadyExists,
    NotADirectory,
    NotAValidPath,

    Conflict,                  // Generic resource conflict

    IoError,                   // Standardized catch-all for I/O
    ParseError,                 // Config or structured file parsing errors
    InvalidData,                // Data exists but is invalid/corrupt

    NetworkError,
    Timeout,

    BadRequest,
    FailedToReadFile,
    DeletingFolderWithoutFlag,

    KnownUnknownError(String), // Legacy/fallback
    Unexpected,                // Truly unknown/unhandled

    Custom { code: u16, message: String }, // Flexible one-off
    MissingAuthHeader,
    InvalidAuthHeader,
    JwtError(String),
}


impl From<std::io::Error> for LunarError {
    fn from(err: std::io::Error) -> Self {
        use std::io::ErrorKind::*;
        match err.kind() {
            PermissionDenied     => LunarError::AccessDeniedBySystem,
            NotFound              => LunarError::FileOrFolderNotFound,
            InvalidInput          => LunarError::NotAValidPath,
            TimedOut              => LunarError::Timeout,
            AlreadyExists         => LunarError::FileOrFolderAlreadyExists,
            NotADirectory         => LunarError::NotADirectory,
            ConnectionRefused
            | ConnectionReset
            | ConnectionAborted
            | NotConnected
            | AddrInUse
            | AddrNotAvailable    => LunarError::NetworkError,
            _ => {
                // Log unexpected error kinds for debugging
                #[cfg(debug_assertions)]
                eprintln!("Unexpected IO ErrorKind: {:?}", err.kind());
                LunarError::Unexpected
            }
        }
    }
}

