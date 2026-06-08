package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults

sealed interface MediaMetadataMutationResult {
    val status: String?
    val shouldRunPlatformSideEffects: Boolean

    data class TrackUpdated(
        val track: Track,
        override val status: String? = null,
    ) : MediaMetadataMutationResult {
        override val shouldRunPlatformSideEffects: Boolean = true
    }

    data class ArtistUpdated(
        val artist: Artist,
        override val status: String? = null,
    ) : MediaMetadataMutationResult {
        override val shouldRunPlatformSideEffects: Boolean = true
    }

    data class AlbumUpdated(
        val album: Album,
        override val status: String? = null,
    ) : MediaMetadataMutationResult {
        override val shouldRunPlatformSideEffects: Boolean = true
    }

    data class Failed(
        override val status: String,
    ) : MediaMetadataMutationResult {
        override val shouldRunPlatformSideEffects: Boolean = false
    }

    data object Skipped : MediaMetadataMutationResult {
        override val status: String? = null
        override val shouldRunPlatformSideEffects: Boolean = false
    }
}

val MediaMetadataMutationResult.updated: Boolean
    get() = shouldRunPlatformSideEffects

class MediaMetadataStateUpdater(
    private val homeContent: () -> HomeContent,
    private val setHomeContent: (HomeContent) -> Unit,
    private val searchResults: () -> MediaSearchResults,
    private val setSearchResults: (MediaSearchResults) -> Unit,
    private val albumDetails: () -> AlbumDetails?,
    private val setAlbumDetails: (AlbumDetails?) -> Unit,
    private val artistDetails: () -> ArtistDetails?,
    private val setArtistDetails: (ArtistDetails?) -> Unit,
    private val updateExtraArtistCollections: (Artist) -> Unit = {},
    private val updateExtraAlbumCollections: (Album) -> Unit = {},
) {
    fun applyArtistUpdate(updatedArtist: Artist) {
        setHomeContent(homeContent().withUpdatedArtist(updatedArtist))
        setSearchResults(searchResults().withUpdatedArtist(updatedArtist))
        setArtistDetails(artistDetails()?.withUpdatedArtist(updatedArtist))
        updateExtraArtistCollections(updatedArtist)
    }

    fun applyAlbumUpdate(updatedAlbum: Album) {
        setHomeContent(homeContent().withUpdatedAlbum(updatedAlbum))
        setSearchResults(searchResults().withUpdatedAlbum(updatedAlbum))
        setAlbumDetails(albumDetails()?.withUpdatedAlbum(updatedAlbum))
        setArtistDetails(artistDetails()?.withUpdatedAlbum(updatedAlbum))
        updateExtraAlbumCollections(updatedAlbum)
    }
}

class MediaTrackMetadataStateUpdater(
    private val nowPlayingTrack: () -> Track?,
    private val setNowPlayingTrack: (Track?) -> Unit,
    private val searchResults: () -> MediaSearchResults,
    private val setSearchResults: (MediaSearchResults) -> Unit,
    private val albumDetails: () -> AlbumDetails?,
    private val setAlbumDetails: (AlbumDetails?) -> Unit,
    private val tracks: () -> List<Track> = { emptyList() },
    private val setTracks: (List<Track>) -> Unit = {},
) {
    fun applyTrackUpdate(updatedTrack: Track): Track? {
        val updatedNowPlaying = nowPlayingTrack().withUpdatedTrack(updatedTrack)
        setNowPlayingTrack(updatedNowPlaying)
        setTracks(tracks().withUpdatedTrack(updatedTrack))
        setSearchResults(searchResults().withUpdatedTrack(updatedTrack))
        setAlbumDetails(albumDetails()?.withUpdatedTrack(updatedTrack))
        return updatedNowPlaying
    }
}

