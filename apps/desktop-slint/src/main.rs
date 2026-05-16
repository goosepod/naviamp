use anyhow::{anyhow, Context, Result};
use directories::ProjectDirs;
use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use slint::{ModelRc, SharedString, VecModel};
use std::cell::RefCell;
use std::fs;
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::rc::Rc;
use std::thread;

slint::include_modules!();

#[derive(Clone, Default, Serialize, Deserialize)]
struct Settings {
    server_url: String,
    username: String,
    password: String,
}

#[derive(Clone, Debug)]
struct Track {
    id: String,
    title: String,
    artist: String,
    album: String,
}

#[derive(Default)]
struct AppState {
    settings: Settings,
    player: Option<Child>,
}

fn main() -> Result<()> {
    let ui = AppWindow::new()?;
    let settings = load_settings().unwrap_or_default();
    ui.set_server_url(settings.server_url.clone().into());
    ui.set_username(settings.username.clone().into());
    ui.set_password(settings.password.clone().into());

    let state = Rc::new(RefCell::new(AppState {
        settings,
        player: None,
    }));

    {
        let ui_weak = ui.as_weak();
        let state = Rc::clone(&state);
        ui.on_connect_requested(move |server_url, username, password| {
            let settings = Settings {
                server_url: server_url.to_string(),
                username: username.to_string(),
                password: password.to_string(),
            };
            state.borrow_mut().settings = settings.clone();
            let message = match save_settings(&settings) {
                Ok(()) => "Connection saved".to_string(),
                Err(error) => format!("Could not save settings: {error}"),
            };
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text(message.into());
            }
        });
    }

    {
        let ui_weak = ui.as_weak();
        let state = Rc::clone(&state);
        ui.on_search_requested(move |query| {
            let settings = state.borrow().settings.clone();
            let query = query.to_string();
            if query.trim().is_empty() {
                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_status_text("Enter a search term".into());
                }
                return;
            }
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text("Searching...".into());
            }

            let ui_weak_for_result = ui_weak.clone();
            thread::spawn(move || {
                let result = search_tracks(&settings, &query);
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(tracks) => {
                                let rows = tracks
                                    .iter()
                                    .map(|track| TrackRow {
                                        title: SharedString::from(track.title.as_str()),
                                        subtitle: SharedString::from(format!(
                                            "{} - {}",
                                            empty_dash(&track.artist),
                                            empty_dash(&track.album),
                                        )),
                                    })
                                    .collect::<Vec<_>>();
                                TRACKS.with(|slot| {
                                    *slot.borrow_mut() = tracks;
                                });
                                ui.set_tracks(ModelRc::new(VecModel::from(rows)));
                                ui.set_status_text("Search complete".into());
                            }
                            Err(error) => {
                                ui.set_status_text(format!("Search failed: {error}").into());
                            }
                        }
                    }
                })
                .ok();
            });
        });
    }

    {
        let ui_weak = ui.as_weak();
        let state = Rc::clone(&state);
        ui.on_play_requested(move |index| {
            let settings = state.borrow().settings.clone();
            let Some(track) = TRACKS.with(|slot| slot.borrow().get(index as usize).cloned()) else {
                return;
            };
            let stream_url = stream_url(&settings, &track.id);
            let mut state = state.borrow_mut();
            if let Some(mut player) = state.player.take() {
                let _ = player.kill();
            }
            match Command::new("mpv")
                .arg("--no-video")
                .arg("--really-quiet")
                .arg(stream_url)
                .stdin(Stdio::null())
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .spawn()
            {
                Ok(child) => {
                    state.player = Some(child);
                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_status_text(format!("Playing {}", track.title).into());
                    }
                }
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_status_text(format!("Could not launch mpv: {error}").into());
                    }
                }
            }
        });
    }

    ui.run()?;
    Ok(())
}

thread_local! {
    static TRACKS: RefCell<Vec<Track>> = const { RefCell::new(Vec::new()) };
}

fn search_tracks(settings: &Settings, query: &str) -> Result<Vec<Track>> {
    validate_settings(settings)?;
    let response: serde_json::Value = Client::new()
        .get(api_url(settings, "search3.view"))
        .query(&[
            ("query", query),
            ("artistCount", "0"),
            ("albumCount", "0"),
            ("songCount", "50"),
        ])
        .send()
        .context("request failed")?
        .error_for_status()
        .context("server returned an error")?
        .json()
        .context("invalid json")?;

    let root = response
        .get("subsonic-response")
        .ok_or_else(|| anyhow!("missing subsonic response"))?;
    if root.get("status").and_then(|value| value.as_str()) == Some("failed") {
        let message = root
            .get("error")
            .and_then(|error| error.get("message"))
            .and_then(|value| value.as_str())
            .unwrap_or("Navidrome rejected the request");
        return Err(anyhow!(message.to_string()));
    }

    let songs = root
        .get("searchResult3")
        .and_then(|result| result.get("song"))
        .and_then(|song| song.as_array())
        .cloned()
        .unwrap_or_default();

    Ok(songs
        .into_iter()
        .filter_map(|song| {
            Some(Track {
                id: song.get("id")?.as_str()?.to_string(),
                title: song
                    .get("title")
                    .and_then(|value| value.as_str())
                    .unwrap_or("Untitled")
                    .to_string(),
                artist: song
                    .get("artist")
                    .and_then(|value| value.as_str())
                    .unwrap_or("")
                    .to_string(),
                album: song
                    .get("album")
                    .and_then(|value| value.as_str())
                    .unwrap_or("")
                    .to_string(),
            })
        })
        .collect())
}

fn stream_url(settings: &Settings, track_id: &str) -> String {
    format!(
        "{}&id={}",
        api_url(settings, "stream.view"),
        urlencoding::encode(track_id),
    )
}

fn api_url(settings: &Settings, endpoint: &str) -> String {
    let salt = "naviamp-slint";
    let token = format!("{:x}", md5::compute(format!("{}{}", settings.password, salt)));
    format!(
        "{}/rest/{}?u={}&t={}&s={}&v=1.16.1&c=NaviampSlint&f=json",
        settings.server_url.trim_end_matches('/'),
        endpoint,
        urlencoding::encode(&settings.username),
        token,
        salt,
    )
}

fn validate_settings(settings: &Settings) -> Result<()> {
    if settings.server_url.trim().is_empty() {
        return Err(anyhow!("server URL is empty"));
    }
    if settings.username.trim().is_empty() {
        return Err(anyhow!("username is empty"));
    }
    if settings.password.is_empty() {
        return Err(anyhow!("password is empty"));
    }
    Ok(())
}

fn empty_dash(value: &str) -> &str {
    if value.trim().is_empty() {
        "-"
    } else {
        value
    }
}

fn settings_path() -> Result<PathBuf> {
    let dirs = ProjectDirs::from("app", "naviamp", "Naviamp Slint")
        .ok_or_else(|| anyhow!("could not find config directory"))?;
    Ok(dirs.config_dir().join("settings.toml"))
}

fn load_settings() -> Result<Settings> {
    let path = settings_path()?;
    let content = fs::read_to_string(path)?;
    Ok(toml::from_str(&content)?)
}

fn save_settings(settings: &Settings) -> Result<()> {
    let path = settings_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, toml::to_string_pretty(settings)?)?;
    Ok(())
}
