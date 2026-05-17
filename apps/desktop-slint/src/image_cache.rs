use crate::storage::StoragePaths;
use anyhow::{Context, Result};
use reqwest::blocking::Client;
use std::fs;
use std::path::PathBuf;

const COVER_ART_SIZE: u32 = 300;

#[derive(Clone)]
pub struct CoverArtCache {
    cache_dir: PathBuf,
    client: Client,
}

impl CoverArtCache {
    pub fn from_storage_paths(paths: StoragePaths) -> Self {
        Self {
            cache_dir: paths.image_cache_dir(),
            client: Client::new(),
        }
    }

    pub fn cover_art_size(&self) -> u32 {
        COVER_ART_SIZE
    }

    pub fn fetch(&self, url: &str) -> Result<PathBuf> {
        fs::create_dir_all(&self.cache_dir)?;
        let path = self.cache_dir.join(cache_file_name(url));
        if path.is_file() {
            return Ok(path);
        }

        let bytes = self
            .client
            .get(url)
            .send()
            .context("cover art request failed")?
            .error_for_status()
            .context("cover art server returned an error")?
            .bytes()
            .context("cover art bytes failed")?;
        fs::write(&path, bytes)?;
        Ok(path)
    }
}

pub fn default_cover_art_cache() -> Result<CoverArtCache> {
    let paths = StoragePaths::new()?;
    paths.ensure_base_dirs()?;
    Ok(CoverArtCache::from_storage_paths(paths))
}

fn cache_file_name(url: &str) -> String {
    format!("{:x}.img", md5::compute(url))
}

#[cfg(test)]
mod tests {
    #[test]
    fn cache_file_name_is_stable() {
        assert_eq!(
            super::cache_file_name("https://music.example/cover?id=1"),
            super::cache_file_name("https://music.example/cover?id=1")
        );
    }
}
