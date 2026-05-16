use crate::storage::StoragePaths;
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

const CURRENT_CONFIG_VERSION: u32 = 1;

#[derive(Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub config_version: u32,
    pub active_source_id: Option<String>,
    pub sources: Vec<SavedMediaSource>,
    pub app: AppPreferences,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            config_version: CURRENT_CONFIG_VERSION,
            active_source_id: None,
            sources: Vec::new(),
            app: AppPreferences::default(),
        }
    }
}

impl Settings {
    pub fn active_source(&self) -> Option<SavedMediaSource> {
        let active_source_id = self.active_source_id.as_ref()?;
        self.sources
            .iter()
            .find(|source| &source.id == active_source_id)
            .cloned()
    }

    pub fn upsert_source(&mut self, source: SavedMediaSource) {
        self.active_source_id = Some(source.id.clone());
        if let Some(existing) = self
            .sources
            .iter_mut()
            .find(|existing| existing.id == source.id)
        {
            *existing = source;
        } else {
            self.sources.push(source);
        }
    }

    pub fn activate_source_by_index(&mut self, index: usize) -> Option<SavedMediaSource> {
        let source = self.sources.get(index)?.clone();
        self.active_source_id = Some(source.id.clone());
        Some(source)
    }

    pub fn remove_source_by_index(&mut self, index: usize) -> Option<SavedMediaSource> {
        if index >= self.sources.len() {
            return None;
        }
        let removed = self.sources.remove(index);
        if self.active_source_id.as_deref() == Some(&removed.id) {
            self.active_source_id = self.sources.first().map(|source| source.id.clone());
        }
        Some(removed)
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum MediaSourceKind {
    Navidrome,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct SavedMediaSource {
    pub id: String,
    pub display_name: String,
    pub kind: MediaSourceKind,
    pub server_url: String,
    pub username: String,
    pub token: String,
    pub salt: String,
    pub tls: TlsSettings,
}

impl Default for SavedMediaSource {
    fn default() -> Self {
        Self {
            id: String::new(),
            display_name: String::new(),
            kind: MediaSourceKind::Navidrome,
            server_url: String::new(),
            username: String::new(),
            token: String::new(),
            salt: String::new(),
            tls: TlsSettings::default(),
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct TlsSettings {
    pub certificate_validation: CertificateValidation,
}

impl Default for TlsSettings {
    fn default() -> Self {
        Self {
            certificate_validation: CertificateValidation::Default,
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum CertificateValidation {
    #[default]
    Default,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct AppPreferences {
    pub window: WindowPreferences,
    pub last_route: String,
}

impl Default for AppPreferences {
    fn default() -> Self {
        Self {
            window: WindowPreferences::default(),
            last_route: "search".to_string(),
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct WindowPreferences {
    pub width: Option<u32>,
    pub height: Option<u32>,
}

#[derive(Clone, Default)]
pub struct ConnectionDraft {
    pub server_url: String,
    pub username: String,
    pub password: String,
}

impl ConnectionDraft {
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

    pub fn normalized_server_url(&self) -> String {
        normalize_server_url(&self.server_url)
    }

    pub fn to_saved_source(&self, token: String, salt: String) -> Result<SavedMediaSource> {
        self.validate_for_connection()?;
        let server_url = self.normalized_server_url();
        let username = self.username.trim().to_string();
        let id = stable_source_id(MediaSourceKind::Navidrome, &server_url, &username);
        Ok(SavedMediaSource {
            id,
            display_name: display_name_for_source(&server_url, &username),
            kind: MediaSourceKind::Navidrome,
            server_url,
            username,
            token,
            salt,
            tls: TlsSettings::default(),
        })
    }
}

pub trait SettingsStore: Send + Sync {
    fn load(&self) -> Result<Settings>;

    fn save(&self, settings: &Settings) -> Result<()>;
}

#[derive(Clone, Debug)]
pub struct TomlSettingsStore {
    path: PathBuf,
}

impl TomlSettingsStore {
    pub fn from_storage_paths(paths: StoragePaths) -> Self {
        Self {
            path: paths.settings_file(),
        }
    }
}

impl SettingsStore for TomlSettingsStore {
    fn load(&self) -> Result<Settings> {
        let content = fs::read_to_string(&self.path)?;
        Ok(toml::from_str(&content)?)
    }

    fn save(&self, settings: &Settings) -> Result<()> {
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(&self.path, toml::to_string_pretty(settings)?)?;
        Ok(())
    }
}

pub fn default_settings_store() -> Result<TomlSettingsStore> {
    let paths = StoragePaths::new()?;
    paths.ensure_base_dirs()?;
    Ok(TomlSettingsStore::from_storage_paths(paths))
}

fn normalize_server_url(value: &str) -> String {
    value.trim().trim_end_matches('/').to_string()
}

fn stable_source_id(kind: MediaSourceKind, server_url: &str, username: &str) -> String {
    let kind = match kind {
        MediaSourceKind::Navidrome => "navidrome",
    };
    format!(
        "{:x}",
        md5::compute(format!("{kind}|{}|{}", server_url, username.to_lowercase()))
    )
}

fn display_name_for_source(server_url: &str, username: &str) -> String {
    format!("{username} @ {server_url}")
}

#[cfg(test)]
mod tests {
    use super::{
        CertificateValidation, ConnectionDraft, MediaSourceKind, Settings, SettingsStore,
        TomlSettingsStore, CURRENT_CONFIG_VERSION,
    };

    #[test]
    fn connection_draft_normalizes_server_url() {
        let draft = ConnectionDraft {
            server_url: " https://music.example.com/// ".to_string(),
            username: "me".to_string(),
            password: "password".to_string(),
        };

        assert_eq!("https://music.example.com", draft.normalized_server_url());
    }

    #[test]
    fn saved_source_uses_stable_source_id() {
        let draft = ConnectionDraft {
            server_url: "https://music.example.com".to_string(),
            username: "Me".to_string(),
            password: "password".to_string(),
        };

        let source = draft
            .to_saved_source("token".to_string(), "salt".to_string())
            .expect("valid source");

        assert_eq!(MediaSourceKind::Navidrome, source.kind);
        assert_eq!(source.id.len(), 32);
        assert_eq!("Me @ https://music.example.com", source.display_name);
    }

    #[test]
    fn removing_active_source_selects_next_source() {
        let first = ConnectionDraft {
            server_url: "https://one.example.com".to_string(),
            username: "me".to_string(),
            password: "password".to_string(),
        }
        .to_saved_source("token-1".to_string(), "salt".to_string())
        .expect("first source");
        let second = ConnectionDraft {
            server_url: "https://two.example.com".to_string(),
            username: "me".to_string(),
            password: "password".to_string(),
        }
        .to_saved_source("token-2".to_string(), "salt".to_string())
        .expect("second source");
        let second_id = second.id.clone();
        let mut settings = Settings::default();
        settings.upsert_source(first);
        settings.upsert_source(second);
        settings.activate_source_by_index(0);

        settings.remove_source_by_index(0);

        assert_eq!(Some(second_id), settings.active_source_id);
    }

    #[test]
    fn toml_settings_store_round_trips_settings() {
        let temp_dir =
            std::env::temp_dir().join(format!("naviamp-settings-test-{}", std::process::id()));
        let path = temp_dir.join("settings.toml");
        let store = TomlSettingsStore { path };
        let mut settings = Settings::default();
        settings.upsert_source(
            ConnectionDraft {
                server_url: "https://music.example.com".to_string(),
                username: "me".to_string(),
                password: "password".to_string(),
            }
            .to_saved_source("token".to_string(), "salt".to_string())
            .expect("source"),
        );

        store.save(&settings).expect("save settings");
        let loaded = store.load().expect("load settings");

        assert_eq!(settings.active_source_id, loaded.active_source_id);
        assert_eq!(1, loaded.sources.len());
        let _ = std::fs::remove_dir_all(temp_dir);
    }

    #[test]
    fn settings_loads_without_newer_optional_fields() {
        let settings: Settings = toml::from_str(
            r#"
active_source_id = "source-1"

[[sources]]
id = "source-1"
display_name = "me @ https://music.example.com"
kind = "Navidrome"
server_url = "https://music.example.com"
username = "me"
token = "token"
salt = "salt"
"#,
        )
        .expect("settings should deserialize with defaults");

        assert_eq!(CURRENT_CONFIG_VERSION, settings.config_version);
        assert_eq!("search", settings.app.last_route);
        assert_eq!(
            CertificateValidation::Default,
            settings.sources[0].tls.certificate_validation
        );
    }
}
