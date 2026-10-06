use crate::config::LunarConfig;
use crate::core::lunar_error::{LunarError, LunarResult};
use crate::server::context::auth_context::AuthContext;
use axum::body::Body;
use axum::http::{Request, Response};
use axum::middleware::Next;
use axum::Extension;

pub async fn auth_middleware(
    Extension(config): Extension<LunarConfig>,
    mut req: Request<Body>,
    next: Next,
) -> LunarResult<Response<Body>> {
    let auth_header = req.headers_mut().get(axum::http::header::AUTHORIZATION);
    let auth_header = match auth_header {
        Some(header) => header.to_str().map_err(|_| LunarError::InvalidAuthHeader)?,
        None => return Err(LunarError::MissingAuthHeader),
    };
    let mut header = auth_header.split_whitespace();
    let (bearer, token) = (header.next(), header.next());
    let _ = bearer;
    let token = match token {
        None => {
            return Err(LunarError::InvalidAuthHeader);
        }
        Some(token) => token,
    };
    let token_data = crate::server::api::auth::jwt::decode_jwt(
        token.to_string(),
        config.auth.jwt_secret.as_bytes(),
    )?;
    let data = token_data.claims.jwt_payload;
    let auth_context = AuthContext {
        user_name: data.user_name,
        permissions: data.permissions,
    };
    req.extensions_mut().insert(auth_context);

    Ok(next.run(req).await)
}
