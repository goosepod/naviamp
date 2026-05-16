use anyhow::{anyhow, Result};
use directories::ProjectDirs;
use std::fs;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct StoragePaths {
    config_dir: PathBuf,
    data_dir: PathBuf,
    cache_dir: PathBuf,
}

impl StoragePaths {
    pub fn new() -> Result<Self> {
        let dirs = ProjectDirs::from("app", "naviamp", "Naviamp Slint")
            .ok_or_else(|| anyhow!("could not find app storage directories"))?;

        Ok(Self {
            config_dir: dirs.config_dir().to_path_buf(),
            data_dir: dirs.data_dir().to_path_buf(),
            cache_dir: dirs.cache_dir().to_path_buf(),
        })
    }

    pub fn ensure_base_dirs(&self) -> Result<()> {
        fs::create_dir_all(&self.config_dir)?;
        fs::create_dir_all(&self.data_dir)?;
        fs::create_dir_all(&self.cache_dir)?;
        Ok(())
    }

    pub fn settings_file(&self) -> PathBuf {
        self.config_dir.join("settings.toml")
    }
}

#[cfg(test)]
mod tests {
    use super::StoragePaths;

    #[test]
    fn settings_file_lives_under_config_dir() {
        let paths = StoragePaths {
            config_dir: "config".into(),
            data_dir: "data".into(),
            cache_dir: "cache".into(),
        };

        assert_eq!(
            paths.settings_file(),
            std::path::PathBuf::from("config/settings.toml")
        );
    }
}
