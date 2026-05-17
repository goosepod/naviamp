pub mod navidrome;

use crate::domain::{
    AlbumDetail, ArtistDetail, ArtistInfo, Genre, InternetRadioStation, Lyrics, Playlist,
    PlaylistDetail, SearchResults, StreamRequest, Track,
};
use anyhow::Result;

#[allow(dead_code)]
pub trait MediaProvider {
    fn validate_connection(&self) -> Result<()>;

    fn search(&self, query: &str) -> Result<SearchResults>;

    fn album(&self, album_id: &str) -> Result<AlbumDetail>;

    fn artist(&self, artist_id: &str) -> Result<ArtistDetail>;

    fn artist_info(&self, artist_id: &str) -> Result<ArtistInfo>;

    fn playlists(&self) -> Result<Vec<Playlist>>;

    fn playlist(&self, playlist_id: &str) -> Result<PlaylistDetail>;

    fn genres(&self) -> Result<Vec<Genre>>;

    fn internet_radio_stations(&self) -> Result<Vec<InternetRadioStation>>;

    fn random_tracks(&self, count: u32) -> Result<Vec<Track>>;

    fn similar_tracks(&self, track_id: &str, count: u32) -> Result<Vec<Track>>;

    fn stream_url(&self, request: &StreamRequest) -> Result<String>;

    fn cover_art_url(&self, cover_art_id: &str, size: u32) -> Result<String>;

    fn set_favorite(&self, item_id: &str, favorite: bool) -> Result<()>;

    fn set_rating(&self, item_id: &str, rating: u8) -> Result<()>;

    fn lyrics(&self, track: &Track) -> Result<Option<Lyrics>>;

    fn report_now_playing(&self, track_id: &str) -> Result<()>;

    fn scrobble(&self, track_id: &str) -> Result<()>;
}
