-- DDL copied from v2.8.0 NaviampStorage.sq (release schema version 27).
-- Keep this fixture tied to the release tag. It must not use the current generated schema.
CREATE TABLE cached_image (
  url TEXT NOT NULL PRIMARY KEY,
  bytes BLOB NOT NULL,
  size_bytes INTEGER NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  last_accessed_epoch_millis INTEGER NOT NULL
);

CREATE TABLE cached_response (
  cache_key TEXT NOT NULL PRIMARY KEY,
  provider_id TEXT NOT NULL,
  resource_type TEXT NOT NULL,
  resource_id TEXT NOT NULL,
  payload TEXT NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  last_accessed_epoch_millis INTEGER NOT NULL
);

CREATE TABLE media_source (
  id TEXT NOT NULL PRIMARY KEY,
  provider_id TEXT NOT NULL,
  cache_namespace TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  base_url TEXT NOT NULL,
  username TEXT NOT NULL,
  token TEXT NOT NULL,
  salt TEXT NOT NULL,
  authentication_mode TEXT NOT NULL DEFAULT 'token',
  insecure_skip_tls_verification INTEGER NOT NULL DEFAULT 0,
  custom_certificate_path TEXT,
  client_certificate_keystore_path TEXT,
  client_certificate_keystore_password TEXT,
  created_at_epoch_millis INTEGER NOT NULL,
  last_connected_at_epoch_millis INTEGER,
  last_sync_started_at_epoch_millis INTEGER,
  last_sync_completed_at_epoch_millis INTEGER,
  last_library_scan_signature TEXT,
  last_library_scan_checked_at_epoch_millis INTEGER,
  native_token TEXT,
  secondary_urls_json TEXT,
  custom_headers_json TEXT,
  server_connection_key TEXT,
  library_scope_key TEXT,
  provider_identity_version INTEGER NOT NULL DEFAULT 0,
  provider_identity_probe_target_version INTEGER,
  provider_identity_probe_server_version TEXT,
  selected_music_folder_ids_json TEXT,
  password TEXT
);

CREATE TABLE library_artist (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_artist_id TEXT NOT NULL,
  name TEXT NOT NULL,
  search_name TEXT NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_artist_id)
);

CREATE TABLE favorite_artist_activity (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_artist_id TEXT NOT NULL,
  artist_name TEXT NOT NULL,
  favorited_at_iso8601 TEXT,
  favorite_active INTEGER NOT NULL DEFAULT 0,
  last_radio_played_at_iso8601 TEXT,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_artist_id)
);

CREATE TABLE library_album (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_album_id TEXT NOT NULL,
  remote_artist_id TEXT,
  title TEXT NOT NULL,
  artist_name TEXT NOT NULL,
  search_title TEXT NOT NULL,
  search_artist_name TEXT NOT NULL,
  cover_art_id TEXT,
  release_year INTEGER,
  updated_at_epoch_millis INTEGER NOT NULL,
  original_release_year INTEGER,
  PRIMARY KEY(source_id, remote_album_id)
);

CREATE TABLE library_track (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  remote_album_id TEXT,
  remote_artist_id TEXT,
  title TEXT NOT NULL,
  artist_name TEXT NOT NULL,
  album_title TEXT,
  search_title TEXT NOT NULL,
  search_artist_name TEXT NOT NULL,
  search_album_title TEXT,
  duration_seconds INTEGER,
  cover_art_id TEXT,
  audio_codec TEXT,
  audio_bitrate_kbps INTEGER,
  audio_content_type TEXT,
  audio_bit_depth INTEGER,
  audio_sampling_rate_hz INTEGER,
  favorited_at_iso8601 TEXT,
  user_rating INTEGER,
  updated_at_epoch_millis INTEGER NOT NULL,
  play_count INTEGER,
  last_played_at_iso8601 TEXT,
  genre_names TEXT,
  music_folder_id TEXT,
  album_release_year INTEGER,
  original_release_year INTEGER,
  PRIMARY KEY(source_id, remote_track_id)
);

CREATE TABLE library_track_artist_credit (
  source_id TEXT NOT NULL,
  remote_track_id TEXT NOT NULL,
  remote_artist_id TEXT NOT NULL,
  artist_name TEXT NOT NULL,
  PRIMARY KEY(source_id, remote_track_id, remote_artist_id),
  FOREIGN KEY(source_id, remote_track_id)
    REFERENCES library_track(source_id, remote_track_id) ON DELETE CASCADE
);

