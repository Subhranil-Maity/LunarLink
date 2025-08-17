use crate::config::LunarConfig;
use std::io::Error;

pub fn load_config(path: &str) -> Result<LunarConfig, Error> {
    println!("WARNING: THE PASSWORD IS NOT ENCRYPTED, DO NOT USE IN PRODUCTION!");
    let config_content = std::fs::read_to_string(path)?;
    let config: LunarConfig = toml::from_str(&config_content)
        .map_err(|e| Error::new(std::io::ErrorKind::InvalidData, e))?;
    Ok(config)
}

pub fn save_default_config(path: &str) -> Result<(), Error> {
    let default_config = LunarConfig::default();
    let config_content = toml::to_string(&default_config)
        .map_err(|e| Error::new(std::io::ErrorKind::InvalidData, e))?;
    std::fs::write(path, config_content)?;
    Ok(())
}
