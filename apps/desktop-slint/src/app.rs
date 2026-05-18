use crate::domain::{InternetRadioStation, SearchResults, StreamRequest, Track};
use crate::image_cache::{default_cover_art_cache, CoverArtCache};
use crate::playback::{default_playback_engine, PlaybackEngine, PlaybackSnapshot};
use crate::provider::navidrome::NavidromeProvider;
use crate::provider::MediaProvider;
use crate::queue::{RepeatMode, TrackQueue};
use crate::settings::{
    default_settings_store, ConnectionDraft, SavedMediaSource, Settings, SettingsStore,
};
use crate::ui::{radio_rows, search_rows, source_rows, AppWindow};
use crate::worker::BackgroundWorker;
use anyhow::{Context, Result};
use reqwest::blocking::Client;
use slint::{ComponentHandle, Image, PhysicalSize, Timer, TimerMode, Weak};
use std::sync::{Arc, Mutex};
use std::time::Duration;

const MIN_WINDOW_WIDTH: u32 = 860;
const MIN_WINDOW_HEIGHT: u32 = 600;

struct AppState {
    settings: Settings,
    search_results: SearchResults,
    radio_stations: Vec<InternetRadioStation>,
    playback: Box<dyn PlaybackEngine>,
    latest_playback_snapshot: PlaybackSnapshot,
    current_playback: Option<CurrentPlayback>,
    queue: TrackQueue,
    playback_session_id: u64,
}

#[derive(Clone)]
struct CurrentPlayback {
    source: SavedMediaSource,
    track: Track,
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
        ui.window().set_size(PhysicalSize::new(
            width.max(MIN_WINDOW_WIDTH),
            height.max(MIN_WINDOW_HEIGHT),
        ));
    }
    ui.set_sources(source_rows(&settings));
    ui.set_password(String::new().into());

    let controller = AppController {
        ui: ui.clone_strong(),
        state: Arc::new(Mutex::new(AppState {
            settings,
            search_results: SearchResults::default(),
            radio_stations: Vec::new(),
            playback: default_playback_engine(),
            latest_playback_snapshot: PlaybackSnapshot::default(),
            current_playback: None,
            queue: TrackQueue::default(),
            playback_session_id: 0,
        })),
        settings_store,
        worker: BackgroundWorker::new("naviamp-background"),
        playback_worker: BackgroundWorker::new("naviamp-playback"),
        playback_timer: Timer::default(),
        cover_art_cache: Arc::new(default_cover_art_cache()?),
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
    playback_worker: BackgroundWorker,
    playback_timer: Timer,
    cover_art_cache: Arc<CoverArtCache>,
}

impl AppController {
    fn bind(&self) {
        self.bind_window_close();
        self.bind_connection();
        self.bind_error_banner();
        self.bind_sources();
        self.bind_playback_controls();
        self.bind_playback_snapshot_polling();
        self.bind_search();
        self.bind_radio();
        self.bind_playback();
    }