CREATE TABLE genre_ontology_metadata (
  singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id = 1),
  source_name TEXT NOT NULL,
  source_url TEXT NOT NULL,
  snapshot_version TEXT NOT NULL,
  payload_sha256 TEXT NOT NULL
);

CREATE TABLE genre_ontology_genre (
  genre_id TEXT NOT NULL PRIMARY KEY,
  canonical_name TEXT NOT NULL,
  disambiguation TEXT NOT NULL DEFAULT ''
);

CREATE TABLE genre_ontology_alias (
  genre_id TEXT NOT NULL REFERENCES genre_ontology_genre(genre_id) ON DELETE CASCADE,
  alias_name TEXT NOT NULL,
  alias_source TEXT NOT NULL,
  PRIMARY KEY(genre_id, alias_name)
);

CREATE TABLE genre_ontology_relation (
  relation_type TEXT NOT NULL,
  source_genre_id TEXT NOT NULL REFERENCES genre_ontology_genre(genre_id) ON DELETE CASCADE,
  target_genre_id TEXT NOT NULL REFERENCES genre_ontology_genre(genre_id) ON DELETE CASCADE,
  PRIMARY KEY(relation_type, source_genre_id, target_genre_id)
);

CREATE INDEX genre_ontology_genre_name
ON genre_ontology_genre(canonical_name);

CREATE INDEX genre_ontology_alias_name
ON genre_ontology_alias(alias_name);

CREATE INDEX genre_ontology_relation_target
ON genre_ontology_relation(relation_type, target_genre_id, source_genre_id);

CREATE TABLE library_genre_inventory_metadata (
  source_id TEXT NOT NULL PRIMARY KEY REFERENCES media_source(id) ON DELETE CASCADE,
  ontology_payload_sha256 TEXT NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL
);

CREATE TABLE library_genre_inventory (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  source_name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  matched_genre_id TEXT REFERENCES genre_ontology_genre(genre_id) ON DELETE SET NULL,
  match_kind TEXT NOT NULL,
  PRIMARY KEY(source_id, normalized_name)
);

CREATE INDEX library_genre_inventory_match
ON library_genre_inventory(source_id, matched_genre_id);

CREATE TABLE library_genre_inventory_count (
  source_id TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  album_count INTEGER,
  track_count INTEGER,
  PRIMARY KEY(source_id, normalized_name),
  FOREIGN KEY(source_id, normalized_name)
    REFERENCES library_genre_inventory(source_id, normalized_name) ON DELETE CASCADE
);

CREATE TABLE artist_popular_track (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_artist_id TEXT NOT NULL,
  popular_source TEXT NOT NULL,
  source_track_id TEXT NOT NULL,
  rank INTEGER NOT NULL,
  title TEXT NOT NULL,
  album_title TEXT,
  duration_seconds INTEGER,
  matched_remote_track_id TEXT,
  fetched_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_artist_id, popular_source, source_track_id)
);

CREATE TABLE cached_audio (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  quality_key TEXT NOT NULL,
  file_path TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  content_type TEXT,
  created_at_epoch_millis INTEGER NOT NULL,
  last_accessed_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id, quality_key)
);

CREATE TABLE downloaded_audio (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  quality_key TEXT NOT NULL,
  file_path TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  content_type TEXT,
  title TEXT NOT NULL,
  artist_id TEXT,
  artist_name TEXT NOT NULL,
  album_id TEXT,
  album_title TEXT,
  album_release_year INTEGER,
  duration_seconds INTEGER,
  cover_art_id TEXT,
  audio_codec TEXT,
  audio_bitrate_kbps INTEGER,
  audio_content_type TEXT,
  audio_bit_depth INTEGER,
  audio_sampling_rate_hz INTEGER,
  favorited_at_iso8601 TEXT,
  user_rating INTEGER,
  downloaded_at_epoch_millis INTEGER NOT NULL,
  original_release_year INTEGER,
  PRIMARY KEY(source_id, remote_track_id, quality_key)
);

