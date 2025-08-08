use axum::Router;
use v_one::fs;
pub mod auth;
mod v_one;
use v_one::v_one_handler;

pub fn api_handler() -> Router {
    Router::new()
        .nest("/v1", v_one_handler())
        .nest("/auth", auth::auth_handler())
}