    fn bind_error_banner(&self) {
        let ui_weak = self.ui.as_weak();
        self.ui.on_error_dismissed(move || {
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_error_text(String::new().into());
            }
        });
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
                                    set_error_text(&ui, format!("Connection failed: {error}"));
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
                    Err(error) => set_error_text(&ui, format!("Source failed: {error}")),
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
                    Err(error) => set_error_text(&ui, format!("Delete failed: {error}")),
                }
            }
        });
    }

    fn bind_playback_controls(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        self.ui.on_playback_pause_requested(move || {
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            playback_worker.submit(move || {
                let result = state_for_worker
                    .lock()
                    .map_err(|error| anyhow::anyhow!(error.to_string()))
                    .and_then(|mut state| state.playback.pause());
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(()) => ui.set_status_text("Paused".into()),
                            Err(error) => set_error_text(&ui, format!("Pause failed: {error}")),
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        self.ui.on_playback_resume_requested(move || {
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            playback_worker.submit(move || {
                let result = state_for_worker
                    .lock()
                    .map_err(|error| anyhow::anyhow!(error.to_string()))
                    .and_then(|mut state| state.playback.resume());
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(()) => ui.set_status_text("Playing".into()),
                            Err(error) => set_error_text(&ui, format!("Resume failed: {error}")),
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        let playback_worker = self.playback_worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_playback_previous_requested(move || {
            if let Some(ui) = ui_weak.upgrade() {
                reset_playback_progress_ui(&ui);
                ui.set_status_text("Starting previous track...".into());
            }
            begin_playback_transition(&state);
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            let worker_for_art = worker.clone();
            let cover_art_cache = Arc::clone(&cover_art_cache);
            playback_worker.submit(move || {
                let result = play_queue_neighbor(&state_for_worker, QueueDirection::Previous);
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(started) => handle_started_track_playback(
                                &ui,
                                &state_for_worker,
                                worker_for_art,
                                ui_weak_for_result.clone(),
                                cover_art_cache,
                                started,
                            ),
                            Err(error) => {
                                set_error_text(&ui, format!("Previous failed: {error:#}"))
                            }
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        let playback_worker = self.playback_worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_playback_next_requested(move || {
            if let Some(ui) = ui_weak.upgrade() {
                reset_playback_progress_ui(&ui);
                ui.set_status_text("Starting next track...".into());
            }
            begin_playback_transition(&state);
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            let worker_for_art = worker.clone();
            let cover_art_cache = Arc::clone(&cover_art_cache);
            playback_worker.submit(move || {
                let result = play_queue_neighbor(&state_for_worker, QueueDirection::Next);
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(started) => handle_started_track_playback(
                                &ui,
                                &state_for_worker,
                                worker_for_art,
                                ui_weak_for_result.clone(),
                                cover_art_cache,
                                started,
                            ),
                            Err(error) => set_error_text(&ui, format!("Next failed: {error:#}")),
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        self.ui.on_playback_shuffle_requested(move || {
            let result = state
                .lock()
                .map_err(|error| anyhow::anyhow!(error.to_string()))
                .map(|mut state| state.queue.shuffle_upcoming());
            if let Some(ui) = ui_weak.upgrade() {
                match result {
                    Ok(()) => ui.set_status_text("Shuffled upcoming tracks".into()),
                    Err(error) => set_error_text(&ui, format!("Shuffle failed: {error}")),
                }
            }
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        self.ui.on_volume_changed(move |volume| {
            let percent = volume.clamp(0.0, 100.0).round() as u8;
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            playback_worker.submit(move || {
                let result = state_for_worker
                    .lock()
                    .map_err(|error| anyhow::anyhow!(error.to_string()))
                    .and_then(|mut state| state.playback.set_volume(percent));
                if let Err(error) = result {
                    slint::invoke_from_event_loop(move || {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            set_error_text(&ui, format!("Volume failed: {error}"));
                        }
                    })
                    .ok();
                }
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        self.ui.on_playback_seek_requested(move |progress| {
            let progress = progress.clamp(0.0, 100.0);
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            playback_worker.submit(move || {
                let result = seek_to_progress(&state_for_worker, progress);
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        apply_seek_result(&ui, result, progress);
                    }
                })
                .ok();
            });
        });
    }

    fn bind_playback_snapshot_polling(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        self.playback_timer
            .start(TimerMode::Repeated, Duration::from_millis(500), move || {
                let state_for_worker = Arc::clone(&state);
                let ui_weak_for_result = ui_weak.clone();
                playback_worker.submit(move || {
                    if let Some(snapshot) = poll_playback_snapshot(&state_for_worker) {
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                ui.set_playback_status_text(playback_status_text(&snapshot).into());
                                ui.set_playback_elapsed_text(
                                    playback_elapsed_text(&snapshot).into(),
                                );
                                ui.set_playback_duration_text(
                                    playback_duration_text(&snapshot).into(),
                                );
                                ui.set_playback_progress(playback_progress(&snapshot));
                                ui.set_playback_is_playing(snapshot.is_playing);
                            }
                        })
                        .ok();
                    }
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
                                set_error_text(&ui, format!("Search failed: {error}"));
                            }
                        }
                    }
                })
                .ok();
            });
        });
    }

    fn bind_radio(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        self.ui.on_radio_requested(move || {
            let settings = state
                .lock()
                .ok()
                .and_then(|state| state.settings.active_source());

            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text("Loading radio stations...".into());
            }

            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            worker.submit(move || {
                let result = settings
                    .ok_or_else(|| anyhow::anyhow!("no saved source"))
                    .and_then(NavidromeProvider::new)
                    .and_then(|provider| provider.internet_radio_stations());
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(stations) => {
                                if let Ok(mut state) = state_for_result.lock() {
                                    state.radio_stations = stations.clone();
                                }
                                ui.set_media_rows(radio_rows(&stations));
                                ui.set_status_text(
                                    format!("{} radio stations", stations.len()).into(),
                                );
                            }
                            Err(error) => {
                                set_error_text(&ui, format!("Radio failed: {error}"));
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
        let playback_worker = self.playback_worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_row_activated(move |kind, index| {
            match kind.as_str() {
                "artist" => {
                    let (source, artist) = {
                        let state = match state.lock() {
                            Ok(state) => state,
                            Err(error) => {
                                if let Some(ui) = ui_weak.upgrade() {
                                    set_error_text(&ui, format!("Artist failed: {error}"));
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
                                        set_error_text(&ui, format!("Artist failed: {error}"));
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
                                    set_error_text(&ui, format!("Album failed: {error}"));
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
                                        set_error_text(&ui, format!("Album failed: {error}"));
                                    }
                                }
                            }
                        })
                        .ok();
                    });
                    return;
                }
                "track" => {}
                "radio" => {
                    let station = {
                        let state = match state.lock() {
                            Ok(state) => state,
                            Err(error) => {
                                if let Some(ui) = ui_weak.upgrade() {
                                    set_error_text(&ui, format!("Radio failed: {error}"));
                                }
                                return;
                            }
                        };
                        let Some(station) = state.radio_stations.get(index as usize).cloned()
                        else {
                            return;
                        };
                        station
                    };

                    if let Some(ui) = ui_weak.upgrade() {
                        reset_playback_progress_ui(&ui);
                        ui.set_status_text(format!("Starting {}", station.name).into());
                    }
                    begin_playback_transition(&state);

                    let state_for_worker = Arc::clone(&state);
                    let ui_weak_for_result = ui_weak.clone();
                    playback_worker.submit(move || {
                        let station_for_result = station.clone();
                        let result = start_radio_playback(&state_for_worker, &station)
                            .context("could not start radio");
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(()) => {
                                        set_now_playing_radio(&ui, &station_for_result);
                                        ui.set_status_text(
                                            format!("Playing {}", station_for_result.name).into(),
                                        );
                                    }
                                    Err(error) => {
                                        set_error_text(&ui, format!("Radio failed: {error:#}"))
                                    }
                                }
                            }
                        })
                        .ok();
                    });
                    return;
                }
                _ => return,
            };

            let (source, track) = {
                let app_state = match state.lock() {
                    Ok(state) => state,
                    Err(error) => {
                        if let Some(ui) = ui_weak.upgrade() {
                            set_error_text(&ui, format!("Playback failed: {error}"));
                        }
                        return;
                    }
                };
                let Some(source) = app_state.settings.active_source() else {
                    return;
                };
                let tracks = app_state.search_results.tracks.clone();
                drop(app_state);

                let mut app_state = match state.lock() {
                    Ok(state) => state,
                    Err(error) => {
                        if let Some(ui) = ui_weak.upgrade() {
                            set_error_text(&ui, format!("Playback failed: {error}"));
                        }
                        return;
                    }
                };
                app_state.queue = TrackQueue::play_from_tracks(tracks, index as usize);
                app_state.queue.set_repeat_mode(RepeatMode::Off);
                let Some(track) = app_state.queue.jump_to(index as usize).cloned() else {
                    return;
                };
                (source, track)
            };

            if let Some(ui) = ui_weak.upgrade() {
                reset_playback_progress_ui(&ui);
                ui.set_status_text(format!("Starting {}", track.title).into());
            }
            begin_playback_transition(&state);
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            let worker_for_art = worker.clone();
            let cover_art_cache = Arc::clone(&cover_art_cache);
            playback_worker.submit(move || {
                let result = start_track_playback(&state_for_worker, source, track)
                    .context("could not start playback");
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(started) => handle_started_track_playback(
                                &ui,
                                &state_for_worker,
                                worker_for_art,
                                ui_weak_for_result.clone(),
                                cover_art_cache,
                                started,
                            ),
                            Err(error) => {
                                set_error_text(&ui, format!("Playback failed: {error:#}"))
                            }
                        }
                    }
                })
                .ok();
            });
        });
    }

    fn stop_playback(&self) {
        if let Ok(mut state) = self.state.lock() {
            state.playback.stop();
        }
    }
}

enum SeekOutcome {
    Noop,
    InPlace(f64),
    RestartStream {
        current: Box<CurrentPlayback>,
        target_seconds: f64,
    },
    Restarted(f64),
}

fn seek_to_progress(state: &Arc<Mutex<AppState>>, progress: f32) -> Result<SeekOutcome> {
    let result = {
        let mut app_state = state
            .lock()
            .map_err(|error| anyhow::anyhow!(error.to_string()))?;
        let Some(duration) = app_state.latest_playback_snapshot.duration_seconds else {
            return Ok(SeekOutcome::Noop);
        };
        if duration <= 0.0 {
            return Ok(SeekOutcome::Noop);
        }

        let target_seconds = duration * f64::from(progress) / 100.0;
        match app_state.playback.seek_absolute(target_seconds) {
            Ok(()) => Ok(SeekOutcome::InPlace(target_seconds)),
            Err(error) if is_unseekable_stream_error(&error) => {
                let current = app_state
                    .current_playback
                    .clone()
                    .ok_or_else(|| anyhow::anyhow!("no active track to seek"))?;
                Ok(SeekOutcome::RestartStream {
                    current: Box::new(current),
                    target_seconds,
                })
            }
            Err(error) => Err(error),
        }
    };

    match result {
        Ok(SeekOutcome::RestartStream {
            current,
            target_seconds,
        }) => restart_stream_at_offset(state, current.as_ref(), target_seconds)
            .map(|()| SeekOutcome::Restarted(target_seconds)),
        other => other,
    }
}

fn apply_seek_result(ui: &AppWindow, result: Result<SeekOutcome>, progress: f32) {
    match result {
        Ok(SeekOutcome::InPlace(target_seconds) | SeekOutcome::Restarted(target_seconds)) => {
            ui.set_playback_elapsed_text(format_seconds(target_seconds).into());
            ui.set_playback_progress(progress);
        }
        Ok(SeekOutcome::Noop) => {}
        Ok(SeekOutcome::RestartStream { .. }) => {}
        Err(error) if error.to_string().contains("seeking is not available") => {
            set_error_text(ui, "Seeking is not available for this stream");
        }
        Err(error) => set_error_text(ui, format!("Seek failed: {error}")),
    }
}

fn poll_playback_snapshot(state: &Arc<Mutex<AppState>>) -> Option<PlaybackSnapshot> {
    let (session_id, snapshot) = state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))
        .and_then(|mut state| {
            let session_id = state.playback_session_id;
            state
                .playback
                .snapshot()
                .map(|snapshot| (session_id, snapshot))
        })
        .ok()?;

    state.lock().ok().and_then(|mut state| {
        if state.playback_session_id != session_id {
            return None;
        }
        state.latest_playback_snapshot = snapshot.clone();
        Some(snapshot)
    })
}