CREATE TABLE keep_downloaded_collection (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  collection_kind TEXT NOT NULL,
  collection_id TEXT NOT NULL,
  name TEXT NOT NULL,
  remove_unneeded_files INTEGER NOT NULL DEFAULT 0,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, collection_kind, collection_id)
);

CREATE TABLE keep_downloaded_collection_track (
  source_id TEXT NOT NULL,
  collection_kind TEXT NOT NULL,
  collection_id TEXT NOT NULL,
  remote_track_id TEXT NOT NULL,
  PRIMARY KEY(source_id, collection_kind, collection_id, remote_track_id),
  FOREIGN KEY(source_id, collection_kind, collection_id)
    REFERENCES keep_downloaded_collection(source_id, collection_kind, collection_id)
    ON DELETE CASCADE
);

CREATE TABLE keep_downloaded_managed_track (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  PRIMARY KEY(source_id, remote_track_id)
);

CREATE TABLE cached_audio_waveform (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  quality_key TEXT NOT NULL,
  audio_file_path TEXT NOT NULL,
  bucket_count INTEGER NOT NULL,
  amplitudes_json TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  last_accessed_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id, quality_key)
);

CREATE TABLE cached_lyrics (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  lyric_source TEXT NOT NULL,
  synced INTEGER NOT NULL,
  lines_json TEXT NOT NULL,
  display_artist TEXT,
  display_title TEXT,
  language TEXT,
  offset_millis INTEGER NOT NULL,
  size_bytes INTEGER NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  last_accessed_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id)
);

CREATE TABLE cached_online_lyrics (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  online_provider_id TEXT NOT NULL,
  lyric_source TEXT NOT NULL,
  synced INTEGER NOT NULL,
  lines_json TEXT NOT NULL,
  display_artist TEXT,
  display_title TEXT,
  language TEXT,
  offset_millis INTEGER NOT NULL,
  size_bytes INTEGER NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  last_accessed_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id, online_provider_id)
);

CREATE TABLE track_lyrics_offset (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  offset_millis INTEGER NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id)
);

CREATE TABLE cached_sidecar_status (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  quality_key TEXT NOT NULL,
  sidecar_type TEXT NOT NULL,
  status TEXT NOT NULL,
  attempts INTEGER NOT NULL,
  last_error TEXT,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id, quality_key, sidecar_type)
);

CREATE TABLE playback_session (
  source_id TEXT NOT NULL PRIMARY KEY REFERENCES media_source(id) ON DELETE CASCADE,
  payload TEXT NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL
);

CREATE TABLE playback_session_state (
  source_id TEXT NOT NULL PRIMARY KEY REFERENCES media_source(id) ON DELETE CASCADE,
  current_index INTEGER NOT NULL,
  play_next_count INTEGER NOT NULL,
  position_seconds REAL,
  internet_radio_payload TEXT,
  now_playing_open INTEGER NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL,
  queue_groups_payload TEXT
);

CREATE TABLE playback_session_queue (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  queue_index INTEGER NOT NULL,
  remote_track_id TEXT NOT NULL,
  payload TEXT NOT NULL,
  PRIMARY KEY(source_id, queue_index)
);

CREATE TABLE playback_profile (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  target_type TEXT NOT NULL,
  target_id TEXT NOT NULL,
  transition_mode TEXT NOT NULL,
  crossfade_duration_seconds INTEGER,
  replay_gain_mode TEXT NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, target_type, target_id)
);

CREATE TABLE playback_history (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  title TEXT NOT NULL,
  artist_id TEXT,
  artist_name TEXT NOT NULL,
  album_id TEXT,
  album_title TEXT,
  album_release_year INTEGER,
  duration_seconds INTEGER,
  cover_art_id TEXT,
  audio_codec TEXT,
  audio_bitrate_kbps INTEGER,
  audio_content_type TEXT,
  audio_bit_depth INTEGER,
  audio_sampling_rate_hz INTEGER,
  favorited_at_iso8601 TEXT,
  user_rating INTEGER,
  played_at_epoch_millis INTEGER NOT NULL,
  original_release_year INTEGER,
  PRIMARY KEY(source_id, remote_track_id, played_at_epoch_millis)
);

