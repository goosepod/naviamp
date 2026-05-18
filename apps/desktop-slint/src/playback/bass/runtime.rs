use anyhow::Result;
use std::env;
use std::path::{Path, PathBuf};

pub(super) struct BassLibraryResolver {
    env_dir: Option<PathBuf>,
    app_dir: Option<PathBuf>,
}

impl BassLibraryResolver {
    pub(super) fn new() -> Self {
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

    pub(super) fn resolve(&self) -> Result<PathBuf> {
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
            if let Some(contents_dir) = app_dir.parent() {
                dirs.push(
                    contents_dir
                        .join("Resources")
                        .join("playback")
                        .join("bass")
                        .join(platform_slug()),
                );
                dirs.push(contents_dir.join("Resources").join("bass"));
            }
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

pub(super) fn bass_plugin_names() -> &'static [&'static str] {
    &[
        "bass_aac", "bassflac", "bassopus", "bassalac", "bassape", "bassdsd", "bass_mpc",
        "basshls", "basswebm", "bassmidi", "bassmix", "bass_fx", "basswv", "basswma",
    ]
}

pub(super) fn dynamic_library_name(stem: &str) -> String {
    if cfg!(target_os = "windows") {
        format!("{stem}.dll")
    } else if cfg!(target_os = "macos") {
        format!("lib{stem}.dylib")
    } else {
        format!("lib{stem}.so")
    }
}

pub(super) fn platform_slug() -> &'static str {
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
    fn resolver_uses_macos_app_bundle_resources() {
        let temp_dir = test_dir("macos-app-bundle");
        let app_dir = temp_dir.join("Contents").join("MacOS");
        let bundled_dir = temp_dir
            .join("Contents")
            .join("Resources")
            .join("playback")
            .join("bass")
            .join(super::platform_slug());
        fs::create_dir_all(&app_dir).expect("create app dir");
        fs::create_dir_all(&bundled_dir).expect("create bundled dir");
        fs::write(bundled_dir.join(dynamic_library_name("bass")), b"")
            .expect("create bass library");
        let resolver = BassLibraryResolver::with_paths(None, Some(app_dir));

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
