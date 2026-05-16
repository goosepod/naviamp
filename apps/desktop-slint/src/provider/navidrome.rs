use crate::domain::{Album, AlbumDetail, Artist, ArtistDetail, SearchResults, Track};
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
        self.request_json_with_params(endpoint, &[])
    }

    fn request_json_with_params(
        &self,
        endpoint: &str,
        params: &[(&str, &str)],
    ) -> Result<serde_json::Value> {
        self.client
            .get(self.api_url(endpoint))
            .query(params)
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
        let response = self.request_json_with_params(
            "search3.view",
            &[
                ("query", query),
                ("artistCount", "20"),
                ("albumCount", "20"),
                ("songCount", "50"),
            ],
        )?;

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

    fn album(&self, album_id: &str) -> Result<AlbumDetail> {
        let response = self.request_json_with_params("getAlbum.view", &[("id", album_id)])?;
        let root = Self::response_root(&response)?;
        let album = root.get("album").ok_or_else(|| anyhow!("missing album"))?;

        Ok(parse_album_detail(album))
    }

    fn artist(&self, artist_id: &str) -> Result<ArtistDetail> {
        let response = self.request_json_with_params("getArtist.view", &[("id", artist_id)])?;
        let root = Self::response_root(&response)?;
        let artist = root
            .get("artist")
            .ok_or_else(|| anyhow!("missing artist"))?;

        Ok(parse_artist_detail(artist))
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
        .filter_map(|artist| parse_artist_summary(&artist))
        .collect()
}

fn parse_albums(result: &serde_json::Value) -> Vec<Album> {
    json_array(result, "album")
        .into_iter()
        .filter_map(|album| parse_album_summary(&album))
        .collect()
}

fn parse_tracks(result: &serde_json::Value) -> Vec<Track> {
    json_array(result, "song")
        .into_iter()
        .filter_map(|song| parse_track(&song))
        .collect()
}

fn parse_artist_summary(artist: &serde_json::Value) -> Option<Artist> {
    Some(Artist {
        id: artist.get("id")?.as_str()?.to_string(),
        name: artist
            .get("name")
            .and_then(|value| value.as_str())
            .unwrap_or("Unknown Artist")
            .to_string(),
    })
}

fn parse_album_summary(album: &serde_json::Value) -> Option<Album> {
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
}

fn parse_track(song: &serde_json::Value) -> Option<Track> {
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
}

fn parse_album_detail(album: &serde_json::Value) -> AlbumDetail {
    AlbumDetail {
        album: parse_album_summary(album).unwrap_or_else(|| Album {
            id: String::new(),
            title: "Untitled Album".to_string(),
            artist: String::new(),
        }),
        tracks: parse_tracks(album),
    }
}

fn parse_artist_detail(artist: &serde_json::Value) -> ArtistDetail {
    ArtistDetail {
        artist: parse_artist_summary(artist).unwrap_or_else(|| Artist {
            id: String::new(),
            name: "Unknown Artist".to_string(),
        }),
        albums: parse_albums(artist),
    }
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
    use super::{parse_album_detail, parse_artist_detail, NavidromeAuth, CLIENT_NAME};
    use serde_json::json;

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

    #[test]
    fn parses_album_detail_tracks() {
        let album = json!({
            "id": "album-1",
            "name": "Blue Record",
            "artist": "The Example",
            "song": [
                { "id": "song-1", "title": "One", "artist": "The Example", "album": "Blue Record" }
            ]
        });

        let detail = parse_album_detail(&album);

        assert_eq!("album-1", detail.album.id);
        assert_eq!("Blue Record", detail.album.title);
        assert_eq!(1, detail.tracks.len());
        assert_eq!("song-1", detail.tracks[0].id);
    }

    #[test]
    fn parses_artist_detail_albums() {
        let artist = json!({
            "id": "artist-1",
            "name": "The Example",
            "album": [
                { "id": "album-1", "name": "Blue Record", "artist": "The Example" }
            ]
        });

        let detail = parse_artist_detail(&artist);

        assert_eq!("artist-1", detail.artist.id);
        assert_eq!("The Example", detail.artist.name);
        assert_eq!(1, detail.albums.len());
        assert_eq!("album-1", detail.albums[0].id);
    }
}
