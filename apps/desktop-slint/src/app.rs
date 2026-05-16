use crate::domain::{SearchResults, StreamRequest};
use crate::playback::{default_playback_engine, PlaybackEngine};
use crate::provider::navidrome::NavidromeProvider;
use crate::provider::MediaProvider;
use crate::settings::{load_settings, save_settings, Settings};
use crate::ui::{search_rows, AppWindow};
use crate::worker::BackgroundWorker;
use anyhow::{Context, Result};
use slint::ComponentHandle;
use std::sync::{Arc, Mutex};

struct AppState {
    settings: Settings,
    search_results: SearchResults,
    playback: Box<dyn PlaybackEngine>,
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
            search_results: SearchResults::default(),
            playback: default_playback_engine(),
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
        let worker = self.worker.clone();
        self.ui
            .on_connect_requested(move |server_url, username, password| {
                let settings = Settings {
                    server_url: server_url.to_string(),
                    username: username.to_string(),
                    password: password.to_string(),
                };
                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_status_text("Checking connection...".into());
                }
                let ui_weak_for_result = ui_weak.clone();
                let state_for_result = Arc::clone(&state);
                worker.submit(move || {
                    let result = NavidromeProvider::new(settings.clone())
                        .and_then(|provider| provider.validate_connection())
                        .and_then(|()| save_settings(&settings))
                        .map(|()| settings);

                    slint::invoke_from_event_loop(move || {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            match result {
                                Ok(settings) => {
                                    if let Ok(mut state) = state_for_result.lock() {
                                        state.settings = settings;
                                    }
                                    ui.set_status_text("Connection saved".into());
                                }
                                Err(error) => {
                                    ui.set_status_text(
                                        format!("Connection failed: {error}").into(),
                                    );
                                }
                            }
                        }
                    })
                    .ok();
                });
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
                let result =
                    NavidromeProvider::new(settings).and_then(|provider| provider.search(&query));
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(results) => {
                                if let Ok(mut state) = state_for_result.lock() {
                                    state.search_results = results.clone();
                                }
                                ui.set_media_rows(search_rows(&results));
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
        let worker = self.worker.clone();
        self.ui.on_row_activated(move |kind, index| {
            match kind.as_str() {
                "artist" => {
                    let (settings, artist) = {
                        let state = match state.lock() {
                            Ok(state) => state,
                            Err(error) => {
                                if let Some(ui) = ui_weak.upgrade() {
                                    ui.set_status_text(format!("Artist failed: {error}").into());
                                }
                                return;
                            }
                        };
                        let Some(artist) =
                            state.search_results.artists.get(index as usize).cloned()
                        else {
                            return;
                        };
                        (state.settings.clone(), artist)
                    };

                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_status_text(format!("Opening {}", artist.name).into());
                    }
                    let ui_weak_for_result = ui_weak.clone();
                    worker.submit(move || {
                        let result = NavidromeProvider::new(settings)
                            .and_then(|provider| provider.artist(&artist.id));
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(detail) => ui.set_status_text(
                                        format!(
                                            "{} albums by {}",
                                            detail.albums.len(),
                                            detail.artist.name
                                        )
                                        .into(),
                                    ),
                                    Err(error) => {
                                        ui.set_status_text(
                                            format!("Artist failed: {error}").into(),
                                        );
                                    }
                                }
                            }
                        })
                        .ok();
                    });
                    return;
                }
                "album" => {
                    let (settings, album) = {
                        let state = match state.lock() {
                            Ok(state) => state,
                            Err(error) => {
                                if let Some(ui) = ui_weak.upgrade() {
                                    ui.set_status_text(format!("Album failed: {error}").into());
                                }
                                return;
                            }
                        };
                        let Some(album) = state.search_results.albums.get(index as usize).cloned()
                        else {
                            return;
                        };
                        (state.settings.clone(), album)
                    };

                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_status_text(format!("Opening {}", album.title).into());
                    }
                    let ui_weak_for_result = ui_weak.clone();
                    worker.submit(move || {
                        let result = NavidromeProvider::new(settings)
                            .and_then(|provider| provider.album(&album.id));
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(detail) => ui.set_status_text(
                                        format!(
                                            "{} tracks on {}",
                                            detail.tracks.len(),
                                            detail.album.title
                                        )
                                        .into(),
                                    ),
                                    Err(error) => {
                                        ui.set_status_text(format!("Album failed: {error}").into());
                                    }
                                }
                            }
                        })
                        .ok();
                    });
                    return;
                }
                "track" => {}
                _ => return,
            };

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
                let Some(track) = state.search_results.tracks.get(index as usize).cloned() else {
                    return;
                };
                (state.settings.clone(), track)
            };

            let result = NavidromeProvider::new(settings)
                .and_then(|provider| provider.stream_url(&StreamRequest::original(&track.id)))
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
