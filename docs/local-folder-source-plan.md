# Local Folder Source Plan

Spike record for [#662](https://github.com/goosepod/naviamp/issues/662), which de-risks
[#623](https://github.com/goosepod/naviamp/issues/623) (Local Folder as a library source) for v2.14.0.
It builds on the offline catalog work tracked in [#27](https://github.com/goosepod/naviamp/issues/27)
and the offline playlist snapshots in [PR #691](https://github.com/goosepod/naviamp/pull/691).

Status: Linux, JVM and Android host evidence is complete. Device evidence (Android SAF on hardware,
iOS, macOS, Windows, Flatpak) is not; see the [device checklist](#owner-device-checklist).

## Summary

- **Folder access is the real risk, and every shipped platform except tvOS has a workable model.**
  Android uses a persisted SAF tree grant, iOS a security-scoped bookmark, and Windows, Linux and
  directly distributed macOS use a plain path. The Flatpak build needs one manifest change. tvOS has no
  document picker, so it cannot offer Local Folder.
- **Tag reading belongs in common Kotlin for every platform.** The spike reader handles ID3v2.2/2.3/2.4,
  ID3v1, APEv2, FLAC, Ogg Vorbis, Ogg Opus, MP4/M4A (AAC and ALAC), Monkey's Audio and WavPack. It
  works on an opaque positional byte source. On 20,080 files written by real encoders and muxers, it
  had zero field mismatches. It read 27.3 KiB per file on average and 55 KiB per 2.6 MiB file. No
  platform needs BASS or native tag reading.
- **Playback needs no new native code.** All three BASS hosts already implement
  `BASS_StreamCreateFileUser` over Core's `PlaybackRandomAccessSource` and `BassFileCallbacks`. A
  Local Folder provider that serves byte ranges plays through the existing provider byte-transport
  path, with seeking and without copying.
- **The catalog fits without a parallel model.** Tracks map to the existing `Track` and source-scoped
  `library_*` tables. Availability plugs into `NaviampCoreOfflinePlaylistSource(playableTrackIds)`
  from #691.
- **Schema impact is one consolidated migration** for grants and file identity. Catalog rows reuse the
  existing tables.

## Existing code this builds on

| Concern | Existing common owner | Notes |
| --- | --- | --- |
| Provider routing | `NaviampCoreProviderSessionRouter`, `NaviampProviderCatalog`, `NaviampStoredApplication` routes | Android and Desktop still compose their routes in platform files (`AndroidCoreProviderSessions.kt`, `DesktopCoreProviderSessionPort.kt`). Add the Local Folder route in Core, not in those files. |
| Source identity | `media_source`, `SavedMediaSource`, `stableMediaSourceId` | A local profile is a `media_source` row with provider ID `local-folder`. |
| Catalog index | `StorageLibraryIndexStore` (`upsertLibraryTracks`, `pruneLibraryTracks`, `searchLibrary`, `librarySnapshot`), `AlbumLibraryIndex` | The rows are already source-scoped and cascade-deleted with the source. |
| Offline availability | `NaviampCoreOfflinePlaylistSource(repository, playableTrackIds)` (PR #691, not merged) | It takes any `playableTrackIds(sourceId)`. |
| Playback bytes | `PlaybackRandomAccessSource`, `BassFileCallbacks`, `NaviampProviderRandomAccessSource`, `NaviampPlaybackStreamRelay` | Desktop JNI, Android JNI and Apple cinterop all implement `createRandomAccessStream`. |
| Tag parsing | `core/domain/.../audio/AudioTagParser.kt` (`audioTagsFromAudioBytes`) behind host `AudioTagReader`s | It covers ID3v2, FLAC and Ogg comments for display and lyrics only, over a fixed 2 MiB prefix. It has no MP4 or APE support. |
| Folder pickers | `AndroidCoreUriPickers` (SAF tree, persisted grant), `DesktopNativeDirectoryPicker`, iOS bookmark helpers in `IosCoreSettingsSyncPort` | These exist for settings sync and download locations. |

## Common contracts

The spike puts these in `tools/spikes/local-folder` (see [Spike code](#spike-code)). Production
should move them to `core:domain` (contracts, scanner, tag reader, mapping) and `core:storage`
(persistence).

```kotlin
/** Opaque, persisted grant. Core stores the token and never parses it. */
data class FolderGrant(val token: String, val displayName: String)

sealed interface FolderGrantState {
    data class Available(val grant: FolderGrant) : FolderGrantState
    data class Refreshed(val refreshed: FolderGrant) : FolderGrantState   // stale bookmark re-issued
    data object NeedsReselection : FolderGrantState                       // revoked, moved, deleted
    data object Unreachable : FolderGrantState                            // volume absent
}

data class FolderEntry(
    val id: String,                 // document ID or root-relative path; host-opaque
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedEpochMillis: Long?,
    val mimeType: String? = null,
)

/** Positional, blocking reads; no shared cursor, so tag reads and playback can share one handle. */
interface LocalByteSource : AutoCloseable {
    val lengthBytes: Long
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int
}

/** The complete native effect a host supplies. */
interface FolderTreeAccess {
    suspend fun resolve(grant: FolderGrant): FolderGrantState
    fun root(grant: FolderGrant): FolderEntry
    suspend fun children(grant: FolderGrant, directory: FolderEntry): List<FolderEntry>
    fun open(grant: FolderGrant, file: FolderEntry): LocalByteSource
}
```

The host also supplies a folder picker that returns a `FolderGrant`. All of these stay common:

- **Enumeration** (`FolderTreeAccess.scan`): an iterative walk with deterministic ordering. It
  filters by extension, skips hidden entries, honors `.nomedia` and checks for cancellation. Each
  directory that fails to list is recorded, and a scan with any failure is marked incomplete.
- **Tag reading** (`readAudioTags(LocalByteSource)`): detects the container from its content, not its
  extension, and reads through a three-slot, 32 KiB read cache. Artwork is recorded as an offset,
  length and MIME type and read lazily.
- **Change detection** (`planRescan`): compares the previous index with the new listing by relative
  path, size and modified time. The result is unchanged, changed (re-read, keep the ID), moved (the
  same name, size and time at a new path keeps the ID), added and removed. Unknown sizes or times
  force a re-read and never count as a move. A manual rescan opens only changed and added files.
- **Mapping** (`localFolderTrack`): builds the existing `Track` with stable IDs (`lf-t-`, `lf-al-`
  and `lf-ar-` FNV-1a hashes of the source ID and key). Missing tags fall back to the file name, the
  folder name as album, and "Unknown Artist". The fallback label must become a string resource
  before it ships.
- **Playback bridge** (`LocalFolderRandomAccessSource`): adapts a `LocalByteSource` to the existing
  `PlaybackRandomAccessSource`.

Reconciliation policy, also common: write catalog changes only after a complete scan. Keep the last
good catalog when the grant is `NeedsReselection` or `Unreachable`, or when a scan is interrupted.
Mark affected tracks unavailable until access returns.

## Native effect per host

Each entry answers the AGENTS.md placement question: which OS API makes this impossible in common
Kotlin.

| Host | Effect | Justification |
| --- | --- | --- |
| Android | Picker: `ACTION_OPEN_DOCUMENT_TREE` plus `takePersistableUriPermission(READ)`. `children`: one `DocumentsContract.buildChildDocumentsUriUsingTree` query per directory, projecting ID, name, MIME type, size and modified time. `open`: `ContentResolver.openFileDescriptor(uri, "r")` plus `Os.pread`. `resolve`: `persistedUriPermissions` plus a root query. | Activity results, `ContentResolver`, `DocumentsContract`, `ParcelFileDescriptor` and `android.system.Os` are Android framework APIs. |
| iOS / iPadOS | Picker: `UIDocumentPickerViewController` with the folder type. `resolve`: `URLByResolvingBookmarkData`, re-creating the bookmark when stale. `children`: `NSFileManager` contents with resource keys. `open`: POSIX `open`/`pread` while `startAccessingSecurityScopedResource` is active, for the life of the grant. | UIKit, Foundation bookmarks and the security-scope lifetime are Apple APIs reached through cinterop. |
| macOS, direct distribution (current build) | The same as Linux: a path from `DesktopNativeDirectoryPicker`, `java.nio` listing and `FileChannel` reads. | JVM filesystem APIs. The current bundle is signed with the hardened runtime but is not sandboxed (`apps/desktop/packaging/macos-entitlements.plist`). |
| macOS, App Store sandbox (not built today) | Add sandbox entitlements (`files.user-selected.read-only`, optionally `assets.music.read-only`). Create and resolve a security-scoped bookmark (`withSecurityScope`, `securityScopeAllowOnlyReadAccess`) through JNA/JNI to Foundation, and keep `startAccessingSecurityScopedResource` active. | Foundation bookmark APIs have no JVM equivalent. |
| Windows (installer and MSIX) | A path picker, `java.nio` listing and `FileChannel`. | JVM filesystem APIs. A full-trust MSIX can read user files outside the package; only `AppData` writes are redirected ([MSIX behind the scenes](https://github.com/MicrosoftDocs/msix-docs/blob/main/msix-src/desktop/desktop-to-uwp-behind-the-scenes.md)). |
| Linux (native) | A path from zenity or kdialog, `java.nio` listing and `FileChannel`. | JVM filesystem APIs. |
| Linux (Flatpak) | Either a static `--filesystem=xdg-music:ro` finish argument, or the FileChooser portal through the existing `GTK_USE_PORTAL=1` zenity path, which returns a `/run/user/$UID/doc/<id>/` path. | Packaging metadata and the portal are outside the JVM. Everything after the path is the Linux effect. |
| tvOS | None. Local Folder is not offered. | `UIDocumentPickerViewController` is available on iOS, iPadOS, Mac Catalyst and visionOS, not tvOS ([Apple](https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller)). |

The Android effect is about 90 lines in
`tools/spikes/local-folder/src/androidMain/.../SafFolderTreeAccess.kt`, plus a 55-line device probe.
It compiles against SDK 37. The JVM effect is about 60 lines in `JvmFolderTreeAccess.kt`. Neither contains scan, tag or
reconciliation policy.

## Platform findings and limitations

### Android (Storage Access Framework)

- **Persistence.** A taken persistable grant survives device restarts. It is lost if the document is
  moved or deleted ([SAF guide](https://developer.android.com/training/data-storage/shared/documents-files#persist-permissions)).
  Clearing app data or uninstalling also revokes it. One grant per profile is far below the per-app
  limit (128, raised to 512 in Android 11, per
  [CommonsWare](https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html)).
- **Tree restrictions.** On Android 11 and later, a tree grant cannot select the root of internal
  storage, the root of a reliable SD card, or `Download`
  ([SAF guide, access restrictions](https://developer.android.com/training/data-storage/shared/documents-files#document-tree-access-restrictions)).
  Users must pick `Music` or a subfolder. The UI must explain this in the empty and error states.
- **Enumeration API.** `DocumentFile` "has substantial overhead", and the documentation recommends
  `DocumentsContract` for performance
  ([DocumentFile](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile)).
  `DocumentFile.listFiles()` returns IDs, and each `getName`, `getType`, `length` and `lastModified`
  call is a separate provider query. For the spike corpus (2,411 directories, 20,080 files), the
  per-property pattern costs about 2,411 + 2 × 2,410 + 4 × 20,080 ≈ 87,500 binder queries. The
  projection pattern in `SafFolderTreeAccess` costs 2,411. Both are implemented in `SafEnumerationProbe` so
  hardware can time them.
- **Reads.** `openFileDescriptor(..., "r")` on `ExternalStorageProvider` returns a seekable descriptor,
  and `Os.pread` gives the positional read the contract needs. Cloud and virtual providers may return
  a pipe (`statSize < 0`) or no binary representation at all. The spike rejects those, and v2.14.0
  should list them as unsupported.
- **Identity.** `ExternalStorageProvider` document IDs are path-derived (`primary:Music/...`), so a
  rename changes the ID like the path does. There is no inode. Moves are detected by name, size and
  time.
- **Grant scope.** The existing `AndroidCoreUriPickers.directory` takes READ and WRITE. Local Folder
  only needs READ, so production should request less.
- **Alternative considered.** MediaStore audio queries with `READ_MEDIA_AUDIO` are faster, but they
  are not folder-scoped, need a runtime permission and use MediaStore's own tag extraction. Treat this
  as a fallback only if SAF timings on hardware fail the budget.

### iOS and iPadOS

- **Persistence.** Since iOS 13, the document picker can return a security-scoped directory URL that
  grants recursive access. The app can store it as a bookmark and resolve it on later launches. Users
  can revoke access at any time in Settings > Privacy > Files and Folders, and the app must handle
  failures from `startAccessingSecurityScopedResource` and from reads
  ([Providing access to directories](https://developer.apple.com/documentation/uikit/providing-access-to-directories)).
- **Existing code.** `IosCoreSettingsSyncPort` already creates and resolves bookmarks, but it passes
  `bookmarkDataIsStale = null`. Local Folder must re-create stale bookmarks (`FolderGrantState.Refreshed`).
  The bookmark helpers should become one shared Apple effect, not a second copy.
- **Coordination and iCloud.** Apple asks apps to use a file coordinator for reads. Files in iCloud
  Drive or third-party providers may not be downloaded, so a read can block or fail. v2.14.0 should
  support "On My iPhone" and local external drives, and mark undownloaded items unavailable.
- **Scope lifetime.** Playback continues in the background (`UIBackgroundModes` audio), so the security
  scope must stay open for the grant's lifetime and close when the profile is removed or switched.
- **Alternative.** Enabling `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` would expose
  the app's own Documents folder in Files with no bookmark. It is simpler, but it is not "my existing
  folder".

### macOS

- **Direct distribution (today).** The app is not sandboxed, so a selected path works directly. TCC
  may prompt the first time the app reads Desktop, Documents, Downloads, removable or network volumes.
- **App Store sandbox (future).** This needs `com.apple.security.files.user-selected.read-only`, or the
  Music-folder capability, plus security-scoped bookmarks created with `withSecurityScope`. Resolved
  bookmarks are not in scope until `startAccessingSecurityScopedResource`, and stale bookmarks must be
  re-created ([Accessing files from the macOS App Sandbox](https://developer.apple.com/documentation/security/accessing-files-from-the-macos-app-sandbox)).
  A sandboxed JVM build is out of scope for v2.14.0.

### Windows

Paths work directly from the installer and the full-trust MSIX. Risks to verify on hardware: drive
letters that change for removable drives (the `Unreachable` state), UNC or mapped network shares,
paths longer than 260 characters, and non-ASCII names.

### Linux and Flatpak

- **Native.** Paths work directly. Found in the spike: under a non-UTF-8 locale (`LANG` unset), the JVM
  cannot map non-ASCII file names to `Path` (`InvalidPathException`). The spike test sets
  `LC_ALL=C.UTF-8`. The Desktop launcher should ensure a UTF-8 locale, or Local Folder must report
  those files instead of failing the scan.
- **Flatpak.** The manifest grants only `--filesystem=xdg-download` (`scripts/flatpak/prepare.py`).
  Flatpak recommends portals over static filesystem access, and read-only XDG directories such as
  `xdg-music` with `:ro` when static access is needed
  ([sandbox permissions](https://github.com/flatpak/flatpak-docs/blob/master/docs/sandbox-permissions.rst)).
  The FileChooser portal can select folders since interface version 3 and returns `file://` URIs
  ([FileChooser](https://github.com/flatpak/xdg-desktop-portal/blob/main/data/org.freedesktop.portal.FileChooser.xml)).
  Those URIs are served by the document portal's FUSE mount at `/run/user/$UID/doc/$DOC_ID/`, whose
  store entries can be persistent or session-only
  ([Documents](https://github.com/flatpak/xdg-desktop-portal/blob/main/data/org.freedesktop.portal.Documents.xml)).
  Whether a folder picked through zenity survives a restart must be checked on a device.
  Recommendation: add `--filesystem=xdg-music:ro` for v2.14.0 so `~/Music` works without the portal,
  and support portal folders once their persistence is verified. Any new finish argument must also be
  reflected in `scripts/flatpak/test_prepare.py`.

## Tag reading: chosen approach and measurements

**Decision: one common Kotlin reader for every platform, over `LocalByteSource`.** It replaces the
fixed-prefix input of `AudioTagParser` with positional reads and adds the formats needed for indexing.
BASS tag functions are not used: they need an open decoder stream per file and lack ReplayGain parsing
across formats. They would also make indexing depend on a native decoder.

The production reader should be one owner in `app.naviamp.domain.audio`. It should produce both the
index fields and the display `AudioTag` list, including comments, lyrics and the other frames that
`AudioTagParser` handles today. The three host `AudioTagReader`s (Android, Desktop, Apple) then
shrink to "open a `LocalByteSource`".

### Corpus

`tools/spikes/local-folder/scripts/generate_corpus.py` encodes one template per format with ffmpeg
6.1.1, then remuxes it with `-c copy` and per-file tags. Every file therefore carries real encoder and
muxer headers. It writes a manifest of the expected tags.

- 20,000 two-second files and 80 three-minute files (mean 2.6 MiB), with 2,411 directories laid out as
  Artist/Album/Track. That is 2,008 albums and 402 artists, 905 MiB in total.
- Eight encodings, 2,510 files each: MP3 with ID3v2.4 (CBR), MP3 with ID3v2.3 (VBR, Xing), FLAC,
  Ogg Vorbis, Ogg Opus, M4A AAC (moov after mdat), M4A ALAC and WavPack (APEv2).
- 10% of the MP3, FLAC and M4A files have embedded 600×600 JPEG artwork. One third of the formats
  that support ReplayGain carry track gain and peak. Some titles contain Latin-1 and CJK characters.
- Monkey's Audio, ID3v2.2, ID3v1, mp3gain-style APEv2 on MP3, MP4 freeform ReplayGain, FLAC with a
  leading ID3v2 tag, and UTF-16 frames are covered by unit fixtures (`TagReaderTest`), because ffmpeg
  cannot write them.

### Results

Machine: Linux container, 4 vCPU Intel Xeon at 2.10 GHz, ext4 on virtio, JDK 21.0.13, test JVM
`-Xmx512m`. "Cold" means the page cache was dropped (`echo 3 > /proc/sys/vm/drop_caches`) before the
Gradle run. These are JVM and Linux numbers. They bound the parser cost, not SAF or bookmark I/O.

| Measurement | Result |
| --- | --- |
| Correctness, 20,080 files | 0 mismatches in title, artist, album artist, album, track and total, disc, year, genre, artwork presence, ReplayGain track gain, duration (±120 ms) and sample rate |
| Enumeration, `java.nio`, 2,411 listing calls | 669 ms and 820 ms cold, 308 ms warm. The scan result retains 3.6 MiB |
| Manual rescan with no changes (walk and diff) | 207 to 238 ms, 0 files to re-read |
| Tag read, cold, 1 thread | 4.12 s (4,869 files/s) |
| Tag read, cold, 4 threads | 2.96 s (6,775 files/s) |
| Tag read, warm, 1 thread | 0.97 to 1.23 s (16,300 to 20,600 files/s) |
| Tag read, warm, 4 threads | 0.64 to 0.72 s (28,000 to 31,300 files/s) |
| I/O per file | 1.7 native reads and 27.3 KiB on average. Three-minute files: 2.1 reads, 55 KiB mean, 96 KiB max |
| Constrained heap | The full run (scan, three read passes, mapping) completes with `-Xmx64m` |
| Catalog mapping | 20,080 `Track` models retain 9.9 MiB, with unique track IDs |
| Existing `AudioTagParser` (2 MiB prefix) | 1.8 to 2.0 s single-threaded. It reads 1,644 KiB per three-minute file (30 times more than the spike reader) and finds no tags in any M4A or WavPack file |
| Playback input, `BassFileCallbacks` over `LocalByteSource` | 98.5 MiB from the 20 largest files at 661 to 843 MiB/s, 73 to 93 µs per 64 KiB callback |

The cost that matters on devices is the number of native calls, not parsing. Per 20,000-file library,
expect about 2,400 directory listings, 20,000 opens and about 34,000 positional reads. On Android, the
time is roughly `listings × t_query + files × (t_open + 1.7 × t_pread)`. The device checklist measures
those three terms.

## Playback input (BASS)

From reading the code:

- `BassAudioBackend.createPlaybackStream` uses `createRandomAccessStream` whenever
  `PlaybackRequest.randomAccess` is set (`core/domain/.../bass/BassAudioBackend.kt`).
- Desktop and Android call `BASS_StreamCreateFileUser(STREAMFILE_NOBUFFER, BASS_SAMPLE_FLOAT |
  BASS_ASYNCFILE | decode, ...)` with JNI `FILEPROCS` that call `BassFileCallbacks`
  (`native/bass-jni/src/naviamp_bass_jni.cpp`). Apple does the same through cinterop
  (`platforms/apple/.../AppleBassPlayback.kt`). Seek is `BassFileCallbacks.seek`, and reads are
  bounded to 64 KiB.
- The engine adapter already routes providers that set `requiresAuthenticatedPlaybackTransport` (or
  `supportsPlaybackByteTransport` for original quality) through `NaviampPlaybackStreamRelay` and
  `NaviampProviderRandomAccessSource`. That path fetches 128 KiB blocks through
  `MediaProvider.streamTrackBytes(request, "bytes=a-b", ...)`, keeps at most 2 MiB per source, and
  supports a prepared next track.

**Recommendation:** the Local Folder provider sets `requiresAuthenticatedPlaybackTransport = true`
and implements `streamTrackBytes` by answering 206 ranges from a `LocalByteSource`. It keeps the
handle open per relay lease so Android does not reopen a descriptor for every block. This plays SAF,
bookmark and path files with seeking, without copying and without engine or native changes.
`LocalFolderRandomAccessSource` and `bassFileCallbacksReadAndSeekThroughLocalSource` prove the byte
contract, and the measured callback cost is far below real-time needs.

Gaps to close in #623:

- `PlaybackRandomAccessSource` has no `close`, so a source that owns a descriptor needs the relay
  lease (above) or a `close` that `BassFileCallbacks.close` calls.
- Random-access streams are created without `BASS_STREAM_PRESCAN`, unlike file streams. Seeking in
  VBR MP3 files without a Xing table is approximate, as it is for remote streams today. A local source
  can afford prescan, which would need a flag on `createRandomAccessStream` (an ABI flag translation,
  with the policy decided in Core).
- Offline policy: `downloadedOnly` and connectivity checks must treat a Local Folder source as always
  local, or offline playback would be refused.
- `PlaybackSource` needs a `LocalFolderFile` value so diagnostics do not label these files as
  "Provider stream".
- Keep originals out of `StorageAudioStore` accounting and eviction. Downloads, transcoding, casting
  and remote scrobbling stay hidden for this source (`ProviderCapabilities.supportsDownloads = false`
  and the other capability flags at their defaults).

## Catalog fit

A Local Folder profile is a normal source:

1. **Source.** It is a `media_source` row with `provider_id = "local-folder"`, a generated
   `cache_namespace` (for example `local-folder:<uuid>`), empty server fields and
   `id = stableMediaSourceId(cacheNamespace)`. Add a `ProviderDescriptor` to `NaviampProviderCatalog`
   and one `NaviampCoreProviderSessionRoute(setOf("local-folder"), ...)` in Core's route composition.
2. **Provider.** A `LocalFolderMediaProvider : MediaProvider` in common code (a `providers/local-folder`
   `commonMain` module, like Navidrome and Jellyfin) answers browsing from the index. It uses
   `StorageLibraryIndexStore.librarySnapshot`, `searchLibrary` and the album and artist queries, and
   `AlbumLibraryIndex` for album lists. `coverArtUrl` returns an opaque `naviamp-local://art/<trackId>`.
   `bytesForOwnedUrl` reads the artwork range, so the existing `artworkCache` fetch
   (`sessions.currentProvider()?.bytesForOwnedUrl(url)` in `NaviampStoredApplication`) caches it
   unchanged.
3. **Index.** After a complete scan, `upsertLibraryArtists`, `upsertLibraryAlbums` and
   `upsertLibraryTracks` write the mapped `Track`s, and `pruneLibraryTracks(retainedIds)` removes
   vanished ones. `markLibrarySyncStarted` and `markLibrarySyncCompleted` record the scan.
4. **Availability and #691.** `playableTrackIds(sourceId)` for a Local Folder source is the set of
   `local_folder_file` rows marked available while the grant resolves. The same
   `NaviampCoreOfflinePlaylistSource` then serves any saved playlist rows for this source. Playlist
   import stays out of scope.
5. **Isolation and removal.** Every row is keyed by `source_id` with `ON DELETE CASCADE`, so removing
   the profile removes its index and grant row but never touches the user's files.
6. **Settings export and sync.** Export the profile (display name and provider ID) but not the grant,
   which is device-specific. On import, the receiving device shows the profile as needing a folder.

## Schema impact

Baseline: `main` and v2.12.0 end at `30.sqm` (database version 31). PR #691 proposes `31.sqm`. Per
AGENTS.md, #623 adds a single consolidated migration after whatever `main` holds when its branch is
cut (`31.sqm` if #691 has not merged, otherwise `32.sqm`). It edits that file and `NaviampStorage.sq`
together until the change ships. No intermediate migrations.

```sql
-- Device-local; never exported or synced.
CREATE TABLE local_folder_grant (
  source_id TEXT NOT NULL PRIMARY KEY REFERENCES media_source(id) ON DELETE CASCADE,
  grant_token TEXT NOT NULL,          -- SAF tree URI, base64 bookmark, or absolute path
  display_name TEXT NOT NULL,
  state TEXT NOT NULL,                -- Available, NeedsReselection, Unreachable
  updated_at_epoch_millis INTEGER NOT NULL
);

-- File identity and scan bookkeeping; catalog fields live in library_track.
CREATE TABLE local_folder_file (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  relative_path TEXT NOT NULL,
  document_id TEXT NOT NULL,
  size_bytes INTEGER,
  modified_epoch_millis INTEGER,
  container TEXT NOT NULL,
  artwork_offset INTEGER,
  artwork_length INTEGER,
  artwork_mime TEXT,
  artwork_encoding TEXT,
  replay_gain_track_db REAL,
  replay_gain_track_peak REAL,
  replay_gain_album_db REAL,
  replay_gain_album_peak REAL,
  available INTEGER NOT NULL DEFAULT 1,
  scan_generation INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id),
  UNIQUE(source_id, relative_path)
);
```

ReplayGain lives in `local_folder_file` because `library_track` has no ReplayGain columns and server
sources do not need them. The provider joins it when it builds `Track.replayGain`. Scan status reuses
`media_source.last_sync_started_at_epoch_millis` and `last_sync_completed_at_epoch_millis`.
`scan_generation` lets an interrupted scan be discarded without touching the last good rows. Verify
with `:core:storage:verifySqlDelightMigration` and an upgrade test from the released baseline.

## Proposed v2.14.0 scope for #623

In scope:

- One user-selected folder per Local Folder profile on Android (SAF), iOS and iPadOS (bookmark),
  Windows, Linux native and macOS direct distribution (path). Flatpak supports `~/Music` through
  `xdg-music:ro`.
- Common contracts, scanner, tag reader (MP3, FLAC, Ogg Vorbis and Opus, M4A AAC and ALAC, Monkey's
  Audio, WavPack), mapping and reconciliation, with shared tests ported from the spike.
- Index in the existing `library_*` tables plus the two tables above. One migration.
- Browse, search, queue, play and seek offline through the existing provider byte transport. Embedded
  artwork through the artwork cache.
- Manual rescan using size and modified-time diffing. Keep the last good catalog on revocation, an
  absent volume or an interrupted scan. Reselect the folder when the grant is lost.
- Profile removal deletes the index and grant only. The profile is exported without its grant.
- New strings in `strings.xml` and every translation (empty folder, tree restriction, reselect
  folder, scanning progress, unsupported provider files).

Follow-ups (file as separate issues):

- Flatpak FileChooser portal folders outside `~/Music`, after persistence is verified.
- macOS App Store sandbox build with security-scoped bookmarks.
- `cover.jpg` and `folder.jpg` artwork, sidecar `.lrc` lyrics, and multiple folders per profile.
- Automatic folder watching (`FileObserver`, `ContentObserver`, FSEvents, `WatchService`).
- Prescan for local VBR MP3 seeking.
- WAV, AIFF and DSF tag reading. They are enumerated now and fall back to the file name.
- MediaStore fallback on Android if SAF fails the performance budget.
- Moving Android and Desktop provider route composition from platform files into Core (existing debt
  found during the spike).
- Out of scope, as in #662: tag write-back, playlist import and casting local files.

## Biggest risks

1. **SAF enumeration speed on real devices is unmeasured.** The query count is fixed (2,411 for this
   corpus), but the per-query and per-open latency depends on the device and the provider. If a
   20,000-file first scan takes minutes, v2.14.0 needs visible progress and resumable scanning
   (`scan_generation`).
2. **Grant loss is silent until use.** Android loses the grant when the folder moves. iOS users can
   revoke access in Settings. Bookmarks go stale. The common `resolve` states and "keep last good
   catalog" policy cover this, but they need device verification.
3. **Provider-backed files.** Cloud providers on Android and iCloud or third-party providers on iOS
   may not offer seekable local bytes. Mark them unsupported in v2.14.0, not partially playable.
4. **Flatpak portal persistence** is unverified. Use the static `xdg-music:ro` path as the fallback.
5. **Linux locale.** Non-ASCII paths fail under a non-UTF-8 locale on the JVM.
6. **Descriptor lifetime.** `PlaybackRandomAccessSource` cannot close a descriptor today. Use the relay
   lease or add `close` before shipping.

## Spike code

Location: `tools/spikes/local-folder/`. It is included in Gradle only with
`-Pnaviamp.spikes.localFolder=true`, nothing depends on it, and no production source set changed.

- `src/commonMain`: contracts, scanner, rescan diff, tag reader (`tags/`), `Track` mapping and the
  playback bridge.
- `src/jvmMain/JvmFolderTreeAccess.kt`: the Desktop effect.
- `src/androidMain/SafFolderTreeAccess.kt`: the Android SAF effect and `SafEnumerationProbe`
  (projection versus per-property timing).
- `src/commonTest`: 12 tests (format fixtures, scanner, rescan, `BassFileCallbacks` over a local
  source, mapping fallbacks). They run on the JVM and as Android host tests.
- `src/jvmTest/LocalFolderCorpusBenchmark.kt`: corpus verification and measurements. It runs only with
  `-Dnaviamp.spike.corpus=<dir>`.
- `scripts/generate_corpus.py`: the corpus generator.
- iOS targets (`iosArm64`, `iosSimulatorArm64`) are declared so the common code is compiled to an iOS
  klib on Linux. Linking and running need macOS.

Commands:

```sh
python3 tools/spikes/local-folder/scripts/generate_corpus.py /tmp/lf-corpus --count 20000 --long 10
./gradlew --no-daemon --max-workers=2 -Pnaviamp.spikes.localFolder=true \
  :tools:spikes:local-folder:jvmTest :tools:spikes:local-folder:testAndroidHostTest \
  :tools:spikes:local-folder:compileAndroidMain :tools:spikes:local-folder:compileKotlinIosArm64
./gradlew --no-daemon --max-workers=2 -Pnaviamp.spikes.localFolder=true \
  :tools:spikes:local-folder:jvmTest --tests '*Corpus*' \
  -Dnaviamp.spike.corpus=/tmp/lf-corpus -Dnaviamp.spike.threads=4
```

## Owner device checklist

Use the same corpus on every device. Copy `library/` from the generator output, or use a real library
of at least 20,000 files, and record the build, OS version and device model. For timings, run each
measurement three times and report the median. "Cold" means right after a reboot.

### Android (phone, plus one Android TV if Local Folder is offered there)

1. Temporarily wire the spike in a local debug build only (do not commit). Launch
   `ACTION_OPEN_DOCUMENT_TREE`, call `takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`,
   and pass `FolderGrant(uri.toString(), name)` to `SafEnumerationProbe.projection` and
   `SafEnumerationProbe.perProperty`. Log the results.
2. Try to pick internal storage root, `Download`, and an SD card root. Confirm Android 11 and later
   refuse them, and record what the picker shows.
3. Pick `Music` (with the corpus). Record the files, directories, queries and milliseconds from both
   probe methods. Reboot, then repeat cold.
4. Time a full tag pass. For each file, call `SafFolderTreeAccess.open` and then `readAudioTags`, using
   1 and 4 threads. Record the total time, and the mean `openFileDescriptor` and `pread` latency.
5. Force-stop the app, reboot the device, relaunch with networking off, and confirm
   `persistedUriPermissions` still contains the tree. Confirm `resolve` returns `Available`.
6. Upgrade the app (install a higher version code over it) and repeat step 5.
7. Rename the picked folder in Files and confirm `resolve` returns `NeedsReselection` or that queries
   fail. Move it back.
8. Put the library on a removable SD card, eject it, and confirm `Unreachable`. Reinsert it and
   confirm `Available`.
9. Pick a Google Drive (or other cloud) folder. Record whether `openFileDescriptor` returns a seekable
   descriptor (`statSize >= 0`).
10. Play one FLAC and one VBR MP3 through BASS over `LocalFolderRandomAccessSource`. Seek to 90% of
    the track and back, with networking off after a cold start.

### iOS / iPadOS

1. In a local debug build, present `UIDocumentPickerViewController(forOpeningContentTypes: [.folder])`.
   Pick a folder in On My iPhone that holds the corpus, then create a bookmark.
2. Kill the app, reboot the device, and resolve the bookmark with networking off. Record `isStale`,
   and confirm that `startAccessingSecurityScopedResource` returns true.
3. Enumerate with `FileManager.contentsOfDirectory(at:includingPropertiesForKeys:[.fileSizeKey,
   .contentModificationDateKey, .isDirectoryKey])` and time a full walk. Time a tag pass with POSIX
   `pread` through `LocalByteSource`.
4. Revoke access in Settings > Privacy > Files and Folders, and confirm that resolution or reads
   fail cleanly.
5. Rename and move the folder in Files, then record whether the bookmark follows it.
6. Pick an iCloud Drive folder with "Optimize Storage" on, and record behavior for undownloaded files.
7. Play one file in the background with the screen locked for 10 minutes while the security scope is
   held. Seek, and confirm playback continues.
8. Upgrade the app over the existing install and confirm the bookmark still resolves.

### macOS (direct distribution)

1. Pick `~/Music/<corpus>` and a folder on an external drive. Run the JVM benchmark against both, cold
   after a reboot.
2. Confirm TCC prompts for Documents, Desktop, Downloads and removable volumes, and record the result
   after denial.
3. Unplug the external drive and confirm `Unreachable`.

### Windows (installer and Microsoft Store MSIX)

1. Run the corpus benchmark on an NTFS folder and on a USB drive, cold after a reboot.
2. Repeat from the MSIX-installed app. Confirm reads outside the package work and that nothing is
   virtualized.
3. Test a path longer than 260 characters, a UNC share and non-ASCII names.
4. Change the USB drive letter and confirm `Unreachable` and then reselection.

### Linux (native and Flatpak)

1. Run the benchmark natively with `LANG` unset and with a UTF-8 locale. Record the non-ASCII
   behavior.
2. In a local Flatpak build with `--filesystem=xdg-music:ro` added, confirm `~/Music` lists and plays,
   and that writes fail.
3. Without that permission, pick a folder through the app's zenity picker (portal). Record the
   returned path (`/run/user/$UID/doc/...`), then restart the app and the session, and confirm whether
   the path still resolves.
4. Confirm `flatpak permission-show` or `flatpak documents` lists the entry, and record how revocation
   behaves.

### Pass criteria to record in #623

- Grants survive restart, upgrade and reboot on every in-scope host.
- The first scan of 20,000 files finishes within a budget the owner sets from these numbers. A
  manual rescan with no changes takes under 10% of the first scan.
- Playback and seek work offline after a cold start with no copy of the file.

## Sources

- Android: [Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files),
  [DocumentFile](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile),
  [Count your SAF URI permission grants](https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html).
- Apple: [Providing access to directories](https://developer.apple.com/documentation/uikit/providing-access-to-directories),
  [UIDocumentPickerViewController](https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller),
  [Accessing files from the macOS App Sandbox](https://developer.apple.com/documentation/security/accessing-files-from-the-macos-app-sandbox),
  [User-selected read-only entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.security.files.user-selected.read-only).
- Flatpak: [Sandbox permissions](https://github.com/flatpak/flatpak-docs/blob/master/docs/sandbox-permissions.rst),
  [FileChooser portal](https://github.com/flatpak/xdg-desktop-portal/blob/main/data/org.freedesktop.portal.FileChooser.xml),
  [Documents portal](https://github.com/flatpak/xdg-desktop-portal/blob/main/data/org.freedesktop.portal.Documents.xml).
- Windows: [Behind the scenes of your packaged desktop application](https://github.com/MicrosoftDocs/msix-docs/blob/main/msix-src/desktop/desktop-to-uwp-behind-the-scenes.md).
- BASS: `BASS_StreamCreateFileUser`, `BASS_FILEPROCS`, `BASS_ASYNCFILE` and `BASS_STREAM_PRESCAN` in
  `native/bass-jni/vendor/include/bass.h`.
