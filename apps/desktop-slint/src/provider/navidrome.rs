use crate::domain::{Album, Artist, SearchResults, Track};
use crate::provider::MediaProvider;
use crate::settings::Settings;
use anyhow::{anyhow, Context, Result};
use reqwest::blocking::Client;

const API_VERSION: &str = "1.16.1";
const CLIENT_NAME: &str = "Naviamp";
const DEFAULT_SALT: &str = "naviamp";

#[derive(Clone)]
pub struct NavidromeProvider {
    settings: Settings,
    client: Client,
    auth: NavidromeAuth,
}

impl NavidromeProvider {
    pub fn new(settings: Settings) -> Result<Self> {
        settings.validate_for_connection()?;
        let auth = NavidromeAuth::from_settings(&settings);
        Ok(Self {
            settings,
            client: Client::new(),
            auth,
        })
    }

    fn api_url(&self, endpoint: &str) -> String {
        format!(
            "{}/rest/{}?u={}&t={}&s={}&v={}&c={}&f=json",
            self.settings.server_url.trim_end_matches('/'),
            endpoint,
            urlencoding::encode(&self.auth.username),
            self.auth.token(),
            urlencoding::encode(&self.auth.salt),
            API_VERSION,
            urlencoding::encode(CLIENT_NAME),
        )
    }

    fn request_json(&self, endpoint: &str) -> Result<serde_json::Value> {
        self.client
            .get(self.api_url(endpoint))
            .send()
            .context("request failed")?
            .error_for_status()
            .context("server returned an error")?
            .json()
            .context("invalid json")
    }

    fn response_root(response: &serde_json::Value) -> Result<&serde_json::Value> {
        let root = response
            .get("subsonic-response")
            .ok_or_else(|| anyhow!("missing subsonic response"))?;
        if root.get("status").and_then(|value| value.as_str()) == Some("failed") {
            let message = root
                .get("error")
                .and_then(|error| error.get("message"))
                .and_then(|value| value.as_str())
                .unwrap_or("Navidrome rejected the request");
            return Err(anyhow!(message.to_string()));
        }
        Ok(root)
    }
}

#[derive(Clone, Debug)]
struct NavidromeAuth {
    username: String,
    password: String,
    salt: String,
}

impl NavidromeAuth {
    fn from_settings(settings: &Settings) -> Self {
        Self {
            username: settings.username.clone(),
            password: settings.password.clone(),
            salt: DEFAULT_SALT.to_string(),
        }
    }

    fn token(&self) -> String {
        format!(
            "{:x}",
            md5::compute(format!("{}{}", self.password, self.salt))
        )
    }
}

impl MediaProvider for NavidromeProvider {
    fn validate_connection(&self) -> Result<()> {
        let response = self.request_json("ping.view")?;
        Self::response_root(&response)?;
        Ok(())
    }

    fn search(&self, query: &str) -> Result<SearchResults> {
        let response: serde_json::Value = self
            .client
            .get(self.api_url("search3.view"))
            .query(&[
                ("query", query),
                ("artistCount", "20"),
                ("albumCount", "20"),
                ("songCount", "50"),
            ])
            .send()
            .context("request failed")?
            .error_for_status()
            .context("server returned an error")?
            .json()
            .context("invalid json")?;

        let root = Self::response_root(&response)?;
        let result = root
            .get("searchResult3")
            .ok_or_else(|| anyhow!("missing search result"))?;

        Ok(SearchResults {
            artists: parse_artists(result),
            albums: parse_albums(result),
            tracks: parse_tracks(result),
        })
    }

    fn stream_url(&self, track_id: &str) -> Result<String> {
        self.settings.validate_for_connection()?;
        Ok(format!(
            "{}&id={}",
            self.api_url("stream.view"),
            urlencoding::encode(track_id),
        ))
    }
}

fn parse_artists(result: &serde_json::Value) -> Vec<Artist> {
    json_array(result, "artist")
        .into_iter()
        .filter_map(|artist| {
            Some(Artist {
                id: artist.get("id")?.as_str()?.to_string(),
                name: artist
                    .get("name")
                    .and_then(|value| value.as_str())
                    .unwrap_or("Unknown Artist")
                    .to_string(),
            })
        })
        .collect()
}

fn parse_albums(result: &serde_json::Value) -> Vec<Album> {
    json_array(result, "album")
        .into_iter()
        .filter_map(|album| {
            Some(Album {
                id: album.get("id")?.as_str()?.to_string(),
                title: album
                    .get("title")
                    .or_else(|| album.get("name"))
                    .and_then(|value| value.as_str())
                    .unwrap_or("Untitled Album")
                    .to_string(),
                artist: album
                    .get("artist")
                    .and_then(|value| value.as_str())
                    .unwrap_or("")
                    .to_string(),
            })
        })
        .collect()
}

fn parse_tracks(result: &serde_json::Value) -> Vec<Track> {
    json_array(result, "song")
        .into_iter()
        .filter_map(|song| {
            Some(Track {
                id: song.get("id")?.as_str()?.to_string(),
                title: song
                    .get("title")
                    .and_then(|value| value.as_str())
                    .unwrap_or("Untitled")
                    .to_string(),
                artist: song
                    .get("artist")
                    .and_then(|value| value.as_str())
                    .unwrap_or("")
                    .to_string(),
                album: song
                    .get("album")
                    .and_then(|value| value.as_str())
                    .unwrap_or("")
                    .to_string(),
            })
        })
        .collect()
}

fn json_array(result: &serde_json::Value, key: &str) -> Vec<serde_json::Value> {
    result
        .get(key)
        .and_then(|value| value.as_array())
        .cloned()
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::{NavidromeAuth, CLIENT_NAME};

    #[test]
    fn auth_token_matches_subsonic_token_formula() {
        let auth = NavidromeAuth {
            username: "user".to_string(),
            password: "password".to_string(),
            salt: "naviamp".to_string(),
        };

        assert_eq!(auth.token(), "06fe650023ce9fc5698876c8c303f6ed");
    }

    #[test]
    fn client_name_is_production_name() {
        assert_eq!(CLIENT_NAME, "Naviamp");
    }
}
