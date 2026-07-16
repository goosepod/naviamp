package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition

interface MediaProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: ProviderCapabilities
    val cacheNamespace: String
        get() = id.value
    val selectedMusicFolderIds: List<String>
        get() = emptyList()

    suspend fun validateConnection(): ConnectionValidation
    suspend fun libraryScanStatus(): LibraryScanStatus? = null
    suspend fun recentlyAddedAlbums(limit: Int = 20): List<Album>
    suspend fun album(albumId: AlbumId): AlbumDetails
    suspend fun artist(artistId: ArtistId): ArtistDetails
    suspend fun artists(limit: Int = 50): List<Artist>
    suspend fun artistsPage(request: MediaPageRequest = MediaPageRequest()): MediaPage<Artist> =
        if (request.offset == 0) {
            request.toMediaPage(artists(limit = request.limit))
        } else {
            request.toMediaPage(emptyList())
        }
    suspend fun albums(limit: Int = 50, offset: Int = 0): List<Album> = emptyList()
    suspend fun albumsPage(request: MediaPageRequest = MediaPageRequest()): MediaPage<Album> =
        request.toMediaPage(albums(limit = request.limit, offset = request.offset))
    suspend fun albumList(type: AlbumListType, limit: Int = 20): List<Album> = emptyList()
    suspend fun albumsByGenre(genre: String, limit: Int = 20): List<Album> = emptyList()
    suspend fun albumsByYear(fromYear: Int, toYear: Int, limit: Int = 20): List<Album> = emptyList()
    suspend fun tracks(limit: Int = 50): List<Track>
    suspend fun favoriteTracks(limit: Int = 5000): List<Track> =
        tracks(limit).filter { it.favoritedAtIso8601 != null }
    suspend fun tracksPage(request: MediaPageRequest = MediaPageRequest()): MediaPage<Track> =
        if (request.offset == 0) {
            request.toMediaPage(tracks(limit = request.limit))
        } else {
            request.toMediaPage(emptyList())
        }
    suspend fun search(query: String, limit: Int = 20): MediaSearchResults
    suspend fun searchArtistsPage(
        query: String,
        request: MediaPageRequest = MediaPageRequest(),
    ): MediaPage<Artist> =
        if (request.offset == 0) {
            request.toMediaPage(search(query, request.limit).artists)
        } else {
            request.toMediaPage(emptyList())
        }
    suspend fun searchAlbumsPage(
        query: String,
        request: MediaPageRequest = MediaPageRequest(),
    ): MediaPage<Album> =
        if (request.offset == 0) {
            request.toMediaPage(search(query, request.limit).albums)
        } else {
            request.toMediaPage(emptyList())
        }
    suspend fun searchTracksPage(
        query: String,
        request: MediaPageRequest = MediaPageRequest(),
    ): MediaPage<Track> =
        if (request.offset == 0) {
            request.toMediaPage(search(query, request.limit).tracks)
        } else {
            request.toMediaPage(emptyList())
        }
    suspend fun playlists(limit: Int = 20): List<Playlist> = emptyList()
    suspend fun playlistTracks(playlistId: String): List<Track> = emptyList()
    suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        throw UnsupportedOperationException("Playlist creation is not supported by $displayName.")
    }
    suspend fun createSmartPlaylist(definition: SmartPlaylistDefinition): Playlist {
        throw UnsupportedOperationException("Smart playlist creation is not supported by $displayName.")
    }
    suspend fun updateSmartPlaylist(playlistId: String, definition: SmartPlaylistDefinition) {
        throw UnsupportedOperationException("Smart playlist edits are not supported by $displayName.")
    }
    suspend fun smartPlaylistDefinition(playlistId: String): SmartPlaylistDefinition {
        throw UnsupportedOperationException("Smart playlist loading is not supported by $displayName.")
    }
    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        throw UnsupportedOperationException("Playlist edits are not supported by $displayName.")
    }
    suspend fun replacePlaylistTracks(playlistId: String, currentTrackCount: Int, trackIds: List<TrackId>) {
        throw UnsupportedOperationException("Playlist track replacement is not supported by $displayName.")
    }
    suspend fun renamePlaylist(playlistId: String, name: String) {
        throw UnsupportedOperationException("Playlist edits are not supported by $displayName.")
    }
    suspend fun deletePlaylist(playlistId: String) {
        throw UnsupportedOperationException("Playlist deletion is not supported by $displayName.")
    }
    suspend fun genres(limit: Int = 50): List<Genre> = emptyList()
    suspend fun randomSongs(
        limit: Int = 50,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<Track> = emptyList()
    suspend fun internetRadioStations(): List<InternetRadioStation> = emptyList()
    suspend fun createInternetRadioStation(
        name: String,
        streamUrl: String,
        homePageUrl: String?,
    ): InternetRadioStation {
        throw UnsupportedOperationException("Internet radio stations are not supported by $displayName.")
    }
    suspend fun updateInternetRadioStation(station: InternetRadioStation) {
        throw UnsupportedOperationException("Internet radio station edits are not supported by $displayName.")
    }
    suspend fun deleteInternetRadioStation(stationId: String) {
        throw UnsupportedOperationException("Internet radio station deletion is not supported by $displayName.")
    }
    suspend fun artistRadio(artistId: ArtistId, count: Int = 50): List<Track> = emptyList()
    suspend fun albumRadio(albumId: AlbumId, count: Int = 50): List<Track> = emptyList()
    suspend fun trackRadio(trackId: TrackId, count: Int = 50): List<Track> = emptyList()
    suspend fun sonicSimilarTracks(trackId: TrackId, count: Int = 50): List<Track> = emptyList()
    suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int = 50): List<SonicSimilarTrack> =
        sonicSimilarTracks(trackId, count).map { track -> SonicSimilarTrack(track = track) }
    suspend fun findSonicPath(
        startTrackId: TrackId,
        endTrackId: TrackId,
        count: Int = 25,
    ): List<SonicPathMatch> = emptyList()
    suspend fun lyrics(trackId: TrackId): Lyrics? = null
    suspend fun reportNowPlaying(trackId: TrackId) = Unit
    suspend fun reportPlayed(trackId: TrackId, playedAtEpochMillis: Long, positionSeconds: Double? = null) = Unit
    suspend fun streamUrl(request: StreamRequest): String
    suspend fun downloadStream(
        url: String,
        httpClient: SharedHttpClient,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean =
        httpClient.download(url, writeChunk = writeChunk)

    suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
        throw UnsupportedOperationException("Track favorites are not supported by $displayName.")
    }
    suspend fun setArtistFavorite(artistId: ArtistId, favorite: Boolean) {
        throw UnsupportedOperationException("Artist favorites are not supported by $displayName.")
    }
    suspend fun setAlbumFavorite(albumId: AlbumId, favorite: Boolean) {
        throw UnsupportedOperationException("Album favorites are not supported by $displayName.")
    }
    suspend fun setTrackRating(trackId: TrackId, rating: Int?) {
        throw UnsupportedOperationException("Track ratings are not supported by $displayName.")
    }
    fun coverArtUrl(coverArtId: String): String
    fun coverArtUrl(coverArtId: String, size: CoverArtSize): String = coverArtUrl(coverArtId)
}

