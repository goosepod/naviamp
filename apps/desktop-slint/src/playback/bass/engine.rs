use anyhow::{Context, Result};
use std::ffi::CString;
use std::sync::Arc;

use super::ffi::{BassLibrary, BASS_ATTRIB_VOL, BASS_POS_BYTE, BASS_STREAM_STATUS};
use crate::playback::{PlaybackEngine, PlaybackSnapshot};

pub struct BassPlaybackEngine {
    bass: Option<Arc<BassLibrary>>,
    stream: Option<u32>,
    volume: u8,
}

impl Default for BassPlaybackEngine {
    fn default() -> Self {
        Self {
            bass: None,
            stream: None,
            volume: 80,
        }
    }
}

impl PlaybackEngine for BassPlaybackEngine {
    fn play_url(&mut self, url: &str) -> Result<()> {
        self.release_stream();
        let bass = self.bass()?;
        let url = CString::new(url).context("stream URL contains an embedded null byte")?;
        let stream = unsafe {
            (bass.stream_create_url)(
                url.as_ptr(),
                0,
                BASS_STREAM_STATUS,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
            )
        };
        if stream == 0 {
            return Err(bass.error("BASS_StreamCreateURL failed"));
        }
        self.stream = Some(stream);
        self.set_volume(self.volume)?;
        self.resume()
    }

    fn embedded_cover_art(&mut self) -> Result<Option<Vec<u8>>> {
        let Some(stream) = self.stream else {
            return Ok(None);
        };
        let bass = self.bass()?;
        Ok(bass.embedded_cover_art(stream))
    }

    fn pause(&mut self) -> Result<()> {
        let Some(stream) = self.stream else {
            return Ok(());
        };
        let bass = self.bass()?;
        bass.check(
            unsafe { (bass.channel_pause)(stream) },
            "BASS_ChannelPause failed",
        )
    }

    fn resume(&mut self) -> Result<()> {
        let Some(stream) = self.stream else {
            return Ok(());
        };
        let bass = self.bass()?;
        bass.check(
            unsafe { (bass.channel_play)(stream, 0) },
            "BASS_ChannelPlay failed",
        )
    }

    fn seek_relative(&mut self, seconds: i32) -> Result<()> {
        let Some(stream) = self.stream else {
            return Ok(());
        };
        let bass = self.bass()?;
        let current_bytes = unsafe { (bass.channel_get_position)(stream, BASS_POS_BYTE) };
        let current_seconds = unsafe { (bass.channel_bytes2_seconds)(stream, current_bytes) };
        let target_seconds = (current_seconds + f64::from(seconds)).max(0.0);
        bass.seek_absolute(stream, target_seconds)
    }

    fn seek_absolute(&mut self, seconds: f64) -> Result<()> {
        let Some(stream) = self.stream else {
            return Ok(());
        };
        let bass = self.bass()?;
        bass.seek_absolute(stream, seconds.max(0.0))
    }

    fn set_volume(&mut self, percent: u8) -> Result<()> {
        self.volume = percent.min(100);
        let Some(stream) = self.stream else {
            return Ok(());
        };
        let bass = self.bass()?;
        bass.check(
            unsafe {
                (bass.channel_set_attribute)(stream, BASS_ATTRIB_VOL, self.volume as f32 / 100.0)
            },
            "BASS_ChannelSetAttribute failed",
        )
    }

    fn snapshot(&mut self) -> Result<PlaybackSnapshot> {
        let Some(stream) = self.stream else {
            return Ok(PlaybackSnapshot {
                volume: self.volume,
                ..PlaybackSnapshot::default()
            });
        };
        let bass = self.bass()?;
        Ok(bass.snapshot(stream, self.volume))
    }

    fn stop(&mut self) {
        self.release_stream();
    }
}

impl BassPlaybackEngine {
    fn bass(&mut self) -> Result<Arc<BassLibrary>> {
        if let Some(bass) = self.bass.as_ref() {
            return Ok(Arc::clone(bass));
        }

        let bass = Arc::new(BassLibrary::load()?);
        bass.init()?;
        self.bass = Some(Arc::clone(&bass));
        Ok(bass)
    }

    fn release_stream(&mut self) {
        if let (Some(bass), Some(stream)) = (self.bass.as_ref(), self.stream.take()) {
            let _ = unsafe { (bass.channel_stop)(stream) };
            let _ = unsafe { (bass.stream_free)(stream) };
        }
    }
}

impl Drop for BassPlaybackEngine {
    fn drop(&mut self) {
        self.stop();
    }
}
