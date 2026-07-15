package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LibraryAlbumYear
import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.storage.Library_track
import app.naviamp.storage.NaviampStorageQueries
import app.naviamp.storage.SelectArtistPopularTracks
import app.naviamp.storage.SelectRecentlyPlayedLibraryTracks

class DesktopLibraryIndexStore(
    private val queries: NaviampStorageQueries,
    private val mediaSources: DesktopMediaSourceStore,
    private val nowMillis: () -> Long,
) : LocalLibraryIndexRepository {
    override fun mediaSource(sourceId: String) =
        mediaSources.mediaSource(sourceId)

    override fun markLibraryScanChecked(sourceId: String, signature: String) {
        mediaSources.markLibraryScanChecked(sourceId, signature)
    }

    override fun markLibrarySyncStarted(sourceId: String) {
        mediaSources.markLibrarySyncStarted(sourceId)
    }

    override fun markLibrarySyncCompleted(sourceId: String) {
        mediaSources.markLibrarySyncCompleted(sourceId)
    }

    override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) {
        val now = nowMillis()
        queries.transaction {
            artists.forEach { artist ->
                queries.upsertLibraryArtist(
                    source_id = sourceId,
                    remote_artist_id = artist.id.value,
                    name = artist.name,
                    search_name = artist.name.searchText(),
                    updated_at_epoch_millis = now,
                )
            }
        }
    }

    override fun replaceLibraryArtists(sourceId: String, artists: List<Artist>) {
        queries.transaction {
            queries.clearLibraryArtistsForSource(sourceId)
            upsertLibraryArtists(sourceId, artists)
        }
    }

    override fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) {
        val now = nowMillis()
        queries.transaction {
            albums.forEach { album ->
                queries.upsertLibraryAlbum(
                    source_id = sourceId,
                    remote_album_id = album.id.value,
                    remote_artist_id = null,
                    title = album.title,
                    artist_name = album.artistName,
                    search_title = album.title.searchText(),
                    search_artist_name = album.artistName.searchText(),
                    cover_art_id = album.coverArtId,
                    release_year = album.releaseYear?.toLong(),
                    updated_at_epoch_millis = now,
                )
            }
        }
    }

    override fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) {
        val now = nowMillis()
        queries.transaction {
            tracks.forEach { track ->
                queries.upsertLibraryTrack(
                    source_id = sourceId,
                    remote_track_id = track.id.value,
                    remote_album_id = track.albumId?.value,
                    remote_artist_id = track.artistId?.value,
                    title = track.title,
                    artist_name = track.artistName,
                    album_title = track.albumTitle,
                    search_title = track.title.searchText(),
                    search_artist_name = track.artistName.searchText(),
                    search_album_title = track.albumTitle?.searchText(),
                    duration_seconds = track.durationSeconds?.toLong(),
                    cover_art_id = track.coverArtId,
                    audio_codec = track.audioInfo?.codec,
                    audio_bitrate_kbps = track.audioInfo?.bitrateKbps?.toLong(),
                    audio_content_type = track.audioInfo?.contentType,
                    audio_bit_depth = track.audioInfo?.bitDepth?.toLong(),
                    audio_sampling_rate_hz = track.audioInfo?.samplingRateHz?.toLong(),
                    favorited_at_iso8601 = track.favoritedAtIso8601,
                    user_rating = track.userRating?.toLong(),
                    play_count = track.playCount?.toLong(),
                    last_played_at_iso8601 = track.lastPlayedAtIso8601,
                    updated_at_epoch_millis = now,
                )
            }
        }
    }

    override fun librarySnapshot(sourceId: String, limit: Long, offset: Long): LibrarySnapshot =
        LibrarySnapshot(
            artists = queries.selectLibraryArtists(sourceId, limit, offset).executeAsList().map {
                Artist(
                    id = ArtistId(it.remote_artist_id),
                    name = it.name,
                )
            },
            albums = queries.selectLibraryAlbums(sourceId, limit, offset).executeAsList().map {
                Album(
                    id = AlbumId(it.remote_album_id),
                    title = it.title,
                    artistName = it.artist_name,
                    coverArtId = it.cover_art_id,
                    recentlyAddedAtIso8601 = null,
                    releaseYear = it.release_year?.toInt(),
                )
            },
            tracks = queries.selectLibraryTracks(sourceId, limit, offset).executeAsList().map {
                it.toTrack()
            },
        )

    override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot {
        val pattern = "%${query.searchText()}%"
        return LibrarySnapshot(
            artists = queries.searchLibraryArtists(sourceId, pattern, limit, offset).executeAsList().map {
                Artist(
                    id = ArtistId(it.remote_artist_id),
                    name = it.name,
                )
            },
            albums = queries.searchLibraryAlbums(sourceId, pattern, pattern, limit, offset).executeAsList().map {
                Album(
                    id = AlbumId(it.remote_album_id),
                    title = it.title,
                    artistName = it.artist_name,
                    coverArtId = it.cover_art_id,
                    recentlyAddedAtIso8601 = null,
                    releaseYear = it.release_year?.toInt(),
                )
            },
            tracks = queries.searchLibraryTracks(sourceId, pattern, pattern, pattern, limit, offset).executeAsList().map {
                it.toTrack()
            },
        )
    }

    override fun recentlyPlayedLibraryTracks(sourceId: String, limit: Long): List<Track> =
        queries.selectRecentlyPlayedLibraryTracks(sourceId, limit).executeAsList().map { it.toTrack() }

    override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? =
        queries.selectRandomLibraryTrackForAlbum(sourceId, albumId.value).executeAsOneOrNull()?.toTrack()

    override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> =
        queries.selectLibraryTracksForAlbum(sourceId, albumId.value, limit).executeAsList().map { it.toTrack() }

    override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
        queries.selectRandomLibraryTrackForArtist(sourceId, artistId.value).executeAsOneOrNull()?.toTrack()

    override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
        queries.selectLibraryTracksForArtist(sourceId, artistId.value, limit).executeAsList().map { it.toTrack() }

    override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
        queries.selectLibraryTracksForArtistName(sourceId, artistName.searchText(), limit).executeAsList().map { it.toTrack() }

    override fun libraryTracksForAlbumTitle(
        sourceId: String,
        albumTitle: String,
        artistName: String?,
        limit: Long,
    ): List<Track> {
        val searchArtistName = artistName?.searchText()
        val searchAlbumTitle = albumTitle.searchText()
        return if (searchArtistName.isNullOrBlank()) {
            queries.selectLibraryTracksForAlbumTitle(sourceId, searchAlbumTitle, limit)
        } else {
            queries.selectLibraryTracksForAlbumTitleAndArtist(sourceId, searchAlbumTitle, searchArtistName, limit)
        }.executeAsList().map { it.toTrack() }
    }

    override fun artistPopularTracks(sourceId: String, artistId: ArtistId, source: String): List<ArtistPopularTrackMatch> =
        queries.selectArtistPopularTracks(sourceId, artistId.value, source).executeAsList().map { it.toPopularTrackMatch() }

    override fun replaceArtistPopularTracks(
        sourceId: String,
        artistId: ArtistId,
        source: String,
        candidates: List<ArtistPopularTrackCandidate>,
        matchedTracksBySourceTrackId: Map<String, Track>,
        fetchedAtEpochMillis: Long,
    ) {
        queries.transaction {
            upsertLibraryTracks(sourceId, matchedTracksBySourceTrackId.values.toList())
            queries.deleteArtistPopularTracks(sourceId, artistId.value, source)
            candidates.forEach { candidate ->
                queries.upsertArtistPopularTrack(
                    source_id = sourceId,
                    remote_artist_id = artistId.value,
                    popular_source = source,
                    source_track_id = candidate.sourceTrackId,
                    rank = candidate.rank.toLong(),
                    title = candidate.title,
                    album_title = candidate.albumTitle,
                    duration_seconds = candidate.durationSeconds?.toLong(),
                    matched_remote_track_id = matchedTracksBySourceTrackId[candidate.sourceTrackId]?.id?.value,
                    fetched_at_epoch_millis = fetchedAtEpochMillis,
                )
            }
        }
    }

    override fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long): List<Track> {
        val albumTracks = track.albumId?.let { libraryTracksForAlbum(sourceId, it, limit) }.orEmpty()
        val artistLimit = (limit - albumTracks.size).coerceAtLeast(12)
        val artistTracks = track.artistId?.let { libraryTracksForArtist(sourceId, it, artistLimit) }.orEmpty()
        return (albumTracks + artistTracks)
            .asSequence()
            .filterNot { it.id == track.id }
            .distinctBy { it.id }
            .take(limit.toInt())
            .toList()
    }

    override fun libraryIndexStats(sourceId: String): LibraryIndexStats =
        LibraryIndexStats(
            artistCount = queries.libraryArtistCountForSource(sourceId).executeAsOne(),
            albumCount = queries.libraryAlbumCountForSource(sourceId).executeAsOne(),
            trackCount = queries.libraryTrackCountForSource(sourceId).executeAsOne(),
        )

    override fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear> =
        queries.selectLibraryAlbumYears(sourceId)
            .executeAsList()
            .map { row ->
                LibraryAlbumYear(
                    year = row.release_year.toInt(),
                    albumCount = row.album_count,
                )
            }

    override fun clearLibraryData(sourceId: String?) {
        queries.transaction {
            if (sourceId == null) {
                queries.clearArtistPopularTracks()
                queries.clearLibraryTracks()
                queries.clearLibraryAlbums()
                queries.clearLibraryArtists()
            } else {
                queries.clearArtistPopularTracksForSource(sourceId)
                queries.clearLibraryForSource(sourceId)
                queries.clearLibraryAlbumsForSource(sourceId)
                queries.clearLibraryArtistsForSource(sourceId)
            }
        }
    }

    fun libraryOffsetForLetter(sourceId: String, tab: DesktopLibraryTab, letter: Char): Long {
        val boundary = letter.librarySearchBoundary()
        return when (tab) {
            DesktopLibraryTab.Artists -> queries.libraryArtistOffsetForLetter(sourceId, boundary).executeAsOne()
            DesktopLibraryTab.Albums -> queries.libraryAlbumOffsetForLetter(sourceId, boundary).executeAsOne()
        }
    }
}

