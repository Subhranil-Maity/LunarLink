use anyhow::Context;
use lunar_lib::config::{generate_default_config, LunarConfig};
use lunar_lib::server::run_server;
use std::path::PathBuf;
use std::process::ExitCode;
use tracing_subscriber::EnvFilter;

/// Usage: `lunar_link [config_path]` (default: `config.toml` in the working directory).
#[tokio::main]
async fn main() -> ExitCode {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .init();

    match run().await {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            eprintln!("error: {e:#}");
            ExitCode::FAILURE
        }
    }
}

async fn run() -> anyhow::Result<()> {
    let path = std::env::args_os()
        .nth(1)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("config.toml"));

    if !path.exists() {
        let generated = generate_default_config();
        std::fs::write(&path, &generated.toml)
            .with_context(|| format!("could not create {}", path.display()))?;
        println!(
            "Created {} with user `admin` and password `{}`. Edit it to add users and folders.",
            path.display(),
            generated.admin_password
        );
    }

    let config = LunarConfig::load(&path)?;
    run_server(config).await
}
