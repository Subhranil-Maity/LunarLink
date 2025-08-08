use axum::{routing::get, Extension, Router};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use crate::config::LunarConfig;
use crate::server::middleware::tracing_layer::create_tracing_layer;

mod middleware;
pub mod api;
pub mod context;
pub(crate) mod utils;

pub async fn run_server(
    config: LunarConfig
) -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::DEBUG)
        .init();
    
    let addr = format!("{}:{}", config.server.host, config.server.port);
    let app = Router::new().route("/", get(root))
        .nest("/api", api::api_handler())
        .layer( create_tracing_layer())
        .layer(Extension(config));
    let listener = tokio::net::TcpListener::bind(addr.as_str()).await.unwrap();
    Ok(axum::serve(listener, app).await?)
}
async fn root() -> &'static str {
    "Hello, World!"
}
