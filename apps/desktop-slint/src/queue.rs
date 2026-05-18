use crate::domain::Track;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum RepeatMode {
    #[default]
    Off,
    Track,
    Queue,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct TrackQueue {
    back_to: Vec<Track>,
    current: Option<Track>,
    up_next: Vec<Track>,
    repeat_mode: RepeatMode,
}

impl TrackQueue {
    pub fn play_from_tracks(tracks: Vec<Track>, index: usize) -> Self {
        let mut tracks = tracks;
        if index >= tracks.len() {
            return Self {
                repeat_mode: RepeatMode::Off,
                ..Self::default()
            };
        }

        let up_next = tracks.split_off(index + 1);
        let current = tracks.pop();
        Self {
            back_to: tracks,
            current,
            up_next,
            repeat_mode: RepeatMode::Off,
        }
    }

    pub fn current(&self) -> Option<&Track> {
        self.current.as_ref()
    }

    pub fn jump_to(&mut self, index: usize) -> Option<&Track> {
        let mut tracks = self.tracks();
        if index >= tracks.len() {
            return None;
        }

        let repeat_mode = self.repeat_mode;
        *self = Self::play_from_tracks(std::mem::take(&mut tracks), index);
        self.repeat_mode = repeat_mode;
        self.current()
    }

    pub fn set_repeat_mode(&mut self, repeat_mode: RepeatMode) {
        self.repeat_mode = repeat_mode;
    }

    #[allow(dead_code)]
    pub fn back_to(&self) -> &[Track] {
        &self.back_to
    }

    #[allow(dead_code)]
    pub fn up_next(&self) -> &[Track] {
        &self.up_next
    }

    fn tracks(&self) -> Vec<Track> {
        self.back_to
            .iter()
            .cloned()
            .chain(self.current.iter().cloned())
            .chain(self.up_next.iter().cloned())
            .collect()
    }

    pub fn shuffle_upcoming(&mut self) {
        let seed = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|duration| duration.as_nanos() as u64)
            .unwrap_or(0);
        self.shuffle_upcoming_with_seed(seed);
    }

    fn shuffle_upcoming_with_seed(&mut self, seed: u64) {
        if self.up_next.len() < 2 {
            return;
        }

        let mut state = seed.max(1);
        for index in (1..self.up_next.len()).rev() {
            let swap_index = next_random(&mut state) as usize % (index + 1);
            self.up_next.swap(index, swap_index);
        }
    }

    pub fn next(&mut self) -> Option<&Track> {
        if self.repeat_mode == RepeatMode::Track {
            return self.current();
        }

        if let Some(next) = self.up_next.first().cloned() {
            if let Some(current) = self.current.replace(next) {
                self.back_to.push(current);
            }
            self.up_next.remove(0);
            return self.current();
        }

        if self.repeat_mode == RepeatMode::Queue {
            let tracks = self.tracks();
            if tracks.is_empty() {
                return None;
            }
            *self = Self::play_from_tracks(tracks, 0);
            self.repeat_mode = RepeatMode::Queue;
            return self.current();
        }

        self.current.as_ref()?;
        None
    }

    pub fn previous(&mut self) -> Option<&Track> {
        if self.repeat_mode == RepeatMode::Track {
            return self.current();
        }

        if let Some(previous) = self.back_to.pop() {
            if let Some(current) = self.current.replace(previous) {
                self.up_next.insert(0, current);
            }
            return self.current();
        }

        if self.repeat_mode == RepeatMode::Queue {
            let tracks = self.tracks();
            let last_index = tracks.len().checked_sub(1)?;
            *self = Self::play_from_tracks(tracks, last_index);
            self.repeat_mode = RepeatMode::Queue;
            return self.current();
        }

        self.current.as_ref()?;
        None
    }
}

fn next_random(state: &mut u64) -> u64 {
    *state ^= *state << 13;
    *state ^= *state >> 7;
    *state ^= *state << 17;
    *state
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
        assert_eq!(vec!["1"], track_ids(queue.back_to()));
        assert_eq!(vec!["3"], track_ids(queue.up_next()));
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
        assert_eq!(vec!["1"], track_ids(queue.back_to()));
        assert!(queue.up_next().is_empty());
        assert!(queue.next().is_none());
        assert_eq!("2", queue.current().unwrap().id);
    }

    #[test]
    fn moves_to_previous_track() {
        let mut queue = TrackQueue::play_from_tracks(vec![track("1"), track("2")], 1);

        assert_eq!("1", queue.previous().unwrap().id);
        assert!(queue.back_to().is_empty());
        assert_eq!(vec!["2"], track_ids(queue.up_next()));
        assert!(queue.previous().is_none());
        assert_eq!("1", queue.current().unwrap().id);
    }

    #[test]
    fn jumps_to_queue_item() {
        let mut queue = TrackQueue::play_from_tracks(vec![track("1"), track("2"), track("3")], 0);

        assert_eq!("3", queue.jump_to(2).unwrap().id);
        assert_eq!(vec!["1", "2"], track_ids(queue.back_to()));
        assert!(queue.up_next().is_empty());
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

    #[test]
    fn shuffles_only_upcoming_tracks() {
        let mut queue = TrackQueue::play_from_tracks(
            vec![
                track("1"),
                track("2"),
                track("3"),
                track("4"),
                track("5"),
                track("6"),
            ],
            1,
        );

        queue.shuffle_upcoming_with_seed(7);

        assert_eq!("2", queue.current().unwrap().id);
        assert_eq!(vec!["1"], track_ids(queue.back_to()));
        let mut upcoming = track_ids(queue.up_next());
        upcoming.sort_unstable();
        assert_eq!(vec!["3", "4", "5", "6"], upcoming);
        assert_ne!(vec!["3", "4", "5", "6"], track_ids(queue.up_next()));
    }

    fn track_ids(tracks: &[Track]) -> Vec<&str> {
        tracks.iter().map(|track| track.id.as_str()).collect()
    }
}
