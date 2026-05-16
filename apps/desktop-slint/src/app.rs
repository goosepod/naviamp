use crate::domain::{SearchResults, StreamRequest};
use crate::playback::{default_playback_engine, PlaybackEngine};
use crate::provider::navidrome::NavidromeProvider;
use crate::provider::MediaProvider;
use crate::settings::{default_settings_store, ConnectionDraft, Settings, SettingsStore};
use crate::ui::{search_rows, source_rows, AppWindow};
use crate::worker::BackgroundWorker;
use anyhow::{Context, Result};
use slint::{ComponentHandle, PhysicalSize};
use std::sync::{Arc, Mutex};

struct AppState {
    settings: Settings,
    search_results: SearchResults,
    playback: Box<dyn PlaybackEngine>,
}

pub fn run() -> Result<()> {
    let settings_store = Arc::new(default_settings_store()?);
    run_with_settings_store(settings_store)
}

fn run_with_settings_store(settings_store: Arc<dyn SettingsStore>) -> Result<()> {
    let ui = AppWindow::new()?;
    let settings = settings_store.load().unwrap_or_default();
    if let Some(source) = settings.active_source() {
        ui.set_server_url(source.server_url.into());
        ui.set_username(source.username.into());
    }
    if let (Some(width), Some(height)) = (settings.app.window.width, settings.app.window.height) {
        ui.window().set_size(PhysicalSize::new(width, height));
    }
    ui.set_sources(source_rows(&settings));
    ui.set_password(String::new().into());

    let controller = AppController {
        ui: ui.clone_strong(),
        state: Arc::new(Mutex::new(AppState {
            settings,
            search_results: SearchResults::default(),
            playback: default_playback_engine(),
        })),
        settings_store,
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
    settings_store: Arc<dyn SettingsStore>,
    worker: BackgroundWorker,
}

impl AppController {
    fn bind(&self) {
        self.bind_window_close();
        self.bind_connection();
        self.bind_sources();
        self.bind_search();
        self.bind_playback();
    }

    fn bind_window_close(&self) {
        let state = Arc::clone(&self.state);
        let ui_weak = self.ui.as_weak();
        let settings_store = Arc::clone(&self.settings_store);
        self.ui.window().on_close_requested(move || {
            if let Ok(mut state) = state.lock() {
                state.playback.stop();
                if let Some(ui) = ui_weak.upgrade() {
                    let size = ui.window().size();
                    state.settings.app.window.width = Some(size.width);
                    state.settings.app.window.height = Some(size.height);
                    state.settings.app.last_route = "search".to_string();
                    let _ = settings_store.save(&state.settings);
                }
            }
            std::process::exit(0);
        });
    }

    fn bind_connection(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let settings_store = Arc::clone(&self.settings_store);
        let worker = self.worker.clone();
        self.ui
            .on_connect_requested(move |server_url, username, password| {
                let draft = ConnectionDraft {
                    server_url: server_url.to_string(),
                    username: username.to_string(),
                    password: password.to_string(),
                };
                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_status_text("Checking connection...".into());
                }
                let ui_weak_for_result = ui_weak.clone();
                let state_for_save = Arc::clone(&state);
                let state_for_result = Arc::clone(&state);
                let settings_store = Arc::clone(&settings_store);
                worker.submit(move || {
                    let result = NavidromeProvider::from_password(&draft)
                        .and_then(|provider| provider.validate_connection())
                        .and_then(|()| NavidromeProvider::saved_source_from_password(&draft))
                        .and_then(|source| {
                            let mut settings = state_for_save
                                .lock()
                                .map_err(|error| anyhow::anyhow!(error.to_string()))
                                .map(|state| state.settings.clone())?;
                            settings.upsert_source(source);
                            settings_store.save(&settings)?;
                            Ok(settings)
                        });

                    slint::invoke_from_event_loop(move || {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            match result {
                                Ok(settings) => {
                                    if let Ok(mut state) = state_for_result.lock() {
                                        state.settings = settings;
                                        ui.set_sources(source_rows(&state.settings));
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

    fn bind_sources(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let settings_store = Arc::clone(&self.settings_store);
        self.ui.on_source_activated(move |index| {
            let result = state
                .lock()
                .map_err(|error| anyhow::anyhow!(error.to_string()))
                .and_then(|mut state| {
                    let source = state
                        .settings
                        .activate_source_by_index(index as usize)
                        .ok_or_else(|| anyhow::anyhow!("source not found"))?;
                    settings_store.save(&state.settings)?;
                    Ok((state.settings.clone(), source))
                });
            if let Some(ui) = ui_weak.upgrade() {
                match result {
                    Ok((settings, source)) => {
                        ui.set_server_url(source.server_url.into());
                        ui.set_username(source.username.into());
                        ui.set_password(String::new().into());
                        ui.set_sources(source_rows(&settings));
                        ui.set_status_text("Source selected".into());
                    }
                    Err(error) => ui.set_status_text(format!("Source failed: {error}").into()),
                }
            }
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let settings_store = Arc::clone(&self.settings_store);
        self.ui.on_source_delete_requested(move |index| {
            let result = state
                .lock()
                .map_err(|error| anyhow::anyhow!(error.to_string()))
                .and_then(|mut state| {
                    let removed = state
                        .settings
                        .remove_source_by_index(index as usize)
                        .ok_or_else(|| anyhow::anyhow!("source not found"))?;
                    settings_store.save(&state.settings)?;
                    Ok((state.settings.clone(), removed))
                });
            if let Some(ui) = ui_weak.upgrade() {
                match result {
                    Ok((settings, removed)) => {
                        if let Some(source) = settings.active_source() {
                            ui.set_server_url(source.server_url.into());
                            ui.set_username(source.username.into());
                        } else {
                            ui.set_server_url(String::new().into());
                            ui.set_username(String::new().into());
                        }
                        ui.set_password(String::new().into());
                        ui.set_sources(source_rows(&settings));
                        ui.set_status_text(format!("Deleted {}", removed.display_name).into());
                    }
                    Err(error) => ui.set_status_text(format!("Delete failed: {error}").into()),
                }
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
                .ok()
                .and_then(|state| state.settings.active_source());
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
                let result = settings
                    .ok_or_else(|| anyhow::anyhow!("no saved source"))
                    .and_then(NavidromeProvider::new)
                    .and_then(|provider| provider.search(&query));
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
                    let (source, artist) = {
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
                        let Some(source) = state.settings.active_source() else {
                            return;
                        };
                        (source, artist)
                    };

                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_status_text(format!("Opening {}", artist.name).into());
                    }
                    let ui_weak_for_result = ui_weak.clone();
                    worker.submit(move || {
                        let result = NavidromeProvider::new(source)
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
                    let (source, album) = {
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
                        let Some(source) = state.settings.active_source() else {
                            return;
                        };
                        (source, album)
                    };

                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_status_text(format!("Opening {}", album.title).into());
                    }
                    let ui_weak_for_result = ui_weak.clone();
                    worker.submit(move || {
                        let result = NavidromeProvider::new(source)
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

            let (source, track) = {
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
                let Some(source) = state.settings.active_source() else {
                    return;
                };
                (source, track)
            };

            let result = NavidromeProvider::new(source)
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