CREATE TABLE pending_provider_action (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  action_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  bool_value INTEGER,
  long_value INTEGER,
  created_at_epoch_millis INTEGER NOT NULL,
  last_attempt_at_epoch_millis INTEGER,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);

CREATE TABLE radio_dj_preset (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  familiarity TEXT NOT NULL,
  artist_spread TEXT NOT NULL,
  same_decade_only INTEGER NOT NULL,
  artist_run_mode TEXT NOT NULL,
  same_artist_run_length INTEGER NOT NULL,
  other_artist_run_length INTEGER NOT NULL,
  sort_order INTEGER NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL
);

CREATE INDEX radio_dj_preset_sort
ON radio_dj_preset(sort_order, name);

CREATE INDEX library_artist_source_name
ON library_artist(source_id, search_name);

CREATE INDEX library_album_source_title
ON library_album(source_id, search_title);

CREATE INDEX library_album_source_artist
ON library_album(source_id, search_artist_name);

CREATE INDEX library_track_source_title
ON library_track(source_id, search_title);

CREATE INDEX library_track_source_artist
ON library_track(source_id, search_artist_name);

CREATE INDEX library_track_artist_credit_source_artist
ON library_track_artist_credit(source_id, remote_artist_id);

CREATE INDEX artist_popular_track_artist
ON artist_popular_track(source_id, remote_artist_id, popular_source, rank);

CREATE INDEX artist_popular_track_match
ON artist_popular_track(source_id, matched_remote_track_id);

CREATE INDEX cached_audio_source_access
ON cached_audio(source_id, last_accessed_epoch_millis);

CREATE INDEX downloaded_audio_source_downloaded_at
ON downloaded_audio(source_id, downloaded_at_epoch_millis);

CREATE INDEX keep_downloaded_collection_track_remote
ON keep_downloaded_collection_track(source_id, remote_track_id);

CREATE INDEX cached_audio_waveform_access
ON cached_audio_waveform(last_accessed_epoch_millis);

CREATE INDEX cached_lyrics_access
ON cached_lyrics(last_accessed_epoch_millis);

CREATE INDEX cached_online_lyrics_access
ON cached_online_lyrics(last_accessed_epoch_millis);

CREATE INDEX cached_sidecar_status_track
ON cached_sidecar_status(source_id, remote_track_id, quality_key);

CREATE INDEX playback_history_source_played_at
ON playback_history(source_id, played_at_epoch_millis);

CREATE INDEX pending_provider_action_source_created
ON pending_provider_action(source_id, created_at_epoch_millis);

CREATE TABLE album_catalog_snapshot (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  catalog_key TEXT NOT NULL,
  albums_json TEXT NOT NULL,
  refreshed_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, catalog_key)
);

INSERT INTO media_source(id, provider_id, cache_namespace, display_name, base_url, username, token, salt, created_at_epoch_millis)
VALUES ('source', 'navidrome', 'release-fixture', 'Release server', 'https://example.test', 'user', '', '', 1);
INSERT INTO downloaded_audio(source_id, remote_track_id, quality_key, file_path, size_bytes, title, artist_id, artist_name, album_id, album_title, downloaded_at_epoch_millis)
VALUES
 ('source', 'one', 'original', '/fixture/one.flac', 100, 'One', 'artist', 'Artist', 'album', 'Album', 2),
 ('source', 'one', 'transcoded:mp3:192', '/fixture/one.mp3', 80, 'One', 'artist', 'Artist', 'album', 'Album', 3),
 ('source', 'two', 'original', '/fixture/two.flac', 120, 'Two', 'artist', 'Artist', 'album', 'Album', 4);
INSERT INTO keep_downloaded_collection(source_id, collection_kind, collection_id, name, updated_at_epoch_millis)
VALUES ('source', 'Playlist', 'playlist', 'Playlist', 5), ('source', 'Favorites', 'favorite-tracks', 'Favorite tracks', 6);
INSERT INTO keep_downloaded_collection_track(source_id, collection_kind, collection_id, remote_track_id)
VALUES ('source', 'Playlist', 'playlist', 'one'), ('source', 'Playlist', 'playlist', 'two'),
       ('source', 'Favorites', 'favorite-tracks', 'one');
INSERT INTO keep_downloaded_managed_track(source_id, remote_track_id) VALUES ('source', 'one');
PRAGMA user_version = 27;
