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
const APP_BACKGROUND: RgbColor = RgbColor::new(16, 17, 20);
const ALBUM_ART_PLACEHOLDER: RgbColor = RgbColor::new(67, 83, 107);

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
    pub background_start: (u8, u8, u8),
    pub background_mid: (u8, u8, u8),
    pub background_end: (u8, u8, u8),
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

    let step_x = (width / 32).max(1);
    let step_y = (height / 32).max(1);
    let mut buckets = HashMap::<u16, ColorBucket>::new();
    for y in (0..height).step_by(step_y as usize) {
        for x in (0..width).step_by(step_x as usize) {
            let pixel = image.get_pixel(x, y).0;
            if pixel[3] <= 200 {
                continue;
            }

            let hsv = rgb_to_hsv(pixel[0], pixel[1], pixel[2]);
            if hsv.saturation <= 0.06 || !(0.12..=0.96).contains(&hsv.value) {
                continue;
            }

            let key = (u16::from(pixel[0] / 32) << 10)
                | (u16::from(pixel[1] / 32) << 5)
                | u16::from(pixel[2] / 32);
            buckets.entry(key).or_default().add(
                pixel[0],
                pixel[1],
                pixel[2],
                hsv.saturation,
                hsv.value,
            );
        }
    }

    album_palette_from_buckets(buckets.values()).unwrap_or_default()
}

impl Default for CoverArtPalette {
    fn default() -> Self {
        player_palette_from_album_palette(AlbumPalette::fallback(ALBUM_ART_PLACEHOLDER))
    }
}

#[derive(Clone, Copy, Debug)]
struct AlbumPalette {
    primary: RgbColor,
    secondary: RgbColor,
    accent: RgbColor,
}