enum QueueDirection {
    Previous,
    Next,
}

struct StartedTrackPlayback {
    source: SavedMediaSource,
    track: Track,
    embedded_cover_art: Option<Vec<u8>>,
    session_id: u64,
}

struct NowPlayingArtRequest {
    session_id: u64,
    source: SavedMediaSource,
    track_id: String,
    cover_art_id: Option<String>,
    embedded_cover_art: Option<Vec<u8>>,
}

fn next_playback_session(state: &mut AppState) -> u64 {
    state.playback_session_id = state.playback_session_id.wrapping_add(1).max(1);
    state.playback_session_id
}

fn begin_playback_transition(state: &Arc<Mutex<AppState>>) {
    if let Ok(mut state) = state.lock() {
        next_playback_session(&mut state);
        reset_latest_playback_snapshot(&mut state);
    }
}

fn reset_latest_playback_snapshot(state: &mut AppState) {
    state.latest_playback_snapshot = PlaybackSnapshot {
        is_playing: true,
        volume: state.latest_playback_snapshot.volume,
        ..PlaybackSnapshot::default()
    };
}

fn play_queue_neighbor(
    state: &Arc<Mutex<AppState>>,
    direction: QueueDirection,
) -> Result<StartedTrackPlayback> {
    let (source, track) = {
        let mut state = state
            .lock()
            .map_err(|error| anyhow::anyhow!(error.to_string()))?;
        let source = state
            .settings
            .active_source()
            .ok_or_else(|| anyhow::anyhow!("no active source"))?;
        let track = match direction {
            QueueDirection::Previous => state.queue.previous(),
            QueueDirection::Next => state.queue.next(),
        }
        .cloned()
        .ok_or_else(|| anyhow::anyhow!("no queued track"))?;
        (source, track)
    };

    start_track_playback(state, source, track)
}

