use crate::domain::Track;
use slint::{ModelRc, SharedString, VecModel};

slint::include_modules!();

pub fn track_rows(tracks: &[Track]) -> ModelRc<TrackRow> {
    let rows = tracks
        .iter()
        .map(|track| TrackRow {
            title: SharedString::from(track.title.as_str()),
            subtitle: SharedString::from(track.subtitle()),
        })
        .collect::<Vec<_>>();
    ModelRc::new(VecModel::from(rows))
}
