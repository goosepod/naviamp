use crate::storage::StoragePaths;
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::fs;

#[derive(Clone, Default, Serialize, Deserialize)]
pub struct Settings {
    pub active_source_id: Option<String>,
    pub sources: Vec<SavedMediaSource>,
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
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum MediaSourceKind {
    Navidrome,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct SavedMediaSource {
    pub id: String,
    pub display_name: String,
    pub kind: MediaSourceKind,
    pub server_url: String,
    pub username: String,
    pub token: String,
    pub salt: String,
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
        })
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
    use super::{ConnectionDraft, MediaSourceKind};

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
}
