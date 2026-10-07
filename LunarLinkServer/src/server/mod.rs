pub mod api;
pub mod error;
pub mod extract;
pub mod middleware;
pub mod state;

use crate::config::LunarConfig;
use crate::core::error::LunarError;
use crate::server::state::AppState;
use anyhow::Context;
use axum::http::{header, Method};
use axum::routing::{get, post};
use axum::Router;
use qrcodegen::{QrCode, QrCodeEcc};
use std::sync::Arc;
use tower::limit::GlobalConcurrencyLimitLayer;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;

/// Builds the full HTTP application.
pub fn build_app(state: Arc<AppState>) -> Router {
    let fs = Router::new()
        .route("/roots", get(api::fs::roots))
        .route("/stat", get(api::fs::stat))
        .route("/list", get(api::fs::list))
        .route("/search", get(api::fs::search))
        .route("/download", get(api::download::download))
        .route("/file", post(api::fs::create_file))
        .route("/dir", post(api::fs::create_dir))
        .route("/copy", post(api::fs::copy))
        .route("/move", post(api::fs::move_entry))
        .route("/delete", post(api::fs::delete));

    let protected = Router::new()
        .nest("/v1/fs", fs)
        .route("/auth/me", get(api::auth::me))
        .route_layer(axum::middleware::from_fn_with_state(
            state.clone(),
            middleware::require_auth,
        ));

    let api = Router::new()
        .route("/info", get(api::info::get_info))
        .route("/auth/login", post(api::auth::login))
        .merge(protected);

    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods([Method::GET, Method::POST, Method::OPTIONS])
        .allow_headers([header::AUTHORIZATION, header::CONTENT_TYPE, header::RANGE])
        .expose_headers([
            header::CONTENT_RANGE,
            header::ACCEPT_RANGES,
            header::CONTENT_DISPOSITION,
            header::RETRY_AFTER,
        ]);

    Router::new()
        .route("/", get(api::info::root))
        .nest("/api", api)
        .fallback(|| async { LunarError::NotFound })
        .layer(cors)
        .layer(GlobalConcurrencyLimitLayer::new(
            state.config.server.max_concurrent_requests,
        ))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

pub async fn run_server(config: LunarConfig) -> anyhow::Result<()> {
    tracing::warn!("passwords are stored in plain text in the config file");
    if !config.auth.enabled {
        tracing::warn!("authentication is DISABLED: anyone who can reach the server has full access to all roots");
    }

    let addr = format!("{}:{}", config.server.host, config.server.port);
    let show_qr = config.server.show_qr;
    let state = AppState::new(config);
    for root in state.roots().roots() {
        tracing::info!(name = %root.name, path = %root.path.display(), "sharing root");
    }
    if state.roots().roots().is_empty() {
        tracing::warn!("no roots are available; clients will not be able to browse anything");
    }

    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .with_context(|| format!("could not listen on {addr} (is the port already in use?)"))?;
    tracing::info!("listening on http://{addr}");
    if show_qr {
        print_connect_qr(&state.config.server.host, state.config.server.port);
    }

    axum::serve(listener, build_app(state))
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
            tracing::info!("shutting down");
        })
        .await
        .context("server error")
}

fn print_connect_qr(host: &str, port: u16) {
    let host = if host == "0.0.0.0" || host == "::" {
        match local_ip_address::local_ip() {
            Ok(ip) => ip.to_string(),
            Err(e) => {
                tracing::warn!(error = %e, "could not find the LAN address, skipping QR code");
                return;
            }
        }
    } else {
        host.to_string()
    };
    let url = format!("http://{host}:{port}");
    let Ok(qr) = QrCode::encode_text(&url, QrCodeEcc::Medium) else {
        return;
    };
    let border = 4;
    let mut out = String::new();
    for y in -border..qr.size() + border {
        for x in -border..qr.size() + border {
            out.push_str(if qr.get_module(x, y) { "██" } else { "  " });
        }
        out.push('\n');
    }
    println!("{out}\n{url}");
}
