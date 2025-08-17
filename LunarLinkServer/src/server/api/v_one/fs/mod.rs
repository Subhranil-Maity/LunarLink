use axum::Router;
use axum::routing::{delete, get, patch, post};

mod mounts;
mod metadata;
mod folder;
mod file;

pub fn fs_handler() -> Router {
    println!("WRITE FUNCTIONALITY IS EXPERIMENTAL MOSTLY");
    Router::new()
        .route("/", get(|| async { "Hello, world!" }))
        .route("/mounts", get(mounts::get_mounts))
        .route("/metadata", get(metadata::get_metadata))
        .route("/get_folder_content", get(folder::get_ls))
        .route("/read_full_file", get(file::get_read_full_file))
        .route("/download_file", get(file::get_chunked_file))
        .route("/rename", patch(file::patch_rename_file))
        .route("/move", patch(file::patch_move_file))
        .route("/copy", post(file::post_copy))
        .route("/delete", delete(file::delete_delete))
        .route("/touch", post(file::post_touch))
        .route("/mkdir", post(folder::post_mkdir))
        // .route("/write_chunked_file", post(file::post_upload_chunk))
}