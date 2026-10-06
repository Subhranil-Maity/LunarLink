use crate::core::error::LunarError;
use axum::extract::rejection::{JsonRejection, QueryRejection};
use axum::http::{header, HeaderValue, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::{json, Value};

pub fn status_of(err: &LunarError) -> StatusCode {
    use LunarError::*;
    match err {
        BadRequest(_) | InvalidPath | NotADirectory | IsADirectory | InvalidOperation(_) => {
            StatusCode::BAD_REQUEST
        }
        Unauthenticated | InvalidToken | TokenExpired | LoginFailed => StatusCode::UNAUTHORIZED,
        PermissionDenied | PathOutsideRoots | SystemAccessDenied => StatusCode::FORBIDDEN,
        NotFound => StatusCode::NOT_FOUND,
        AlreadyExists | DirectoryNotEmpty => StatusCode::CONFLICT,
        TooManyAttempts { .. } => StatusCode::TOO_MANY_REQUESTS,
        TrashFailed(_) | Internal(_) => StatusCode::INTERNAL_SERVER_ERROR,
    }
}

impl IntoResponse for LunarError {
    fn into_response(self) -> Response {
        let status = status_of(&self);
        if status.is_server_error() {
            tracing::error!(error = %self, "request failed");
        } else {
            tracing::debug!(error = %self, "request rejected");
        }
        let details = match &self {
            LunarError::TooManyAttempts { retry_after_secs } => {
                json!({ "retry_after_seconds": retry_after_secs })
            }
            _ => Value::Null,
        };
        let body = json!({
            "error": { "code": self.code(), "message": self.message(), "details": details }
        });
        let mut response = (status, Json(body)).into_response();
        if let LunarError::TooManyAttempts { retry_after_secs } = self {
            response
                .headers_mut()
                .insert(header::RETRY_AFTER, HeaderValue::from(retry_after_secs));
        }
        response
    }
}

impl From<JsonRejection> for LunarError {
    fn from(rejection: JsonRejection) -> Self {
        LunarError::BadRequest(rejection.body_text())
    }
}

impl From<QueryRejection> for LunarError {
    fn from(rejection: QueryRejection) -> Self {
        LunarError::BadRequest(rejection.body_text())
    }
}
