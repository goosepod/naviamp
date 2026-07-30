# Word-Timed Lyrics Samples

These copyright-safe fixtures use original text created for Naviamp testing. They may be copied,
modified, and redistributed with the project.

- `naviamp-karaoke-richsync.json` mirrors the useful fields in a Musixmatch rich-sync payload.
  Each line has an absolute start (`ts`), absolute end (`te`), full text (`x`), and fragments (`l`).
  Fragment offsets (`o`) are relative to the line start.
- `naviamp-karaoke-enhanced.lrc` represents the same timing with absolute inline timestamps. A
  timestamp after the final fragment records the line's explicit end, which is important for a
  sustained final word.
- `naviamp-karaoke.ttml` represents the same timing with explicit begin/end intervals on every
  fragment.

The standalone `Oh` begins at 8.2 seconds and ends at 12.4 seconds. A renderer should gradually
highlight that single fragment for the full 4.2 seconds rather than filling it immediately.
