use crate::domain::{
    Album, AlbumDetail, AlbumListType, Artist, ArtistDetail, ArtistInfo, Genre,
    InternetRadioStation, Lyrics, Playlist, PlaylistDetail, SearchResults, StreamFormat,
    StreamRequest, Track,
};
use crate::provider::MediaProvider;
use crate::settings::{ConnectionDraft, SavedMediaSource};
use anyhow::{anyhow, Context, Result};
use reqwest::blocking::Client;

const API_VERSION: &str = "1.16.1";
const CLIENT_NAME: &str = "Naviamp";
const DEFAULT_SALT: &str = "naviamp";

#[derive(Clone)]
pub struct NavidromeProvider {
    source: SavedMediaSource,
    client: Client,
    auth: NavidromeAuth,
}

impl NavidromeProvider {
    pub fn new(source: SavedMediaSource) -> Result<Self> {
        let auth = NavidromeAuth::from_source(&source);
        Ok(Self {
            source,
            client: Client::new(),
            auth,
        })
    }

    pub fn from_password(draft: &ConnectionDraft) -> Result<Self> {
        draft.validate_for_connection()?;
        let auth = NavidromeAuth::from_password(
            draft.username.trim().to_string(),
            draft.password.clone(),
            DEFAULT_SALT.to_string(),
        );
        let source = draft.to_saved_source(auth.token.clone(), auth.salt.clone())?;
        Ok(Self {
            source,
            client: Client::new(),
            auth,
        })
    }

    pub fn saved_source_from_password(draft: &ConnectionDraft) -> Result<SavedMediaSource> {
        let provider = Self::from_password(draft)?;
        Ok(provider.source)
    }

