use anyhow::{Context, Result};
use libloading::{Library, Symbol};
use std::env;
use std::ffi::{c_char, c_void, CStr, CString};
use std::path::{Path, PathBuf};
use std::sync::Arc;

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

type BassBool = i32;
type BassDword = u32;
type BassQword = u64;
type BassHandle = u32;

const BASS_POS_BYTE: BassDword = 0;
const BASS_ACTIVE_PLAYING: BassDword = 1;
const BASS_ATTRIB_VOL: BassDword = 2;
const BASS_STREAM_STATUS: BassDword = 0x800000;
const BASS_ERROR_POSITION: i32 = 7;
const BASS_ERROR_NOTAVAIL: i32 = 37;
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

pub struct BassPlaybackEngine {
    bass: Option<Arc<BassLibrary>>,
    stream: Option<BassHandle>,
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
        let flags = BASS_STREAM_STATUS;
        let stream =
            unsafe { (bass.stream_create_url)(url.as_ptr(), 0, flags, null_mut(), null_mut()) };
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

struct BassLibrary {
    _library: Library,
    plugin_handles: Vec<BassHandle>,
    free: BassFree,
    get_error_code: BassGetErrorCode,
    stream_create_url: BassStreamCreateUrl,
    stream_free: BassStreamFree,
    plugin_free: BassPluginFree,
    init: BassInit,
    channel_play: BassChannelPlay,
    channel_pause: BassChannelPause,
    channel_stop: BassChannelStop,
    _channel_is_active: BassChannelIsActive,
    channel_set_attribute: BassChannelSetAttribute,
    channel_get_length: BassChannelGetLength,
    channel_get_position: BassChannelGetPosition,
    channel_set_position: BassChannelSetPosition,
    channel_bytes2_seconds: BassChannelBytes2Seconds,
    channel_seconds2_bytes: BassChannelSeconds2Bytes,
    channel_get_tags: BassChannelGetTags,
}

impl BassLibrary {
    fn load() -> Result<Self> {
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
                _channel_is_active: channel_is_active,
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

    fn init(&self) -> Result<()> {
        self.check(
            unsafe { (self.init)(-1, 44100, 0, null_mut(), null_mut()) },
            "BASS_Init failed",
        )
    }

    fn check(&self, ok: BassBool, message: &str) -> Result<()> {
        if ok == 0 {
            Err(self.error(message))
        } else {
            Ok(())
        }
    }

    fn error(&self, message: &str) -> anyhow::Error {
        let error_code = unsafe { (self.get_error_code)() };
        anyhow::anyhow!("{}: {}", message, bass_error_message(error_code))
    }

    fn snapshot(&self, stream: BassHandle, volume: u8) -> PlaybackSnapshot {
        let position_bytes = unsafe { (self.channel_get_position)(stream, BASS_POS_BYTE) };
        let length_bytes = unsafe { (self.channel_get_length)(stream, BASS_POS_BYTE) };
        PlaybackSnapshot {
            position_seconds: bass_seconds(unsafe {
                (self.channel_bytes2_seconds)(stream, position_bytes)
            }),
            duration_seconds: bass_seconds(unsafe {
                (self.channel_bytes2_seconds)(stream, length_bytes)
            }),
            is_playing: unsafe { (self._channel_is_active)(stream) } == BASS_ACTIVE_PLAYING,
            volume,
            stream_title: self.stream_title(stream),
        }
    }

    fn seek_absolute(&self, stream: BassHandle, seconds: f64) -> Result<()> {
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

    fn embedded_cover_art(&self, stream: BassHandle) -> Option<Vec<u8>> {
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

        unsafe { CStr::from_ptr(value as *const c_char) }
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

struct BassLibraryResolver {
    env_dir: Option<PathBuf>,
    app_dir: Option<PathBuf>,
}

impl BassLibraryResolver {
    fn new() -> Self {
        Self {
            env_dir: env::var_os("NAVIAMP_BASS_DIR").map(PathBuf::from),
            app_dir: env::current_exe()
                .ok()
                .and_then(|path| path.parent().map(Path::to_path_buf)),
        }
    }

    #[cfg(test)]
    fn with_paths(env_dir: Option<PathBuf>, app_dir: Option<PathBuf>) -> Self {
        Self { env_dir, app_dir }
    }

    fn resolve(&self) -> Result<PathBuf> {
        self.candidate_dirs()
            .into_iter()
            .find(|dir| dir.join(dynamic_library_name("bass")).is_file())
            .ok_or_else(|| {
                anyhow::anyhow!(
                    "could not find BASS; set NAVIAMP_BASS_DIR or put {} beside Naviamp",
                    dynamic_library_name("bass")
                )
            })
    }

    fn candidate_dirs(&self) -> Vec<PathBuf> {
        let mut dirs = Vec::new();
        if let Some(dir) = self
            .env_dir
            .as_ref()
            .filter(|dir| !dir.as_os_str().is_empty())
        {
            dirs.push(dir.clone());
        }
        if let Some(app_dir) = &self.app_dir {
            dirs.push(app_dir.clone());
            dirs.push(app_dir.join("bass"));
            dirs.push(app_dir.join("resources").join("bass"));
            dirs.push(
                app_dir
                    .join("resources")
                    .join("playback")
                    .join("bass")
                    .join(platform_slug()),
            );
            dirs.push(app_dir.join("playback").join("bass").join(platform_slug()));
        }
        if let Ok(current_dir) = env::current_dir() {
            dirs.push(
                current_dir
                    .join("vendor")
                    .join("bass")
                    .join(platform_slug()),
            );
            dirs.push(
                current_dir
                    .join("apps")
                    .join("desktop-slint")
                    .join("vendor")
                    .join("bass")
                    .join(platform_slug()),
            );
        }
        dirs
    }
}

fn bass_plugin_names() -> &'static [&'static str] {
    &[
        "bassflac", "bassopus", "bassalac", "bassape", "bassdsd", "bass_mpc", "basshls",
        "basswebm", "bassmidi", "bassmix", "bass_fx", "basswma",
    ]
}

fn dynamic_library_name(stem: &str) -> String {
    if cfg!(target_os = "windows") {
        format!("{stem}.dll")
    } else if cfg!(target_os = "macos") {
        format!("lib{stem}.dylib")
    } else {
        format!("lib{stem}.so")
    }
}

fn platform_slug() -> &'static str {
    if cfg!(target_os = "windows") {
        if cfg!(target_arch = "aarch64") {
            "windows-arm64"
        } else {
            "windows-x64"
        }
    } else if cfg!(target_os = "macos") {
        if cfg!(target_arch = "aarch64") {
            "macos-arm64"
        } else {
            "macos-x64"
        }
    } else if cfg!(target_os = "linux") {
        if cfg!(target_arch = "aarch64") {
            "linux-arm64"
        } else {
            "linux-x64"
        }
    } else {
        "unknown"
    }
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
        _ => format!("BASS error {error_code}"),
    }
}

fn extract_id3_cover_art(tag: &[u8]) -> Option<Vec<u8>> {
    if tag.len() < 10 || &tag[0..3] != b"ID3" {
        return None;
    }

    let version = tag[3];
    let tag_size = synchsafe_u32(&tag[6..10])? as usize;
    let tag_end = 10usize.saturating_add(tag_size).min(tag.len());
    let mut offset = 10usize;

    while offset < tag_end {
        let frame = match version {
            2 => next_id3v22_frame(tag, offset, tag_end)?,
            3 | 4 => next_id3v23_or_24_frame(tag, offset, tag_end, version)?,
            _ => return None,
        };

        if frame.id == "APIC" || frame.id == "PIC" {
            return extract_image_from_frame(frame.data);
        }
        offset = frame.next_offset;
    }

    None
}

fn parse_icy_stream_title(meta: &str) -> Option<String> {
    let marker = "StreamTitle='";
    let start = meta.find(marker)? + marker.len();
    let rest = &meta[start..];
    let end = rest.find("';").or_else(|| rest.find('\''))?;
    rest[..end].trim().to_string().into_non_empty()
}

fn parse_icy_header_title(header: String) -> Option<String> {
    let (key, value) = header.split_once(':')?;
    if !matches!(
        key.trim().to_ascii_lowercase().as_str(),
        "icy-name" | "icy-description"
    ) {
        return None;
    }

    value.trim().to_string().into_non_empty()
}

struct Id3Frame<'a> {
    id: &'a str,
    data: &'a [u8],
    next_offset: usize,
}

fn next_id3v23_or_24_frame<'a>(
    tag: &'a [u8],
    offset: usize,
    tag_end: usize,
    version: u8,
) -> Option<Id3Frame<'a>> {
    if offset + 10 > tag_end || tag[offset..offset + 4].iter().all(|byte| *byte == 0) {
        return None;
    }

    let id = std::str::from_utf8(&tag[offset..offset + 4]).ok()?;
    let size = if version == 4 {
        synchsafe_u32(&tag[offset + 4..offset + 8])?
    } else {
        u32::from_be_bytes(tag[offset + 4..offset + 8].try_into().ok()?)
    } as usize;
    let data_start = offset + 10;
    let data_end = data_start.checked_add(size)?.min(tag_end);
    Some(Id3Frame {
        id,
        data: &tag[data_start..data_end],
        next_offset: data_end,
    })
}

