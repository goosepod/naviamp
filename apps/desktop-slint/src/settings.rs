use crate::storage::StoragePaths;
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::fs;

#[derive(Clone, Default, Serialize, Deserialize)]
pub struct Settings {
    pub server_url: String,
    pub username: String,
    pub password: String,
}

impl Settings {
    pub fn validate_for_connection(&self) -> Result<()> {
        if self.server_url.trim().is_empty() {
            return Err(anyhow!("server URL is empty"));
        }
        if self.username.trim().is_empty() {
            return Err(anyhow!("username is empty"));
        }
        if self.password.is_empty() {
            return Err(anyhow!("password is empty"));
        }
        Ok(())
    }
}

pub fn load_settings() -> Result<Settings> {
    let path = StoragePaths::new()?.settings_file();
    let content = fs::read_to_string(path)?;
    Ok(toml::from_str(&content)?)
}

pub fn save_settings(settings: &Settings) -> Result<()> {
    let paths = StoragePaths::new()?;
    paths.ensure_base_dirs()?;
    let path = paths.settings_file();
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, toml::to_string_pretty(settings)?)?;
    Ok(())
}