fn start_track_playback(
    state: &Arc<Mutex<AppState>>,
    source: SavedMediaSource,
    track: Track,
) -> Result<StartedTrackPlayback> {
    let provider = NavidromeProvider::new(source.clone())?;
    let url = provider.stream_url(&StreamRequest::original(&track.id))?;
    state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))
        .and_then(|mut state| {
            let session_id = next_playback_session(&mut state);
            state.playback.play_url(&url)?;
            let embedded_cover_art = state.playback.embedded_cover_art()?;
            reset_latest_playback_snapshot(&mut state);
            state.current_playback = Some(CurrentPlayback {
                source: source.clone(),
                track: track.clone(),
            });
            Ok(StartedTrackPlayback {
                source,
                track,
                embedded_cover_art,
                session_id,
            })
        })
}

fn restart_stream_at_offset(
    state: &Arc<Mutex<AppState>>,
    current: &CurrentPlayback,
    target_seconds: f64,
) -> Result<()> {
    let start_seconds = target_seconds.max(0.0).round().min(u32::MAX as f64) as u32;
    let provider = NavidromeProvider::new(current.source.clone())?;
    let url = provider.stream_url(&StreamRequest::original_from(
        &current.track.id,
        start_seconds,
    ))?;

    state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))
        .and_then(|mut state| {
            next_playback_session(&mut state);
            state.playback.play_url(&url)?;
            reset_latest_playback_snapshot(&mut state);
            state.current_playback = Some(current.clone());
            Ok(())
        })
}