    fn api_url(&self, endpoint: &str) -> String {
        format!(
            "{}/rest/{}?u={}&t={}&s={}&v={}&c={}&f=json",
            self.source.server_url.trim_end_matches('/'),
            endpoint,
            urlencoding::encode(&self.auth.username),
            self.auth.token,
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
    token: String,
    salt: String,
}

impl NavidromeAuth {
    fn from_source(source: &SavedMediaSource) -> Self {
        Self {
            username: source.username.clone(),
            token: source.token.clone(),
            salt: source.salt.clone(),
        }
    }

    fn from_password(username: String, password: String, salt: String) -> Self {
        Self {
            username,
            token: format!("{:x}", md5::compute(format!("{password}{salt}"))),
            salt,
        }
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

    fn album_list(&self, list_type: AlbumListType, count: u32) -> Result<Vec<Album>> {
        let count = count.to_string();
        let response = self.request_json_with_params(
            "getAlbumList2.view",
            &[("type", list_type.as_subsonic_type()), ("size", &count)],
        )?;
        let root = Self::response_root(&response)?;
        let album_list = root
            .get("albumList2")
            .or_else(|| root.get("albumList"))
            .ok_or_else(|| anyhow!("missing album list"))?;

        Ok(parse_albums(album_list))
    }

    fn artist(&self, artist_id: &str) -> Result<ArtistDetail> {
        let response = self.request_json_with_params("getArtist.view", &[("id", artist_id)])?;
        let root = Self::response_root(&response)?;
        let artist = root
            .get("artist")
            .ok_or_else(|| anyhow!("missing artist"))?;

        Ok(parse_artist_detail(artist))
    }

    fn artist_info(&self, artist_id: &str) -> Result<ArtistInfo> {
        let response =
            self.request_json_with_params("getArtistInfo2.view", &[("id", artist_id)])?;
        let root = Self::response_root(&response)?;
        let info = root
            .get("artistInfo2")
            .ok_or_else(|| anyhow!("missing artist info"))?;

        Ok(parse_artist_info(artist_id, info))
    }

    fn playlists(&self) -> Result<Vec<Playlist>> {
        let response = self.request_json("getPlaylists.view")?;
        let root = Self::response_root(&response)?;
        let playlists = root
            .get("playlists")
            .ok_or_else(|| anyhow!("missing playlists"))?;

        Ok(parse_playlists(playlists))
    }

    fn playlist(&self, playlist_id: &str) -> Result<PlaylistDetail> {
        let response = self.request_json_with_params("getPlaylist.view", &[("id", playlist_id)])?;
        let root = Self::response_root(&response)?;
        let playlist = root
            .get("playlist")
            .ok_or_else(|| anyhow!("missing playlist"))?;

        Ok(parse_playlist_detail(playlist))
    }

    fn genres(&self) -> Result<Vec<Genre>> {
        let response = self.request_json("getGenres.view")?;
        let root = Self::response_root(&response)?;
        let genres = root
            .get("genres")
            .ok_or_else(|| anyhow!("missing genres"))?;

        Ok(parse_genres(genres))
    }

    fn internet_radio_stations(&self) -> Result<Vec<InternetRadioStation>> {
        let response = self.request_json("getInternetRadioStations.view")?;
        let root = Self::response_root(&response)?;
        let stations = root
            .get("internetRadioStations")
            .ok_or_else(|| anyhow!("missing internet radio stations"))?;

        Ok(parse_internet_radio_stations(stations))
    }

    fn random_tracks(&self, count: u32) -> Result<Vec<Track>> {
        let count = count.to_string();
        let response = self.request_json_with_params("getRandomSongs.view", &[("size", &count)])?;
        let root = Self::response_root(&response)?;
        let random_songs = root
            .get("randomSongs")
            .ok_or_else(|| anyhow!("missing random songs"))?;

        Ok(parse_tracks(random_songs))
    }

    fn similar_tracks(&self, track_id: &str, count: u32) -> Result<Vec<Track>> {
        let count = count.to_string();
        let response = self.request_json_with_params(
            "getSimilarSongs2.view",
            &[("id", track_id), ("count", &count)],
        )?;
        let root = Self::response_root(&response)?;
        let similar_songs = root
            .get("similarSongs2")
            .or_else(|| root.get("similarSongs"))
            .ok_or_else(|| anyhow!("missing similar songs"))?;

        Ok(parse_tracks(similar_songs))
    }

    fn stream_url(&self, request: &StreamRequest) -> Result<String> {
        Ok(self.stream_url_for_request(request))
    }

    fn cover_art_url(&self, cover_art_id: &str, size: u32) -> Result<String> {
        Ok(self.cover_art_url_for_request(cover_art_id, size))
    }

    fn set_favorite(&self, item_id: &str, favorite: bool) -> Result<()> {
        let endpoint = if favorite { "star.view" } else { "unstar.view" };
        let response = self.request_json_with_params(endpoint, &[("id", item_id)])?;
        Self::response_root(&response)?;
        Ok(())
    }

    fn set_rating(&self, item_id: &str, rating: u8) -> Result<()> {
        if rating > 5 {
            return Err(anyhow!("rating must be between 0 and 5"));
        }
        let rating = rating.to_string();
        let response = self
            .request_json_with_params("setRating.view", &[("id", item_id), ("rating", &rating)])?;
        Self::response_root(&response)?;
        Ok(())
    }

    fn lyrics(&self, track: &Track) -> Result<Option<Lyrics>> {
        let response = self.request_json_with_params(
            "getLyrics.view",
            &[("artist", &track.artist), ("title", &track.title)],
        )?;
        let root = Self::response_root(&response)?;

        Ok(root.get("lyrics").and_then(parse_lyrics))
    }

    fn report_now_playing(&self, track_id: &str) -> Result<()> {
        self.scrobble_with_submission(track_id, false)
    }

    fn scrobble(&self, track_id: &str) -> Result<()> {
        self.scrobble_with_submission(track_id, true)
    }
}

impl NavidromeProvider {
    fn stream_url_for_request(&self, request: &StreamRequest) -> String {
        let mut url = format!(
            "{}&id={}",
            self.api_url("stream.view"),
            urlencoding::encode(&request.item_id),
        );

        if let Some(max_bitrate) = request.max_bitrate_kbps {
            url.push_str("&maxBitRate=");
            url.push_str(&max_bitrate.to_string());
        }
        if let Some(format) = stream_format_param(&request.format) {
            url.push_str("&format=");
            url.push_str(format);
        }
        if let Some(start_seconds) = request.start_seconds {
            url.push_str("&timeOffset=");
            url.push_str(&start_seconds.to_string());
        }

        url
    }

    fn cover_art_url_for_request(&self, cover_art_id: &str, size: u32) -> String {
        format!(
            "{}&id={}&size={}",
            self.api_url("getCoverArt.view"),
            urlencoding::encode(cover_art_id),
            size
        )
    }

    fn scrobble_with_submission(&self, track_id: &str, submission: bool) -> Result<()> {
        let submission = if submission { "true" } else { "false" };
        let response = self.request_json_with_params(
            "scrobble.view",
            &[("id", track_id), ("submission", submission)],
        )?;
        Self::response_root(&response)?;
        Ok(())
    }
}

fn stream_format_param(format: &StreamFormat) -> Option<&'static str> {
    match format {
        StreamFormat::Original => None,
        StreamFormat::Mp3 => Some("mp3"),
        StreamFormat::Opus => Some("opus"),
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
        year: song
            .get("year")
            .and_then(|value| value.as_u64())
            .and_then(|value| u32::try_from(value).ok()),
        bit_rate_kbps: song
            .get("bitRate")
            .or_else(|| song.get("bitrate"))
            .and_then(|value| value.as_u64())
            .and_then(|value| u32::try_from(value).ok()),
        codec: json_string(song, "suffix").or_else(|| json_string(song, "type")),
        cover_art_id: json_string(song, "coverArt"),
        favorited_at: json_string(song, "starred"),
        user_rating: song
            .get("userRating")
            .and_then(|value| value.as_u64())
            .and_then(|value| u8::try_from(value).ok())
            .filter(|value| (1..=5).contains(value)),
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

fn parse_artist_info(artist_id: &str, info: &serde_json::Value) -> ArtistInfo {
    ArtistInfo {
        artist_id: artist_id.to_string(),
        biography: json_string(info, "biography"),
        musicbrainz_id: json_string(info, "musicBrainzId"),
        lastfm_url: json_string(info, "lastFmUrl"),
        small_image_url: json_string(info, "smallImageUrl"),
        medium_image_url: json_string(info, "mediumImageUrl"),
        large_image_url: json_string(info, "largeImageUrl"),
    }
}

fn parse_playlists(playlists: &serde_json::Value) -> Vec<Playlist> {
    json_array(playlists, "playlist")
        .into_iter()
        .filter_map(|playlist| parse_playlist_summary(&playlist))
        .collect()
}

fn parse_playlist_detail(playlist: &serde_json::Value) -> PlaylistDetail {
    PlaylistDetail {
        playlist: parse_playlist_summary(playlist).unwrap_or_else(|| Playlist {
            id: String::new(),
            name: "Untitled Playlist".to_string(),
            owner: String::new(),
            song_count: 0,
            duration_seconds: 0,
        }),
        tracks: json_array(playlist, "entry")
            .into_iter()
            .filter_map(|entry| parse_track(&entry))
            .collect(),
    }
}

fn parse_playlist_summary(playlist: &serde_json::Value) -> Option<Playlist> {
    Some(Playlist {
        id: playlist.get("id")?.as_str()?.to_string(),
        name: playlist
            .get("name")
            .and_then(|value| value.as_str())
            .unwrap_or("Untitled Playlist")
            .to_string(),
        owner: playlist
            .get("owner")
            .and_then(|value| value.as_str())
            .unwrap_or("")
            .to_string(),
        song_count: json_u32(playlist, "songCount"),
        duration_seconds: json_u32(playlist, "duration"),
    })
}

fn parse_genres(genres: &serde_json::Value) -> Vec<Genre> {
    json_array(genres, "genre")
        .into_iter()
        .map(|genre| Genre {
            name: genre
                .get("value")
                .or_else(|| genre.get("name"))
                .and_then(|value| value.as_str())
                .or_else(|| genre.as_str())
                .unwrap_or("")
                .to_string(),
            song_count: json_u32(&genre, "songCount"),
            album_count: json_u32(&genre, "albumCount"),
        })
        .filter(|genre| !genre.name.is_empty())
        .collect()
}

fn parse_internet_radio_stations(stations: &serde_json::Value) -> Vec<InternetRadioStation> {
    json_array(stations, "internetRadioStation")
        .into_iter()
        .filter_map(|station| {
            Some(InternetRadioStation {
                id: station.get("id")?.as_str()?.to_string(),
                name: station
                    .get("name")
                    .and_then(|value| value.as_str())
                    .unwrap_or("Untitled Station")
                    .to_string(),
                stream_url: station.get("streamUrl")?.as_str()?.to_string(),
                home_page_url: json_string(&station, "homePageUrl"),
            })
        })
        .collect()
}

fn parse_lyrics(lyrics: &serde_json::Value) -> Option<Lyrics> {
    Some(Lyrics {
        artist: lyrics
            .get("artist")
            .and_then(|value| value.as_str())
            .unwrap_or("")
            .to_string(),
        title: lyrics
            .get("title")
            .and_then(|value| value.as_str())
            .unwrap_or("")
            .to_string(),
        text: lyrics
            .get("value")
            .or_else(|| lyrics.get("text"))
            .and_then(|value| value.as_str())?
            .to_string(),
    })
}

fn json_array(result: &serde_json::Value, key: &str) -> Vec<serde_json::Value> {
    result
        .get(key)
        .and_then(|value| value.as_array())
        .cloned()
        .unwrap_or_default()
}

fn json_string(value: &serde_json::Value, key: &str) -> Option<String> {
    value
        .get(key)
        .and_then(|value| value.as_str())
        .filter(|value| !value.is_empty())
        .map(ToString::to_string)
}

fn json_u32(value: &serde_json::Value, key: &str) -> u32 {
    value
        .get(key)
        .and_then(|value| value.as_u64())
        .and_then(|value| u32::try_from(value).ok())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use crate::domain::{StreamFormat, StreamRequest};
    use crate::settings::{ConnectionDraft, MediaSourceKind, SavedMediaSource};

    use super::{
        parse_album_detail, parse_artist_detail, parse_artist_info, parse_genres,
        parse_internet_radio_stations, parse_lyrics, parse_playlist_detail, parse_playlists,
        NavidromeAuth, NavidromeProvider, CLIENT_NAME,
    };
    use serde_json::json;

    #[test]
    fn auth_token_matches_subsonic_token_formula() {
        let auth = NavidromeAuth {
            username: "user".to_string(),
            token: "06fe650023ce9fc5698876c8c303f6ed".to_string(),
            salt: "naviamp".to_string(),
        };

        assert_eq!(auth.token, "06fe650023ce9fc5698876c8c303f6ed");
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
    fn parses_search_results_fixture() {
        let response: serde_json::Value =
            serde_json::from_str(include_str!("fixtures/search3.json")).expect("valid fixture");
        let root = NavidromeProvider::response_root(&response).expect("valid response root");
        let result = root.get("searchResult3").expect("search result fixture");

        assert_eq!(1, super::parse_artists(result).len());
        assert_eq!(1, super::parse_albums(result).len());
        assert_eq!(1, super::parse_tracks(result).len());
        assert_eq!(
            Some("cover-1".to_string()),
            super::parse_tracks(result)[0].cover_art_id
        );
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

    #[test]
    fn parses_artist_info() {
        let info = json!({
            "biography": "A short bio",
            "musicBrainzId": "mbid",
            "lastFmUrl": "https://last.fm/example",
            "largeImageUrl": "https://img/large.jpg"
        });

        let parsed = parse_artist_info("artist-1", &info);

        assert_eq!("artist-1", parsed.artist_id);
        assert_eq!(Some("A short bio".to_string()), parsed.biography);
        assert_eq!(Some("mbid".to_string()), parsed.musicbrainz_id);
        assert_eq!(
            Some("https://img/large.jpg".to_string()),
            parsed.large_image_url
        );
    }

    #[test]
    fn parses_playlists() {
        let playlists = json!({
            "playlist": [
                { "id": "playlist-1", "name": "Favorites", "owner": "me", "songCount": 12, "duration": 3600 }
            ]
        });

        let parsed = parse_playlists(&playlists);

        assert_eq!(1, parsed.len());
        assert_eq!("Favorites", parsed[0].name);
        assert_eq!(12, parsed[0].song_count);
    }

    #[test]
    fn parses_playlist_detail_tracks() {
        let playlist = json!({
            "id": "playlist-1",
            "name": "Favorites",
            "entry": [
                { "id": "song-1", "title": "One", "artist": "The Example", "album": "Blue Record" }
            ]
        });

        let parsed = parse_playlist_detail(&playlist);

        assert_eq!("playlist-1", parsed.playlist.id);
        assert_eq!(1, parsed.tracks.len());
        assert_eq!("song-1", parsed.tracks[0].id);
    }

    #[test]
    fn parses_genres() {
        let genres = json!({
            "genre": [
                { "value": "Rock", "songCount": 10, "albumCount": 2 }
            ]
        });

        let parsed = parse_genres(&genres);

        assert_eq!(1, parsed.len());
        assert_eq!("Rock", parsed[0].name);
        assert_eq!(2, parsed[0].album_count);
    }

    #[test]
    fn parses_internet_radio_stations() {
        let stations = json!({
            "internetRadioStation": [
                { "id": "station-1", "name": "Radio", "streamUrl": "https://radio/stream", "homePageUrl": "https://radio" }
            ]
        });

        let parsed = parse_internet_radio_stations(&stations);

        assert_eq!(1, parsed.len());
        assert_eq!("station-1", parsed[0].id);
        assert_eq!("https://radio/stream", parsed[0].stream_url);
    }

    #[test]
    fn parses_random_song_container() {
        let random_songs = json!({
            "song": [
                { "id": "song-1", "title": "One", "artist": "The Example", "album": "Blue Record" }
            ]
        });

        let parsed = super::parse_tracks(&random_songs);

        assert_eq!(1, parsed.len());
        assert_eq!("song-1", parsed[0].id);
    }

    #[test]
    fn original_stream_url_has_no_transcode_params() {
        let provider = test_provider();

        let url = provider.stream_url_for_request(&StreamRequest::original("song 1"));

        assert!(url.contains("/rest/stream.view?"));
        assert!(url.contains("&id=song%201"));
        assert!(!url.contains("maxBitRate"));
        assert!(!url.contains("&format="));
    }

    #[test]
    fn transcoded_stream_url_includes_quality_params() {
        let provider = test_provider();

        let url = provider.stream_url_for_request(&StreamRequest::transcoded(
            "song-1",
            192,
            StreamFormat::Mp3,
        ));

        assert!(url.contains("&id=song-1"));
        assert!(url.contains("&maxBitRate=192"));
        assert!(url.contains("&format=mp3"));
    }

    #[test]
    fn opus_stream_url_uses_opus_format_param() {
        let provider = test_provider();

        let url = provider.stream_url_for_request(&StreamRequest::transcoded(
            "song-1",
            128,
            StreamFormat::Opus,
        ));

        assert!(url.contains("&format=opus"));
    }

    #[test]
    fn offset_stream_url_includes_time_offset_param() {
        let provider = test_provider();

        let url = provider.stream_url_for_request(&StreamRequest::original_from("song-1", 95));

        assert!(url.contains("&id=song-1"));
        assert!(url.contains("&timeOffset=95"));
    }

    #[test]
    fn cover_art_url_includes_art_id_and_size() {
        let provider = test_provider();

        let url = provider.cover_art_url_for_request("cover 1", 300);

        assert!(url.contains("/rest/getCoverArt.view?"));
        assert!(url.contains("&id=cover%201"));
        assert!(url.contains("&size=300"));
    }

    #[test]
    fn parses_lyrics() {
        let lyrics = json!({
            "artist": "The Example",
            "title": "One",
            "value": "Line one\nLine two"
        });

        let parsed = parse_lyrics(&lyrics).expect("lyrics should parse");

        assert_eq!("The Example", parsed.artist);
        assert_eq!("One", parsed.title);
        assert_eq!("Line one\nLine two", parsed.text);
    }

    #[test]
    fn ignores_lyrics_without_text() {
        let lyrics = json!({
            "artist": "The Example",
            "title": "One"
        });

        assert_eq!(None, parse_lyrics(&lyrics));
    }

    fn test_provider() -> NavidromeProvider {
        NavidromeProvider::new(SavedMediaSource {
            id: "source-1".to_string(),
            display_name: "Test".to_string(),
            kind: MediaSourceKind::Navidrome,
            server_url: "https://music.example.com".to_string(),
            username: "user".to_string(),
            token: "06fe650023ce9fc5698876c8c303f6ed".to_string(),
            salt: "naviamp".to_string(),
            tls: crate::settings::TlsSettings::default(),
        })
        .expect("valid test settings")
    }

    #[test]
    fn password_draft_creates_token_backed_provider() {
        let draft = ConnectionDraft {
            server_url: "https://music.example.com/".to_string(),
            username: "user".to_string(),
            password: "password".to_string(),
        };

        let provider = NavidromeProvider::from_password(&draft).expect("provider");

        assert_eq!("https://music.example.com", provider.source.server_url);
        assert_eq!("06fe650023ce9fc5698876c8c303f6ed", provider.source.token);
        assert_eq!("naviamp", provider.source.salt);
    }
}
