use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use rand::distr::Alphanumeric;
use rand::rand_core::OsRng;
use rand::{Rng, RngCore};
use crate::core::lunar_error::LunarError;

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
