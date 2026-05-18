use crate::domain::Track;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum RepeatMode {
    #[default]
    Off,
    Track,
    Queue,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct TrackQueue {
    tracks: Vec<Track>,
    current_index: Option<usize>,
    repeat_mode: RepeatMode,
}

impl TrackQueue {
    pub fn play_from_tracks(tracks: Vec<Track>, index: usize) -> Self {
        let current_index = (index < tracks.len()).then_some(index);
        Self {
            tracks,
            current_index,
            repeat_mode: RepeatMode::Off,
        }
    }

    pub fn current(&self) -> Option<&Track> {
        self.current_index.and_then(|index| self.tracks.get(index))
    }

    pub fn jump_to(&mut self, index: usize) -> Option<&Track> {
        if index >= self.tracks.len() {
            return None;
        }
        self.current_index = Some(index);
        self.current()
    }

    pub fn set_repeat_mode(&mut self, repeat_mode: RepeatMode) {
        self.repeat_mode = repeat_mode;
    }

    pub fn next(&mut self) -> Option<&Track> {
        if self.repeat_mode == RepeatMode::Track {
            return self.current();
        }

        let next_index = self.current_index? + 1;
        if next_index >= self.tracks.len() {
            if self.repeat_mode == RepeatMode::Queue && !self.tracks.is_empty() {
                self.current_index = Some(0);
                return self.current();
            }
            return None;
        }
        self.current_index = Some(next_index);
        self.current()
    }

    pub fn previous(&mut self) -> Option<&Track> {
        if self.repeat_mode == RepeatMode::Track {
            return self.current();
        }

        let current_index = self.current_index?;
        let Some(previous_index) = current_index.checked_sub(1) else {
            if self.repeat_mode == RepeatMode::Queue && !self.tracks.is_empty() {
                self.current_index = Some(self.tracks.len() - 1);
                return self.current();
            }
            return None;
        };
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

    #[test]
    fn jumps_to_queue_item() {
        let mut queue = TrackQueue::play_from_tracks(vec![track("1"), track("2"), track("3")], 0);

        assert_eq!("3", queue.jump_to(2).unwrap().id);
        assert!(queue.jump_to(9).is_none());
        assert_eq!("3", queue.current().unwrap().id);
    }

    #[test]
    fn repeat_track_keeps_current_track() {
        let mut queue = TrackQueue::play_from_tracks(vec![track("1"), track("2")], 0);
        queue.set_repeat_mode(RepeatMode::Track);

        assert_eq!("1", queue.next().unwrap().id);
        assert_eq!("1", queue.previous().unwrap().id);
    }

    #[test]
    fn repeat_queue_wraps_at_edges() {
        let mut queue = TrackQueue::play_from_tracks(vec![track("1"), track("2")], 1);
        queue.set_repeat_mode(RepeatMode::Queue);

        assert_eq!("1", queue.next().unwrap().id);
        assert_eq!("2", queue.previous().unwrap().id);
    }
}
