use crate::domain::{
    Album, AlbumDetail, AlbumListType, Artist, ArtistDetail, Genre, InternetRadioStation, Playlist,
    SearchResults, StreamRequest, Track,
};
use crate::image_cache::{default_cover_art_cache, CoverArtCache, CoverArtPalette};
use crate::library_store::{default_library_store, LibrarySnapshot, LibraryStats, LibraryStore};
use crate::playback::{default_playback_engine, PlaybackEngine, PlaybackSnapshot};
use crate::provider::navidrome::NavidromeProvider;
use crate::provider::MediaProvider;
use crate::queue::{RepeatMode, TrackQueue};
use crate::settings::{
    default_settings_store, ConnectionDraft, SavedMediaSource, Settings, SettingsStore,
};
use crate::ui::{
    album_detail_rows, artist_detail_rows, back_to_rows, radio_rows, search_rows, source_rows,
    up_next_rows, visualizer_levels, AppWindow, MediaRow,
};
use crate::visualizer::{default_visualizer_backend, VisualizerBackend, VisualizerFrame};
use crate::worker::BackgroundWorker;
use anyhow::{Context, Result};
use reqwest::blocking::Client;
use slint::{
    Color, ComponentHandle, Image, ModelRc, PhysicalSize, SharedString, Timer, TimerMode, VecModel,
    Weak,
};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;

const MIN_WINDOW_WIDTH: u32 = 860;
const MIN_WINDOW_HEIGHT: u32 = 600;

struct AppState {
    settings: Settings,
    route_history: Vec<String>,
    search_cache: HashMap<String, SearchResults>,
    home_cache: Option<HomeContent>,
    library_index: LibraryIndex,
    search_results: SearchResults,
    current_album_detail: Option<AlbumDetail>,
    current_artist_detail: Option<ArtistDetail>,
    radio_stations: Vec<InternetRadioStation>,
    playback: Box<dyn PlaybackEngine>,
    latest_playback_snapshot: PlaybackSnapshot,
    current_playback: Option<CurrentPlayback>,
    queue: TrackQueue,
    playback_session_id: u64,
    visualizer: Box<dyn VisualizerBackend>,
}

#[derive(Clone)]
struct CurrentPlayback {
    source: SavedMediaSource,
    track: Track,
}

#[derive(Clone, Debug, Default)]
struct HomeContent {
    recently_added_albums: Vec<Album>,
    random_albums: Vec<Album>,
    frequent_albums: Vec<Album>,
    recent_albums: Vec<Album>,
    playlists: Vec<Playlist>,
    genres: Vec<Genre>,
    spotlight_tracks: Vec<Track>,
}

#[derive(Clone, Debug, Default)]
struct LibraryIndex {
    source_id: Option<String>,
    artists: Vec<Artist>,
    albums: Vec<Album>,
    tracks: Vec<Track>,
    filter: String,
    synced_artist_count: usize,
    synced_album_count: usize,
    synced_track_count: usize,
    status: String,
    is_synced: bool,
}

struct TrackPlaybackRequest {
    source: SavedMediaSource,
    tracks: Vec<Track>,
    start_index: usize,
    status_text: &'static str,
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
    ui.set_query(settings.app.last_search_query.clone().into());
    ui.set_current_route(settings.app.last_route.clone().into());
    ui.set_search_state_text("Search for artists, albums, or tracks".into());
    ui.set_visualizer_levels(visualizer_levels(&VisualizerFrame::default()));