fn start_radio_playback(
    state: &Arc<Mutex<AppState>>,
    station: &InternetRadioStation,
) -> Result<()> {
    let stream_url = resolve_radio_stream_url(&station.stream_url)?;
    state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))
        .and_then(|mut state| {
            next_playback_session(&mut state);
            state.playback.play_url(&stream_url)?;
            reset_latest_playback_snapshot(&mut state);
            state.current_playback = None;
            state.queue = TrackQueue::default();
            Ok(())
        })
}

fn is_unseekable_stream_error(error: &anyhow::Error) -> bool {
    error.to_string().contains("seeking is not available")
}

fn handle_started_track_playback(
    ui: &AppWindow,
    state: &Arc<Mutex<AppState>>,
    worker: BackgroundWorker,
    ui_weak: Weak<AppWindow>,
    cover_art_cache: Arc<CoverArtCache>,
    started: StartedTrackPlayback,
) {
    set_now_playing(ui, &started.track);
    ui.set_status_text(format!("Playing {}", started.track.title).into());
    load_now_playing_art(
        worker,
        ui_weak,
        Arc::clone(state),
        cover_art_cache,
        NowPlayingArtRequest {
            session_id: started.session_id,
            source: started.source,
            track_id: started.track.id,
            cover_art_id: started.track.cover_art_id,
            embedded_cover_art: started.embedded_cover_art,
        },
    );
}

