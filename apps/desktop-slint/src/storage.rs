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
        let dirs = ProjectDirs::from("app", "naviamp", "Naviamp")
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

    pub fn image_cache_dir(&self) -> PathBuf {
        self.cache_dir.join("images")
    }

    pub fn image_cache_database(&self) -> PathBuf {
        self.data_dir.join("image-cache.sqlite")
    }

    pub fn library_database(&self) -> PathBuf {
        self.data_dir.join("library.sqlite")
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

    #[test]
    fn image_cache_lives_under_cache_dir() {
        let paths = StoragePaths {
            config_dir: "config".into(),
            data_dir: "data".into(),
            cache_dir: "cache".into(),
        };

        assert_eq!(
            paths.image_cache_dir(),
            std::path::PathBuf::from("cache/images")
        );
    }

    #[test]
    fn image_cache_database_lives_under_data_dir() {
        let paths = StoragePaths {
            config_dir: "config".into(),
            data_dir: "data".into(),
            cache_dir: "cache".into(),
        };

        assert_eq!(
            paths.image_cache_database(),
            std::path::PathBuf::from("data/image-cache.sqlite")
        );
    }

    #[test]
    fn library_database_lives_under_data_dir() {
        let paths = StoragePaths {
            config_dir: "config".into(),
            data_dir: "data".into(),
            cache_dir: "cache".into(),
        };

        assert_eq!(
            paths.library_database(),
            std::path::PathBuf::from("data/library.sqlite")
        );
    }
}
