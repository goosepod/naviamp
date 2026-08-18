# Genre ontology dataset research

Status: initial recommendation, 2026-08-17

## Decision

Use the MusicBrainz genre entities and genre-to-genre relationships as Naviamp's primary ontology
source. Generate a small, versioned artifact during development and bundle that artifact with the
application. Do not query an external ontology service while the user is building or playing a mix.

Use Wikidata only as a research and gap-analysis source. It should not silently override the
MusicBrainz hierarchy at runtime.

## Why MusicBrainz is the best starting point

MusicBrainz currently provides the pieces needed for the Poppy-style experience:

- Stable MBIDs and names for approximately 2,184 genre entities.
- Genre aliases for matching spelling, punctuation, and localized names.
- Explicit genre-to-genre relationship types for `subgenre`, `fusion of`, and `influenced by`.
- Approximately 1,557 explicit subgenre relationships and 3,442 total genre-to-genre
  relationships in the June 2026 statistics.
- Community maintenance and database snapshots generated twice each week.

MusicBrainz classifies genre as a primary/core entity, and relationships are included in its core
data. Core data is CC0. This is materially different from MusicBrainz's user-submitted genre/tag
associations for artists, recordings, and releases, which are supplementary data under
CC BY-NC-SA 3.0. Naviamp should import only the CC0 ontology records and must not accidentally pull
in supplementary tag-association tables.

Sources:

- [MusicBrainz genre list](https://musicbrainz.org/genres)
- [MusicBrainz genre-to-genre relationship types](https://musicbrainz.org/relationships/genre-genre)
- [MusicBrainz relationship statistics](https://musicbrainz.org/statistics/relationships)
- [MusicBrainz database schema](https://musicbrainz.org/doc/MusicBrainz_Database/Schema)
- [MusicBrainz data licensing](https://musicbrainz.org/doc/About/Data_License)
- [MusicBrainz database downloads](https://musicbrainz.org/doc/MusicBrainz_Database/Download)

## Implemented ingestion boundary

The release-time importer now fetches the canonical genre list from the MusicBrainz web service and
the public genre pages needed to recover aliases and genre-to-genre relationships. MusicBrainz's
API does not currently expose either of those datasets on the genre-list response, so the importer
keeps a local page cache, rate-limits requests to MusicBrainz's public-service limit, and records a
caller-supplied snapshot date for reproducible release artifacts.

It extracts only these concepts:

- Genre: MBID, canonical name, and aliases.
- Genre relationships: endpoints, direction, and type for `subgenre`, `fusion of`, and
  `influenced by`.

Naviamp-owned compatibility aliases are stored with separate provenance rather than represented as
MusicBrainz data. The initial compatibility rule maps the common Navidrome tag `rap` to
MusicBrainz's canonical `hip hop` genre; the provider's original `Rap` spelling is still sent back
to Navidrome when building the mix.

Recognize relationship types by their stable UUIDs rather than display strings:

- Subgenre: `9d61bc67-fa39-4719-8025-ea056a5bd7e6`
- Influenced by: `59117855-52db-4371-8dd3-87a16f285499`
- Fusion of: `723732ec-762c-4cb3-a2d0-e7e797c51915`

The importer emits deterministic JSON containing nodes, edges, the source snapshot date,
relationship UUIDs, license/provenance metadata, and a SHA-256 checksum. It then generates a chunked
Kotlin payload in shared storage so Android, Desktop, and iOS install identical data without a
runtime network request. The raw response cache and intermediate JSON remain release-tool inputs and
are not shipped.

The first audited import, dated 2026-08-15, contains 2,184 genres and 3,485 relationships. Its full
results are recorded in [the import audit](genre-ontology-import-audit.md).

## Other candidates

### Wikidata

Wikidata structured data is CC0 and its subclass graph can fill or flag gaps. It has useful external
identifiers and multilingual aliases, but its music genre boundary and subclass usage are less
consistent. SPARQL availability also makes it a poor runtime dependency. It is a good comparison
source for an offline report.

Sources:

- [Wikidata licensing](https://www.wikidata.org/wiki/Wikidata:Licensing)
- [Wikidata Query Service](https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service)

### Free Music Archive taxonomy

FMA provides a clean `genres.csv` with parent links, making it useful as a test fixture or a small
fallback seed. It contains only 163 genres, dates from the 2017 dataset, and its metadata is
CC BY 4.0, so it is too small and stale to be the main ontology.

Source: [FMA dataset](https://github.com/mdeff/fma)

### Discogs

Discogs has a simple and understandable genre/style model: 15 broad genres with styles acting as
subgenres. That is attractive for top-level grouping, but it is intentionally shallow and no
redistributable open-data license for the hierarchy was established during this review. Do not
import or scrape it unless its reuse terms are separately cleared.

Source: [Discogs genre/style guidelines](https://support.discogs.com/hc/en-us/articles/360005055213-Database-Guidelines-9-Genres-Styles)

### Every Noise, Spotify-derived lists, Rate Your Music, and AllMusic

Do not use these as bundled sources without explicit redistribution rights. Public visibility or an
unofficial Git repository does not establish a license for the underlying taxonomy. They may be
useful for manual gap discovery, but not as provenance for shipped edges.

## Questions the prototype must answer

1. How much of a representative Naviamp library's raw genre vocabulary matches MusicBrainz by
   canonical name or alias?
2. How many matched genres have no path to a useful top-level genre?
3. Does the MusicBrainz subgenre graph contain cycles or multiple parents, and how often?
4. Which parent should the UI prefer when a genre has multiple valid parents?
5. Which common server tags need Naviamp-owned aliases or normalization rules?
6. Is the resulting compressed artifact small enough to bundle unchanged on Android, Desktop, and
   iOS?

## Next research task

Measure canonical-name match coverage against genre names returned by configured Naviamp servers,
then add normalization and alias handling for common misses. The per-server library projection
should expose only matched genres and the ancestor nodes required to organize them; ontology nodes
that do not occur in that library should remain hidden.

## Library projection implementation

Naviamp now stores a separate genre inventory for each media source. A full shared Library refresh,
the retained offline-index sync, and the Genre Mix Builder's initial fallback load all use the
provider's genre endpoint to replace that source's inventory. Matching ignores case, surrounding
whitespace, punctuation, and separator differences while preserving the original provider name for
playback requests.

The projection starts with matched inventory genres and walks only `subgenre` parent edges. It keeps
every ancestor needed to organize those matches, retains multiple valid parents, and excludes every
unrelated ontology branch. Unmatched provider genres remain selectable and are reported separately
so incomplete ontology coverage never hides music that exists in the user's library. Each inventory
records the bundled ontology checksum; a later ontology release automatically rematches its stored
provider names before rebuilding the projection.

The shared Genre Mix Builder renders that projection as an expandable browser. Ancestor-only rows
are navigation containers, direct library matches are selectable, and unmatched provider genres
appear in a separate section. Search remains a flat list of the provider's original genre names.
Multiple ontology parents may expose the same node through more than one path, while selection is
still keyed globally by the provider name so playback seeds are never duplicated.
