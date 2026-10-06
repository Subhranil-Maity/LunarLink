use crate::auth::jwt;
use crate::core::error::LunarError;
use crate::core::permission::Permissions;
use crate::server::state::AppState;
use axum::extract::{Request, State};
use axum::http::header::AUTHORIZATION;
use axum::middleware::Next;
use axum::response::Response;
use std::sync::Arc;

/// The authenticated caller, inserted into request extensions by [`require_auth`].
#[derive(Debug, Clone)]
pub struct AuthUser {
    pub username: String,
    pub permissions: Permissions,
}

pub async fn require_auth(
    State(state): State<Arc<AppState>>,
    mut req: Request,
    next: Next,
) -> Result<Response, LunarError> {
    let user = match req.headers().get(AUTHORIZATION) {
        None if !state.config.auth.enabled => AuthUser {
            username: "anonymous".into(),
            permissions: Permissions::ALL,
        },
        None => return Err(LunarError::Unauthenticated),
        Some(value) => {
            let value = value.to_str().map_err(|_| LunarError::InvalidToken)?;
            let token = match value.split_once(' ') {
                Some((scheme, token)) if scheme.eq_ignore_ascii_case("bearer") => token.trim(),
                _ => return Err(LunarError::InvalidToken),
            };
            let claims = jwt::verify(token, state.config.auth.jwt_secret.as_bytes())?;
            // Permissions come from the loaded config rather than the token, so after a
            // config change and restart, existing tokens get the new permissions and
            // removed users are rejected.
            let user = state
                .config
                .user(&claims.sub)
                .ok_or(LunarError::InvalidToken)?;
            AuthUser {
                username: user.username.clone(),
                permissions: user.permissions,
            }
        }
    };
    req.extensions_mut().insert(user);
    Ok(next.run(req).await)
}
