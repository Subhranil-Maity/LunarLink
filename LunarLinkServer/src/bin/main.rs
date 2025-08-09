use std::fs::File;
use std::io::ErrorKind;
use lunar_lib::config::read_write::{load_config, save_default_config};
use lunar_lib::server::run_server;

#[tokio::main]
async fn main() {

    match File::open("config.toml") {
        Ok(_) => {}
        Err(ref e) if e.kind() == ErrorKind::NotFound => {
            println!("Config.toml not found");
            println!("Creating default config.toml");
            save_default_config("config.toml").unwrap();
        }
        Err(e) => {
            panic!("{}", e)
        }
    }

    let config = load_config("config.toml").unwrap();
    run_server(config).await.unwrap();
}