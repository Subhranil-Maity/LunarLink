use tower_http::trace::{HttpMakeClassifier, TraceLayer};

pub fn create_tracing_layer() -> TraceLayer<HttpMakeClassifier> {
    TraceLayer::new_for_http()
}