class MediaMetadataMutationController(
    private val provider: () -> MediaProvider?,
    private val favoritedAtIso8601: () -> String,
    private val setStatus: (String) -> Unit,
    private val knownTracks: () -> List<Track> = { emptyList() },
    private val knownArtists: () -> List<Artist> = { emptyList() },
    private val knownAlbums: () -> List<Album> = { emptyList() },
    private val applyTrackUpdate: (Track) -> Unit,
    private val applyArtistUpdate: (Artist) -> Unit,
    private val applyAlbumUpdate: (Album) -> Unit,
) {
    fun findTrack(trackId: String): Track? =
        knownTracks().firstOrNull { track -> track.id.value == trackId }

    fun findArtist(artistId: String): Artist? =
        knownArtists().firstOrNull { artist -> artist.id.value == artistId }

    fun findAlbum(albumId: String): Album? =
        knownAlbums().firstOrNull { album -> album.id.value == albumId }

    suspend fun toggleTrackFavoriteById(trackId: String): Boolean =
        toggleTrackFavoriteByIdResult(trackId).updated

    suspend fun toggleTrackFavoriteByIdResult(trackId: String): MediaMetadataMutationResult {
        val track = findTrack(trackId) ?: return missingItem("Track not found.")
        return toggleTrackFavoriteResult(track)
    }

    suspend fun toggleArtistFavoriteById(artistId: String): Boolean =
        toggleArtistFavoriteByIdResult(artistId).updated

    suspend fun toggleArtistFavoriteByIdResult(artistId: String): MediaMetadataMutationResult {
        val artist = findArtist(artistId) ?: return missingItem("Artist not found.")
        return toggleArtistFavoriteResult(artist)
    }

    suspend fun toggleAlbumFavoriteById(albumId: String): Boolean =
        toggleAlbumFavoriteByIdResult(albumId).updated

    suspend fun toggleAlbumFavoriteByIdResult(albumId: String): MediaMetadataMutationResult {
        val album = findAlbum(albumId) ?: return missingItem("Album not found.")
        return toggleAlbumFavoriteResult(album)
    }

    suspend fun toggleTrackFavorite(track: Track): Boolean =
        toggleTrackFavoriteResult(track).updated

    suspend fun toggleTrackFavoriteResult(track: Track): MediaMetadataMutationResult {
        val activeProvider = provider() ?: return MediaMetadataMutationResult.Skipped
        if (!activeProvider.capabilities.supportsTrackFavorites) return MediaMetadataMutationResult.Skipped
        return runMediaMutation(
            errorMessage = "Could not update favorite.",
            mutation = {
                favoriteTrackUpdate(
                    provider = activeProvider,
                    track = track,
                    favoritedAtIso8601 = favoritedAtIso8601(),
                )
            },
            updatedResult = { track -> MediaMetadataMutationResult.TrackUpdated(track) },
        )
    }

    suspend fun toggleArtistFavorite(artist: Artist): Boolean =
        toggleArtistFavoriteResult(artist).updated

    suspend fun toggleArtistFavoriteResult(artist: Artist): MediaMetadataMutationResult {
        val activeProvider = provider() ?: return MediaMetadataMutationResult.Skipped
        if (!activeProvider.capabilities.supportsArtistFavorites) return MediaMetadataMutationResult.Skipped
        return runMediaMutation(
            errorMessage = "Could not update artist favorite.",
            mutation = {
                favoriteArtistUpdate(
                    provider = activeProvider,
                    artist = artist,
                    favoritedAtIso8601 = favoritedAtIso8601(),
                )
            },
            updatedResult = { updatedArtist -> MediaMetadataMutationResult.ArtistUpdated(updatedArtist) },
        )
    }

    suspend fun toggleAlbumFavorite(album: Album): Boolean =
        toggleAlbumFavoriteResult(album).updated

    suspend fun toggleAlbumFavoriteResult(album: Album): MediaMetadataMutationResult {
        val activeProvider = provider() ?: return MediaMetadataMutationResult.Skipped
        if (!activeProvider.capabilities.supportsAlbumFavorites) return MediaMetadataMutationResult.Skipped
        return runMediaMutation(
            errorMessage = "Could not update album favorite.",
            mutation = {
                favoriteAlbumUpdate(
                    provider = activeProvider,
                    album = album,
                    favoritedAtIso8601 = favoritedAtIso8601(),
                )
            },
            updatedResult = { updatedAlbum -> MediaMetadataMutationResult.AlbumUpdated(updatedAlbum) },
        )
    }

    suspend fun setTrackRating(track: Track, rating: Int?): Boolean =
        setTrackRatingResult(track, rating).updated

    suspend fun setTrackRatingResult(track: Track, rating: Int?): MediaMetadataMutationResult {
        val activeProvider = provider() ?: return MediaMetadataMutationResult.Skipped
        if (!activeProvider.capabilities.supportsTrackRatings) return MediaMetadataMutationResult.Skipped
        return runMediaMutation(
            errorMessage = "Could not update rating.",
            mutation = { ratedTrackUpdate(activeProvider, track, rating) },
            updatedResult = { updatedTrack -> MediaMetadataMutationResult.TrackUpdated(updatedTrack) },
        )
    }

    private suspend fun <T> runMediaMutation(
        errorMessage: String,
        mutation: suspend () -> T?,
        updatedResult: (T) -> MediaMetadataMutationResult,
    ): MediaMetadataMutationResult {
        val result = runCatching { mutation() }
            .fold(
                onSuccess = { value ->
                    value?.let(updatedResult) ?: MediaMetadataMutationResult.Skipped
                },
                onFailure = { error ->
                    MediaMetadataMutationResult.Failed(error.message ?: errorMessage)
                },
            )
        return applyResult(result)
    }

    private fun applyResult(result: MediaMetadataMutationResult): MediaMetadataMutationResult {
        when (result) {
            is MediaMetadataMutationResult.TrackUpdated -> applyTrackUpdate(result.track)
            is MediaMetadataMutationResult.ArtistUpdated -> applyArtistUpdate(result.artist)
            is MediaMetadataMutationResult.AlbumUpdated -> applyAlbumUpdate(result.album)
            is MediaMetadataMutationResult.Failed,
            MediaMetadataMutationResult.Skipped,
            -> Unit
        }
        result.status?.let(setStatus)
        return result
    }

    private fun missingItem(message: String): MediaMetadataMutationResult {
        val result = MediaMetadataMutationResult.Failed(message)
        return applyResult(result)
    }
}

