use axum::http::Method;
use axum::routing::post;
use axum::Router;
use tower_http::cors::{Any, CorsLayer};

mod auth_login;
pub mod jwt;
pub fn auth_handler() -> Router {
    let cors = CorsLayer::new()
        .allow_methods([Method::POST])
        .allow_origin(Any);

    Router::new()
        .route("/login", post(auth_login::login_handler))
        .layer(cors)
}
