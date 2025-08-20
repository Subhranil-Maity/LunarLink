use axum::response::IntoResponse;
use axum::{Json, Router};
use axum::routing::get;

pub mod auth;
mod v_one;
use v_one::v_one_handler;
use crate::server::api::system::PersonalDevice;

mod utils;
mod system;

pub fn api_handler() -> Router {
    Router::new()
        .nest("/v1", v_one_handler())
        .route("/get_info", get(get_system_info))
        .nest("/auth", auth::auth_handler())
}

async fn get_system_info() -> impl IntoResponse {
    Json(PersonalDevice::detect())
}