impl AlbumPalette {
    fn fallback(color: RgbColor) -> Self {
        Self {
            primary: color,
            secondary: color.mix(RgbColor::new(124, 18, 50), 0.45),
            accent: color,
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
struct ColorBucket {
    red: u64,
    green: u64,
    blue: u64,
    saturation: f64,
    brightness: f64,
    count: u64,
}

impl ColorBucket {
    fn add(&mut self, red: u8, green: u8, blue: u8, saturation: f32, brightness: f32) {
        self.red += u64::from(red);
        self.green += u64::from(green);
        self.blue += u64::from(blue);
        self.saturation += f64::from(saturation);
        self.brightness += f64::from(brightness);
        self.count += 1;
    }

    fn color(self) -> RgbColor {
        RgbColor::new(
            (self.red / self.count) as u8,
            (self.green / self.count) as u8,
            (self.blue / self.count) as u8,
        )
    }

    fn score(self) -> f64 {
        let saturation_average = self.saturation_average() as f64;
        let brightness_average = self.brightness_average() as f64;
        let brightness_score = 1.0 - (brightness_average - 0.58).abs().min(0.58) / 0.58;
        (self.count as f64).powf(0.55) * (saturation_average + 0.12) * (brightness_score + 0.55)
    }

    fn accent_score(self, primary: ColorBucket) -> f64 {
        self.score()
            * (1.0 + f64::from(self.saturation_average()))
            * (1.0 + f64::from(self.color_distance(primary)))
    }

    fn saturation_average(self) -> f32 {
        (self.saturation / self.count as f64) as f32
    }

    fn brightness_average(self) -> f32 {
        (self.brightness / self.count as f64) as f32
    }

    fn hue_distance(self, other: ColorBucket) -> f32 {
        self.color().hue_distance(other.color())
    }

    fn color_distance(self, other: ColorBucket) -> f32 {
        self.color().color_distance(other.color())
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
struct RgbColor {
    red: f32,
    green: f32,
    blue: f32,
}

impl RgbColor {
    const fn new(red: u8, green: u8, blue: u8) -> Self {
        Self {
            red: red as f32 / 255.0,
            green: green as f32 / 255.0,
            blue: blue as f32 / 255.0,
        }
    }

    fn mix(self, other: RgbColor, amount: f32) -> Self {
        let amount = amount.clamp(0.0, 1.0);
        Self {
            red: self.red + (other.red - self.red) * amount,
            green: self.green + (other.green - self.green) * amount,
            blue: self.blue + (other.blue - self.blue) * amount,
        }
    }

    fn shift_hue(self, amount: f32) -> Self {
        let hsv = rgb_to_hsv(
            (self.red * 255.0) as u8,
            (self.green * 255.0) as u8,
            (self.blue * 255.0) as u8,
        );
        hsv_to_rgb(
            (hsv.hue + amount).rem_euclid(1.0),
            (hsv.saturation * 1.08).clamp(0.0, 1.0),
            (hsv.value * 0.9).clamp(0.0, 1.0),
        )
    }

    fn hue_distance(self, other: RgbColor) -> f32 {
        let hsv = rgb_to_hsv(
            (self.red * 255.0) as u8,
            (self.green * 255.0) as u8,
            (self.blue * 255.0) as u8,
        );
        let other_hsv = rgb_to_hsv(
            (other.red * 255.0) as u8,
            (other.green * 255.0) as u8,
            (other.blue * 255.0) as u8,
        );
        let distance = (hsv.hue - other_hsv.hue).abs();
        distance.min(1.0 - distance)
    }

    fn color_distance(self, other: RgbColor) -> f32 {
        let red_distance = self.red - other.red;
        let green_distance = self.green - other.green;
        let blue_distance = self.blue - other.blue;
        red_distance * red_distance
            + green_distance * green_distance
            + blue_distance * blue_distance
    }

    fn to_tuple(self) -> (u8, u8, u8) {
        (
            (self.red.clamp(0.0, 1.0) * 255.0).round() as u8,
            (self.green.clamp(0.0, 1.0) * 255.0).round() as u8,
            (self.blue.clamp(0.0, 1.0) * 255.0).round() as u8,
        )
    }
}

#[derive(Clone, Copy, Debug)]
struct Hsv {
    hue: f32,
    saturation: f32,
    value: f32,
}

fn album_palette_from_buckets<'a>(
    buckets: impl Iterator<Item = &'a ColorBucket>,
) -> Option<CoverArtPalette> {
    let mut candidates = buckets
        .copied()
        .filter(|bucket| bucket.count >= 2)
        .collect::<Vec<_>>();
    candidates.sort_by(|left, right| right.score().total_cmp(&left.score()));

    let primary = candidates.first().copied()?;
    let secondary = candidates
        .iter()
        .copied()
        .find(|candidate| primary.color_distance(*candidate) > 0.045)
        .or_else(|| {
            candidates
                .iter()
                .copied()
                .find(|candidate| primary.hue_distance(*candidate) > 0.08)
        })
        .or_else(|| candidates.get(1).copied())
        .unwrap_or(primary);
    let accent = candidates
        .iter()
        .copied()
        .filter(|candidate| {
            primary.color_distance(*candidate) > 0.025 || primary.hue_distance(*candidate) > 0.06
        })
        .max_by(|left, right| {
            left.accent_score(primary)
                .total_cmp(&right.accent_score(primary))
        })
        .unwrap_or(primary);

    Some(player_palette_from_album_palette(AlbumPalette {
        primary: primary.color(),
        secondary: secondary.color(),
        accent: accent.color(),
    }))
}

fn player_palette_from_album_palette(palette: AlbumPalette) -> CoverArtPalette {
    let secondary = if palette.primary.hue_distance(palette.secondary) < 0.08 {
        palette.secondary.shift_hue(0.09)
    } else {
        palette.secondary
    };
    let left = palette
        .primary
        .mix(
            RgbColor {
                red: 1.0,
                green: 1.0,
                blue: 1.0,
            },
            0.03,
        )
        .mix(
            RgbColor {
                red: 0.0,
                green: 0.0,
                blue: 0.0,
            },
            0.34,
        )
        .mix(APP_BACKGROUND, 0.16);
    let middle = palette
        .accent
        .mix(palette.primary, 0.28)
        .mix(
            RgbColor {
                red: 0.0,
                green: 0.0,
                blue: 0.0,
            },
            0.42,
        )
        .mix(APP_BACKGROUND, 0.10);
    let right = secondary
        .mix(
            RgbColor {
                red: 0.0,
                green: 0.0,
                blue: 0.0,
            },
            0.66,
        )
        .mix(APP_BACKGROUND, 0.12);

    CoverArtPalette {
        background_start: left.to_tuple(),
        background_mid: middle.to_tuple(),
        background_end: right.to_tuple(),
        accent: palette
            .accent
            .mix(
                RgbColor {
                    red: 1.0,
                    green: 1.0,
                    blue: 1.0,
                },
                0.08,
            )
            .to_tuple(),
        surface: right.to_tuple(),
    }
}

fn rgb_to_hsv(red: u8, green: u8, blue: u8) -> Hsv {
    let red = f32::from(red) / 255.0;
    let green = f32::from(green) / 255.0;
    let blue = f32::from(blue) / 255.0;
    let max = red.max(green).max(blue);
    let min = red.min(green).min(blue);
    let delta = max - min;
    let hue = if delta == 0.0 {
        0.0
    } else if max == red {
        ((green - blue) / delta).rem_euclid(6.0) / 6.0
    } else if max == green {
        (((blue - red) / delta) + 2.0) / 6.0
    } else {
        (((red - green) / delta) + 4.0) / 6.0
    };
    let saturation = if max == 0.0 { 0.0 } else { delta / max };
    Hsv {
        hue,
        saturation,
        value: max,
    }
}

fn hsv_to_rgb(hue: f32, saturation: f32, value: f32) -> RgbColor {
    let h = (hue.clamp(0.0, 1.0) * 6.0).rem_euclid(6.0);
    let c = value * saturation;
    let x = c * (1.0 - ((h.rem_euclid(2.0)) - 1.0).abs());
    let m = value - c;
    let (red, green, blue) = if h < 1.0 {
        (c, x, 0.0)
    } else if h < 2.0 {
        (x, c, 0.0)
    } else if h < 3.0 {
        (0.0, c, x)
    } else if h < 4.0 {
        (0.0, x, c)
    } else if h < 5.0 {
        (x, 0.0, c)
    } else {
        (c, 0.0, x)
    };
    RgbColor {
        red: red + m,
        green: green + m,
        blue: blue + m,
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
                background_start: (43, 52, 65),
                background_mid: (37, 45, 58),
                background_end: (30, 18, 27),
                accent: (82, 97, 119),
                surface: (30, 18, 27),
            }
        );
    }
}
