use crate::domain::{Album, Artist, Track};
use crate::storage::StoragePaths;
use anyhow::Result;
use rusqlite::{params, Connection, OptionalExtension};
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone)]
pub struct LibraryStore {
    database_path: PathBuf,
}

#[derive(Clone, Debug, Default)]
pub struct LibrarySnapshot {
    pub artists: Vec<Artist>,
    pub albums: Vec<Album>,
    pub tracks: Vec<Track>,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct LibraryStats {
    pub artist_count: usize,
    pub album_count: usize,
    pub track_count: usize,
}

impl LibraryStore {
    pub fn from_storage_paths(paths: StoragePaths) -> Result<Self> {
        let store = Self {
            database_path: paths.library_database(),
        };
        store.migrate()?;
        Ok(store)
    }

    pub fn clear_source(&self, source_id: &str) -> Result<()> {
        let connection = self.connection()?;
        connection.execute(
            "DELETE FROM library_track WHERE source_id = ?1",
            params![source_id],
        )?;
        connection.execute(
            "DELETE FROM library_album WHERE source_id = ?1",
            params![source_id],
        )?;
        connection.execute(
            "DELETE FROM library_artist WHERE source_id = ?1",
            params![source_id],
        )?;
        Ok(())
    }

    pub fn replace_source(
        &self,
        source_id: &str,
        artists: &[Artist],
        albums: &[Album],
        tracks: &[Track],
    ) -> Result<()> {
        let mut connection = self.connection()?;
        let transaction = connection.transaction()?;
        transaction.execute(
            "DELETE FROM library_track WHERE source_id = ?1",
            params![source_id],
        )?;
        transaction.execute(
            "DELETE FROM library_album WHERE source_id = ?1",
            params![source_id],
        )?;
        transaction.execute(
            "DELETE FROM library_artist WHERE source_id = ?1",
            params![source_id],
        )?;

        let now = now_millis();
        for artist in artists {
            transaction.execute(
                "INSERT OR REPLACE INTO library_artist(
                    source_id, remote_artist_id, name, search_name, updated_at_epoch_millis
                ) VALUES (?1, ?2, ?3, ?4, ?5)",
                params![
                    source_id,
                    artist.id,
                    artist.name,
                    search_text(&artist.name),
                    now
                ],
            )?;
        }
        for album in albums {
            transaction.execute(
                "INSERT OR REPLACE INTO library_album(
                    source_id, remote_album_id, remote_artist_id, title, artist_name,
                    search_title, search_artist_name, cover_art_id, release_year,
                    updated_at_epoch_millis
                ) VALUES (?1, ?2, NULL, ?3, ?4, ?5, ?6, NULL, NULL, ?7)",
                params![
                    source_id,
                    album.id,
                    album.title,
                    album.artist,
                    search_text(&album.title),
                    search_text(&album.artist),
                    now
                ],
            )?;
        }
        for track in tracks {
            let bit_rate_kbps = track.bit_rate_kbps.map(i64::from);
            let user_rating = track.user_rating.map(i64::from);
            transaction.execute(
                "INSERT OR REPLACE INTO library_track(
                    source_id, remote_track_id, remote_album_id, remote_artist_id,
                    title, artist_name, album_title, search_title, search_artist_name,
                    search_album_title, duration_seconds, cover_art_id, audio_codec,
                    audio_bitrate_kbps, audio_content_type, audio_bit_depth,
                    audio_sampling_rate_hz, favorited_at_iso8601, user_rating,
                    updated_at_epoch_millis
                ) VALUES (?1, ?2, NULL, NULL, ?3, ?4, ?5, ?6, ?7, ?8, NULL, ?9, ?10, ?11, NULL, NULL, NULL, ?12, ?13, ?14)",
                params![
                    source_id,
                    track.id,
                    track.title,
                    track.artist,
                    track.album,
                    search_text(&track.title),
                    search_text(&track.artist),
                    search_text(&track.album),
                    track.cover_art_id,
                    track.codec,
                    bit_rate_kbps,
                    track.favorited_at,
                    user_rating,
                    now
                ],
            )?;
        }
        transaction.commit()?;
        Ok(())
    }

    pub fn snapshot(&self, source_id: &str, limit: usize) -> Result<LibrarySnapshot> {
        self.query(source_id, None, limit)
    }

    pub fn search(&self, source_id: &str, query: &str, limit: usize) -> Result<LibrarySnapshot> {
        let query = query.trim();
        if query.is_empty() {
            self.snapshot(source_id, limit)
        } else {
            self.query(source_id, Some(query), limit)
        }
    }

    pub fn stats(&self, source_id: &str) -> Result<LibraryStats> {
        let connection = self.connection()?;
        Ok(LibraryStats {
            artist_count: count_table(&connection, "library_artist", source_id)?,
            album_count: count_table(&connection, "library_album", source_id)?,
            track_count: count_table(&connection, "library_track", source_id)?,
        })
    }

    fn query(&self, source_id: &str, query: Option<&str>, limit: usize) -> Result<LibrarySnapshot> {
        let connection = self.connection()?;
        let limit = i64::try_from(limit).unwrap_or(100);
        let filter = query.map(search_filter);

        let artists = match filter.as_ref() {
            Some(SearchFilter::Like(like)) => {
                let mut statement = connection.prepare(
                    "SELECT remote_artist_id, name
                     FROM library_artist
                     WHERE source_id = ?1 AND search_name LIKE ?2
                     ORDER BY search_name
                     LIMIT ?3",
                )?;
                let rows = statement
                    .query_map(params![source_id, like, limit], artist_from_row)?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                rows
            }
            Some(SearchFilter::DigitPrefix) => {
                let mut statement = connection.prepare(
                    "SELECT remote_artist_id, name
                     FROM library_artist
                     WHERE source_id = ?1 AND substr(search_name, 1, 1) BETWEEN '0' AND '9'
                     ORDER BY search_name
                     LIMIT ?2",
                )?;
                let rows = statement
                    .query_map(params![source_id, limit], artist_from_row)?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                rows
            }
            Some(SearchFilter::SymbolPrefix) => {
                let mut statement = connection.prepare(
                    "SELECT remote_artist_id, name
                     FROM library_artist
                     WHERE source_id = ?1
                       AND search_name != ''
                       AND NOT (
                           substr(search_name, 1, 1) BETWEEN 'a' AND 'z'
                           OR substr(search_name, 1, 1) BETWEEN '0' AND '9'
                       )
                     ORDER BY search_name
                     LIMIT ?2",
                )?;
                let rows = statement
                    .query_map(params![source_id, limit], artist_from_row)?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                rows
            }
            None => {
                let mut statement = connection.prepare(
                    "SELECT remote_artist_id, name
                     FROM library_artist
                     WHERE source_id = ?1
                     ORDER BY search_name
                     LIMIT ?2",
                )?;
                let rows = statement
                    .query_map(params![source_id, limit], artist_from_row)?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                rows
            }
        };

        let albums = if let Some(SearchFilter::Like(like)) = filter.as_ref() {
            let mut statement = connection.prepare(
                "SELECT remote_album_id, title, artist_name
                 FROM library_album
                 WHERE source_id = ?1 AND (search_title LIKE ?2 OR search_artist_name LIKE ?2)
                 ORDER BY search_artist_name, search_title
                 LIMIT ?3",
            )?;
            let rows = statement
                .query_map(params![source_id, like, limit], |row| {
                    Ok(Album {
                        id: row.get(0)?,
                        title: row.get(1)?,
                        artist: row.get(2)?,
                    })
                })?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        } else if matches!(filter, Some(SearchFilter::DigitPrefix)) {
            let mut statement = connection.prepare(
                "SELECT remote_album_id, title, artist_name
                 FROM library_album
                 WHERE source_id = ?1
                   AND (
                       substr(search_title, 1, 1) BETWEEN '0' AND '9'
                       OR substr(search_artist_name, 1, 1) BETWEEN '0' AND '9'
                   )
                 ORDER BY search_artist_name, search_title
                 LIMIT ?2",
            )?;
            let rows = statement
                .query_map(params![source_id, limit], |row| {
                    Ok(Album {
                        id: row.get(0)?,
                        title: row.get(1)?,
                        artist: row.get(2)?,
                    })
                })?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        } else if matches!(filter, Some(SearchFilter::SymbolPrefix)) {
            let mut statement = connection.prepare(
                "SELECT remote_album_id, title, artist_name
                 FROM library_album
                 WHERE source_id = ?1
                   AND (
                       (search_title != ''
                        AND NOT (
                            substr(search_title, 1, 1) BETWEEN 'a' AND 'z'
                            OR substr(search_title, 1, 1) BETWEEN '0' AND '9'
                        ))
                       OR (search_artist_name != ''
                           AND NOT (
                               substr(search_artist_name, 1, 1) BETWEEN 'a' AND 'z'
                               OR substr(search_artist_name, 1, 1) BETWEEN '0' AND '9'
                           ))
                   )
                 ORDER BY search_artist_name, search_title
                 LIMIT ?2",
            )?;
            let rows = statement
                .query_map(params![source_id, limit], |row| {
                    Ok(Album {
                        id: row.get(0)?,
                        title: row.get(1)?,
                        artist: row.get(2)?,
                    })
                })?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        } else {
            let mut statement = connection.prepare(
                "SELECT remote_album_id, title, artist_name
                 FROM library_album
                 WHERE source_id = ?1
                 ORDER BY search_artist_name, search_title
                 LIMIT ?2",
            )?;
            let rows = statement
                .query_map(params![source_id, limit], |row| {
                    Ok(Album {
                        id: row.get(0)?,
                        title: row.get(1)?,
                        artist: row.get(2)?,
                    })
                })?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        };

        let tracks = if let Some(SearchFilter::Like(like)) = filter.as_ref() {
            let mut statement = connection.prepare(
                "SELECT library_track.remote_track_id, library_track.title,
                        library_track.artist_name, library_track.album_title,
                        library_track.cover_art_id, library_track.favorited_at_iso8601,
                        library_track.user_rating, library_album.release_year,
                        library_track.audio_bitrate_kbps, library_track.audio_codec
                 FROM library_track
                 LEFT JOIN library_album
                   ON library_album.source_id = library_track.source_id
                  AND library_album.title = library_track.album_title
                 WHERE library_track.source_id = ?1
                   AND (library_track.search_title LIKE ?2
                        OR library_track.search_artist_name LIKE ?2
                        OR library_track.search_album_title LIKE ?2)
                 ORDER BY library_track.search_artist_name,
                          library_track.search_album_title,
                          library_track.search_title
                 LIMIT ?3",
            )?;
            let rows = statement
                .query_map(params![source_id, like, limit], track_from_row)?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        } else if matches!(filter, Some(SearchFilter::DigitPrefix)) {
            let mut statement = connection.prepare(
                "SELECT library_track.remote_track_id, library_track.title,
                        library_track.artist_name, library_track.album_title,
                        library_track.cover_art_id, library_track.favorited_at_iso8601,
                        library_track.user_rating, library_album.release_year,
                        library_track.audio_bitrate_kbps, library_track.audio_codec
                 FROM library_track
                 LEFT JOIN library_album
                   ON library_album.source_id = library_track.source_id
                  AND library_album.title = library_track.album_title
                 WHERE library_track.source_id = ?1
                   AND (
                       substr(library_track.search_title, 1, 1) BETWEEN '0' AND '9'
                       OR substr(library_track.search_artist_name, 1, 1) BETWEEN '0' AND '9'
                       OR substr(library_track.search_album_title, 1, 1) BETWEEN '0' AND '9'
                   )
                 ORDER BY library_track.search_artist_name,
                          library_track.search_album_title,
                          library_track.search_title
                 LIMIT ?2",
            )?;
            let rows = statement
                .query_map(params![source_id, limit], track_from_row)?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        } else if matches!(filter, Some(SearchFilter::SymbolPrefix)) {
            let mut statement = connection.prepare(
                "SELECT library_track.remote_track_id, library_track.title,
                        library_track.artist_name, library_track.album_title,
                        library_track.cover_art_id, library_track.favorited_at_iso8601,
                        library_track.user_rating, library_album.release_year,
                        library_track.audio_bitrate_kbps, library_track.audio_codec
                 FROM library_track
                 LEFT JOIN library_album
                   ON library_album.source_id = library_track.source_id
                  AND library_album.title = library_track.album_title
                 WHERE library_track.source_id = ?1
                   AND (
                       (library_track.search_title != ''
                        AND NOT (
                            substr(library_track.search_title, 1, 1) BETWEEN 'a' AND 'z'
                            OR substr(library_track.search_title, 1, 1) BETWEEN '0' AND '9'
                        ))
                       OR (library_track.search_artist_name != ''
                           AND NOT (
                               substr(library_track.search_artist_name, 1, 1) BETWEEN 'a' AND 'z'
                               OR substr(library_track.search_artist_name, 1, 1) BETWEEN '0' AND '9'
                           ))
                       OR (library_track.search_album_title != ''
                           AND NOT (
                               substr(library_track.search_album_title, 1, 1) BETWEEN 'a' AND 'z'
                               OR substr(library_track.search_album_title, 1, 1) BETWEEN '0' AND '9'
                           ))
                   )
                 ORDER BY library_track.search_artist_name,
                          library_track.search_album_title,
                          library_track.search_title
                 LIMIT ?2",
            )?;
            let rows = statement
                .query_map(params![source_id, limit], track_from_row)?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        } else {
            let mut statement = connection.prepare(
                "SELECT library_track.remote_track_id, library_track.title,
                        library_track.artist_name, library_track.album_title,
                        library_track.cover_art_id, library_track.favorited_at_iso8601,
                        library_track.user_rating, library_album.release_year,
                        library_track.audio_bitrate_kbps, library_track.audio_codec
                 FROM library_track
                 LEFT JOIN library_album
                   ON library_album.source_id = library_track.source_id
                  AND library_album.title = library_track.album_title
                 WHERE library_track.source_id = ?1
                 ORDER BY library_track.search_artist_name,
                          library_track.search_album_title,
                          library_track.search_title
                 LIMIT ?2",
            )?;
            let rows = statement
                .query_map(params![source_id, limit], track_from_row)?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            rows
        };

        Ok(LibrarySnapshot {
            artists,
            albums,
            tracks,
        })
    }

    fn connection(&self) -> Result<Connection> {
        if let Some(parent) = self.database_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let connection = Connection::open(&self.database_path)?;
        connection.pragma_update(None, "foreign_keys", "ON")?;
        Ok(connection)
    }

    fn migrate(&self) -> Result<()> {
        let connection = self.connection()?;
        connection.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS library_artist (
              source_id TEXT NOT NULL,
              remote_artist_id TEXT NOT NULL,
              name TEXT NOT NULL,
              search_name TEXT NOT NULL,
              updated_at_epoch_millis INTEGER NOT NULL,
              PRIMARY KEY(source_id, remote_artist_id)
            );

            CREATE TABLE IF NOT EXISTS library_album (
              source_id TEXT NOT NULL,
              remote_album_id TEXT NOT NULL,
              remote_artist_id TEXT,
              title TEXT NOT NULL,
              artist_name TEXT NOT NULL,
              search_title TEXT NOT NULL,
              search_artist_name TEXT NOT NULL,
              cover_art_id TEXT,
              release_year INTEGER,
              updated_at_epoch_millis INTEGER NOT NULL,
              PRIMARY KEY(source_id, remote_album_id)
            );

            CREATE TABLE IF NOT EXISTS library_track (
              source_id TEXT NOT NULL,
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
              PRIMARY KEY(source_id, remote_track_id)
            );

            CREATE INDEX IF NOT EXISTS library_artist_source_name
            ON library_artist(source_id, search_name);
            CREATE INDEX IF NOT EXISTS library_album_source_title
            ON library_album(source_id, search_title);
            CREATE INDEX IF NOT EXISTS library_album_source_artist
            ON library_album(source_id, search_artist_name);
            CREATE INDEX IF NOT EXISTS library_track_source_title
            ON library_track(source_id, search_title);
            CREATE INDEX IF NOT EXISTS library_track_source_artist
            ON library_track(source_id, search_artist_name);
            ",
        )?;
        Ok(())
    }
}

