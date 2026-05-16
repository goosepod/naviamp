pub mod navidrome;

use crate::domain::Track;
use anyhow::Result;

pub trait MediaProvider {
    fn search_tracks(&self, query: &str) -> Result<Vec<Track>>;

    fn stream_url(&self, track_id: &str) -> Result<String>;
}
