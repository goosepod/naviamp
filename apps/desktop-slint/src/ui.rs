use crate::domain::{AlbumDetail, ArtistDetail, InternetRadioStation, SearchResults, Track};
use crate::queue::TrackQueue;
use crate::settings::Settings;
use crate::visualizer::VisualizerFrame;
use slint::{ModelRc, SharedString, VecModel};

slint::include_modules!();

pub fn search_rows(results: &SearchResults) -> ModelRc<MediaRow> {
    let mut rows = Vec::new();

    if !results.artists.is_empty() {
        rows.push(header_row("Artists", results.artists.len()));
    }
    rows.extend(
        results
            .artists
            .iter()
            .enumerate()
            .map(|(index, artist)| MediaRow {
                kind: SharedString::from("artist"),
                title: SharedString::from(artist.name.as_str()),
                subtitle: SharedString::from("Artist"),
                source_index: index as i32,
                is_header: false,
                action_label: SharedString::from("..."),
            }),
    );

    if !results.albums.is_empty() {
        rows.push(header_row("Albums", results.albums.len()));
    }
    rows.extend(
        results
            .albums
            .iter()
            .enumerate()
            .map(|(index, album)| MediaRow {
                kind: SharedString::from("album"),
                title: SharedString::from(album.title.as_str()),
                subtitle: SharedString::from(format!("Album - {}", album.subtitle())),
                source_index: index as i32,
                is_header: false,
                action_label: SharedString::from("..."),
            }),
    );

    if !results.tracks.is_empty() {
        rows.push(header_row("Tracks", results.tracks.len()));
    }
    rows.extend(results.tracks.iter().enumerate().map(track_row));
    ModelRc::new(VecModel::from(rows))
}

pub fn album_detail_rows(detail: &AlbumDetail) -> ModelRc<MediaRow> {
    let mut rows = vec![header_row(
        format!("{} tracks on {}", detail.tracks.len(), detail.album.title),
        detail.tracks.len(),
    )];
    rows.extend(detail.tracks.iter().enumerate().map(track_row));
    ModelRc::new(VecModel::from(rows))
}

pub fn artist_detail_rows(detail: &ArtistDetail) -> ModelRc<MediaRow> {
    let mut rows = vec![header_row(
        format!("{} albums by {}", detail.albums.len(), detail.artist.name),
        detail.albums.len(),
    )];
    rows.extend(
        detail
            .albums
            .iter()
            .enumerate()
            .map(|(index, album)| MediaRow {
                kind: SharedString::from("album"),
                title: SharedString::from(album.title.as_str()),
                subtitle: SharedString::from(format!("Album - {}", album.subtitle())),
                source_index: index as i32,
                is_header: false,
                action_label: SharedString::from("..."),
            }),
    );
    ModelRc::new(VecModel::from(rows))
}

fn track_row((index, track): (usize, &Track)) -> MediaRow {
    MediaRow {
        kind: SharedString::from("track"),
        title: SharedString::from(track.title.as_str()),
        subtitle: SharedString::from(format!("Track - {}", track_metadata_line(track))),
        source_index: index as i32,
        is_header: false,
        action_label: SharedString::from(if track.favorited_at.is_some() {
            "Unfavorite"
        } else {
            "Favorite"
        }),
    }
}

fn track_metadata_line(track: &Track) -> String {
    let favorite = track.favorited_at.as_ref().map(|_| "favorite");
    let rating = track.user_rating.map(|rating| match rating {
        1 => "1 star".to_string(),
        value => format!("{value} stars"),
    });
    [Some(track.subtitle()), favorite.map(str::to_string), rating]
        .into_iter()
        .flatten()
        .collect::<Vec<_>>()
        .join(" - ")
}

fn header_row(label: impl Into<String>, count: usize) -> MediaRow {
    MediaRow {
        kind: SharedString::from("header"),
        title: SharedString::from(format!("{} ({count})", label.into())),
        subtitle: SharedString::new(),
        source_index: -1,
        is_header: true,
        action_label: SharedString::new(),
    }
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
            is_header: false,
            action_label: SharedString::from("..."),
        })
        .collect::<Vec<_>>();
    ModelRc::new(VecModel::from(rows))
}

pub fn back_to_rows(queue: &TrackQueue) -> ModelRc<MediaRow> {
    queue_track_rows(queue.back_to().iter().enumerate())
}

pub fn up_next_rows(queue: &TrackQueue) -> ModelRc<MediaRow> {
    let offset = queue.back_to().len() + usize::from(queue.current().is_some());
    queue_track_rows(
        queue
            .up_next()
            .iter()
            .enumerate()
            .map(|(index, track)| (offset + index, track)),
    )
}

fn queue_track_rows<'a>(tracks: impl Iterator<Item = (usize, &'a Track)>) -> ModelRc<MediaRow> {
    let rows = tracks
        .map(|(index, track)| MediaRow {
            kind: SharedString::from("queue-track"),
            title: SharedString::from(track.title.as_str()),
            subtitle: SharedString::from(track_metadata_line(track)),
            source_index: index as i32,
            is_header: false,
            action_label: SharedString::new(),
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

pub fn visualizer_levels(frame: &VisualizerFrame) -> ModelRc<f32> {
    ModelRc::new(VecModel::from(frame.levels.to_vec()))
}
