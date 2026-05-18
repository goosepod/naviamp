use crate::domain::Track;

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct TrackQueue {
    tracks: Vec<Track>,
    current_index: Option<usize>,
}

impl TrackQueue {
    pub fn play_from_tracks(tracks: Vec<Track>, index: usize) -> Self {
        let current_index = (index < tracks.len()).then_some(index);
        Self {
            tracks,
            current_index,
        }
    }

    pub fn current(&self) -> Option<&Track> {
        self.current_index.and_then(|index| self.tracks.get(index))
    }

    pub fn next(&mut self) -> Option<&Track> {
        let next_index = self.current_index? + 1;
        if next_index >= self.tracks.len() {
            return None;
        }
        self.current_index = Some(next_index);
        self.current()
    }

    pub fn previous(&mut self) -> Option<&Track> {
        let current_index = self.current_index?;
        let previous_index = current_index.checked_sub(1)?;
        self.current_index = Some(previous_index);
        self.current()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn track(id: &str) -> Track {
        Track {
            id: id.to_string(),
            title: format!("Track {id}"),
            artist: "Artist".to_string(),
            album: "Album".to_string(),
            cover_art_id: None,
        }
    }

    #[test]
    fn plays_from_selected_index() {
        let queue = TrackQueue::play_from_tracks(vec![track("1"), track("2"), track("3")], 1);

        assert_eq!("2", queue.current().unwrap().id);
    }

    #[test]
    fn invalid_start_index_creates_empty_selection() {
        let queue = TrackQueue::play_from_tracks(vec![track("1")], 4);

        assert!(queue.current().is_none());
    }

    #[test]
    fn advances_to_next_track() {
        let mut queue = TrackQueue::play_from_tracks(vec![track("1"), track("2")], 0);

        assert_eq!("2", queue.next().unwrap().id);
        assert!(queue.next().is_none());
        assert_eq!("2", queue.current().unwrap().id);
    }

    #[test]
    fn moves_to_previous_track() {
        let mut queue = TrackQueue::play_from_tracks(vec![track("1"), track("2")], 1);

        assert_eq!("1", queue.previous().unwrap().id);
        assert!(queue.previous().is_none());
        assert_eq!("1", queue.current().unwrap().id);
    }
}
