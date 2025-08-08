use axum::{middleware, Router};
use crate::server;

pub mod fs;

pub fn v_one_handler() -> Router {
    Router::new()
        .nest("/fs/", fs::fs_handler()
            .layer(middleware::from_fn(server::middleware::auth_middleware::auth_middleware)))
}