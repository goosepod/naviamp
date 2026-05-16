pub mod navidrome;

use crate::domain::SearchResults;
use anyhow::Result;

pub trait MediaProvider {
    fn validate_connection(&self) -> Result<()>;

    fn search(&self, query: &str) -> Result<SearchResults>;

    fn stream_url(&self, track_id: &str) -> Result<String>;
}