fn set_now_playing(ui: &AppWindow, track: &Track) {
    ui.set_now_playing_title(track.title.clone().into());
    ui.set_now_playing_subtitle(track.subtitle().into());
    ui.set_now_playing_art(Image::default());
    reset_playback_progress_ui(ui);
}

fn set_now_playing_radio(ui: &AppWindow, station: &InternetRadioStation) {
    ui.set_now_playing_title(station.name.clone().into());
    ui.set_now_playing_subtitle(
        station
            .home_page_url
            .as_deref()
            .unwrap_or("Internet radio")
            .into(),
    );
    ui.set_now_playing_art(Image::default());
    reset_playback_progress_ui(ui);
}

fn set_error_text(ui: &AppWindow, message: impl Into<String>) {
    ui.set_error_text(message.into().into());
}

fn reset_playback_progress_ui(ui: &AppWindow) {
    ui.set_playback_status_text("Playing".into());
    ui.set_playback_elapsed_text("0:00".into());
    ui.set_playback_duration_text("--:--".into());
    ui.set_playback_progress(0.0);
    ui.set_playback_is_playing(true);
}

fn resolve_radio_stream_url(url: &str) -> Result<String> {
    if !looks_like_radio_playlist(url) {
        return Ok(url.to_string());
    }

    let body = Client::builder()
        .timeout(Duration::from_secs(10))
        .build()?
        .get(url)
        .send()
        .context("radio playlist request failed")?
        .error_for_status()
        .context("radio playlist server returned an error")?
        .text()
        .context("radio playlist text failed")?;

    parse_radio_playlist(&body)
        .ok_or_else(|| anyhow::anyhow!("radio playlist had no supported stream URL"))
}

fn looks_like_radio_playlist(url: &str) -> bool {
    let url = url.to_ascii_lowercase();
    url.contains(".pls") || (url.contains(".m3u") && !url.contains(".m3u8"))
}

fn parse_radio_playlist(body: &str) -> Option<String> {
    body.lines().map(str::trim).find_map(|line| {
        if line.is_empty() || line.starts_with('#') || line.starts_with('[') {
            return None;
        }
        if let Some((key, value)) = line.split_once('=') {
            if key.trim().to_ascii_lowercase().starts_with("file") {
                return non_empty_url(value.trim());
            }
        }
        non_empty_url(line)
    })
}

fn non_empty_url(value: &str) -> Option<String> {
    (value.starts_with("http://") || value.starts_with("https://")).then(|| value.to_string())
}

fn load_now_playing_art(
    worker: BackgroundWorker,
    ui_weak: Weak<AppWindow>,
    state: Arc<Mutex<AppState>>,
    cache: Arc<CoverArtCache>,
    request: NowPlayingArtRequest,
) {
    if request.embedded_cover_art.is_none() && request.cover_art_id.is_none() {
        return;
    }

    worker.submit(move || {
        let result = if let Some(bytes) = request.embedded_cover_art {
            cache.store_embedded(&request.track_id, &bytes)
        } else {
            let cover_art_id = request
                .cover_art_id
                .expect("cover art id was checked before submit");
            NavidromeProvider::new(request.source)
                .and_then(|provider| provider.cover_art_url(&cover_art_id, cache.cover_art_size()))
                .and_then(|url| cache.fetch(&url))
        };

        slint::invoke_from_event_loop(move || {
            let Some(ui) = ui_weak.upgrade() else {
                return;
            };
            if !is_current_playback_session(&state, request.session_id) {
                return;
            }

            match result {
                Ok(path) => match Image::load_from_path(&path) {
                    Ok(image) => ui.set_now_playing_art(image),
                    Err(error) => set_error_text(&ui, format!("Cover art decode failed: {error}")),
                },
                Err(error) => set_error_text(&ui, format!("Cover art failed: {error}")),
            }
        })
        .ok();
    });
}

fn is_current_playback_session(state: &Arc<Mutex<AppState>>, session_id: u64) -> bool {
    state
        .lock()
        .map(|state| state.playback_session_id == session_id)
        .unwrap_or(false)
}

