use crate::domain::Track;
use crate::playback::MpvPlaybackEngine;
use crate::provider::navidrome::NavidromeProvider;
use crate::provider::MediaProvider;
use crate::settings::{load_settings, save_settings, Settings};
use crate::ui::{track_rows, AppWindow};
use crate::worker::BackgroundWorker;
use anyhow::{Context, Result};
use slint::ComponentHandle;
use std::sync::{Arc, Mutex};

#[derive(Default)]
struct AppState {
    settings: Settings,
    tracks: Vec<Track>,
    playback: MpvPlaybackEngine,
}

pub fn run() -> Result<()> {
    let ui = AppWindow::new()?;
    let settings = load_settings().unwrap_or_default();
    ui.set_server_url(settings.server_url.clone().into());
    ui.set_username(settings.username.clone().into());
    ui.set_password(settings.password.clone().into());

    let controller = AppController {
        ui: ui.clone_strong(),
        state: Arc::new(Mutex::new(AppState {
            settings,
            tracks: Vec::new(),
            playback: MpvPlaybackEngine::default(),
        })),
        worker: BackgroundWorker::new("naviamp-background"),
    };
    controller.bind();

    ui.run()?;
    controller.stop_playback();
    Ok(())
}

struct AppController {
    ui: AppWindow,
    state: Arc<Mutex<AppState>>,
    worker: BackgroundWorker,
}

impl AppController {
    fn bind(&self) {
        self.bind_window_close();
        self.bind_connection();
        self.bind_search();
        self.bind_playback();
    }

    fn bind_window_close(&self) {
        let state = Arc::clone(&self.state);
        self.ui.window().on_close_requested(move || {
            if let Ok(mut state) = state.lock() {
                state.playback.stop();
            }
            std::process::exit(0);
        });
    }

    fn bind_connection(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        self.ui
            .on_connect_requested(move |server_url, username, password| {
                let settings = Settings {
                    server_url: server_url.to_string(),
                    username: username.to_string(),
                    password: password.to_string(),
                };
                if let Ok(mut state) = state.lock() {
                    state.settings = settings.clone();
                }
                let message = match save_settings(&settings) {
                    Ok(()) => "Connection saved".to_string(),
                    Err(error) => format!("Could not save settings: {error}"),
                };
                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_status_text(message.into());
                }
            });
    }

    fn bind_search(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        self.ui.on_search_requested(move |query| {
            let settings = state
                .lock()
                .map(|state| state.settings.clone())
                .unwrap_or_default();
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
            let state_for_result = Arc::clone(&state);
            worker.submit(move || {
                let result = NavidromeProvider::new(settings)
                    .and_then(|provider| provider.search_tracks(&query));
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(tracks) => {
                                if let Ok(mut state) = state_for_result.lock() {
                                    state.tracks = tracks.clone();
                                }
                                ui.set_tracks(track_rows(&tracks));
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

    fn bind_playback(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        self.ui.on_play_requested(move |index| {
            let (settings, track) = {
                let state = match state.lock() {
                    Ok(state) => state,
                    Err(error) => {
                        if let Some(ui) = ui_weak.upgrade() {
                            ui.set_status_text(format!("Playback failed: {error}").into());
                        }
                        return;
                    }
                };
                let Some(track) = state.tracks.get(index as usize).cloned() else {
                    return;
                };
                (state.settings.clone(), track)
            };

            let result = NavidromeProvider::new(settings)
                .and_then(|provider| provider.stream_url(&track.id))
                .and_then(|url| {
                    state
                        .lock()
                        .map_err(|error| anyhow::anyhow!(error.to_string()))
                        .and_then(|mut state| state.playback.play_url(&url))
                        .context("could not start playback")
                });

            if let Some(ui) = ui_weak.upgrade() {
                match result {
                    Ok(()) => ui.set_status_text(format!("Playing {}", track.title).into()),
                    Err(error) => ui.set_status_text(format!("Playback failed: {error}").into()),
                }
            }
        });
    }

    fn stop_playback(&self) {
        if let Ok(mut state) = self.state.lock() {
            state.playback.stop();
        }
    }
}