fn next_id3v22_frame<'a>(tag: &'a [u8], offset: usize, tag_end: usize) -> Option<Id3Frame<'a>> {
    if offset + 6 > tag_end || tag[offset..offset + 3].iter().all(|byte| *byte == 0) {
        return None;
    }

    let id = std::str::from_utf8(&tag[offset..offset + 3]).ok()?;
    let size = ((tag[offset + 3] as usize) << 16)
        | ((tag[offset + 4] as usize) << 8)
        | tag[offset + 5] as usize;
    let data_start = offset + 6;
    let data_end = data_start.checked_add(size)?.min(tag_end);
    Some(Id3Frame {
        id,
        data: &tag[data_start..data_end],
        next_offset: data_end,
    })
}

fn synchsafe_u32(bytes: &[u8]) -> Option<u32> {
    if bytes.len() != 4 || bytes.iter().any(|byte| byte & 0x80 != 0) {
        return None;
    }

    Some(
        ((bytes[0] as u32) << 21)
            | ((bytes[1] as u32) << 14)
            | ((bytes[2] as u32) << 7)
            | bytes[3] as u32,
    )
}

fn extract_image_from_frame(frame: &[u8]) -> Option<Vec<u8>> {
    let start = image_magic_offset(frame)?;
    Some(frame[start..].to_vec())
}