fun mediaMetadataMutationController(
    provider: () -> MediaProvider?,
    favoritedAtIso8601: () -> String,
    setStatus: (String) -> Unit,
    trackLookupSources: () -> MediaTrackLookupSources,
    homeContent: () -> HomeContent,
    setHomeContent: (HomeContent) -> Unit,
    searchResults: () -> MediaSearchResults,
    setSearchResults: (MediaSearchResults) -> Unit,
    albumDetails: () -> AlbumDetails?,
    setAlbumDetails: (AlbumDetails?) -> Unit,
    artistDetails: () -> ArtistDetails?,
    setArtistDetails: (ArtistDetails?) -> Unit,
    nowPlayingTrack: () -> Track?,
    setNowPlayingTrack: (Track?) -> Unit,
    tracks: () -> List<Track> = { emptyList() },
    setTracks: (List<Track>) -> Unit = {},
    extraKnownArtists: () -> List<Artist> = { emptyList() },
    extraKnownAlbums: () -> List<Album> = { emptyList() },
    updateExtraArtistCollections: (Artist) -> Unit = {},
    updateExtraAlbumCollections: (Album) -> Unit = {},
    afterTrackUpdate: (updatedTrack: Track, updatedNowPlaying: Track?) -> Unit = { _, _ -> },
): MediaMetadataMutationController =
    MediaMetadataMutationController(
        provider = provider,
        favoritedAtIso8601 = favoritedAtIso8601,
        setStatus = setStatus,
        knownTracks = { knownTracksForMediaActions(trackLookupSources()) },
        knownArtists = {
            knownArtistsForMetadata(
                homeContent = homeContent(),
                searchResults = searchResults(),
                artistDetails = artistDetails(),
                extraArtists = extraKnownArtists(),
            )
        },
        knownAlbums = {
            knownAlbumsForMetadata(
                homeContent = homeContent(),
                searchResults = searchResults(),
                albumDetails = albumDetails(),
                artistDetails = artistDetails(),
                extraAlbums = extraKnownAlbums(),
            )
        },
        applyTrackUpdate = { updatedTrack ->
            val updatedNowPlaying = MediaTrackMetadataStateUpdater(
                nowPlayingTrack = nowPlayingTrack,
                setNowPlayingTrack = setNowPlayingTrack,
                searchResults = searchResults,
                setSearchResults = setSearchResults,
                albumDetails = albumDetails,
                setAlbumDetails = setAlbumDetails,
                tracks = tracks,
                setTracks = setTracks,
            ).applyTrackUpdate(updatedTrack)
            afterTrackUpdate(updatedTrack, updatedNowPlaying)
        },
        applyArtistUpdate = { updatedArtist ->
            MediaMetadataStateUpdater(
                homeContent = homeContent,
                setHomeContent = setHomeContent,
                searchResults = searchResults,
                setSearchResults = setSearchResults,
                albumDetails = albumDetails,
                setAlbumDetails = setAlbumDetails,
                artistDetails = artistDetails,
                setArtistDetails = setArtistDetails,
                updateExtraArtistCollections = updateExtraArtistCollections,
                updateExtraAlbumCollections = updateExtraAlbumCollections,
            ).applyArtistUpdate(updatedArtist)
        },
        applyAlbumUpdate = { updatedAlbum ->
            MediaMetadataStateUpdater(
                homeContent = homeContent,
                setHomeContent = setHomeContent,
                searchResults = searchResults,
                setSearchResults = setSearchResults,
                albumDetails = albumDetails,
                setAlbumDetails = setAlbumDetails,
                artistDetails = artistDetails,
                setArtistDetails = setArtistDetails,
                updateExtraArtistCollections = updateExtraArtistCollections,
                updateExtraAlbumCollections = updateExtraAlbumCollections,
            ).applyAlbumUpdate(updatedAlbum)
        },
    )
