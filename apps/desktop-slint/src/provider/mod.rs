pub mod navidrome;

use crate::domain::{AlbumDetail, ArtistDetail, SearchResults};
use anyhow::Result;

pub trait MediaProvider {
    fn validate_connection(&self) -> Result<()>;

    fn search(&self, query: &str) -> Result<SearchResults>;

    fn album(&self, album_id: &str) -> Result<AlbumDetail>;

    fn artist(&self, artist_id: &str) -> Result<ArtistDetail>;

    fn stream_url(&self, track_id: &str) -> Result<String>;
}
