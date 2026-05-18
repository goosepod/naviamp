use anyhow::{Context, Result};
use libloading::{Library, Symbol};
use std::ffi::{c_char, c_void, CString};

use super::runtime::{bass_plugin_names, dynamic_library_name, BassLibraryResolver};
use super::tags::{
    extract_id3_cover_art, parse_icy_header_title, parse_icy_stream_title,
    read_null_terminated_string_list, NonEmptyString,
};
use crate::playback::PlaybackSnapshot;

type BassBool = i32;
type BassDword = u32;
type BassQword = u64;
type BassHandle = u32;

pub(super) const BASS_POS_BYTE: BassDword = 0;
const BASS_ACTIVE_PLAYING: BassDword = 1;
pub(super) const BASS_ATTRIB_VOL: BassDword = 2;
pub(super) const BASS_STREAM_STATUS: BassDword = 0x800000;
const BASS_ERROR_POSITION: i32 = 7;
const BASS_ERROR_NOTAVAIL: i32 = 37;
const BASS_ERROR_TIMEOUT: i32 = 40;
const BASS_ERROR_FILEFORM: i32 = 41;
const BASS_ERROR_CODEC: i32 = 44;
const BASS_ERROR_PROTOCOL: i32 = 48;
const BASS_ERROR_DENIED: i32 = 49;
const BASS_TAG_HTTP: BassDword = 3;
const BASS_TAG_ICY: BassDword = 4;
const BASS_TAG_META: BassDword = 5;
const BASS_TAG_ID3V2_BINARY: BassDword = 20;
const BASS_TAG_ID3V2_2_BINARY: BassDword = 21;
const BASS_TAG_MP4_COVERART: BassDword = 0x1400;

type BassInit =
    unsafe extern "system" fn(i32, u32, BassDword, *mut c_void, *mut c_void) -> BassBool;
type BassFree = unsafe extern "system" fn() -> BassBool;
type BassGetVersion = unsafe extern "system" fn() -> BassDword;
type BassGetErrorCode = unsafe extern "system" fn() -> i32;
type BassStreamCreateUrl = unsafe extern "system" fn(
    *const c_char,
    BassDword,
    BassDword,
    *mut c_void,
    *mut c_void,
) -> BassHandle;
type BassStreamFree = unsafe extern "system" fn(BassHandle) -> BassBool;
type BassPluginLoad = unsafe extern "system" fn(*const c_char, BassDword) -> BassHandle;
type BassPluginFree = unsafe extern "system" fn(BassHandle) -> BassBool;
type BassChannelPlay = unsafe extern "system" fn(BassHandle, BassBool) -> BassBool;
type BassChannelPause = unsafe extern "system" fn(BassHandle) -> BassBool;
type BassChannelStop = unsafe extern "system" fn(BassHandle) -> BassBool;
type BassChannelIsActive = unsafe extern "system" fn(BassHandle) -> BassDword;
type BassChannelSetAttribute = unsafe extern "system" fn(BassHandle, BassDword, f32) -> BassBool;
type BassChannelGetLength = unsafe extern "system" fn(BassHandle, BassDword) -> BassQword;
type BassChannelGetPosition = unsafe extern "system" fn(BassHandle, BassDword) -> BassQword;
type BassChannelSetPosition =
    unsafe extern "system" fn(BassHandle, BassQword, BassDword) -> BassBool;
type BassChannelBytes2Seconds = unsafe extern "system" fn(BassHandle, BassQword) -> f64;
type BassChannelSeconds2Bytes = unsafe extern "system" fn(BassHandle, f64) -> BassQword;
type BassChannelGetTags = unsafe extern "system" fn(BassHandle, BassDword) -> *const c_void;

#[repr(C)]
struct BassTagBinary {
    data: *const c_void,
    length: BassDword,
}

