# Genre ontology importer

This release-time tool imports the CC0 MusicBrainz genre list, aliases, and genre-to-genre
relationships, caches every network response, audits the graph, and generates Naviamp's bundled
common Kotlin snapshot. Relationship concepts are anchored to MusicBrainz's stable relationship
type UUIDs. Each release import re-reads the live phrases for those UUIDs and fails if a definition
or expected relationship type disappears.

Run from the repository root:

```powershell
.\gradlew.bat :tools:genre-ontology:run --args="--snapshot YYYY-MM-DD"
```

The MusicBrainz web service is rate-limited. The default delay intentionally makes an uncached full
run take roughly 80 minutes because MusicBrainz aliases require one entity lookup per genre.
Subsequent runs reuse `.tmp/genre-ontology/musicbrainz` and only require
deleting cached pages whose records should be refreshed. Do not lower the delay for public release
runs without permission from MetaBrainz.

`--snapshot` is an explicit Naviamp release label, not a claim that every cached page was fetched on
that date. A sorted SHA-256 manifest of every cached input is written to
`.tmp/genre-ontology/input-manifest.sha256`; its checksum is embedded in the generated payload along
with the CC0 license URL, ingestion method, and relationship type UUIDs. Archive the input cache and
manifest with release evidence when regenerating the bundled snapshot.

For a quick parser/audit smoke test without replacing the bundled snapshot:

```powershell
.\gradlew.bat :tools:genre-ontology:run --args="--max-pages 5"
```

Run importer unit tests with:

```powershell
.\gradlew.bat :tools:genre-ontology:test
```

## Audit real libraries before enabling the tree

Export each representative server's genre inventory as JSON:

```json
{"source":"Living room Navidrome","genres":[{"name":"Dream-Pop","albumCount":12,"trackCount":345}]}
```

Then compare any number of inventories with the generated ontology:

```powershell
.\gradlew.bat :tools:genre-ontology:auditLibraries --args="--ontology .tmp/genre-ontology/ontology.json --library server-one.json --library server-two.json --library server-three.json --output .tmp/genre-ontology/library-audit.md"
```

The tool can also audit every source in an existing Naviamp database without exporting credentials
or connection details. It reads only `source_id` and indexed `genre_names`, and anonymizes source
labels in the report:

```powershell
.\gradlew.bat :tools:genre-ontology:auditLibraries --args="--ontology-kotlin core/storage/src/commonMain/kotlin/app/naviamp/storage/GeneratedGenreOntologySnapshot.kt --database /path/to/naviamp-storage.db --output .tmp/genre-ontology/library-audit.md"
```

The report includes name and track-weighted match coverage, initial root choices, largest selectable
group, and the same shared Tree/Flat decision used by the app. A tree is shown only when at least
four genres are present, weighted coverage is at least 60%, a parent groups at least two selectable
server genres, and the initial hierarchy reduces the choice set by at least 20%.
