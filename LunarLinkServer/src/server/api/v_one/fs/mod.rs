use axum::Router;
use axum::routing::get;

mod mounts;

pub fn fs_handler() -> Router {
    Router::new()
        .route("/", get(|| async { "Hello, world!" }))
        .route("/mounts", get(mounts::get_mounts))
}