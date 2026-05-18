use std::ffi::{c_char, CStr};

pub(super) fn extract_id3_cover_art(tag: &[u8]) -> Option<Vec<u8>> {
    if tag.len() < 10 || &tag[0..3] != b"ID3" {
        return None;
    }

    let version = tag[3];
    let tag_size = synchsafe_u32(&tag[6..10])? as usize;
    let tag_end = 10usize.saturating_add(tag_size).min(tag.len());
    let mut offset = 10usize;

    while offset < tag_end {
        let frame = match version {
            2 => next_id3v22_frame(tag, offset, tag_end)?,
            3 | 4 => next_id3v23_or_24_frame(tag, offset, tag_end, version)?,
            _ => return None,
        };

        if frame.id == "APIC" || frame.id == "PIC" {
            return extract_image_from_frame(frame.data);
        }
        offset = frame.next_offset;
    }

    None
}

pub(super) fn parse_icy_stream_title(meta: &str) -> Option<String> {
    let marker = "StreamTitle='";
    let start = meta.find(marker)? + marker.len();
    let rest = &meta[start..];
    let end = rest.find("';").or_else(|| rest.find('\''))?;
    rest[..end].trim().to_string().into_non_empty()
}

pub(super) fn parse_icy_header_title(header: String) -> Option<String> {
    let (key, value) = header.split_once(':')?;
    if !matches!(
        key.trim().to_ascii_lowercase().as_str(),
        "icy-name" | "icy-description"
    ) {
        return None;
    }

    value.trim().to_string().into_non_empty()
}

struct Id3Frame<'a> {
    id: &'a str,
    data: &'a [u8],
    next_offset: usize,
}

fn next_id3v23_or_24_frame<'a>(
    tag: &'a [u8],
    offset: usize,
    tag_end: usize,
    version: u8,
) -> Option<Id3Frame<'a>> {
    if offset + 10 > tag_end || tag[offset..offset + 4].iter().all(|byte| *byte == 0) {
        return None;
    }

    let id = std::str::from_utf8(&tag[offset..offset + 4]).ok()?;
    let size = if version == 4 {
        synchsafe_u32(&tag[offset + 4..offset + 8])?
    } else {
        u32::from_be_bytes(tag[offset + 4..offset + 8].try_into().ok()?)
    } as usize;
    let data_start = offset + 10;
    let data_end = data_start.checked_add(size)?.min(tag_end);
    Some(Id3Frame {
        id,
        data: &tag[data_start..data_end],
        next_offset: data_end,
    })
}

fn next_id3v22_frame<'a>(tag: &'a [u8], offset: usize, tag_end: usize) -> Option<Id3Frame<'a>> {
    if offset + 6 > tag_end || tag[offset..offset + 3].iter().all(|byte| *byte == 0) {
        return None;
    }

    let id = std::str::from_utf8(&tag[offset..offset + 3]).ok()?;
    let size = ((tag[offset + 3] as usize) << 16)
        | ((tag[offset + 4] as usize) << 8)
        | tag[offset + 5] as usize;
    let data_start = offset + 6;
    let data_end = data_start.checked_add(size)?.min(tag_end);
    Some(Id3Frame {
        id,
        data: &tag[data_start..data_end],
        next_offset: data_end,
    })
}

fn synchsafe_u32(bytes: &[u8]) -> Option<u32> {
    if bytes.len() != 4 || bytes.iter().any(|byte| byte & 0x80 != 0) {
        return None;
    }

    Some(
        ((bytes[0] as u32) << 21)
            | ((bytes[1] as u32) << 14)
            | ((bytes[2] as u32) << 7)
            | bytes[3] as u32,
    )
}

fn extract_image_from_frame(frame: &[u8]) -> Option<Vec<u8>> {
    let start = image_magic_offset(frame)?;
    Some(frame[start..].to_vec())
}

fn image_magic_offset(bytes: &[u8]) -> Option<usize> {
    bytes.windows(4).position(|window| {
        window.starts_with(&[0xff, 0xd8, 0xff])
            || window == b"\x89PNG"
            || window == b"GIF8"
            || window == b"RIFF"
    })
}

pub(super) unsafe fn read_null_terminated_string_list(
    values: *const c_char,
    max_bytes: usize,
) -> Vec<String> {
    let mut strings = Vec::new();
    let mut offset = 0usize;
    while offset < max_bytes {
        let current = values.add(offset);
        if *current == 0 {
            break;
        }

        let value = CStr::from_ptr(current).to_string_lossy().trim().to_string();
        offset += value.len() + 1;
        if let Some(value) = value.into_non_empty() {
            strings.push(value);
        }
    }
    strings
}

pub(super) trait NonEmptyString {
    fn into_non_empty(self) -> Option<String>;
}

impl NonEmptyString for String {
    fn into_non_empty(self) -> Option<String> {
        (!self.is_empty()).then_some(self)
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn extracts_cover_art_from_id3v23_apic_frame() {
        let image = [0xff, 0xd8, 0xff, 0xdb, 1, 2, 3];
        let mut frame = Vec::new();
        frame.extend_from_slice(&[0]);
        frame.extend_from_slice(b"image/jpeg");
        frame.push(0);
        frame.push(3);
        frame.push(0);
        frame.extend_from_slice(&image);

        let mut tag = Vec::new();
        tag.extend_from_slice(b"ID3");
        tag.extend_from_slice(&[3, 0, 0]);
        let tag_size = 10 + frame.len();
        tag.extend_from_slice(&synchsafe(tag_size as u32));
        tag.extend_from_slice(b"APIC");
        tag.extend_from_slice(&(frame.len() as u32).to_be_bytes());
        tag.extend_from_slice(&[0, 0]);
        tag.extend_from_slice(&frame);

        assert_eq!(Some(image.to_vec()), super::extract_id3_cover_art(&tag));
    }

    #[test]
    fn parses_icy_stream_title() {
        assert_eq!(
            Some("Artist - Song".to_string()),
            super::parse_icy_stream_title("StreamTitle='Artist - Song';StreamUrl='';")
        );
        assert_eq!(None, super::parse_icy_stream_title("StreamUrl='';"));
    }

    #[test]
    fn parses_icy_header_title() {
        assert_eq!(
            Some("Station".to_string()),
            super::parse_icy_header_title("icy-name: Station".to_string())
        );
        assert_eq!(
            Some("Description".to_string()),
            super::parse_icy_header_title("icy-description: Description".to_string())
        );
        assert_eq!(
            None,
            super::parse_icy_header_title("content-type: audio/mpeg".to_string())
        );
    }

    fn synchsafe(value: u32) -> [u8; 4] {
        [
            ((value >> 21) & 0x7f) as u8,
            ((value >> 14) & 0x7f) as u8,
            ((value >> 7) & 0x7f) as u8,
            (value & 0x7f) as u8,
        ]
    }
}
