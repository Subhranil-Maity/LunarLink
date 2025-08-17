use crate::server;
use crate::server::api::v_one::system::PersonalDevice;
use axum::response::IntoResponse;
use axum::routing::get;
use axum::{middleware, Json, Router};

pub mod fs;
mod system;

pub fn v_one_handler() -> Router {
    Router::new()
        .nest("/fs/", fs::fs_handler())
        .route("/get_info", get(get_system_info))
        .layer(middleware::from_fn(
            server::middleware::auth_middleware::auth_middleware,
        ))
}

async fn get_system_info() -> impl IntoResponse {
    Json(PersonalDevice::detect())
}
