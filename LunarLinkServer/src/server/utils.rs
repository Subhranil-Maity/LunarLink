use axum::extract::multipart::MultipartError;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use rand::distr::Alphanumeric;
use rand::rand_core::OsRng;
use rand::{Rng, RngCore};
use tokio::fs::OpenOptions;
use tokio::io::AsyncWriteExt;
use crate::core::lunar_error::{LunarError, LunarResult};

impl IntoResponse for LunarError {
    fn into_response(self) -> Response {
        tracing::error!("{:?}", self);
        tracing::warn!("Error messages must be user-friendly and not contain sensitive information. TODO");
        (StatusCode::INTERNAL_SERVER_ERROR, "Unhandled_client_error").into_response()
    }
}

pub fn generate_random_string(len: usize) -> String {
    rand::rng()
        .sample_iter(&Alphanumeric)
        .take(len)
        .map(char::from)
        .collect()
}

impl From<MultipartError> for LunarError{
    fn from(value: MultipartError) -> Self {
        LunarError::BadRequest
    }
}

pub async fn is_upload_complete(temp_dir: &str, total_chunks: usize) -> bool {
    let mut read = match tokio::fs::read_dir(temp_dir).await{
        Ok(entries) => entries,
        Err(_) => return false,
    };
    let mut count = 0;
    while let Some(entry) = read.next_entry().await.unwrap() {
        if entry.file_name().to_str().unwrap().starts_with("chunk_") {
            count += 1;
        }
    }
    return count == total_chunks;
}

pub async fn assemble_file(temp_dir: &str, file_name: &str, total_chunks: usize) -> LunarResult<()> {
    let output_path = format!("./uploads/{}", file_name);
    let mut output_file = OpenOptions::new()
        .create(true)
        .write(true)
        .open(&output_path).await?;

    for chunk_number in 0..total_chunks {
        let chunk_path = format!("{}/chunk_{}", temp_dir, chunk_number);
        let chunk_data = tokio::fs::read(&chunk_path).await?;
        output_file.write_all(&chunk_data).await?;
    }

    tokio::fs::remove_dir_all(temp_dir).await?;
    Ok(())
}


