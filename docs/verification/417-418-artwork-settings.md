# Album artwork display settings

Tracking: #417 (square album corners) and #418 (filled Now Playing artwork), selected for v2.11.0.

## Square album artwork

`InterfaceSettings.squareAlbumArtwork` defaults to false and is serialized through the existing
common settings document, local value store and trusted-device settings sync. The shared shape
policy applies right-angle corners to Album and Track artwork, including placeholders. Artist
portraits remain circular; playlists and radio stations retain their own shapes. Shared artwork
and row contracts carry explicit media kinds, including the TV grid and hero components.

The control appears under Experience > Albums and in TV Display settings. All 17 maintained
string catalogs include its label and explanation. There are no schema or platform-production changes.

Validation: the settings mapping suite (14 cases), settings value-store suite (8 cases), and
selected artwork/continuity/rendered suite (8 cases) passed. After the final row-contract audit,
the shape-policy and rendered corner-toggle cases passed again, along with common Android, JVM,
iOS ARM64 and iOS Simulator ARM64 compilation. Architecture verification passed. The rendered
test checks both loaded images and placeholders, preserves artist circles, and confirms that
toggling the preference does not reload the image.

Existing owner AVDs contain valuable data and must not be treated as disposable. This work uses
shared compilation and desktop rendering; any future destructive Android acceptance needs a
separately created test AVD.
