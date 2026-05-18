use crate::domain::{InternetRadioStation, SearchResults};
use crate::settings::Settings;
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

pub fn radio_rows(stations: &[InternetRadioStation]) -> ModelRc<MediaRow> {
    let rows = stations
        .iter()
        .enumerate()
        .map(|(index, station)| MediaRow {
            kind: SharedString::from("radio"),
            title: SharedString::from(station.name.as_str()),
            subtitle: SharedString::from(
                station
                    .home_page_url
                    .as_deref()
                    .filter(|value| !value.is_empty())
                    .unwrap_or("Internet radio"),
            ),
            source_index: index as i32,
        })
        .collect::<Vec<_>>();
    ModelRc::new(VecModel::from(rows))
}

pub fn source_rows(settings: &Settings) -> ModelRc<SourceRow> {
    let active_source_id = settings.active_source_id.as_deref();
    let rows = settings
        .sources
        .iter()
        .enumerate()
        .map(|(index, source)| SourceRow {
            title: SharedString::from(source.display_name.as_str()),
            subtitle: SharedString::from(if active_source_id == Some(source.id.as_str()) {
                "Active"
            } else {
                "Saved"
            }),
            source_index: index as i32,
        })
        .collect::<Vec<_>>();
    ModelRc::new(VecModel::from(rows))
}
