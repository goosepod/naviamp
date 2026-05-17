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
        for extension in ["jpg", "png", "gif", "webp"] {
            let path = self.cache_dir.join(cache_file_name(url, extension));
            if path.is_file() {
                return Ok(path);
            }
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
        let extension = image_extension(&bytes).unwrap_or("img");
        let path = self.cache_dir.join(cache_file_name(url, extension));
        fs::write(&path, bytes)?;
        Ok(path)
    }

    pub fn store_embedded(&self, key: &str, bytes: &[u8]) -> Result<PathBuf> {
        fs::create_dir_all(&self.cache_dir)?;
        let extension = image_extension(bytes).unwrap_or("img");
        let cache_key = format!("embedded:{key}:{:x}", md5::compute(bytes));
        let path = self.cache_dir.join(cache_file_name(&cache_key, extension));
        if path.is_file() {
            return Ok(path);
        }

        fs::write(&path, bytes)?;
        Ok(path)
    }
}

pub fn default_cover_art_cache() -> Result<CoverArtCache> {
    let paths = StoragePaths::new()?;
    paths.ensure_base_dirs()?;
    Ok(CoverArtCache::from_storage_paths(paths))
}

fn cache_file_name(key: &str, extension: &str) -> String {
    format!("{:x}.{extension}", md5::compute(key))
}

fn image_extension(bytes: &[u8]) -> Option<&'static str> {
    if bytes.starts_with(&[0xff, 0xd8, 0xff]) {
        Some("jpg")
    } else if bytes.starts_with(b"\x89PNG") {
        Some("png")
    } else if bytes.starts_with(b"GIF8") {
        Some("gif")
    } else if bytes.starts_with(b"RIFF") && bytes.get(8..12) == Some(b"WEBP") {
        Some("webp")
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn cache_file_name_is_stable() {
        assert_eq!(
            super::cache_file_name("https://music.example/cover?id=1", "jpg"),
            super::cache_file_name("https://music.example/cover?id=1", "jpg")
        );
    }

    #[test]
    fn detects_image_extensions_from_magic_bytes() {
        assert_eq!(Some("jpg"), super::image_extension(&[0xff, 0xd8, 0xff]));
        assert_eq!(Some("png"), super::image_extension(b"\x89PNG\r\n"));
        assert_eq!(Some("gif"), super::image_extension(b"GIF89a"));
        assert_eq!(Some("webp"), super::image_extension(b"RIFFxxxxWEBP"));
        assert_eq!(None, super::image_extension(b"nope"));
    }
}
