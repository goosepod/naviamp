# SomaFM channel logos (consolidated into #583)

The owner tested #579 and found that Synphaera, The Trip and Groove Salad Classic still lacked
logos. The original fallback guessed every channel as `somafm.com/img3/{slug}-400.png` and
inferred its ID from the display name. The official channel catalog instead publishes JPEG
logos for these channels; its Synphaera display name is also Synphaera Radio.

The shared presentation policy now uses the exact artwork filenames and channel identities
from https://somafm.com/channels.json, verified November 11, 2026. The snapshot covers all 51
currently listed channels, including JPEG/PNG and hyphenated filenames. There is no new runtime
catalog request. Known SomaFM stream/homepage paths identify renamed stations, and catalog
names/IDs resolve branded names when a station uses a proxy URL. Bitrate suffixes do not strip
digits belonging to channel IDs, such as Groove Salad 2 and SF 10-33. Unknown channels fall
through to the existing homepage favicon/generated tile rather than inventing a dead image URL.

All 51 exact logo URLs returned HTTP 200 with an image content type. The three reported logos
were also downloaded as valid JPEG responses:

- https://api.somafm.com/logos/512/synphaera512.jpg
- https://api.somafm.com/logos/512/thetrip512.jpg
- https://api.somafm.com/logos/512/gsclassic512.jpg

Local verification output is `/private/tmp/naviamp-584-logo-verification.json`. This confirms
remote image availability, not a manual painted-app acceptance result.

Shared tests cover each presentation, native bitrate/playlist paths, renamed/branded stations,
nonstandard filenames, channel-ID digits, unknown/unrelated stations and server/stream artwork
precedence. The test build also includes PR #579's blank server-artwork correction. No Android/Desktop/iOS production adapters, UI strings, settings or schema change.