pub(super) struct BassLibrary {
    _library: Library,
    plugin_handles: Vec<BassHandle>,
    free: BassFree,
    get_error_code: BassGetErrorCode,
    pub(super) stream_create_url: BassStreamCreateUrl,
    pub(super) stream_free: BassStreamFree,
    plugin_free: BassPluginFree,
    init: BassInit,
    pub(super) channel_play: BassChannelPlay,
    pub(super) channel_pause: BassChannelPause,
    pub(super) channel_stop: BassChannelStop,
    channel_is_active: BassChannelIsActive,
    pub(super) channel_set_attribute: BassChannelSetAttribute,
    channel_get_length: BassChannelGetLength,
    pub(super) channel_get_position: BassChannelGetPosition,
    channel_set_position: BassChannelSetPosition,
    pub(super) channel_bytes2_seconds: BassChannelBytes2Seconds,
    channel_seconds2_bytes: BassChannelSeconds2Bytes,
    channel_get_tags: BassChannelGetTags,
}

impl BassLibrary {
    pub(super) fn load() -> Result<Self> {
        let bass_dir = BassLibraryResolver::new().resolve()?;
        let bass_path = bass_dir.join(dynamic_library_name("bass"));
        let library = unsafe { Library::new(&bass_path) }
            .with_context(|| format!("could not load {}", bass_path.display()))?;

        unsafe {
            let init = load_symbol::<BassInit>(&library, b"BASS_Init\0")?;
            let free = load_symbol::<BassFree>(&library, b"BASS_Free\0")?;
            let get_version = load_symbol::<BassGetVersion>(&library, b"BASS_GetVersion\0")?;
            let get_error_code = load_symbol::<BassGetErrorCode>(&library, b"BASS_ErrorGetCode\0")?;
            let stream_create_url =
                load_symbol::<BassStreamCreateUrl>(&library, b"BASS_StreamCreateURL\0")?;
            let stream_free = load_symbol::<BassStreamFree>(&library, b"BASS_StreamFree\0")?;
            let plugin_load = load_symbol::<BassPluginLoad>(&library, b"BASS_PluginLoad\0")?;
            let plugin_free = load_symbol::<BassPluginFree>(&library, b"BASS_PluginFree\0")?;
            let channel_play = load_symbol::<BassChannelPlay>(&library, b"BASS_ChannelPlay\0")?;
            let channel_pause = load_symbol::<BassChannelPause>(&library, b"BASS_ChannelPause\0")?;
            let channel_stop = load_symbol::<BassChannelStop>(&library, b"BASS_ChannelStop\0")?;
            let channel_is_active =
                load_symbol::<BassChannelIsActive>(&library, b"BASS_ChannelIsActive\0")?;
            let channel_set_attribute =
                load_symbol::<BassChannelSetAttribute>(&library, b"BASS_ChannelSetAttribute\0")?;
            let channel_get_length =
                load_symbol::<BassChannelGetLength>(&library, b"BASS_ChannelGetLength\0")?;
            let channel_get_position =
                load_symbol::<BassChannelGetPosition>(&library, b"BASS_ChannelGetPosition\0")?;
            let channel_set_position =
                load_symbol::<BassChannelSetPosition>(&library, b"BASS_ChannelSetPosition\0")?;
            let channel_bytes2_seconds =
                load_symbol::<BassChannelBytes2Seconds>(&library, b"BASS_ChannelBytes2Seconds\0")?;
            let channel_seconds2_bytes =
                load_symbol::<BassChannelSeconds2Bytes>(&library, b"BASS_ChannelSeconds2Bytes\0")?;
            let channel_get_tags =
                load_symbol::<BassChannelGetTags>(&library, b"BASS_ChannelGetTags\0")?;

            let mut plugin_handles = Vec::new();
            for plugin in bass_plugin_names() {
                let plugin_path = bass_dir.join(dynamic_library_name(plugin));
                if !plugin_path.is_file() {
                    continue;
                }
                let plugin_path = CString::new(plugin_path.to_string_lossy().as_bytes())
                    .context("BASS plugin path contains an embedded null byte")?;
                let handle = plugin_load(plugin_path.as_ptr(), 0);
                if handle != 0 {
                    plugin_handles.push(handle);
                }
            }

            let version = get_version();
            if version == 0 {
                anyhow::bail!("BASS_GetVersion returned zero");
            }

            Ok(Self {
                _library: library,
                plugin_handles,
                free,
                get_error_code,
                stream_create_url,
                stream_free,
                plugin_free,
                init,
                channel_play,
                channel_pause,
                channel_stop,
                channel_is_active,
                channel_set_attribute,
                channel_get_length,
                channel_get_position,
                channel_set_position,
                channel_bytes2_seconds,
                channel_seconds2_bytes,
                channel_get_tags,
            })
        }
    }

