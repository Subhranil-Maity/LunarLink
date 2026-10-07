use crate::config::LunarConfig;
use crate::server::state::AppState;
use axum::extract::State;
use axum::Json;
use serde::Serialize;
use std::sync::Arc;
use sysinfo::System;

pub const API_VERSION: u32 = 1;
pub const SERVER_VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Serialize)]
pub struct Info {
    name: String,
    os_type: &'static str,
    os_version: String,
    server_version: &'static str,
    api_version: u32,
    auth_required: bool,
    /// Optional capabilities, so clients can hide what an older server doesn't support.
    features: Vec<&'static str>,
}

/// Public details shown before login. Never includes the OS user name.
pub async fn get_info(State(state): State<Arc<AppState>>) -> Json<Info> {
    let name = display_name(&state.config);
    Json(Info {
        name,
        os_type: std::env::consts::OS,
        os_version: System::os_version().unwrap_or_else(|| "unknown".into()),
        server_version: SERVER_VERSION,
        api_version: API_VERSION,
        auth_required: state.config.auth.enabled,
        features: features(&state),
    })
}

/// `server.display_name`, or the machine's host name.
pub fn display_name(config: &LunarConfig) -> String {
    config
        .server
        .display_name
        .clone()
        .or_else(System::host_name)
        .unwrap_or_else(|| "LunarLink".into())
}

fn features(state: &AppState) -> Vec<&'static str> {
    let mut features = vec!["search", "jobs"];
    if state.thumbnails.is_some() {
        features.push("thumbnails");
    }
    features
}

pub async fn root() -> String {
    format!("LunarLink {SERVER_VERSION}")
}
