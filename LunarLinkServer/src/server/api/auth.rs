use crate::auth::{jwt, verify_credentials};
use crate::core::error::{LunarError, LunarResult};
use crate::core::permission::Permissions;
use crate::server::extract::ApiJson;
use crate::server::middleware::AuthUser;
use crate::server::state::AppState;
use axum::extract::State;
use axum::{Extension, Json};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LoginRequest {
    username: String,
    password: String,
}

#[derive(Serialize)]
pub struct UserInfo {
    username: String,
    permissions: Permissions,
}

#[derive(Serialize)]
pub struct LoginResponse {
    token: String,
    token_type: &'static str,
    expires_at: u64,
    user: UserInfo,
}

pub async fn login(
    State(state): State<Arc<AppState>>,
    ApiJson(req): ApiJson<LoginRequest>,
) -> LunarResult<Json<LoginResponse>> {
    if let Err(wait) = state.login_guard.check(&req.username) {
        return Err(LunarError::TooManyAttempts {
            retry_after_secs: wait.as_secs_f64().ceil().max(1.0) as u64,
        });
    }
    let Some(user) = verify_credentials(&state.config.users, &req.username, &req.password) else {
        if state.config.user(&req.username).is_some() {
            state.login_guard.record_failure(&req.username);
        }
        return Err(LunarError::LoginFailed);
    };
    state.login_guard.record_success(&user.username);
    let (token, expires_at) = jwt::issue(
        &user.username,
        state.config.auth.token_ttl_minutes,
        state.config.auth.jwt_secret.as_bytes(),
    )?;
    Ok(Json(LoginResponse {
        token,
        token_type: "Bearer",
        expires_at,
        user: UserInfo {
            username: user.username.clone(),
            permissions: user.permissions,
        },
    }))
}

pub async fn me(Extension(user): Extension<AuthUser>) -> Json<UserInfo> {
    Json(UserInfo {
        username: user.username,
        permissions: user.permissions,
    })
}