enum class CoverArtSize(val pixels: Int) {
    Thumbnail(512),
    Hero(1024),
}

data class MediaSearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

enum class AlbumListType(val providerValue: String) {
    Newest("newest"),
    Random("random"),
    Frequent("frequent"),
    Recent("recent"),
    Starred("starred"),
}

data class ProviderCapabilities(
    val supportsStreamingTranscode: Boolean,
    val supportsDownloadTranscode: Boolean,
    val supportsArtistRadio: Boolean,
    val supportsAlbumRadio: Boolean,
    val supportsTrackRadio: Boolean,
    val supportsTrackFavorites: Boolean = false,
    val supportsArtistFavorites: Boolean = false,
    val supportsAlbumFavorites: Boolean = false,
    val supportsTrackRatings: Boolean = false,
    val supportsPlayReporting: Boolean = false,
    val supportsSmartPlaylists: Boolean = false,
    val supportsSonicSimilarity: Boolean = false,
)

data class ConnectionValidation(
    val serverVersion: String?,
    val apiVersion: String?,
)

data class LibraryScanStatus(
    val scanning: Boolean?,
    val count: Int?,
    val lastScan: String?,
    val folderCount: Int?,
) {
    val signature: String?
        get() = lastScan?.takeIf { it.isNotBlank() }
}

data class SonicSimilarTrack(
    val track: Track,
    val similarity: Double? = null,
)

data class SonicPathMatch(
    val track: Track,
    val similarity: Double? = null,
)
