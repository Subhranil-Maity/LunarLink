use lunar_lib::config::read_write::{load_config, save_default_config};

fn main() {
    // save_default_config("config.toml").unwrap()
    let config = load_config("config.toml").unwrap();
    println!("{:#?}", config);
}