private fun Library_track.toTrack(): Track =
    Track(
        id = TrackId(remote_track_id),
        title = title,
        artistId = remote_artist_id?.let { ArtistId(it) },
        artistName = artist_name,
        albumId = remote_album_id?.let { AlbumId(it) },
        albumTitle = album_title,
        albumReleaseYear = null,
        durationSeconds = duration_seconds?.toInt(),
        coverArtId = cover_art_id,
        audioInfo = AudioInfo(
            codec = audio_codec,
            bitrateKbps = audio_bitrate_kbps?.toInt(),
            contentType = audio_content_type,
            bitDepth = audio_bit_depth?.toInt(),
            samplingRateHz = audio_sampling_rate_hz?.toInt(),
        ).takeIf {
            it.codec != null ||
                it.bitrateKbps != null ||
                it.contentType != null ||
                it.bitDepth != null ||
                it.samplingRateHz != null
        },
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
        playCount = play_count?.toInt(),
        lastPlayedAtIso8601 = last_played_at_iso8601,
    )

private fun SelectRecentlyPlayedLibraryTracks.toTrack(): Track =
    Track(
        id = TrackId(remote_track_id),
        title = title,
        artistId = remote_artist_id?.let { ArtistId(it) },
        artistName = artist_name,
        albumId = remote_album_id?.let { AlbumId(it) },
        albumTitle = album_title,
        albumReleaseYear = null,
        durationSeconds = duration_seconds?.toInt(),
        coverArtId = cover_art_id,
        audioInfo = AudioInfo(
            codec = audio_codec,
            bitrateKbps = audio_bitrate_kbps?.toInt(),
            contentType = audio_content_type,
            bitDepth = audio_bit_depth?.toInt(),
            samplingRateHz = audio_sampling_rate_hz?.toInt(),
        ).takeIf {
            it.codec != null ||
                it.bitrateKbps != null ||
                it.contentType != null ||
                it.bitDepth != null ||
                it.samplingRateHz != null
        },
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
        playCount = play_count?.toInt(),
        lastPlayedAtIso8601 = last_played_at_iso8601,
    )