    pub(super) fn init(&self) -> Result<()> {
        self.check(
            unsafe { (self.init)(-1, 44100, 0, std::ptr::null_mut(), std::ptr::null_mut()) },
            "BASS_Init failed",
        )
    }

    pub(super) fn check(&self, ok: BassBool, message: &str) -> Result<()> {
        if ok == 0 {
            Err(self.error(message))
        } else {
            Ok(())
        }
    }

    pub(super) fn error(&self, message: &str) -> anyhow::Error {
        let error_code = unsafe { (self.get_error_code)() };
        anyhow::anyhow!("{}: {}", message, bass_error_message(error_code))
    }

    pub(super) fn snapshot(&self, stream: BassHandle, volume: u8) -> PlaybackSnapshot {
        let position_bytes = unsafe { (self.channel_get_position)(stream, BASS_POS_BYTE) };
        let length_bytes = unsafe { (self.channel_get_length)(stream, BASS_POS_BYTE) };
        PlaybackSnapshot {
            position_seconds: bass_seconds(unsafe {
                (self.channel_bytes2_seconds)(stream, position_bytes)
            }),
            duration_seconds: bass_seconds(unsafe {
                (self.channel_bytes2_seconds)(stream, length_bytes)
            }),
            is_playing: unsafe { (self.channel_is_active)(stream) } == BASS_ACTIVE_PLAYING,
            volume,
            stream_title: self.stream_title(stream),
        }
    }

    pub(super) fn seek_absolute(&self, stream: BassHandle, seconds: f64) -> Result<()> {
        let target_bytes = unsafe { (self.channel_seconds2_bytes)(stream, seconds) };
        if unsafe { (self.channel_set_position)(stream, target_bytes, BASS_POS_BYTE) } == 0 {
            let error_code = unsafe { (self.get_error_code)() };
            if matches!(error_code, BASS_ERROR_POSITION | BASS_ERROR_NOTAVAIL) {
                anyhow::bail!("seeking is not available for this stream");
            }
            anyhow::bail!(
                "BASS_ChannelSetPosition failed: {}",
                bass_error_message(error_code)
            );
        }
        Ok(())
    }

    pub(super) fn embedded_cover_art(&self, stream: BassHandle) -> Option<Vec<u8>> {
        self.mp4_cover_art(stream)
            .or_else(|| self.id3v2_cover_art(stream, BASS_TAG_ID3V2_BINARY))
            .or_else(|| self.id3v2_cover_art(stream, BASS_TAG_ID3V2_2_BINARY))
    }

    fn stream_title(&self, stream: BassHandle) -> Option<String> {
        self.single_string_tag(stream, BASS_TAG_META)
            .as_deref()
            .and_then(parse_icy_stream_title)
            .or_else(|| {
                self.string_list_tag(stream, BASS_TAG_ICY)
                    .into_iter()
                    .find_map(parse_icy_header_title)
            })
            .or_else(|| {
                self.string_list_tag(stream, BASS_TAG_HTTP)
                    .into_iter()
                    .find_map(parse_icy_header_title)
            })
    }

    fn mp4_cover_art(&self, stream: BassHandle) -> Option<Vec<u8>> {
        self.binary_tag(stream, BASS_TAG_MP4_COVERART)
            .map(ToOwned::to_owned)
    }