fn image_magic_offset(bytes: &[u8]) -> Option<usize> {
    bytes.windows(4).position(|window| {
        window.starts_with(&[0xff, 0xd8, 0xff])
            || window == b"\x89PNG"
            || window == b"GIF8"
            || window == b"RIFF"
    })
}

unsafe fn read_null_terminated_string_list(values: *const c_char, max_bytes: usize) -> Vec<String> {
    let mut strings = Vec::new();
    let mut offset = 0usize;
    while offset < max_bytes {
        let current = values.add(offset);
        if *current == 0 {
            break;
        }

        let value = CStr::from_ptr(current).to_string_lossy().trim().to_string();
        offset += value.len() + 1;
        if let Some(value) = value.into_non_empty() {
            strings.push(value);
        }
    }
    strings
}

trait NonEmptyString {
    fn into_non_empty(self) -> Option<String>;
}

impl NonEmptyString for String {
    fn into_non_empty(self) -> Option<String> {
        (!self.is_empty()).then_some(self)
    }
}

fn null_mut<T>() -> *mut T {
    std::ptr::null_mut()
}

#[cfg(test)]
mod tests {
    use super::{dynamic_library_name, BassLibraryResolver};
    use std::fs;
    use std::path::PathBuf;

    #[test]
    fn resolver_prefers_env_dir() {
        let temp_dir = test_dir("env");
        fs::create_dir_all(&temp_dir).expect("create temp dir");
        fs::write(temp_dir.join(dynamic_library_name("bass")), b"").expect("create bass library");
        let resolver = BassLibraryResolver::with_paths(Some(temp_dir.clone()), None);

        assert_eq!(temp_dir.clone(), resolver.resolve().expect("resolve"));

        let _ = fs::remove_dir_all(temp_dir);
    }

