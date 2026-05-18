use crate::storage::StoragePaths;
use anyhow::{Context, Result};
use image::GenericImageView;
use reqwest::blocking::Client;
use rusqlite::{params, Connection, OptionalExtension};
use std::collections::HashMap;
use std::fs;
use std::io::Cursor;
use std::path::PathBuf;
use std::sync::Mutex;

const COVER_ART_SIZE: u32 = 300;

#[derive(Clone)]
pub struct CoverArtCache {
    cache_dir: PathBuf,
    database_path: PathBuf,
    client: Client,
    hot_cache: std::sync::Arc<Mutex<HashMap<String, CachedCoverArt>>>,
}

#[derive(Clone, Debug)]
pub struct CachedCoverArt {
    pub path: PathBuf,
    pub palette: CoverArtPalette,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CoverArtPalette {
    pub accent: (u8, u8, u8),
    pub surface: (u8, u8, u8),
}

impl CoverArtCache {
    pub fn from_storage_paths(paths: StoragePaths) -> Self {
        Self {
            cache_dir: paths.image_cache_dir(),
            database_path: paths.image_cache_database(),
            client: Client::new(),
            hot_cache: std::sync::Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub fn cover_art_size(&self) -> u32 {
        COVER_ART_SIZE
    }

    pub fn fetch(&self, url: &str) -> Result<CachedCoverArt> {
        if let Some(cached) = self.hot_get(url) {
            return Ok(cached);
        }

        fs::create_dir_all(&self.cache_dir)?;
        for extension in ["jpg", "png", "gif", "webp"] {
            let path = self.cache_dir.join(cache_file_name(url, extension));
            if path.is_file() {
                let bytes = fs::read(&path).context("cached cover art read failed")?;
                let cached = CachedCoverArt {
                    path,
                    palette: sample_palette(&bytes),
                };
                self.hot_put(url, cached.clone());
                return Ok(cached);
            }
        }

        if let Some((extension, bytes)) = self.sqlite_get(url)? {
            let path = self.cache_dir.join(cache_file_name(url, &extension));
            fs::write(&path, &bytes)?;
            let cached = CachedCoverArt {
                path,
                palette: sample_palette(&bytes),
            };
            self.hot_put(url, cached.clone());
            return Ok(cached);
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
        fs::write(&path, &bytes)?;
        self.sqlite_put(url, extension, &bytes)?;
        let cached = CachedCoverArt {
            path,
            palette: sample_palette(&bytes),
        };
        self.hot_put(url, cached.clone());
        Ok(cached)
    }

    pub fn store_embedded(&self, key: &str, bytes: &[u8]) -> Result<CachedCoverArt> {
        fs::create_dir_all(&self.cache_dir)?;
        let extension = image_extension(bytes).unwrap_or("img");
        let cache_key = format!("embedded:{key}:{:x}", md5::compute(bytes));
        if let Some(cached) = self.hot_get(&cache_key) {
            return Ok(cached);
        }
        let path = self.cache_dir.join(cache_file_name(&cache_key, extension));
        if !path.is_file() {
            fs::write(&path, bytes)?;
        }

        self.sqlite_put(&cache_key, extension, bytes)?;
        let cached = CachedCoverArt {
            path,
            palette: sample_palette(bytes),
        };
        self.hot_put(&cache_key, cached.clone());
        Ok(cached)
    }

    fn hot_get(&self, key: &str) -> Option<CachedCoverArt> {
        self.hot_cache.lock().ok()?.get(key).cloned()
    }

    fn hot_put(&self, key: &str, value: CachedCoverArt) {
        if let Ok(mut cache) = self.hot_cache.lock() {
            if cache.len() >= 64 {
                if let Some(first_key) = cache.keys().next().cloned() {
                    cache.remove(&first_key);
                }
            }
            cache.insert(key.to_string(), value);
        }
    }

    fn sqlite_connection(&self) -> Result<Connection> {
        if let Some(parent) = self.database_path.parent() {
            fs::create_dir_all(parent)?;
        }
        let connection = Connection::open(&self.database_path)?;
        connection.execute(
            "CREATE TABLE IF NOT EXISTS image_cache (
                cache_key TEXT PRIMARY KEY,
                extension TEXT NOT NULL,
                bytes BLOB NOT NULL,
                updated_at INTEGER NOT NULL DEFAULT (unixepoch())
            )",
            [],
        )?;
        Ok(connection)
    }

    fn sqlite_get(&self, key: &str) -> Result<Option<(String, Vec<u8>)>> {
        let connection = self.sqlite_connection()?;
        connection
            .query_row(
                "SELECT extension, bytes FROM image_cache WHERE cache_key = ?1",
                params![key],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()
            .map_err(Into::into)
    }

    fn sqlite_put(&self, key: &str, extension: &str, bytes: &[u8]) -> Result<()> {
        let connection = self.sqlite_connection()?;
        connection.execute(
            "INSERT INTO image_cache(cache_key, extension, bytes, updated_at)
             VALUES (?1, ?2, ?3, unixepoch())
             ON CONFLICT(cache_key) DO UPDATE SET
                extension = excluded.extension,
                bytes = excluded.bytes,
                updated_at = excluded.updated_at",
            params![key, extension, bytes],
        )?;
        Ok(())
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

fn sample_palette(bytes: &[u8]) -> CoverArtPalette {
    let Ok(reader) = image::ImageReader::new(Cursor::new(bytes)).with_guessed_format() else {
        return CoverArtPalette::default();
    };
    let Ok(image) = reader.decode() else {
        return CoverArtPalette::default();
    };

    let (width, height) = image.dimensions();
    if width == 0 || height == 0 {
        return CoverArtPalette::default();
    }

    let step_x = (width / 24).max(1);
    let step_y = (height / 24).max(1);
    let mut red: u64 = 0;
    let mut green: u64 = 0;
    let mut blue: u64 = 0;
    let mut count: u64 = 0;
    for y in (0..height).step_by(step_y as usize) {
        for x in (0..width).step_by(step_x as usize) {
            let pixel = image.get_pixel(x, y).0;
            red += u64::from(pixel[0]);
            green += u64::from(pixel[1]);
            blue += u64::from(pixel[2]);
            count += 1;
        }
    }
    if count == 0 {
        return CoverArtPalette::default();
    }

    let accent = (
        ((red / count) as u8).saturating_add(24),
        ((green / count) as u8).saturating_add(24),
        ((blue / count) as u8).saturating_add(24),
    );
    let surface = (
        ((u16::from(accent.0) * 20 / 100) as u8).max(18),
        ((u16::from(accent.1) * 20 / 100) as u8).max(20),
        ((u16::from(accent.2) * 20 / 100) as u8).max(24),
    );
    CoverArtPalette { accent, surface }
}

impl Default for CoverArtPalette {
    fn default() -> Self {
        Self {
            accent: (127, 199, 232),
            surface: (21, 25, 31),
        }
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

    #[test]
    fn default_palette_is_restrained() {
        assert_eq!(
            super::CoverArtPalette::default(),
            super::CoverArtPalette {
                accent: (127, 199, 232),
                surface: (21, 25, 31),
            }
        );
    }
}