    let controller = AppController {
        ui: ui.clone_strong(),
        state: Arc::new(Mutex::new(AppState {
            route_history: Vec::new(),
            search_cache: HashMap::new(),
            home_cache: None,
            library_index: LibraryIndex {
                status: "Library not synced".to_string(),
                ..LibraryIndex::default()
            },
            settings,
            search_results: SearchResults::default(),
            current_album_detail: None,
            current_artist_detail: None,
            radio_stations: Vec::new(),
            playback: default_playback_engine(),
            latest_playback_snapshot: PlaybackSnapshot::default(),
            current_playback: None,
            queue: TrackQueue::default(),
            playback_session_id: 0,
            visualizer: default_visualizer_backend(),
        })),
        settings_store,
        worker: BackgroundWorker::new("naviamp-background"),
        playback_worker: BackgroundWorker::new("naviamp-playback"),
        playback_timer: Timer::default(),
        cover_art_cache: Arc::new(default_cover_art_cache()?),
        library_store: Arc::new(default_library_store()?),
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
    library_store: Arc<LibraryStore>,
}

impl AppController {
    fn bind(&self) {
        self.bind_window_close();
        self.bind_routes();
        self.bind_connection();
        self.bind_error_banner();
        self.bind_sources();
        self.bind_playback_controls();
        self.bind_now_playing_controls();
        self.bind_playback_snapshot_polling();
        self.bind_home();
        self.bind_library();
        self.bind_search();
        self.bind_radio();
        self.bind_row_actions();
        self.bind_detail_actions();
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
                    state.settings.app.last_route = ui.get_current_route().to_string();
                    let _ = settings_store.save(&state.settings);
                }
            }
            std::process::exit(0);
        });
    }

    fn bind_routes(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let settings_store = Arc::clone(&self.settings_store);
        let library_store = Arc::clone(&self.library_store);
        let ui_weak_for_route = ui_weak.clone();
        self.ui.on_route_requested(move |route| {
            if let Ok(mut state) = state.lock() {
                let route = route.to_string();
                if state.settings.app.last_route != route {
                    let previous = state.settings.app.last_route.clone();
                    state.route_history.push(previous);
                }
                state.settings.app.last_route = route.to_string();
                if route == "library" {
                    refresh_library_index_from_store(&mut state, &library_store);
                }
                let _ = settings_store.save(&state.settings);
            }
            if let Some(ui) = ui_weak_for_route.upgrade() {
                restore_route_rows(&ui, &state);
            }
        });

        let state = Arc::clone(&self.state);
        let settings_store = Arc::clone(&self.settings_store);
        let library_store = Arc::clone(&self.library_store);
        self.ui.on_back_requested(move || {
            if let Ok(mut app_state) = state.lock() {
                let route = app_state
                    .route_history
                    .pop()
                    .unwrap_or_else(|| "search".to_string());
                app_state.settings.app.last_route = route.clone();
                if route == "library" {
                    refresh_library_index_from_store(&mut app_state, &library_store);
                }
                let _ = settings_store.save(&app_state.settings);
                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_current_route(route.into());
                    drop(app_state);
                    restore_route_rows(&ui, &state);
                }
            }
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
            let command_session_id = current_playback_session(&state);
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            playback_worker.submit(move || {
                let result = state_for_worker
                    .lock()
                    .map_err(|error| anyhow::anyhow!(error.to_string()))
                    .and_then(|mut state| state.playback.pause());
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        if !is_current_playback_session(&state_for_worker, command_session_id) {
                            return;
                        }
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
            let command_session_id = current_playback_session(&state);
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            playback_worker.submit(move || {
                let result = state_for_worker
                    .lock()
                    .map_err(|error| anyhow::anyhow!(error.to_string()))
                    .and_then(|mut state| state.playback.resume());
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        if !is_current_playback_session(&state_for_worker, command_session_id) {
                            return;
                        }
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
            let transition_session_id = begin_playback_transition(&state);
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
                            Err(error)
                                if is_current_playback_session(
                                    &state_for_worker,
                                    transition_session_id,
                                ) =>
                            {
                                set_error_text(&ui, format!("Previous failed: {error:#}"))
                            }
                            Err(_) => {}
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
            let transition_session_id = begin_playback_transition(&state);
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
                            Err(error)
                                if is_current_playback_session(
                                    &state_for_worker,
                                    transition_session_id,
                                ) =>
                            {
                                set_error_text(&ui, format!("Next failed: {error:#}"))
                            }
                            Err(_) => {}
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
                    Ok(()) => {
                        refresh_now_playing_context(&ui, &state);
                        ui.set_status_text("Shuffled upcoming tracks".into());
                    }
                    Err(error) => set_error_text(&ui, format!("Shuffle failed: {error}")),
                }
            }
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        self.ui.on_volume_changed(move |volume| {
            let percent = volume.clamp(0.0, 100.0).round() as u8;
            let command_session_id = current_playback_session(&state);
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
                            if !is_current_playback_session(&state_for_worker, command_session_id) {
                                return;
                            }
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
            let command_session_id = current_playback_session(&state);
            let state_for_worker = Arc::clone(&state);
            let ui_weak_for_result = ui_weak.clone();
            playback_worker.submit(move || {
                let result = seek_to_progress(&state_for_worker, progress);
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        apply_seek_result(
                            &ui,
                            &state_for_worker,
                            command_session_id,
                            result,
                            progress,
                        );
                    }
                })
                .ok();
            });
        });
    }

    fn bind_now_playing_controls(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        self.ui.on_now_playing_favorite_requested(move || {
            let (source, track, favorite) = match current_track_action(&state) {
                Ok((source, track)) => (source, track.clone(), track.favorited_at.is_none()),
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Favorite failed: {error}"));
                    }
                    return;
                }
            };
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text(
                    if favorite {
                        "Favoriting track..."
                    } else {
                        "Unfavoriting track..."
                    }
                    .into(),
                );
            }
            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            worker.submit(move || {
                let result = NavidromeProvider::new(source)
                    .and_then(|provider| provider.set_favorite(&track.id, favorite));
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(()) => {
                                update_current_track(&state_for_result, |track| {
                                    track.favorited_at = favorite.then(|| "now".to_string());
                                });
                                refresh_now_playing_context(&ui, &state_for_result);
                                ui.set_status_text(
                                    if favorite {
                                        "Track favorited"
                                    } else {
                                        "Track unfavorited"
                                    }
                                    .into(),
                                );
                            }
                            Err(error) => set_error_text(&ui, format!("Favorite failed: {error}")),
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        self.ui.on_now_playing_rating_requested(move |rating| {
            let rating = rating.clamp(1, 5) as u8;
            let (source, track) = match current_track_action(&state) {
                Ok(value) => value,
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Rating failed: {error}"));
                    }
                    return;
                }
            };
            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            worker.submit(move || {
                let result = NavidromeProvider::new(source)
                    .and_then(|provider| provider.set_rating(&track.id, rating));
                slint::invoke_from_event_loop(move || {
                    if let Some(ui) = ui_weak_for_result.upgrade() {
                        match result {
                            Ok(()) => {
                                update_current_track(&state_for_result, |track| {
                                    track.user_rating = Some(rating);
                                });
                                refresh_now_playing_context(&ui, &state_for_result);
                                ui.set_status_text(format!("Rated {rating} stars").into());
                            }
                            Err(error) => set_error_text(&ui, format!("Rating failed: {error}")),
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        self.ui.on_now_playing_link_requested(move |target| {
            let query = state
                .lock()
                .ok()
                .and_then(|state| {
                    state
                        .current_playback
                        .as_ref()
                        .map(|playback| playback.track.clone())
                })
                .map(|track| {
                    if target.as_str() == "album" {
                        track.album
                    } else {
                        track.artist
                    }
                })
                .unwrap_or_default();
            if query.is_empty() {
                return;
            }
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_query(query.clone().into());
                ui.set_current_route("search".into());
                ui.set_status_text(format!("Search ready for {query}").into());
            }
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        let playback_worker = self.playback_worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_queue_row_activated(move |index| {
            let (source, tracks, start_index) = match queue_tracks_source(&state, index as usize) {
                Ok(value) => value,
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Queue failed: {error}"));
                    }
                    return;
                }
            };
            start_tracks_from_ui(
                &ui_weak,
                &state,
                &playback_worker,
                worker.clone(),
                Arc::clone(&cover_art_cache),
                TrackPlaybackRequest {
                    source,
                    tracks,
                    start_index,
                    status_text: "Starting queued track...",
                },
            );
        });
    }

    fn bind_home(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        self.ui.on_home_requested(move || {
            submit_home_load(&ui_weak, &state, &worker);
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        let worker = self.worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_home_library_radio_requested(move || {
            let source = match active_source_from_state(&state) {
                Ok(source) => source,
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Library radio failed: {error}"));
                    }
                    return;
                }
            };
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text("Building library radio...".into());
            }
            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            let playback_worker_for_result = playback_worker.clone();
            let worker_for_art = worker.clone();
            let cover_art_cache = Arc::clone(&cover_art_cache);
            worker.submit(move || {
                let tracks = NavidromeProvider::new(source.clone())
                    .and_then(|provider| provider.random_tracks(50));
                slint::invoke_from_event_loop(move || match tracks {
                    Ok(tracks) => start_tracks_from_ui(
                        &ui_weak_for_result,
                        &state_for_result,
                        &playback_worker_for_result,
                        worker_for_art,
                        cover_art_cache,
                        TrackPlaybackRequest {
                            source,
                            tracks,
                            start_index: 0,
                            status_text: "Starting library radio...",
                        },
                    ),
                    Err(error) => {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            set_error_text(&ui, format!("Library radio failed: {error}"));
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        let worker = self.worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_home_random_album_radio_requested(move || {
            let source = match active_source_from_state(&state) {
                Ok(source) => source,
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Random album radio failed: {error}"));
                    }
                    return;
                }
            };
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text("Choosing a random album...".into());
            }
            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            let playback_worker_for_result = playback_worker.clone();
            let worker_for_art = worker.clone();
            let cover_art_cache = Arc::clone(&cover_art_cache);
            worker.submit(move || {
                let result = random_album_radio_tracks(source.clone());
                slint::invoke_from_event_loop(move || match result {
                    Ok(tracks) => start_tracks_from_ui(
                        &ui_weak_for_result,
                        &state_for_result,
                        &playback_worker_for_result,
                        worker_for_art,
                        cover_art_cache,
                        TrackPlaybackRequest {
                            source,
                            tracks,
                            start_index: 0,
                            status_text: "Starting random album radio...",
                        },
                    ),
                    Err(error) => {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            set_error_text(&ui, format!("Random album radio failed: {error}"));
                        }
                    }
                })
                .ok();
            });
        });

        if self.ui.get_current_route().as_str() == "home" {
            submit_home_load(&self.ui.as_weak(), &self.state, &self.worker);
        }
    }

    fn bind_library(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        let library_store = Arc::clone(&self.library_store);
        self.ui.on_library_sync_requested(move || {
            submit_library_sync(&ui_weak, &state, &worker, Arc::clone(&library_store));
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let library_store = Arc::clone(&self.library_store);
        self.ui.on_library_clear_requested(move || {
            if let Ok(mut state) = state.lock() {
                if let Some(source_id) = state.library_index.source_id.as_deref() {
                    let _ = library_store.clear_source(source_id);
                } else if let Some(source) = state.settings.active_source() {
                    let _ = library_store.clear_source(&source.id);
                }
                state.library_index = LibraryIndex {
                    status: "Library index cleared".to_string(),
                    ..LibraryIndex::default()
                };
                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_media_rows(library_rows(&state.library_index));
                    ui.set_search_state_text(state.library_index.status.clone().into());
                    ui.set_status_text("Library cleared".into());
                }
            }
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let library_store = Arc::clone(&self.library_store);
        self.ui.on_library_filter_requested(move |query| {
            if let Ok(mut state) = state.lock() {
                state.library_index.filter = query.to_string();
                refresh_library_index_from_store(&mut state, &library_store);
                refresh_library_ui(&ui_weak, &state);
            }
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let library_store = Arc::clone(&self.library_store);
        self.ui.on_library_letter_requested(move |letter| {
            if let Ok(mut state) = state.lock() {
                state.library_index.filter = letter.to_string();
                refresh_library_index_from_store(&mut state, &library_store);
                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_query(state.library_index.filter.clone().into());
                    ui.set_media_rows(library_rows(&state.library_index));
                    ui.set_search_state_text(library_summary(&state.library_index).into());
                }
            }
        });

        if self.ui.get_current_route().as_str() == "library" {
            if let Ok(mut state) = self.state.lock() {
                refresh_library_index_from_store(&mut state, &self.library_store);
                refresh_library_ui(&self.ui.as_weak(), &state);
            }
        }
    }

    fn bind_playback_snapshot_polling(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        let playback_worker = self.playback_worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.playback_timer
            .start(TimerMode::Repeated, Duration::from_millis(500), move || {
                let state_for_worker = Arc::clone(&state);
                let ui_weak_for_result = ui_weak.clone();
                let worker_for_art = worker.clone();
                let cover_art_cache = Arc::clone(&cover_art_cache);
                playback_worker.submit(move || {
                    if let Some(update) = poll_playback_snapshot(&state_for_worker) {
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match update {
                                    PlaybackPollUpdate::Snapshot {
                                        snapshot,
                                        visualizer_frame,
                                    } => {
                                        ui.set_playback_status_text(
                                            playback_status_text(&snapshot).into(),
                                        );
                                        ui.set_playback_elapsed_text(
                                            playback_elapsed_text(&snapshot).into(),
                                        );
                                        ui.set_playback_duration_text(
                                            playback_duration_text(&snapshot).into(),
                                        );
                                        ui.set_playback_progress(playback_progress(&snapshot));
                                        ui.set_playback_is_playing(snapshot.is_playing);
                                        ui.set_visualizer_levels(visualizer_levels(
                                            &visualizer_frame,
                                        ));
                                    }
                                    PlaybackPollUpdate::TrackStarted(started) => {
                                        handle_started_track_playback(
                                            &ui,
                                            &state_for_worker,
                                            worker_for_art,
                                            ui_weak_for_result.clone(),
                                            cover_art_cache,
                                            *started,
                                        );
                                    }
                                }
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
        let settings_store = Arc::clone(&self.settings_store);
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
                    ui.set_search_state_text("Enter a search term".into());
                }
                return;
            }
            let cache_key = query.trim().to_ascii_lowercase();
            if let Ok(mut state) = state.lock() {
                state.settings.app.last_search_query = query.clone();
                let _ = settings_store.save(&state.settings);
                if let Some(results) = state.search_cache.get(&cache_key).cloned() {
                    state.search_results = results.clone();
                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_media_rows(search_rows(&results));
                        ui.set_search_state_text(search_state_text(&results).into());
                        ui.set_status_text("Search restored from cache".into());
                    }
                    return;
                }
            }
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text("Searching...".into());
                ui.set_search_state_text("Searching...".into());
            }

            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            let settings_store = Arc::clone(&settings_store);
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
                                    state.settings.app.last_search_query = query.clone();
                                    let _ = settings_store.save(&state.settings);
                                    state.search_cache.insert(cache_key, results.clone());
                                    state.search_results = results.clone();
                                }
                                ui.set_media_rows(search_rows(&results));
                                ui.set_search_state_text(search_state_text(&results).into());
                                ui.set_status_text(if results.is_empty() {
                                    "No search results".into()
                                } else {
                                    "Search complete".into()
                                });
                            }
                            Err(error) => {
                                ui.set_search_state_text("Search failed".into());
                                set_error_text(&ui, format!("Search failed: {error}"));
                            }
                        }
                    }
                })
                .ok();
            });
        });
    }

    fn bind_row_actions(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let worker = self.worker.clone();
        self.ui
            .on_row_action_requested(move |kind, index, action_label| {
                if kind.as_str() != "track" {
                    if let Some(ui) = ui_weak.upgrade() {
                        ui.set_status_text("Open the row for more options".into());
                    }
                    return;
                }

                let (source, track, favorite) = {
                    let state = match state.lock() {
                        Ok(state) => state,
                        Err(error) => {
                            if let Some(ui) = ui_weak.upgrade() {
                                set_error_text(&ui, format!("Track action failed: {error}"));
                            }
                            return;
                        }
                    };
                    let Some(track) = state.search_results.tracks.get(index as usize).cloned()
                    else {
                        return;
                    };
                    let Some(source) = state.settings.active_source() else {
                        return;
                    };
                    (source, track, action_label.as_str() == "Favorite")
                };

                if let Some(ui) = ui_weak.upgrade() {
                    ui.set_status_text(
                        format!(
                            "{} {}",
                            if favorite {
                                "Favoriting"
                            } else {
                                "Unfavoriting"
                            },
                            track.title
                        )
                        .into(),
                    );
                }

                let ui_weak_for_result = ui_weak.clone();
                let state_for_result = Arc::clone(&state);
                worker.submit(move || {
                    let result = NavidromeProvider::new(source)
                        .and_then(|provider| provider.set_favorite(&track.id, favorite));
                    slint::invoke_from_event_loop(move || {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            match result {
                                Ok(()) => {
                                    if let Ok(mut state) = state_for_result.lock() {
                                        if let Some(track) =
                                            state.search_results.tracks.get_mut(index as usize)
                                        {
                                            track.favorited_at =
                                                favorite.then(|| "now".to_string());
                                        }
                                        if let Some(detail) = state.current_album_detail.as_mut() {
                                            if let Some(track) = detail
                                                .tracks
                                                .iter_mut()
                                                .find(|item| item.id == track.id)
                                            {
                                                track.favorited_at =
                                                    favorite.then(|| "now".to_string());
                                            }
                                        }
                                    }
                                    restore_route_rows(&ui, &state_for_result);
                                    ui.set_status_text(
                                        if favorite {
                                            "Track favorited"
                                        } else {
                                            "Track unfavorited"
                                        }
                                        .into(),
                                    );
                                }
                                Err(error) => {
                                    set_error_text(&ui, format!("Track action failed: {error}"));
                                }
                            }
                        }
                    })
                    .ok();
                });
            });
    }

    fn bind_detail_actions(&self) {
        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        let worker = self.worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_album_play_requested(move || {
            let (source, tracks) = match detail_album_source_tracks(&state) {
                Ok(value) => value,
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Album playback failed: {error}"));
                    }
                    return;
                }
            };
            start_tracks_from_ui(
                &ui_weak,
                &state,
                &playback_worker,
                worker.clone(),
                Arc::clone(&cover_art_cache),
                TrackPlaybackRequest {
                    source,
                    tracks,
                    start_index: 0,
                    status_text: "Starting album...",
                },
            );
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        let worker = self.worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_album_radio_requested(move || {
            let (source, album) = match detail_album_source(&state) {
                Ok(value) => value,
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Album radio failed: {error}"));
                    }
                    return;
                }
            };
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text("Building album radio...".into());
            }

            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            let playback_worker_for_result = playback_worker.clone();
            let worker_for_art = worker.clone();
            let cover_art_cache = Arc::clone(&cover_art_cache);
            worker.submit(move || {
                let tracks = album_radio_tracks(source.clone(), &album);
                slint::invoke_from_event_loop(move || match tracks {
                    Ok(tracks) => start_tracks_from_ui(
                        &ui_weak_for_result,
                        &state_for_result,
                        &playback_worker_for_result,
                        worker_for_art,
                        cover_art_cache,
                        TrackPlaybackRequest {
                            source,
                            tracks,
                            start_index: 0,
                            status_text: "Starting album radio...",
                        },
                    ),
                    Err(error) => {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            set_error_text(&ui, format!("Album radio failed: {error}"));
                        }
                    }
                })
                .ok();
            });
        });

        let ui_weak = self.ui.as_weak();
        let state = Arc::clone(&self.state);
        let playback_worker = self.playback_worker.clone();
        let worker = self.worker.clone();
        let cover_art_cache = Arc::clone(&self.cover_art_cache);
        self.ui.on_artist_radio_requested(move || {
            let (source, artist) = match detail_artist_source(&state) {
                Ok(value) => value,
                Err(error) => {
                    if let Some(ui) = ui_weak.upgrade() {
                        set_error_text(&ui, format!("Artist radio failed: {error}"));
                    }
                    return;
                }
            };
            if let Some(ui) = ui_weak.upgrade() {
                ui.set_status_text("Building artist radio...".into());
            }

            let ui_weak_for_result = ui_weak.clone();
            let state_for_result = Arc::clone(&state);
            let playback_worker_for_result = playback_worker.clone();
            let worker_for_art = worker.clone();
            let cover_art_cache = Arc::clone(&cover_art_cache);
            worker.submit(move || {
                let tracks = artist_radio_tracks(source.clone(), &artist);
                slint::invoke_from_event_loop(move || match tracks {
                    Ok(tracks) => start_tracks_from_ui(
                        &ui_weak_for_result,
                        &state_for_result,
                        &playback_worker_for_result,
                        worker_for_art,
                        cover_art_cache,
                        TrackPlaybackRequest {
                            source,
                            tracks,
                            start_index: 0,
                            status_text: "Starting artist radio...",
                        },
                    ),
                    Err(error) => {
                        if let Some(ui) = ui_weak_for_result.upgrade() {
                            set_error_text(&ui, format!("Artist radio failed: {error}"));
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
        let settings_store = Arc::clone(&self.settings_store);
        self.ui.on_row_activated(move |kind, index| {
            match kind.as_str() {
                "header" => return,
                "home-album" => {
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
                        let Some(content) = state.home_cache.as_ref() else {
                            return;
                        };
                        let Some(album) = home_album(content, index as usize) else {
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
                    let state_for_result = Arc::clone(&state);
                    let settings_store_for_result = Arc::clone(&settings_store);
                    worker.submit(move || {
                        let result = NavidromeProvider::new(source)
                            .and_then(|provider| provider.album(&album.id));
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(detail) => {
                                        if let Ok(mut state) = state_for_result.lock() {
                                            state.search_results.artists.clear();
                                            state.search_results.albums.clear();
                                            state.search_results.tracks = detail.tracks.clone();
                                            state.current_album_detail = Some(detail.clone());
                                            navigate_to_locked(
                                                &mut state,
                                                "album-detail",
                                                &settings_store_for_result,
                                            );
                                        }
                                        ui.set_media_rows(album_detail_rows(&detail));
                                        ui.set_detail_title(detail.album.title.clone().into());
                                        ui.set_detail_subtitle(
                                            format!(
                                                "{} - {} tracks",
                                                detail.album.artist,
                                                detail.tracks.len()
                                            )
                                            .into(),
                                        );
                                        ui.set_current_route("album-detail".into());
                                        ui.set_status_text(
                                            format!(
                                                "{} tracks on {}",
                                                detail.tracks.len(),
                                                detail.album.title
                                            )
                                            .into(),
                                        );
                                    }
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
                "home-track" => {
                    let (source, tracks) = {
                        let state = match state.lock() {
                            Ok(state) => state,
                            Err(error) => {
                                if let Some(ui) = ui_weak.upgrade() {
                                    set_error_text(&ui, format!("Playback failed: {error}"));
                                }
                                return;
                            }
                        };
                        let Some(content) = state.home_cache.as_ref() else {
                            return;
                        };
                        let Some(source) = state.settings.active_source() else {
                            return;
                        };
                        (source, content.spotlight_tracks.clone())
                    };
                    start_tracks_from_ui(
                        &ui_weak,
                        &state,
                        &playback_worker,
                        worker.clone(),
                        Arc::clone(&cover_art_cache),
                        TrackPlaybackRequest {
                            source,
                            tracks,
                            start_index: index as usize,
                            status_text: "Starting spotlight track...",
                        },
                    );
                    return;
                }
                "home-genre" => {
                    let genre = state
                        .lock()
                        .ok()
                        .and_then(|state| state.home_cache.clone())
                        .and_then(|content| content.genres.get(index as usize).cloned());
                    if let (Some(ui), Some(genre)) = (ui_weak.upgrade(), genre) {
                        ui.set_query(genre.name.clone().into());
                        ui.set_current_route("search".into());
                        ui.set_status_text(format!("Search ready for {}", genre.name).into());
                    }
                    return;
                }
                "home-playlist" => {
                    let playlist = state
                        .lock()
                        .ok()
                        .and_then(|state| state.home_cache.clone())
                        .and_then(|content| content.playlists.get(index as usize).cloned());
                    if let (Some(ui), Some(playlist)) = (ui_weak.upgrade(), playlist) {
                        ui.set_status_text(
                            format!("Playlist detail comes in Phase 13: {}", playlist.name).into(),
                        );
                    }
                    return;
                }
                "library-artist" => {
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
                        let Some(artist) = state.library_index.artists.get(index as usize).cloned()
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
                    let state_for_result = Arc::clone(&state);
                    let settings_store_for_result = Arc::clone(&settings_store);
                    worker.submit(move || {
                        let result = NavidromeProvider::new(source)
                            .and_then(|provider| provider.artist(&artist.id));
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(detail) => {
                                        if let Ok(mut state) = state_for_result.lock() {
                                            state.search_results.artists.clear();
                                            state.search_results.albums = detail.albums.clone();
                                            state.search_results.tracks.clear();
                                            state.current_artist_detail = Some(detail.clone());
                                            navigate_to_locked(
                                                &mut state,
                                                "artist-detail",
                                                &settings_store_for_result,
                                            );
                                        }
                                        ui.set_media_rows(artist_detail_rows(&detail));
                                        ui.set_detail_title(detail.artist.name.clone().into());
                                        ui.set_detail_subtitle(
                                            format!("{} albums", detail.albums.len()).into(),
                                        );
                                        ui.set_current_route("artist-detail".into());
                                    }
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
                "library-album" => {
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
                        let Some(album) = state.library_index.albums.get(index as usize).cloned()
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
                    let state_for_result = Arc::clone(&state);
                    let settings_store_for_result = Arc::clone(&settings_store);
                    worker.submit(move || {
                        let result = NavidromeProvider::new(source)
                            .and_then(|provider| provider.album(&album.id));
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(detail) => {
                                        if let Ok(mut state) = state_for_result.lock() {
                                            state.search_results.artists.clear();
                                            state.search_results.albums.clear();
                                            state.search_results.tracks = detail.tracks.clone();
                                            state.current_album_detail = Some(detail.clone());
                                            navigate_to_locked(
                                                &mut state,
                                                "album-detail",
                                                &settings_store_for_result,
                                            );
                                        }
                                        ui.set_media_rows(album_detail_rows(&detail));
                                        ui.set_detail_title(detail.album.title.clone().into());
                                        ui.set_detail_subtitle(
                                            format!(
                                                "{} - {} tracks",
                                                detail.album.artist,
                                                detail.tracks.len()
                                            )
                                            .into(),
                                        );
                                        ui.set_current_route("album-detail".into());
                                    }
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
                "library-track" => {
                    let (source, tracks) = {
                        let state = match state.lock() {
                            Ok(state) => state,
                            Err(error) => {
                                if let Some(ui) = ui_weak.upgrade() {
                                    set_error_text(&ui, format!("Playback failed: {error}"));
                                }
                                return;
                            }
                        };
                        let Some(source) = state.settings.active_source() else {
                            return;
                        };
                        (source, state.library_index.tracks.clone())
                    };
                    start_tracks_from_ui(
                        &ui_weak,
                        &state,
                        &playback_worker,
                        worker.clone(),
                        Arc::clone(&cover_art_cache),
                        TrackPlaybackRequest {
                            source,
                            tracks,
                            start_index: index as usize,
                            status_text: "Starting library track...",
                        },
                    );
                    return;
                }
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
                    let state_for_result = Arc::clone(&state);
                    let settings_store_for_result = Arc::clone(&settings_store);
                    worker.submit(move || {
                        let result = NavidromeProvider::new(source)
                            .and_then(|provider| provider.artist(&artist.id));
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(detail) => {
                                        if let Ok(mut state) = state_for_result.lock() {
                                            state.search_results.artists.clear();
                                            state.search_results.albums = detail.albums.clone();
                                            state.search_results.tracks.clear();
                                            state.current_artist_detail = Some(detail.clone());
                                            navigate_to_locked(
                                                &mut state,
                                                "artist-detail",
                                                &settings_store_for_result,
                                            );
                                        }
                                        ui.set_media_rows(artist_detail_rows(&detail));
                                        ui.set_detail_title(detail.artist.name.clone().into());
                                        ui.set_detail_subtitle(
                                            format!("{} albums", detail.albums.len()).into(),
                                        );
                                        ui.set_current_route("artist-detail".into());
                                        ui.set_status_text(
                                            format!(
                                                "{} albums by {}",
                                                detail.albums.len(),
                                                detail.artist.name
                                            )
                                            .into(),
                                        );
                                    }
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
                    let state_for_result = Arc::clone(&state);
                    let settings_store_for_result = Arc::clone(&settings_store);
                    worker.submit(move || {
                        let result = NavidromeProvider::new(source)
                            .and_then(|provider| provider.album(&album.id));
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(detail) => {
                                        if let Ok(mut state) = state_for_result.lock() {
                                            state.search_results.artists.clear();
                                            state.search_results.albums.clear();
                                            state.search_results.tracks = detail.tracks.clone();
                                            state.current_album_detail = Some(detail.clone());
                                            navigate_to_locked(
                                                &mut state,
                                                "album-detail",
                                                &settings_store_for_result,
                                            );
                                        }
                                        ui.set_media_rows(album_detail_rows(&detail));
                                        ui.set_detail_title(detail.album.title.clone().into());
                                        ui.set_detail_subtitle(
                                            format!(
                                                "{} - {} tracks",
                                                detail.album.artist,
                                                detail.tracks.len()
                                            )
                                            .into(),
                                        );
                                        ui.set_current_route("album-detail".into());
                                        ui.set_status_text(
                                            format!(
                                                "{} tracks on {}",
                                                detail.tracks.len(),
                                                detail.album.title
                                            )
                                            .into(),
                                        );
                                    }
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
                    let transition_session_id = begin_playback_transition(&state);

                    let state_for_worker = Arc::clone(&state);
                    let ui_weak_for_result = ui_weak.clone();
                    playback_worker.submit(move || {
                        let station_for_result = station.clone();
                        let result = start_radio_playback(&state_for_worker, &station)
                            .context("could not start radio");
                        slint::invoke_from_event_loop(move || {
                            if let Some(ui) = ui_weak_for_result.upgrade() {
                                match result {
                                    Ok(session_id)
                                        if is_current_playback_session(
                                            &state_for_worker,
                                            session_id,
                                        ) =>
                                    {
                                        set_now_playing_radio(&ui, &station_for_result);
                                        refresh_now_playing_context(&ui, &state_for_worker);
                                        ui.set_status_text(
                                            format!("Playing {}", station_for_result.name).into(),
                                        );
                                    }
                                    Ok(_) => {}
                                    Err(error)
                                        if is_current_playback_session(
                                            &state_for_worker,
                                            transition_session_id,
                                        ) =>
                                    {
                                        set_error_text(&ui, format!("Radio failed: {error:#}"))
                                    }
                                    Err(_) => {}
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
            let transition_session_id = begin_playback_transition(&state);
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
                            Err(error)
                                if is_current_playback_session(
                                    &state_for_worker,
                                    transition_session_id,
                                ) =>
                            {
                                set_error_text(&ui, format!("Playback failed: {error:#}"))
                            }
                            Err(_) => {}
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
    InPlace {
        target_seconds: f64,
        session_id: u64,
    },
    RestartStream {
        current: Box<CurrentPlayback>,
        target_seconds: f64,
    },
    Restarted {
        target_seconds: f64,
        session_id: u64,
    },
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
        let session_id = app_state.playback_session_id;
        match app_state.playback.seek_absolute(target_seconds) {
            Ok(()) => Ok(SeekOutcome::InPlace {
                target_seconds,
                session_id,
            }),
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
        }) => restart_stream_at_offset(state, current.as_ref(), target_seconds).map(|session_id| {
            SeekOutcome::Restarted {
                target_seconds,
                session_id,
            }
        }),
        other => other,
    }
}

fn apply_seek_result(
    ui: &AppWindow,
    state: &Arc<Mutex<AppState>>,
    command_session_id: u64,
    result: Result<SeekOutcome>,
    progress: f32,
) {
    match result {
        Ok(
            SeekOutcome::InPlace {
                target_seconds,
                session_id,
            }
            | SeekOutcome::Restarted {
                target_seconds,
                session_id,
            },
        ) if is_current_playback_session(state, session_id) => {
            ui.set_playback_elapsed_text(format_seconds(target_seconds).into());
            ui.set_playback_progress(progress);
        }
        Ok(SeekOutcome::InPlace { .. } | SeekOutcome::Restarted { .. }) => {}
        Ok(SeekOutcome::Noop) => {}
        Ok(SeekOutcome::RestartStream { .. }) => {}
        Err(error)
            if is_current_playback_session(state, command_session_id)
                && error.to_string().contains("seeking is not available") =>
        {
            set_error_text(ui, "Seeking is not available for this stream");
        }
        Err(error) if is_current_playback_session(state, command_session_id) => {
            set_error_text(ui, format!("Seek failed: {error}"));
        }
        Err(_) => {}
    }
}

enum PlaybackPollUpdate {
    Snapshot {
        snapshot: PlaybackSnapshot,
        visualizer_frame: VisualizerFrame,
    },
    TrackStarted(Box<StartedTrackPlayback>),
}

fn poll_playback_snapshot(state: &Arc<Mutex<AppState>>) -> Option<PlaybackPollUpdate> {
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

    let snapshot_update = state.lock().ok().and_then(|mut state| {
        if state.playback_session_id != session_id {
            return None;
        }
        let previous = state.latest_playback_snapshot.clone();
        let snapshot = merge_playback_snapshot(snapshot, &previous);
        let should_auto_advance =
            state.current_playback.is_some() && playback_reached_end(&previous, &snapshot);
        state.latest_playback_snapshot = snapshot.clone();
        let visualizer_frame = state.visualizer.next_frame(&snapshot);
        Some((snapshot, visualizer_frame, should_auto_advance))
    })?;

    let (snapshot, visualizer_frame, should_auto_advance) = snapshot_update;
    if should_auto_advance {
        if let Ok(started) = play_queue_neighbor(state, QueueDirection::Next) {
            return Some(PlaybackPollUpdate::TrackStarted(Box::new(started)));
        }
    }

    Some(PlaybackPollUpdate::Snapshot {
        snapshot,
        visualizer_frame,
    })
}

fn merge_playback_snapshot(
    current: PlaybackSnapshot,
    previous: &PlaybackSnapshot,
) -> PlaybackSnapshot {
    PlaybackSnapshot {
        position_seconds: merge_position_seconds(
            current.position_seconds,
            previous.position_seconds,
        ),
        duration_seconds: merge_duration_seconds(
            current.duration_seconds,
            previous.duration_seconds,
        ),
        is_playing: current.is_playing,
        volume: current.volume,
        stream_title: current
            .stream_title
            .or_else(|| previous.stream_title.clone()),
    }
}

fn merge_position_seconds(current: Option<f64>, previous: Option<f64>) -> Option<f64> {
    match (current, previous) {
        (None, previous) => previous,
        (current, None) => current,
        (Some(current), Some(previous)) if current >= previous => Some(current),
        (Some(current), Some(previous)) if previous - current <= 1.0 => Some(current),
        (Some(_), Some(previous)) => Some(previous),
    }
}

fn merge_duration_seconds(current: Option<f64>, previous: Option<f64>) -> Option<f64> {
    match (current, previous) {
        (None, previous) => previous,
        (current, None) => current,
        (Some(current), Some(previous)) if current >= previous => Some(current),
        (Some(_), Some(previous)) => Some(previous),
    }
}

fn playback_reached_end(previous: &PlaybackSnapshot, current: &PlaybackSnapshot) -> bool {
    if !previous.is_playing || current.is_playing {
        return false;
    }

    match (current.position_seconds, current.duration_seconds) {
        (Some(position), Some(duration)) if duration > 0.0 => position >= duration - 1.0,
        _ => false,
    }
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

fn begin_playback_transition(state: &Arc<Mutex<AppState>>) -> u64 {
    if let Ok(mut state) = state.lock() {
        let session_id = next_playback_session(&mut state);
        reset_latest_playback_snapshot(&mut state);
        session_id
    } else {
        0
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

fn submit_home_load(
    ui_weak: &Weak<AppWindow>,
    state: &Arc<Mutex<AppState>>,
    worker: &BackgroundWorker,
) {
    let source = match active_source_from_state(state) {
        Ok(source) => source,
        Err(error) => {
            if let Some(ui) = ui_weak.upgrade() {
                set_error_text(&ui, format!("Home failed: {error}"));
            }
            return;
        }
    };

    if let Some(ui) = ui_weak.upgrade() {
        ui.set_status_text("Loading home...".into());
        ui.set_search_state_text("Loading home sections...".into());
    }

    let ui_weak_for_result = ui_weak.clone();
    let state_for_result = Arc::clone(state);
    worker.submit(move || {
        let result = load_home_content(source);
        slint::invoke_from_event_loop(move || {
            if let Some(ui) = ui_weak_for_result.upgrade() {
                match result {
                    Ok(content) => {
                        if let Ok(mut state) = state_for_result.lock() {
                            state.home_cache = Some(content.clone());
                        }
                        ui.set_media_rows(home_rows(&content));
                        ui.set_search_state_text(home_summary(&content).into());
                        ui.set_status_text("Home loaded".into());
                    }
                    Err(error) => {
                        ui.set_search_state_text("Home failed".into());
                        set_error_text(&ui, format!("Home failed: {error}"));
                    }
                }
            }
        })
        .ok();
    });
}

fn load_home_content(source: SavedMediaSource) -> Result<HomeContent> {
    let provider = NavidromeProvider::new(source)?;
    Ok(HomeContent {
        recently_added_albums: provider.album_list(AlbumListType::Newest, 8)?,
        random_albums: provider.album_list(AlbumListType::Random, 8)?,
        frequent_albums: provider.album_list(AlbumListType::Frequent, 8)?,
        recent_albums: provider.album_list(AlbumListType::Recent, 8)?,
        playlists: provider.playlists()?.into_iter().take(8).collect(),
        genres: provider.genres()?.into_iter().take(10).collect(),
        spotlight_tracks: provider.random_tracks(25)?,
    })
}

fn submit_library_sync(
    ui_weak: &Weak<AppWindow>,
    state: &Arc<Mutex<AppState>>,
    worker: &BackgroundWorker,
    library_store: Arc<LibraryStore>,
) {
    let source = match active_source_from_state(state) {
        Ok(source) => source,
        Err(error) => {
            if let Some(ui) = ui_weak.upgrade() {
                set_error_text(&ui, format!("Library sync failed: {error}"));
            }
            return;
        }
    };

    if let Ok(mut state) = state.lock() {
        state.library_index.status = "Syncing library...".to_string();
        if let Some(ui) = ui_weak.upgrade() {
            ui.set_search_state_text(state.library_index.status.clone().into());
            ui.set_status_text("Syncing library...".into());
        }
    }

    let ui_weak_for_result = ui_weak.clone();
    let state_for_result = Arc::clone(state);
    worker.submit(move || {
        let result = sync_library_index(source, &library_store);
        slint::invoke_from_event_loop(move || {
            if let Some(ui) = ui_weak_for_result.upgrade() {
                match result {
                    Ok(index) => {
                        if let Ok(mut state) = state_for_result.lock() {
                            state.library_index = index;
                            ui.set_media_rows(library_rows(&state.library_index));
                            ui.set_search_state_text(library_summary(&state.library_index).into());
                        }
                        ui.set_status_text("Library synced".into());
                    }
                    Err(error) => {
                        ui.set_search_state_text("Library sync failed".into());
                        set_error_text(&ui, format!("Library sync failed: {error}"));
                    }
                }
            }
        })
        .ok();
    });
}

fn sync_library_index(
    source: SavedMediaSource,
    library_store: &LibraryStore,
) -> Result<LibraryIndex> {
    let source_id = source.id.clone();
    let provider = NavidromeProvider::new(source)?;
    let artists = provider.artists()?;
    let mut albums = Vec::new();
    let page_size = 100;
    for page in 0..50 {
        let page_albums = provider.album_list_paged(
            AlbumListType::AlphabeticalByName,
            page_size,
            page * page_size,
        )?;
        if page_albums.is_empty() {
            break;
        }
        let page_len = page_albums.len();
        albums.extend(page_albums);
        if page_len < page_size as usize {
            break;
        }
    }

    let mut tracks = Vec::new();
    for album in &albums {
        if let Ok(detail) = provider.album(&album.id) {
            tracks.extend(detail.tracks);
        }
    }
    tracks.sort_by(|left, right| {
        left.artist
            .cmp(&right.artist)
            .then(left.album.cmp(&right.album))
            .then(left.title.cmp(&right.title))
    });

    library_store.replace_source(&source_id, &artists, &albums, &tracks)?;
    let snapshot = library_store.snapshot(&source_id, 150)?;
    let stats = library_store.stats(&source_id)?;
    Ok(library_index_from_snapshot(
        Some(source_id),
        String::new(),
        snapshot,
        stats,
    ))
}

fn library_index_from_snapshot(
    source_id: Option<String>,
    filter: String,
    snapshot: LibrarySnapshot,
    stats: LibraryStats,
) -> LibraryIndex {
    LibraryIndex {
        status: format!(
            "Synced {} artists, {} albums, {} tracks",
            stats.artist_count, stats.album_count, stats.track_count
        ),
        synced_artist_count: stats.artist_count,
        synced_album_count: stats.album_count,
        synced_track_count: stats.track_count,
        is_synced: true,
        source_id,
        artists: snapshot.artists,
        albums: snapshot.albums,
        tracks: snapshot.tracks,
        filter,
    }
}

fn refresh_library_index_from_store(state: &mut AppState, library_store: &LibraryStore) {
    let source_id = state
        .library_index
        .source_id
        .clone()
        .or_else(|| state.settings.active_source().map(|source| source.id));
    let Some(source_id) = source_id else {
        return;
    };
    let filter = state.library_index.filter.clone();
    if let (Ok(snapshot), Ok(stats)) = (
        library_store.search(&source_id, &filter, 150),
        library_store.stats(&source_id),
    ) {
        state.library_index = library_index_from_snapshot(Some(source_id), filter, snapshot, stats);
    }
}

fn refresh_library_ui(ui_weak: &Weak<AppWindow>, state: &AppState) {
    if let Some(ui) = ui_weak.upgrade() {
        ui.set_media_rows(library_rows(&state.library_index));
        ui.set_search_state_text(library_summary(&state.library_index).into());
    }
}

fn library_rows(index: &LibraryIndex) -> ModelRc<MediaRow> {
    let filter = index.filter.trim().to_ascii_lowercase();
    let mut rows = Vec::new();

    let artists = index
        .artists
        .iter()
        .enumerate()
        .filter(|(_, artist)| library_matches(&artist.name, &filter))
        .take(100)
        .collect::<Vec<_>>();
    if !artists.is_empty() {
        rows.push(home_header("Artists", artists.len()));
        rows.extend(artists.into_iter().map(|(source_index, artist)| MediaRow {
            kind: SharedString::from("library-artist"),
            title: SharedString::from(artist.name.as_str()),
            subtitle: SharedString::from("Artist"),
            source_index: source_index as i32,
            is_header: false,
            action_label: SharedString::new(),
        }));
    }

    let albums = index
        .albums
        .iter()
        .enumerate()
        .filter(|(_, album)| {
            library_matches(&album.title, &filter) || library_matches(&album.artist, &filter)
        })
        .take(100)
        .collect::<Vec<_>>();
    if !albums.is_empty() {
        rows.push(home_header("Albums", albums.len()));
        rows.extend(albums.into_iter().map(|(source_index, album)| MediaRow {
            kind: SharedString::from("library-album"),
            title: SharedString::from(album.title.as_str()),
            subtitle: SharedString::from(format!("Album - {}", album.subtitle())),
            source_index: source_index as i32,
            is_header: false,
            action_label: SharedString::new(),
        }));
    }

    let tracks = index
        .tracks
        .iter()
        .enumerate()
        .filter(|(_, track)| {
            library_matches(&track.title, &filter)
                || library_matches(&track.artist, &filter)
                || library_matches(&track.album, &filter)
        })
        .take(150)
        .collect::<Vec<_>>();
    if !tracks.is_empty() {
        rows.push(home_header("Tracks", tracks.len()));
        rows.extend(tracks.into_iter().map(|(source_index, track)| MediaRow {
            kind: SharedString::from("library-track"),
            title: SharedString::from(track.title.as_str()),
            subtitle: SharedString::from(track.subtitle()),
            source_index: source_index as i32,
            is_header: false,
            action_label: SharedString::new(),
        }));
    }

    if rows.is_empty() {
        rows.push(home_header(
            if index.is_synced {
                "No local matches"
            } else {
                "Sync library to browse locally"
            },
            0,
        ));
    }

    ModelRc::new(VecModel::from(rows))
}

fn library_matches(value: &str, filter: &str) -> bool {
    if filter.is_empty() {
        return true;
    }

    let value = value.to_ascii_lowercase();
    let filter = filter.to_ascii_lowercase();
    match library_quick_filter(&filter) {
        Some(LibraryQuickFilter::Letter(letter)) => value.starts_with(letter),
        Some(LibraryQuickFilter::Digit) => value
            .chars()
            .next()
            .is_some_and(|character| character.is_ascii_digit()),
        Some(LibraryQuickFilter::Symbol) => value.chars().next().is_some_and(|character| {
            !character.is_ascii_alphabetic() && !character.is_ascii_digit()
        }),
        None => value.contains(&filter),
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum LibraryQuickFilter<'a> {
    Letter(&'a str),
    Digit,
    Symbol,
}

fn library_quick_filter(filter: &str) -> Option<LibraryQuickFilter<'_>> {
    if filter.len() == 1 && filter.as_bytes()[0].is_ascii_alphabetic() {
        Some(LibraryQuickFilter::Letter(filter))
    } else if filter == "0-9" {
        Some(LibraryQuickFilter::Digit)
    } else if filter == "#" {
        Some(LibraryQuickFilter::Symbol)
    } else {
        None
    }
}

fn library_summary(index: &LibraryIndex) -> String {
    if !index.is_synced {
        return index.status.clone();
    }
    let filter = index.filter.trim();
    let suffix = if filter.is_empty() {
        String::new()
    } else {
        format!(" - filter: {filter}")
    };
    format!(
        "{} artists, {} albums, {} tracks indexed{}",
        index.synced_artist_count, index.synced_album_count, index.synced_track_count, suffix
    )
}

fn active_source_from_state(state: &Arc<Mutex<AppState>>) -> Result<SavedMediaSource> {
    state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))?
        .settings
        .active_source()
        .ok_or_else(|| anyhow::anyhow!("no active source"))
}

fn random_album_radio_tracks(source: SavedMediaSource) -> Result<Vec<Track>> {
    let provider = NavidromeProvider::new(source)?;
    let albums = provider.album_list(AlbumListType::Random, 1)?;
    let album = albums
        .first()
        .ok_or_else(|| anyhow::anyhow!("no random album found"))?;
    let detail = provider.album(&album.id)?;
    if detail.tracks.is_empty() {
        provider.random_tracks(25)
    } else {
        Ok(detail.tracks)
    }
}

fn home_rows(content: &HomeContent) -> ModelRc<MediaRow> {
    let mut rows = Vec::new();
    let mut album_index = 0usize;
    append_home_albums(
        &mut rows,
        &mut album_index,
        "Recently Added",
        &content.recently_added_albums,
    );
    append_home_albums(
        &mut rows,
        &mut album_index,
        "Random Albums",
        &content.random_albums,
    );
    append_home_albums(
        &mut rows,
        &mut album_index,
        "Frequent Albums",
        &content.frequent_albums,
    );
    append_home_albums(
        &mut rows,
        &mut album_index,
        "Recent Albums",
        &content.recent_albums,
    );

    if !content.playlists.is_empty() {
        rows.push(home_header("Playlists", content.playlists.len()));
        rows.extend(
            content
                .playlists
                .iter()
                .enumerate()
                .map(|(index, playlist)| MediaRow {
                    kind: SharedString::from("home-playlist"),
                    title: SharedString::from(playlist.name.as_str()),
                    subtitle: SharedString::from(format!(
                        "{} tracks - {}",
                        playlist.song_count, playlist.owner
                    )),
                    source_index: index as i32,
                    is_header: false,
                    action_label: SharedString::new(),
                }),
        );
    }

    if !content.genres.is_empty() {
        rows.push(home_header("Genres", content.genres.len()));
        rows.extend(
            content
                .genres
                .iter()
                .enumerate()
                .map(|(index, genre)| MediaRow {
                    kind: SharedString::from("home-genre"),
                    title: SharedString::from(genre.name.as_str()),
                    subtitle: SharedString::from(format!(
                        "{} songs - {} albums",
                        genre.song_count, genre.album_count
                    )),
                    source_index: index as i32,
                    is_header: false,
                    action_label: SharedString::from("Search"),
                }),
        );
    }

    let decade = spotlight_decade(&content.spotlight_tracks);
    if !content.spotlight_tracks.is_empty() {
        rows.push(home_header(
            decade
                .map(|decade| format!("{decade}s Spotlight"))
                .unwrap_or_else(|| "Year Spotlight".to_string()),
            content.spotlight_tracks.len(),
        ));
        rows.extend(
            content
                .spotlight_tracks
                .iter()
                .enumerate()
                .map(|(index, track)| MediaRow {
                    kind: SharedString::from("home-track"),
                    title: SharedString::from(track.title.as_str()),
                    subtitle: SharedString::from(track.subtitle()),
                    source_index: index as i32,
                    is_header: false,
                    action_label: SharedString::new(),
                }),
        );
    }

    ModelRc::new(VecModel::from(rows))
}

fn append_home_albums(
    rows: &mut Vec<MediaRow>,
    album_index: &mut usize,
    label: &str,
    albums: &[Album],
) {
    if albums.is_empty() {
        return;
    }
    rows.push(home_header(label, albums.len()));
    for album in albums {
        rows.push(MediaRow {
            kind: SharedString::from("home-album"),
            title: SharedString::from(album.title.as_str()),
            subtitle: SharedString::from(format!("Album - {}", album.subtitle())),
            source_index: *album_index as i32,
            is_header: false,
            action_label: SharedString::new(),
        });
        *album_index += 1;
    }
}

fn home_header(label: impl Into<String>, count: usize) -> MediaRow {
    MediaRow {
        kind: SharedString::from("header"),
        title: SharedString::from(format!("{} ({count})", label.into())),
        subtitle: SharedString::new(),
        source_index: -1,
        is_header: true,
        action_label: SharedString::new(),
    }
}

fn home_album(content: &HomeContent, index: usize) -> Option<Album> {
    content
        .recently_added_albums
        .iter()
        .chain(content.random_albums.iter())
        .chain(content.frequent_albums.iter())
        .chain(content.recent_albums.iter())
        .nth(index)
        .cloned()
}

fn spotlight_decade(tracks: &[Track]) -> Option<u32> {
    let mut counts = HashMap::<u32, usize>::new();
    for decade in tracks
        .iter()
        .filter_map(|track| track.year.map(|year| year / 10 * 10))
    {
        *counts.entry(decade).or_default() += 1;
    }
    counts
        .into_iter()
        .max_by_key(|(_, count)| *count)
        .map(|(decade, _)| decade)
}

fn home_summary(content: &HomeContent) -> String {
    format!(
        "{} albums, {} playlists, {} genres",
        content.recently_added_albums.len()
            + content.random_albums.len()
            + content.frequent_albums.len()
            + content.recent_albums.len(),
        content.playlists.len(),
        content.genres.len()
    )
}

fn current_track_action(state: &Arc<Mutex<AppState>>) -> Result<(SavedMediaSource, Track)> {
    let state = state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))?;
    let playback = state
        .current_playback
        .clone()
        .ok_or_else(|| anyhow::anyhow!("nothing playing"))?;
    Ok((playback.source, playback.track))
}

fn update_current_track(state: &Arc<Mutex<AppState>>, update: impl Fn(&mut Track)) {
    if let Ok(mut state) = state.lock() {
        let Some(current) = state.current_playback.as_mut() else {
            return;
        };
        update(&mut current.track);
        let current_track = current.track.clone();
        if let Some(queue_track) = state.queue.current_mut() {
            *queue_track = current_track.clone();
        }
        if let Some(search_track) = state
            .search_results
            .tracks
            .iter_mut()
            .find(|track| track.id == current_track.id)
        {
            *search_track = current_track.clone();
        }
        if let Some(detail) = state.current_album_detail.as_mut() {
            if let Some(track) = detail
                .tracks
                .iter_mut()
                .find(|track| track.id == current_track.id)
            {
                *track = current_track;
            }
        }
    }
}

fn queue_tracks_source(
    state: &Arc<Mutex<AppState>>,
    start_index: usize,
) -> Result<(SavedMediaSource, Vec<Track>, usize)> {
    let state = state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))?;
    let source = state
        .settings
        .active_source()
        .ok_or_else(|| anyhow::anyhow!("no active source"))?;
    let tracks = state.queue.tracks();
    if start_index >= tracks.len() {
        return Err(anyhow::anyhow!("queue track not found"));
    }
    Ok((source, tracks, start_index))
}

fn detail_album_source_tracks(
    state: &Arc<Mutex<AppState>>,
) -> Result<(SavedMediaSource, Vec<Track>)> {
    let state = state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))?;
    let source = state
        .settings
        .active_source()
        .ok_or_else(|| anyhow::anyhow!("no active source"))?;
    let tracks = state
        .current_album_detail
        .as_ref()
        .map(|detail| detail.tracks.clone())
        .filter(|tracks| !tracks.is_empty())
        .ok_or_else(|| anyhow::anyhow!("album has no tracks"))?;
    Ok((source, tracks))
}

fn detail_album_source(state: &Arc<Mutex<AppState>>) -> Result<(SavedMediaSource, AlbumDetail)> {
    let state = state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))?;
    let source = state
        .settings
        .active_source()
        .ok_or_else(|| anyhow::anyhow!("no active source"))?;
    let album = state
        .current_album_detail
        .clone()
        .ok_or_else(|| anyhow::anyhow!("no album selected"))?;
    Ok((source, album))
}

fn detail_artist_source(state: &Arc<Mutex<AppState>>) -> Result<(SavedMediaSource, ArtistDetail)> {
    let state = state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))?;
    let source = state
        .settings
        .active_source()
        .ok_or_else(|| anyhow::anyhow!("no active source"))?;
    let artist = state
        .current_artist_detail
        .clone()
        .ok_or_else(|| anyhow::anyhow!("no artist selected"))?;
    Ok((source, artist))
}

fn album_radio_tracks(source: SavedMediaSource, album: &AlbumDetail) -> Result<Vec<Track>> {
    let seed = album
        .tracks
        .first()
        .ok_or_else(|| anyhow::anyhow!("album has no tracks"))?;
    let provider = NavidromeProvider::new(source)?;
    let tracks = provider.similar_tracks(&seed.id, 25)?;
    if tracks.is_empty() {
        Ok(album.tracks.clone())
    } else {
        Ok(tracks)
    }
}

fn artist_radio_tracks(source: SavedMediaSource, artist: &ArtistDetail) -> Result<Vec<Track>> {
    let provider = NavidromeProvider::new(source)?;
    let Some(album) = artist.albums.first() else {
        return provider.random_tracks(25);
    };
    let album = provider.album(&album.id)?;
    let Some(seed) = album.tracks.first() else {
        return provider.random_tracks(25);
    };
    let tracks = provider.similar_tracks(&seed.id, 25)?;
    if tracks.is_empty() {
        Ok(album.tracks)
    } else {
        Ok(tracks)
    }
}

fn start_tracks_from_ui(
    ui_weak: &Weak<AppWindow>,
    state: &Arc<Mutex<AppState>>,
    playback_worker: &BackgroundWorker,
    worker_for_art: BackgroundWorker,
    cover_art_cache: Arc<CoverArtCache>,
    request: TrackPlaybackRequest,
) {
    let track = {
        let mut state = match state.lock() {
            Ok(state) => state,
            Err(error) => {
                if let Some(ui) = ui_weak.upgrade() {
                    set_error_text(&ui, format!("Playback failed: {error}"));
                }
                return;
            }
        };
        state.queue = TrackQueue::play_from_tracks(request.tracks, request.start_index);
        state.queue.set_repeat_mode(RepeatMode::Off);
        let Some(track) = state.queue.jump_to(request.start_index).cloned() else {
            if let Some(ui) = ui_weak.upgrade() {
                set_error_text(&ui, "Playback failed: queue is empty");
            }
            return;
        };
        track
    };

    if let Some(ui) = ui_weak.upgrade() {
        reset_playback_progress_ui(&ui);
        ui.set_status_text(request.status_text.into());
    }
    let transition_session_id = begin_playback_transition(state);
    let state_for_worker = Arc::clone(state);
    let ui_weak_for_result = ui_weak.clone();
    let source = request.source;
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
                    Err(error)
                        if is_current_playback_session(
                            &state_for_worker,
                            transition_session_id,
                        ) =>
                    {
                        set_error_text(&ui, format!("Playback failed: {error:#}"))
                    }
                    Err(_) => {}
                }
            }
        })
        .ok();
    });
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
) -> Result<u64> {
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
            let session_id = next_playback_session(&mut state);
            state.playback.play_url(&url)?;
            reset_latest_playback_snapshot(&mut state);
            state.current_playback = Some(current.clone());
            Ok(session_id)
        })
}

fn start_radio_playback(
    state: &Arc<Mutex<AppState>>,
    station: &InternetRadioStation,
) -> Result<u64> {
    let stream_url = resolve_radio_stream_url(&station.stream_url)?;
    state
        .lock()
        .map_err(|error| anyhow::anyhow!(error.to_string()))
        .and_then(|mut state| {
            let session_id = next_playback_session(&mut state);
            state.playback.play_url(&stream_url)?;
            reset_latest_playback_snapshot(&mut state);
            state.current_playback = None;
            state.queue = TrackQueue::default();
            Ok(session_id)
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
    if !is_current_playback_session(state, started.session_id) {
        return;
    }

    handle_track_started(ui, &started.track);
    refresh_now_playing_context(ui, state);
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

fn handle_track_started(ui: &AppWindow, track: &Track) {
    set_now_playing(ui, track);
}

fn set_now_playing(ui: &AppWindow, track: &Track) {
    ui.set_now_playing_title(track.title.clone().into());
    ui.set_now_playing_subtitle(track.subtitle().into());
    ui.set_now_playing_artist(track.artist.clone().into());
    ui.set_now_playing_album(track.album.clone().into());
    ui.set_now_playing_year(
        track
            .year
            .map(|year| year.to_string())
            .unwrap_or_else(|| "--".to_string())
            .into(),
    );
    ui.set_now_playing_codec_line(track_codec_line(track).into());
    ui.set_now_playing_details(track_details_line(track).into());
    ui.set_now_playing_favorite(track.favorited_at.is_some());
    ui.set_now_playing_rating(i32::from(track.user_rating.unwrap_or(0)));
    ui.set_now_playing_art(Image::default());
    apply_player_palette(ui, &CoverArtPalette::default());
    reset_playback_progress_ui(ui);
}

fn set_now_playing_radio(ui: &AppWindow, station: &InternetRadioStation) {
    ui.set_now_playing_title(station.name.clone().into());
    ui.set_now_playing_artist(String::new().into());
    ui.set_now_playing_album("Internet radio".into());
    ui.set_now_playing_year("--".into());
    ui.set_now_playing_codec_line("Live stream".into());
    ui.set_now_playing_details(station.stream_url.clone().into());
    ui.set_now_playing_favorite(false);
    ui.set_now_playing_rating(0);
    apply_player_palette(ui, &CoverArtPalette::default());
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

fn refresh_now_playing_context(ui: &AppWindow, state: &Arc<Mutex<AppState>>) {
    let Ok(state) = state.lock() else {
        return;
    };
    ui.set_up_next_rows(up_next_rows(&state.queue));
    ui.set_back_to_rows(back_to_rows(&state.queue));
    if let Some(current) = state.current_playback.as_ref() {
        ui.set_now_playing_title(current.track.title.clone().into());
        ui.set_now_playing_subtitle(current.track.subtitle().into());
        ui.set_now_playing_artist(current.track.artist.clone().into());
        ui.set_now_playing_album(current.track.album.clone().into());
        ui.set_now_playing_year(
            current
                .track
                .year
                .map(|year| year.to_string())
                .unwrap_or_else(|| "--".to_string())
                .into(),
        );
        ui.set_now_playing_codec_line(track_codec_line(&current.track).into());
        ui.set_now_playing_details(track_details_line(&current.track).into());
        ui.set_now_playing_favorite(current.track.favorited_at.is_some());
        ui.set_now_playing_rating(i32::from(current.track.user_rating.unwrap_or(0)));
    }
}

fn track_codec_line(track: &Track) -> String {
    match (track.codec.as_deref(), track.bit_rate_kbps) {
        (Some(codec), Some(bit_rate)) => format!("{} - {} kbps", codec.to_uppercase(), bit_rate),
        (Some(codec), None) => codec.to_uppercase(),
        (None, Some(bit_rate)) => format!("{bit_rate} kbps"),
        (None, None) => "--".to_string(),
    }
}

fn track_details_line(track: &Track) -> String {
    format!(
        "Track ID: {} | Artist: {} | Album: {} | Year: {} | Audio: {}",
        track.id,
        display_player_field(&track.artist),
        display_player_field(&track.album),
        track
            .year
            .map(|year| year.to_string())
            .unwrap_or_else(|| "--".to_string()),
        track_codec_line(track)
    )
}

fn display_player_field(value: &str) -> &str {
    if value.trim().is_empty() {
        "--"
    } else {
        value
    }
}

fn set_error_text(ui: &AppWindow, message: impl Into<String>) {
    ui.set_error_text(message.into().into());
}

fn navigate_to_locked(
    state: &mut AppState,
    route: impl Into<String>,
    settings_store: &Arc<dyn SettingsStore>,
) {
    let route = route.into();
    if state.settings.app.last_route != route {
        let previous = state.settings.app.last_route.clone();
        state.route_history.push(previous);
    }
    state.settings.app.last_route = route;
    let _ = settings_store.save(&state.settings);
}

fn restore_route_rows(ui: &AppWindow, state: &Arc<Mutex<AppState>>) {
    let route = ui.get_current_route().to_string();
    let Ok(state) = state.lock() else {
        return;
    };

    match route.as_str() {
        "home" => {
            if let Some(content) = state.home_cache.as_ref() {
                ui.set_media_rows(home_rows(content));
                ui.set_search_state_text(home_summary(content).into());
            } else {
                ui.set_search_state_text(
                    "Refresh Home to load albums, playlists, and genres".into(),
                );
            }
        }
        "library" => {
            ui.set_media_rows(library_rows(&state.library_index));
            ui.set_search_state_text(library_summary(&state.library_index).into());
        }
        "search" => ui.set_media_rows(search_rows(&state.search_results)),
        "album-detail" => {
            if let Some(detail) = state.current_album_detail.as_ref() {
                ui.set_media_rows(album_detail_rows(detail));
                ui.set_detail_title(detail.album.title.clone().into());
                ui.set_detail_subtitle(
                    format!("{} - {} tracks", detail.album.artist, detail.tracks.len()).into(),
                );
            }
        }
        "artist-detail" => {
            if let Some(detail) = state.current_artist_detail.as_ref() {
                ui.set_media_rows(artist_detail_rows(detail));
                ui.set_detail_title(detail.artist.name.clone().into());
                ui.set_detail_subtitle(format!("{} albums", detail.albums.len()).into());
            }
        }
        "radio" => ui.set_media_rows(radio_rows(&state.radio_stations)),
        _ => {}
    }
}

fn reset_playback_progress_ui(ui: &AppWindow) {
    ui.set_playback_status_text("Playing".into());
    ui.set_playback_elapsed_text("0:00".into());
    ui.set_playback_duration_text("--:--".into());
    ui.set_playback_progress(0.0);
    ui.set_playback_is_playing(true);
}

fn search_state_text(results: &SearchResults) -> String {
    if results.is_empty() {
        return "No results".to_string();
    }

    format!(
        "{} artists, {} albums, {} tracks",
        results.artists.len(),
        results.albums.len(),
        results.tracks.len()
    )
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
                Ok(cached) => match Image::load_from_path(&cached.path) {
                    Ok(image) => {
                        ui.set_now_playing_art(image);
                        apply_player_palette(&ui, &cached.palette);
                    }
                    Err(error) => set_error_text(&ui, format!("Cover art decode failed: {error}")),
                },
                Err(error) => set_error_text(&ui, format!("Cover art failed: {error}")),
            }
        })
        .ok();
    });
}

fn apply_player_palette(ui: &AppWindow, palette: &CoverArtPalette) {
    ui.set_player_accent(color_from_rgb(palette.accent));
    ui.set_player_surface(color_from_rgb(palette.surface));
}

fn color_from_rgb((red, green, blue): (u8, u8, u8)) -> Color {
    Color::from_rgb_u8(red, green, blue)
}

fn is_current_playback_session(state: &Arc<Mutex<AppState>>, session_id: u64) -> bool {
    state
        .lock()
        .map(|state| state.playback_session_id == session_id)
        .unwrap_or(false)
}

fn current_playback_session(state: &Arc<Mutex<AppState>>) -> u64 {
    state
        .lock()
        .map(|state| state.playback_session_id)
        .unwrap_or(0)
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
        format_seconds, library_matches, library_summary, merge_playback_snapshot,
        parse_radio_playlist, playback_duration_text, playback_elapsed_text, playback_progress,
        playback_reached_end, playback_status_text, search_state_text, LibraryIndex,
    };
    use crate::domain::SearchResults;
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
    fn search_state_summarizes_empty_results() {
        assert_eq!("No results", search_state_text(&SearchResults::default()));
    }

    #[test]
    fn library_summary_uses_indexed_totals_not_loaded_rows() {
        let index = LibraryIndex {
            synced_artist_count: 345,
            synced_album_count: 2647,
            synced_track_count: 19_456,
            is_synced: true,
            ..LibraryIndex::default()
        };

        assert_eq!(
            "345 artists, 2647 albums, 19456 tracks indexed",
            library_summary(&index)
        );
    }

    #[test]
    fn library_matches_supports_digit_and_symbol_quick_filters() {
        assert!(library_matches("3 Doors Down", "0-9"));
        assert!(library_matches("(hed) pe", "#"));
        assert!(library_matches("Garbage", "G"));
        assert!(!library_matches("The Gap Band", "G"));
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
    fn merge_playback_snapshot_keeps_previous_values_when_current_read_is_unknown() {
        let previous = PlaybackSnapshot {
            position_seconds: Some(42.0),
            duration_seconds: Some(180.0),
            is_playing: true,
            volume: 80,
            stream_title: None,
        };

        let merged = merge_playback_snapshot(
            PlaybackSnapshot {
                is_playing: true,
                volume: 80,
                ..PlaybackSnapshot::default()
            },
            &previous,
        );

        assert_eq!(Some(42.0), merged.position_seconds);
        assert_eq!(Some(180.0), merged.duration_seconds);
    }

    #[test]
    fn merge_playback_snapshot_ignores_large_backward_position_jumps() {
        let previous = PlaybackSnapshot {
            position_seconds: Some(42.0),
            duration_seconds: Some(180.0),
            is_playing: true,
            volume: 80,
            stream_title: None,
        };

        let merged = merge_playback_snapshot(
            PlaybackSnapshot {
                position_seconds: Some(0.0),
                duration_seconds: Some(180.0),
                is_playing: true,
                volume: 80,
                stream_title: None,
            },
            &previous,
        );

        assert_eq!(Some(42.0), merged.position_seconds);
        assert_eq!(Some(180.0), merged.duration_seconds);
    }

    #[test]
    fn merge_playback_snapshot_allows_small_backward_position_correction() {
        let previous = PlaybackSnapshot {
            position_seconds: Some(42.0),
            duration_seconds: Some(180.0),
            is_playing: true,
            volume: 80,
            stream_title: None,
        };

        let merged = merge_playback_snapshot(
            PlaybackSnapshot {
                position_seconds: Some(41.4),
                duration_seconds: Some(180.0),
                is_playing: true,
                volume: 80,
                stream_title: None,
            },
            &previous,
        );

        assert_eq!(Some(41.4), merged.position_seconds);
    }

    #[test]
    fn playback_reached_end_requires_playing_to_idle_at_known_end() {
        let previous = PlaybackSnapshot {
            position_seconds: Some(179.0),
            duration_seconds: Some(180.0),
            is_playing: true,
            volume: 80,
            stream_title: None,
        };
        let current = PlaybackSnapshot {
            position_seconds: Some(180.0),
            duration_seconds: Some(180.0),
            is_playing: false,
            volume: 80,
            stream_title: None,
        };

        assert!(playback_reached_end(&previous, &current));
        assert!(!playback_reached_end(&current, &current));
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