fn playback_status_text(snapshot: &PlaybackSnapshot) -> String {
    if let Some(stream_title) = snapshot.stream_title.as_ref() {
        return stream_title.clone();
    }

    let state = if snapshot.is_playing {
        "Playing"
    } else {
        "Idle"
    };
    match (snapshot.position_seconds, snapshot.duration_seconds) {
        (Some(position), Some(duration)) if duration > 0.0 => {
            format!(
                "{state} {} / {}",
                format_seconds(position),
                format_seconds(duration)
            )
        }
        (Some(position), _) => format!("{state} {}", format_seconds(position)),
        _ => state.to_string(),
    }
}

fn playback_elapsed_text(snapshot: &PlaybackSnapshot) -> String {
    snapshot
        .position_seconds
        .map(format_seconds)
        .unwrap_or_else(|| "0:00".to_string())
}

fn playback_duration_text(snapshot: &PlaybackSnapshot) -> String {
    snapshot
        .duration_seconds
        .filter(|duration| *duration > 0.0)
        .map(format_seconds)
        .unwrap_or_else(|| "--:--".to_string())
}

fn playback_progress(snapshot: &PlaybackSnapshot) -> f32 {
    match (snapshot.position_seconds, snapshot.duration_seconds) {
        (Some(position), Some(duration)) if duration > 0.0 => {
            ((position / duration) * 100.0).clamp(0.0, 100.0) as f32
        }
        _ => 0.0,
    }
}

fn format_seconds(seconds: f64) -> String {
    let seconds = seconds.max(0.0).round() as u64;
    let minutes = seconds / 60;
    let seconds = seconds % 60;
    format!("{minutes}:{seconds:02}")
}

#[cfg(test)]
mod tests {
    use super::{
        format_seconds, parse_radio_playlist, playback_duration_text, playback_elapsed_text,
        playback_progress, playback_status_text,
    };
    use crate::playback::PlaybackSnapshot;

    #[test]
    fn formats_playback_status_with_duration() {
        assert_eq!(
            "Playing 1:05 / 3:02",
            playback_status_text(&PlaybackSnapshot {
                position_seconds: Some(65.0),
                duration_seconds: Some(182.0),
                is_playing: true,
                volume: 80,
                stream_title: None,
            })
        );
    }

    #[test]
    fn formats_seconds_as_minutes() {
        assert_eq!("0:00", format_seconds(0.0));
        assert_eq!("1:02", format_seconds(62.0));
    }

    #[test]
    fn derives_playback_progress_percent() {
        assert_eq!(
            50.0,
            playback_progress(&PlaybackSnapshot {
                position_seconds: Some(30.0),
                duration_seconds: Some(60.0),
                is_playing: true,
                volume: 80,
                stream_title: None,
            })
        );
        assert_eq!(
            0.0,
            playback_progress(&PlaybackSnapshot {
                position_seconds: Some(30.0),
                duration_seconds: None,
                is_playing: true,
                volume: 80,
                stream_title: None,
            })
        );
    }

    #[test]
    fn formats_elapsed_and_unknown_duration() {
        let snapshot = PlaybackSnapshot {
            position_seconds: Some(4.0),
            duration_seconds: None,
            is_playing: false,
            volume: 80,
            stream_title: None,
        };

        assert_eq!("0:04", playback_elapsed_text(&snapshot));
        assert_eq!("--:--", playback_duration_text(&snapshot));
    }

    #[test]
    fn playback_status_prefers_stream_title() {
        assert_eq!(
            "Live Artist - Live Song",
            playback_status_text(&PlaybackSnapshot {
                position_seconds: None,
                duration_seconds: None,
                is_playing: true,
                volume: 80,
                stream_title: Some("Live Artist - Live Song".to_string()),
            })
        );
    }

    #[test]
    fn parses_pls_radio_playlist() {
        assert_eq!(
            Some("https://stream.example/radio".to_string()),
            parse_radio_playlist("[playlist]\nFile1=https://stream.example/radio\nTitle1=Radio")
        );
    }

    #[test]
    fn parses_m3u_radio_playlist() {
        assert_eq!(
            Some("http://stream.example/live".to_string()),
            parse_radio_playlist("#EXTM3U\n#EXTINF:-1,Radio\nhttp://stream.example/live")
        );
    }
}
