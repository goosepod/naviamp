#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Artist {
    pub id: String,
    pub name: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Album {
    pub id: String,
    pub title: String,
    pub artist: String,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AlbumListType {
    Newest,
    Random,
    Frequent,
    Recent,
}

impl AlbumListType {
    pub fn as_subsonic_type(self) -> &'static str {
        match self {
            Self::Newest => "newest",
            Self::Random => "random",
            Self::Frequent => "frequent",
            Self::Recent => "recent",
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AlbumDetail {
    pub album: Album,
    pub tracks: Vec<Track>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ArtistDetail {
    pub artist: Artist,
    pub albums: Vec<Album>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct ArtistInfo {
    pub artist_id: String,
    pub biography: Option<String>,
    pub musicbrainz_id: Option<String>,
    pub lastfm_url: Option<String>,
    pub small_image_url: Option<String>,
    pub medium_image_url: Option<String>,
    pub large_image_url: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Playlist {
    pub id: String,
    pub name: String,
    pub owner: String,
    pub song_count: u32,
    pub duration_seconds: u32,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PlaylistDetail {
    pub playlist: Playlist,
    pub tracks: Vec<Track>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Genre {
    pub name: String,
    pub song_count: u32,
    pub album_count: u32,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct InternetRadioStation {
    pub id: String,
    pub name: String,
    pub stream_url: String,
    pub home_page_url: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Lyrics {
    pub artist: String,
    pub title: String,
    pub text: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
#[allow(dead_code)]
pub enum StreamFormat {
    Original,
    Mp3,
    Opus,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct StreamRequest {
    pub item_id: String,
    pub max_bitrate_kbps: Option<u32>,
    pub format: StreamFormat,
    pub start_seconds: Option<u32>,
}

impl StreamRequest {
    pub fn original(item_id: impl Into<String>) -> Self {
        Self {
            item_id: item_id.into(),
            max_bitrate_kbps: None,
            format: StreamFormat::Original,
            start_seconds: None,
        }
    }

    pub fn original_from(item_id: impl Into<String>, start_seconds: u32) -> Self {
        Self {
            start_seconds: Some(start_seconds),
            ..Self::original(item_id)
        }
    }

    #[allow(dead_code)]
    pub fn transcoded(
        item_id: impl Into<String>,
        max_bitrate_kbps: u32,
        format: StreamFormat,
    ) -> Self {
        Self {
            item_id: item_id.into(),
            max_bitrate_kbps: Some(max_bitrate_kbps),
            format,
            start_seconds: None,
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Track {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub year: Option<u32>,
    pub bit_rate_kbps: Option<u32>,
    pub codec: Option<String>,
    pub cover_art_id: Option<String>,
    pub favorited_at: Option<String>,
    pub user_rating: Option<u8>,
}

impl Track {
    pub fn subtitle(&self) -> String {
        format!(
            "{} - {}",
            display_or_dash(&self.artist),
            display_or_dash(&self.album)
        )
    }
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct SearchResults {
    pub artists: Vec<Artist>,
    pub albums: Vec<Album>,
    pub tracks: Vec<Track>,
}

impl SearchResults {
    pub fn is_empty(&self) -> bool {
        self.artists.is_empty() && self.albums.is_empty() && self.tracks.is_empty()
    }
}

impl Album {
    pub fn subtitle(&self) -> String {
        display_or_dash(&self.artist).to_string()
    }
}

fn display_or_dash(value: &str) -> &str {
    if value.trim().is_empty() {
        "-"
    } else {
        value
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn subtitle_uses_dash_for_missing_values() {
        let track = Track {
            id: "1".into(),
            title: "Song".into(),
            artist: String::new(),
            album: String::new(),
            year: None,
            bit_rate_kbps: None,
            codec: None,
            cover_art_id: None,
            favorited_at: None,
            user_rating: None,
        };

        assert_eq!("- - -", track.subtitle());
    }
}
