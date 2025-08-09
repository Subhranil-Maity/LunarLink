use crate::config::LunarConfig;
use crate::server::middleware::tracing_layer::create_tracing_layer;
use axum::{routing::get, Extension, Router};
use local_ip_address::local_ip;
use qrcodegen::{QrCode, QrCodeEcc};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

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
    let port = config.server.port;
    let addr = format!("{}:{}", config.server.host, port.clone());
    let app = Router::new().route("/", get(root))
        .nest("/api", api::api_handler())
        .layer( create_tracing_layer())
        .layer(Extension(config));
    let public_ip = local_ip().unwrap().to_string();
    let listener = tokio::net::TcpListener::bind(addr.as_str()).await.unwrap();
    let qr = QrCode::encode_text(
        format!("http://{}:{}", public_ip, port.clone()).as_str(),
        QrCodeEcc::Medium
    ).unwrap();
    print_qr(&qr);
    Ok(axum::serve(listener, app).await?)
}

fn print_qr(qr: &QrCode) {
    let border: i32 = 4;
    for y in -border .. qr.size() + border {
        for x in -border .. qr.size() + border {
            let c: char = if qr.get_module(x, y) { '█' } else { ' ' };
            print!("{0}{0}", c);
        }
        println!();
    }
    println!();
}

async fn root() -> &'static str {
    "Hello, World!"
}
