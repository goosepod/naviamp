use crate::domain::SearchResults;
use slint::{ModelRc, SharedString, VecModel};

slint::include_modules!();

pub fn search_rows(results: &SearchResults) -> ModelRc<MediaRow> {
    let artist_rows = results
        .artists
        .iter()
        .enumerate()
        .map(|(index, artist)| MediaRow {
            kind: SharedString::from("artist"),
            title: SharedString::from(artist.name.as_str()),
            subtitle: SharedString::from("Artist"),
            source_index: index as i32,
        });
    let album_rows = results
        .albums
        .iter()
        .enumerate()
        .map(|(index, album)| MediaRow {
            kind: SharedString::from("album"),
            title: SharedString::from(album.title.as_str()),
            subtitle: SharedString::from(format!("Album - {}", album.subtitle())),
            source_index: index as i32,
        });
    let track_rows = results
        .tracks
        .iter()
        .enumerate()
        .map(|(index, track)| MediaRow {
            kind: SharedString::from("track"),
            title: SharedString::from(track.title.as_str()),
            subtitle: SharedString::from(format!("Track - {}", track.subtitle())),
            source_index: index as i32,
        });
    let rows = artist_rows
        .chain(album_rows)
        .chain(track_rows)
        .collect::<Vec<_>>();
    ModelRc::new(VecModel::from(rows))
}
