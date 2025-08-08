use axum::{Extension, Json};
use axum::response::IntoResponse;
use crate::auth::is_creds_valid;
use crate::config::{LunarConfig, UserPermissions};
use crate::core::lunar_error::{LunarError, LunarResult};
use crate::server::api::auth::jwt::{encode_jwt, JwtPayload};

#[derive(serde::Deserialize)]
pub struct LoginRequest {
    username: String,
    password: String,
}

#[derive(serde::Serialize)]
pub struct LoginResponse {
    token: String,
}


pub async fn login_handler(
    Extension(config): Extension<LunarConfig>,
    Json(request): Json<LoginRequest>,
) -> LunarResult<Json<LoginResponse>> {
    let is_valid_user = is_creds_valid(request.username.as_str(), request.password.as_str(), &config.users);
    return match is_valid_user {
        None => {
            Err(LunarError::LoginFail)
        }
        Some(permissions) => {
            let payload = JwtPayload{
                user_name: request.username,
                permissions,
            };
            let token = encode_jwt(payload, &config.auth.jwt_secret.as_bytes())?;
            let response = LoginResponse {
                token,
            };
            Ok(Json(response))
        }
    }
}