pub fn default_library_store() -> Result<LibraryStore> {
    let paths = StoragePaths::new()?;
    paths.ensure_base_dirs()?;
    LibraryStore::from_storage_paths(paths)
}

fn artist_from_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<Artist> {
    Ok(Artist {
        id: row.get(0)?,
        name: row.get(1)?,
    })
}

fn track_from_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<Track> {
    let year = row
        .get::<_, Option<i64>>(7)?
        .and_then(|value| u32::try_from(value).ok());
    let bit_rate_kbps = row
        .get::<_, Option<i64>>(8)?
        .and_then(|value| u32::try_from(value).ok());
    let user_rating = row
        .get::<_, Option<i64>>(6)?
        .and_then(|value| u8::try_from(value).ok());
    Ok(Track {
        id: row.get(0)?,
        title: row.get(1)?,
        artist: row.get(2)?,
        album: row.get::<_, Option<String>>(3)?.unwrap_or_default(),
        cover_art_id: row.get(4)?,
        favorited_at: row.get(5)?,
        user_rating,
        year,
        bit_rate_kbps,
        codec: row.get(9)?,
    })
}

fn count_table(connection: &Connection, table: &str, source_id: &str) -> Result<usize> {
    let sql = format!("SELECT COUNT(*) FROM {table} WHERE source_id = ?1");
    let count: i64 = connection
        .query_row(&sql, params![source_id], |row| row.get(0))
        .optional()?
        .unwrap_or_default();
    Ok(usize::try_from(count).unwrap_or_default())
}

