# Genre ontology importer

This release-time tool imports the CC0 MusicBrainz genre list, aliases, and genre-to-genre relationships,
caches every network response, audits the graph, and generates Naviamp's bundled common Kotlin
snapshot.

Run from the repository root:

```powershell
.\gradlew.bat :tools:genre-ontology:run --args="--snapshot YYYY-MM-DD"
```

The MusicBrainz web service is rate-limited. The default delay intentionally makes an uncached full
run take roughly 80 minutes because MusicBrainz aliases require one entity lookup per genre.
Subsequent runs reuse `.tmp/genre-ontology/musicbrainz` and only require
deleting cached pages whose records should be refreshed. Do not lower the delay for public release
runs without permission from MetaBrainz.

For a quick parser/audit smoke test without replacing the bundled snapshot:

```powershell
.\gradlew.bat :tools:genre-ontology:run --args="--max-pages 5"
```

Run importer unit tests with:

```powershell
.\gradlew.bat :tools:genre-ontology:test
```
