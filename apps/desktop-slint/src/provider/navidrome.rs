use crate::domain::Track;
use crate::provider::MediaProvider;
use crate::settings::Settings;
use anyhow::{anyhow, Context, Result};
use reqwest::blocking::Client;

#[derive(Clone)]
pub struct NavidromeProvider {
    settings: Settings,
    client: Client,
}

impl NavidromeProvider {
    pub fn new(settings: Settings) -> Result<Self> {
        settings.validate_for_connection()?;
        Ok(Self {
            settings,
            client: Client::new(),
        })
    }

    fn api_url(&self, endpoint: &str) -> String {
        let salt = "naviamp-slint";
        let token = format!(
            "{:x}",
            md5::compute(format!("{}{}", self.settings.password, salt))
        );
        format!(
            "{}/rest/{}?u={}&t={}&s={}&v=1.16.1&c=NaviampSlint&f=json",
            self.settings.server_url.trim_end_matches('/'),
            endpoint,
            urlencoding::encode(&self.settings.username),
            token,
            salt,
        )
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

impl MediaProvider for NavidromeProvider {
    fn search_tracks(&self, query: &str) -> Result<Vec<Track>> {
        let response: serde_json::Value = self
            .client
            .get(self.api_url("search3.view"))
            .query(&[
                ("query", query),
                ("artistCount", "0"),
                ("albumCount", "0"),
                ("songCount", "50"),
            ])
            .send()
            .context("request failed")?
            .error_for_status()
            .context("server returned an error")?
            .json()
            .context("invalid json")?;

        let root = Self::response_root(&response)?;
        let songs = root
            .get("searchResult3")
            .and_then(|result| result.get("song"))
            .and_then(|song| song.as_array())
            .cloned()
            .unwrap_or_default();

        Ok(songs
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
            .collect())
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