    fn id3v2_cover_art(&self, stream: BassHandle, tag: BassDword) -> Option<Vec<u8>> {
        self.binary_tag(stream, tag).and_then(extract_id3_cover_art)
    }

    fn binary_tag(&self, stream: BassHandle, tag: BassDword) -> Option<&[u8]> {
        let tag = unsafe { (self.channel_get_tags)(stream, tag) };
        if tag.is_null() {
            return None;
        }

        let tag = unsafe { &*(tag as *const BassTagBinary) };
        if tag.data.is_null() || tag.length == 0 {
            return None;
        }

        Some(unsafe { std::slice::from_raw_parts(tag.data as *const u8, tag.length as usize) })
    }

    fn single_string_tag(&self, stream: BassHandle, tag: BassDword) -> Option<String> {
        let value = unsafe { (self.channel_get_tags)(stream, tag) };
        if value.is_null() {
            return None;
        }

        unsafe { std::ffi::CStr::from_ptr(value as *const c_char) }
            .to_string_lossy()
            .trim()
            .to_string()
            .into_non_empty()
    }

    fn string_list_tag(&self, stream: BassHandle, tag: BassDword) -> Vec<String> {
        let values = unsafe { (self.channel_get_tags)(stream, tag) };
        if values.is_null() {
            return Vec::new();
        }

        unsafe { read_null_terminated_string_list(values as *const c_char, 8192) }
    }
}

impl Drop for BassLibrary {
    fn drop(&mut self) {
        for handle in self.plugin_handles.drain(..) {
            let _ = unsafe { (self.plugin_free)(handle) };
        }
        let _ = unsafe { (self.free)() };
    }
}

unsafe fn load_symbol<T: Copy>(library: &Library, name: &[u8]) -> Result<T> {
    let symbol: Symbol<T> = library
        .get(name)
        .with_context(|| format!("could not load symbol {}", String::from_utf8_lossy(name)))?;
    Ok(*symbol)
}

fn bass_seconds(seconds: f64) -> Option<f64> {
    seconds
        .is_finite()
        .then_some(seconds)
        .filter(|value| *value >= 0.0)
}

fn bass_error_message(error_code: i32) -> String {
    match error_code {
        BASS_ERROR_POSITION => "BASS error 7: invalid position".to_string(),
        BASS_ERROR_NOTAVAIL => "BASS error 37: requested action is not available".to_string(),
        BASS_ERROR_TIMEOUT => "BASS error 40: connection timed out".to_string(),
        BASS_ERROR_FILEFORM => "BASS error 41: unsupported file or playlist format".to_string(),
        BASS_ERROR_CODEC => "BASS error 44: codec is not available or supported".to_string(),
        BASS_ERROR_PROTOCOL => "BASS error 48: unsupported protocol".to_string(),
        BASS_ERROR_DENIED => "BASS error 49: access denied".to_string(),
        _ => format!("BASS error {error_code}"),
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn bass_seconds_rejects_unknown_values() {
        assert_eq!(Some(12.5), super::bass_seconds(12.5));
        assert_eq!(Some(0.0), super::bass_seconds(0.0));
        assert_eq!(None, super::bass_seconds(-1.0));
        assert_eq!(None, super::bass_seconds(f64::NAN));
        assert_eq!(None, super::bass_seconds(f64::INFINITY));
    }

    #[test]
    fn bass_error_message_names_seek_failures() {
        assert_eq!(
            "BASS error 7: invalid position",
            super::bass_error_message(super::BASS_ERROR_POSITION)
        );
        assert_eq!(
            "BASS error 37: requested action is not available",
            super::bass_error_message(super::BASS_ERROR_NOTAVAIL)
        );
        assert_eq!(
            "BASS error 41: unsupported file or playlist format",
            super::bass_error_message(super::BASS_ERROR_FILEFORM)
        );
    }
}
