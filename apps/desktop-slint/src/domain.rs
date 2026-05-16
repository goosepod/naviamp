#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Track {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
}

impl Track {
    pub fn subtitle(&self) -> String {
        format!(
            "{} - {}",
            display_or_dash(&self.artist),
            display_or_dash(&self.album)
        )
    }
}

fn display_or_dash(value: &str) -> &str {
    if value.trim().is_empty() {
        "-"
    } else {
        value
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn subtitle_uses_dash_for_missing_values() {
        let track = Track {
            id: "1".into(),
            title: "Song".into(),
            artist: String::new(),
            album: String::new(),
        };

        assert_eq!("- - -", track.subtitle());
    }
}
