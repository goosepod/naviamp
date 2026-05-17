use anyhow::{Context, Result};
use libloading::{Library, Symbol};
use std::env;
use std::ffi::{c_char, c_void, CString};
use std::path::{Path, PathBuf};
use std::sync::Arc;

#[allow(dead_code)]
pub trait PlaybackEngine: Send {
    fn play_url(&mut self, url: &str) -> Result<()>;

    fn pause(&mut self) -> Result<()>;

    fn resume(&mut self) -> Result<()>;

    fn seek_relative(&mut self, seconds: i32) -> Result<()>;

    fn set_volume(&mut self, percent: u8) -> Result<()>;

    fn stop(&mut self);
}

pub fn default_playback_engine() -> Box<dyn PlaybackEngine> {
    Box::<BassPlaybackEngine>::default()
}

type BassBool = i32;
type BassDword = u32;
type BassQword = u64;
type BassHandle = u32;

const BASS_POS_BYTE: BassDword = 0;
const BASS_ATTRIB_VOL: BassDword = 2;
const BASS_STREAM_BLOCK: BassDword = 0x100000;
const BASS_STREAM_STATUS: BassDword = 0x800000;

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
type BassChannelGetPosition = unsafe extern "system" fn(BassHandle, BassDword) -> BassQword;
type BassChannelSetPosition =
    unsafe extern "system" fn(BassHandle, BassQword, BassDword) -> BassBool;
type BassChannelBytes2Seconds = unsafe extern "system" fn(BassHandle, BassQword) -> f64;
type BassChannelSeconds2Bytes = unsafe extern "system" fn(BassHandle, f64) -> BassQword;

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
        self.stop();
        let bass = self.bass()?;
        let url = CString::new(url).context("stream URL contains an embedded null byte")?;
        let flags = BASS_STREAM_BLOCK | BASS_STREAM_STATUS;
        let stream =
            unsafe { (bass.stream_create_url)(url.as_ptr(), 0, flags, null_mut(), null_mut()) };
        if stream == 0 {
            return Err(bass.error("BASS_StreamCreateURL failed"));
        }
        self.stream = Some(stream);
        self.set_volume(self.volume)?;
        self.resume()
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
        let target_bytes = unsafe { (bass.channel_seconds2_bytes)(stream, target_seconds) };
        bass.check(
            unsafe { (bass.channel_set_position)(stream, target_bytes, BASS_POS_BYTE) },
            "BASS_ChannelSetPosition failed",
        )
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

    fn stop(&mut self) {
        if let (Some(bass), Some(stream)) = (self.bass.as_ref(), self.stream.take()) {
            let _ = unsafe { (bass.channel_stop)(stream) };
            let _ = unsafe { (bass.stream_free)(stream) };
        }
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
    channel_get_position: BassChannelGetPosition,
    channel_set_position: BassChannelSetPosition,
    channel_bytes2_seconds: BassChannelBytes2Seconds,
    channel_seconds2_bytes: BassChannelSeconds2Bytes,
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
            let channel_get_position =
                load_symbol::<BassChannelGetPosition>(&library, b"BASS_ChannelGetPosition\0")?;
            let channel_set_position =
                load_symbol::<BassChannelSetPosition>(&library, b"BASS_ChannelSetPosition\0")?;
            let channel_bytes2_seconds =
                load_symbol::<BassChannelBytes2Seconds>(&library, b"BASS_ChannelBytes2Seconds\0")?;
            let channel_seconds2_bytes =
                load_symbol::<BassChannelSeconds2Bytes>(&library, b"BASS_ChannelSeconds2Bytes\0")?;

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
                channel_get_position,
                channel_set_position,
                channel_bytes2_seconds,
                channel_seconds2_bytes,
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
        anyhow::anyhow!("{message}: BASS error {}", unsafe {
            (self.get_error_code)()
        })
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

    fn test_dir(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "naviamp-bass-resolver-{name}-{}",
            std::process::id()
        ))
    }
}
