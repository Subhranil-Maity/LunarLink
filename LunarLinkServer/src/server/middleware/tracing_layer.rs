use axum::http::{Request, Response, HeaderMap};
use axum::middleware::Next;
use axum::response::IntoResponse;
use axum::body::Bytes;
use tracing::{info_span, Span};
use std::time::Duration;
use axum::extract::MatchedPath;
use tower_http::classify::ServerErrorsFailureClass;
use tower_http::trace::{HttpMakeClassifier, TraceLayer};

pub fn create_tracing_layer() -> TraceLayer<HttpMakeClassifier> {
    TraceLayer::new_for_http()
}
