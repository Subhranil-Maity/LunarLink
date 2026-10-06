use crate::core::lunar_error::LunarError;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use rand::distr::Alphanumeric;
use rand::Rng;
use regex::Regex;
use serde::Serialize;
use std::env;

pub fn error_to_code_and_message(err: &LunarError) -> (u64, &'static str) {
    match err {
        LunarError::LoginFail => (1001, "Login failed. Please check your credentials."),
        LunarError::Unauthorized => (1002, "Unauthorized access."),
        LunarError::PermissionDenied => (1003, "You do not have the required permissions."),
        LunarError::AccessDeniedBySystem => (1004, "Access denied by system policy."),
        LunarError::FileOrFolderNotFound => (1005, "File or folder not found."),
        LunarError::FileOrFolderAlreadyExists => (1006, "File or folder already exists."),
        LunarError::NotADirectory => (1007, "The specified path is not a directory."),
        LunarError::NotAValidPath => (1008, "The specified path is invalid."),
        LunarError::Conflict => (1009, "Conflict with existing resource."),
        LunarError::IoError => (1010, "I/O error occurred."),
        LunarError::ParseError => (1011, "Failed to parse data."),
        LunarError::InvalidData => (1012, "Invalid data format."),
        LunarError::NetworkError => (1013, "Network error occurred."),
        LunarError::Timeout => (1014, "Operation timed out."),
        LunarError::BadRequest => (1015, "Bad request."),
        LunarError::FailedToReadFile => (1016, "Failed to read file."),
        LunarError::DeletingFolderWithoutFlag => (1017, "Deleting folder without required flag."),
        LunarError::KnownUnknownError(_) => (1018, "An internal error occurred."),
        LunarError::Unexpected => (1019, "Unexpected error."),
        LunarError::Custom { .. } => (1020, "Custom error."),
        LunarError::MissingAuthHeader => (1021, "Missing authentication header."),
        LunarError::InvalidAuthHeader => (1022, "Invalid authentication header."),
        LunarError::JwtError(_) => (1023, "JWT token error."),
        LunarError::PathNotProvided => (1024, "Path parameter not provided."),
        LunarError::NewPathNotProvided => (1025, "New path parameter not provided."),
    }
}
#[derive(Serialize)]
pub struct PublicError {
    pub message: String,
    pub code: u64,
}

impl IntoResponse for LunarError {
    fn into_response(self) -> Response {
        tracing::error!("{:?}", self);

        let (code, message) = error_to_code_and_message(&self);

        let body = Json(PublicError {
            message: message.to_string(),
            code,
        });

        // Map certain errors to specific HTTP status codes
        let status = match self {
            LunarError::Unauthorized
            | LunarError::MissingAuthHeader
            | LunarError::InvalidAuthHeader
            | LunarError::JwtError(_)
            | LunarError::LoginFail => StatusCode::UNAUTHORIZED,
            LunarError::PermissionDenied | LunarError::AccessDeniedBySystem => {
                StatusCode::FORBIDDEN
            }
            LunarError::FileOrFolderNotFound => StatusCode::NOT_FOUND,
            LunarError::BadRequest | LunarError::InvalidData | LunarError::ParseError => {
                StatusCode::BAD_REQUEST
            }
            _ => StatusCode::INTERNAL_SERVER_ERROR,
        };

        (status, body).into_response()
    }
}

pub fn generate_random_string(len: usize) -> String {
    rand::rng()
        .sample_iter(&Alphanumeric)
        .take(len)
        .map(char::from)
        .collect()
}

pub fn expand_env_vars(path: &str) -> String {
    // Match %VAR_NAME% (allowing letters, digits, and underscores)
    let re = Regex::new(r"%([^%]+)%").unwrap();

    re.replace_all(path, |caps: &regex::Captures| {
        let var_name = &caps[1];
        env::var(var_name).unwrap_or_else(|_| caps[0].to_string())
    })
    .into_owned()
}