    #[test]
    fn resolver_uses_adjacent_library() {
        let temp_dir = test_dir("adjacent");
        fs::create_dir_all(&temp_dir).expect("create temp dir");
        fs::write(temp_dir.join(dynamic_library_name("bass")), b"").expect("create bass library");
        let resolver = BassLibraryResolver::with_paths(None, Some(temp_dir.clone()));

        assert_eq!(temp_dir.clone(), resolver.resolve().expect("resolve"));

        let _ = fs::remove_dir_all(temp_dir);
    }

    #[test]
    fn resolver_uses_bundled_library() {
        let temp_dir = test_dir("bundled");
        let bundled_dir = temp_dir.join("resources").join("bass");
        fs::create_dir_all(&bundled_dir).expect("create bundled dir");
        fs::write(bundled_dir.join(dynamic_library_name("bass")), b"")
            .expect("create bass library");
        let resolver = BassLibraryResolver::with_paths(None, Some(temp_dir.clone()));

        assert_eq!(bundled_dir, resolver.resolve().expect("resolve"));

        let _ = fs::remove_dir_all(temp_dir);
    }

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
    }

    #[test]
    fn extracts_cover_art_from_id3v23_apic_frame() {
        let image = [0xff, 0xd8, 0xff, 0xdb, 1, 2, 3];
        let mut frame = Vec::new();
        frame.extend_from_slice(&[0]);
        frame.extend_from_slice(b"image/jpeg");
        frame.push(0);
        frame.push(3);
        frame.push(0);
        frame.extend_from_slice(&image);

        let mut tag = Vec::new();
        tag.extend_from_slice(b"ID3");
        tag.extend_from_slice(&[3, 0, 0]);
        let tag_size = 10 + frame.len();
        tag.extend_from_slice(&synchsafe(tag_size as u32));
        tag.extend_from_slice(b"APIC");
        tag.extend_from_slice(&(frame.len() as u32).to_be_bytes());
        tag.extend_from_slice(&[0, 0]);
        tag.extend_from_slice(&frame);

        assert_eq!(Some(image.to_vec()), super::extract_id3_cover_art(&tag));
    }

    #[test]
    fn parses_icy_stream_title() {
        assert_eq!(
            Some("Artist - Song".to_string()),
            super::parse_icy_stream_title("StreamTitle='Artist - Song';StreamUrl='';")
        );
        assert_eq!(None, super::parse_icy_stream_title("StreamUrl='';"));
    }

    #[test]
    fn parses_icy_header_title() {
        assert_eq!(
            Some("Station".to_string()),
            super::parse_icy_header_title("icy-name: Station".to_string())
        );
        assert_eq!(
            Some("Description".to_string()),
            super::parse_icy_header_title("icy-description: Description".to_string())
        );
        assert_eq!(
            None,
            super::parse_icy_header_title("content-type: audio/mpeg".to_string())
        );
    }

    fn synchsafe(value: u32) -> [u8; 4] {
        [
            ((value >> 21) & 0x7f) as u8,
            ((value >> 14) & 0x7f) as u8,
            ((value >> 7) & 0x7f) as u8,
            (value & 0x7f) as u8,
        ]
    }

    fn test_dir(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "naviamp-bass-resolver-{name}-{}",
            std::process::id()
        ))
    }
}
