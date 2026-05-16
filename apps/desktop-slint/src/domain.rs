#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Artist {
    pub id: String,
    pub name: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Album {
    pub id: String,
    pub title: String,
    pub artist: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AlbumDetail {
    pub album: Album,
    pub tracks: Vec<Track>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ArtistDetail {
    pub artist: Artist,
    pub albums: Vec<Album>,
}

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

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct SearchResults {
    pub artists: Vec<Artist>,
    pub albums: Vec<Album>,
    pub tracks: Vec<Track>,
}

impl Album {
    pub fn subtitle(&self) -> String {
        display_or_dash(&self.artist).to_string()
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