fn search_text(value: &str) -> String {
    value.trim().to_ascii_lowercase()
}

#[derive(Clone, Debug, Eq, PartialEq)]
enum SearchFilter {
    Like(String),
    DigitPrefix,
    SymbolPrefix,
}

fn search_filter(value: &str) -> SearchFilter {
    let value = search_text(value);
    if value == "0-9" {
        SearchFilter::DigitPrefix
    } else if value == "#" {
        SearchFilter::SymbolPrefix
    } else if value.len() == 1 && value.as_bytes()[0].is_ascii_alphabetic() {
        SearchFilter::Like(format!("{value}%"))
    } else {
        SearchFilter::Like(format!("%{value}%"))
    }
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| i64::try_from(duration.as_millis()).unwrap_or(i64::MAX))
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn search_text_normalizes_case_and_edges() {
        assert_eq!("the cure", search_text("  The Cure "));
    }

    #[test]
    fn search_filter_uses_prefix_for_quick_filters() {
        assert_eq!(SearchFilter::Like("g%".to_string()), search_filter("g"));
        assert_eq!(SearchFilter::Like("%ga%".to_string()), search_filter("ga"));
        assert_eq!(SearchFilter::DigitPrefix, search_filter("0-9"));
        assert_eq!(SearchFilter::SymbolPrefix, search_filter("#"));
    }
}
