mod bass;

use anyhow::Result;

use self::bass::BassPlaybackEngine;

#[allow(dead_code)]
pub trait PlaybackEngine: Send {
    fn play_url(&mut self, url: &str) -> Result<()>;

    fn embedded_cover_art(&mut self) -> Result<Option<Vec<u8>>>;

    fn pause(&mut self) -> Result<()>;

    fn resume(&mut self) -> Result<()>;

    fn seek_relative(&mut self, seconds: i32) -> Result<()>;

    fn seek_absolute(&mut self, seconds: f64) -> Result<()>;

    fn set_volume(&mut self, percent: u8) -> Result<()>;

    fn snapshot(&mut self) -> Result<PlaybackSnapshot>;

    fn stop(&mut self);
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct PlaybackSnapshot {
    pub position_seconds: Option<f64>,
    pub duration_seconds: Option<f64>,
    pub is_playing: bool,
    pub volume: u8,
    pub stream_title: Option<String>,
}

pub fn default_playback_engine() -> Box<dyn PlaybackEngine> {
    Box::<BassPlaybackEngine>::default()
}
