use lunar_lib::config::read_write::{load_config, save_default_config};
use lunar_lib::server::run_server;

#[tokio::main]
async fn main() {
    // save_default_config("config.toml").unwrap();
    let config = load_config("config.toml").unwrap();
    run_server(config).await.unwrap();
}
