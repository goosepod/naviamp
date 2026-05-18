use crate::playback::PlaybackSnapshot;

pub const VISUALIZER_BAR_COUNT: usize = 16;

#[derive(Clone, Debug, PartialEq)]
pub struct VisualizerFrame {
    pub levels: [f32; VISUALIZER_BAR_COUNT],
}

impl Default for VisualizerFrame {
    fn default() -> Self {
        Self {
            levels: [0.0; VISUALIZER_BAR_COUNT],
        }
    }
}

pub trait VisualizerBackend: Send {
    fn next_frame(&mut self, snapshot: &PlaybackSnapshot) -> VisualizerFrame;
}

#[derive(Default)]
pub struct SoftwareVisualizerBackend {
    phase: f32,
    levels: [f32; VISUALIZER_BAR_COUNT],
}

impl VisualizerBackend for SoftwareVisualizerBackend {
    fn next_frame(&mut self, snapshot: &PlaybackSnapshot) -> VisualizerFrame {
        if !snapshot.is_playing {
            for level in &mut self.levels {
                *level *= 0.72;
            }
            return VisualizerFrame {
                levels: self.levels,
            };
        }

        self.phase = snapshot
            .position_seconds
            .map(|position| position as f32 * 0.85)
            .unwrap_or(self.phase + 0.35);
        for (index, level) in self.levels.iter_mut().enumerate() {
            let band = index as f32 + 1.0;
            let wave = (self.phase + band * 0.62).sin().abs();
            let accent = (self.phase * 0.43 + band * 0.27).cos().abs();
            *level = (0.12 + wave * 0.62 + accent * 0.26).clamp(0.05, 1.0);
        }

        VisualizerFrame {
            levels: self.levels,
        }
    }
}

pub fn default_visualizer_backend() -> Box<dyn VisualizerBackend> {
    Box::<SoftwareVisualizerBackend>::default()
}

#[cfg(test)]
mod tests {
    use super::{SoftwareVisualizerBackend, VisualizerBackend, VISUALIZER_BAR_COUNT};
    use crate::playback::PlaybackSnapshot;

    #[test]
    fn software_visualizer_emits_stable_bar_count() {
        let mut visualizer = SoftwareVisualizerBackend::default();
        let frame = visualizer.next_frame(&PlaybackSnapshot {
            is_playing: true,
            position_seconds: Some(4.0),
            ..PlaybackSnapshot::default()
        });

        assert_eq!(VISUALIZER_BAR_COUNT, frame.levels.len());
        assert!(frame.levels.iter().all(|level| (0.0..=1.0).contains(level)));
    }

    #[test]
    fn software_visualizer_decays_when_idle() {
        let mut visualizer = SoftwareVisualizerBackend::default();
        let playing = visualizer.next_frame(&PlaybackSnapshot {
            is_playing: true,
            ..PlaybackSnapshot::default()
        });
        let idle = visualizer.next_frame(&PlaybackSnapshot::default());

        assert!(idle
            .levels
            .iter()
            .zip(playing.levels)
            .all(|(idle, playing)| *idle < playing));
    }
}