private fun SelectArtistPopularTracks.toPopularTrackMatch(): ArtistPopularTrackMatch =
    ArtistPopularTrackMatch(
        candidate = ArtistPopularTrackCandidate(
            source = popular_source,
            sourceTrackId = source_track_id,
            rank = rank.toInt(),
            title = popular_title,
            albumTitle = popular_album_title,
            durationSeconds = popular_duration_seconds?.toInt(),
        ),
        matchedTrack = Track(
            id = TrackId(remote_track_id),
            title = title,
            artistId = remote_artist_id?.let { ArtistId(it) },
            artistName = artist_name,
            albumId = remote_album_id?.let { AlbumId(it) },
            albumTitle = album_title,
            albumReleaseYear = null,
            durationSeconds = duration_seconds?.toInt(),
            coverArtId = cover_art_id,
            audioInfo = AudioInfo(
                codec = audio_codec,
                bitrateKbps = audio_bitrate_kbps?.toInt(),
                contentType = audio_content_type,
                bitDepth = audio_bit_depth?.toInt(),
                samplingRateHz = audio_sampling_rate_hz?.toInt(),
            ).takeIf {
                it.codec != null ||
                    it.bitrateKbps != null ||
                    it.contentType != null ||
                    it.bitDepth != null ||
                    it.samplingRateHz != null
            },
            replayGain = null,
            favoritedAtIso8601 = favorited_at_iso8601,
            userRating = user_rating?.toInt(),
            playCount = play_count?.toInt(),
            lastPlayedAtIso8601 = last_played_at_iso8601,
        ),
        fetchedAtEpochMillis = fetched_at_epoch_millis,
    )

private fun String.searchText(): String =
    lowercase().trim()

private fun Char.librarySearchBoundary(): String =
    if (this == '#') "" else lowercaseChar().toString()
