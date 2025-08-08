use axum::Router;
use axum::routing::get;

mod mounts;
mod metadata;
mod folder;

pub fn fs_handler() -> Router {
    Router::new()
        .route("/", get(|| async { "Hello, world!" }))
        .route("/mounts", get(mounts::get_mounts))
        .route("/metadata", get(metadata::get_metadata))
        .route("/get_folder_content", get(folder::get_ls))
}