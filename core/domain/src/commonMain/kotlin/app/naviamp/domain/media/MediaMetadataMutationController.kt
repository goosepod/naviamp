package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults

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

    suspend fun toggleTrackFavoriteById(trackId: String): Boolean {
        val track = findTrack(trackId) ?: return missingItem("Track not found.")
        return toggleTrackFavorite(track)
    }

    suspend fun toggleArtistFavoriteById(artistId: String): Boolean {
        val artist = findArtist(artistId) ?: return missingItem("Artist not found.")
        return toggleArtistFavorite(artist)
    }

    suspend fun toggleAlbumFavoriteById(albumId: String): Boolean {
        val album = findAlbum(albumId) ?: return missingItem("Album not found.")
        return toggleAlbumFavorite(album)
    }

    suspend fun toggleTrackFavorite(track: Track): Boolean {
        val activeProvider = provider() ?: return false
        if (!activeProvider.capabilities.supportsTrackFavorites) return false
        return runMediaMutation(
            errorMessage = "Could not update favorite.",
            mutation = {
                favoriteTrackUpdate(
                    provider = activeProvider,
                    track = track,
                    favoritedAtIso8601 = favoritedAtIso8601(),
                )
            },
            applyUpdate = applyTrackUpdate,
        )
    }

    suspend fun toggleArtistFavorite(artist: Artist): Boolean {
        val activeProvider = provider() ?: return false
        if (!activeProvider.capabilities.supportsArtistFavorites) return false
        return runMediaMutation(
            errorMessage = "Could not update artist favorite.",
            mutation = {
                favoriteArtistUpdate(
                    provider = activeProvider,
                    artist = artist,
                    favoritedAtIso8601 = favoritedAtIso8601(),
                )
            },
            applyUpdate = applyArtistUpdate,
        )
    }

    suspend fun toggleAlbumFavorite(album: Album): Boolean {
        val activeProvider = provider() ?: return false
        if (!activeProvider.capabilities.supportsAlbumFavorites) return false
        return runMediaMutation(
            errorMessage = "Could not update album favorite.",
            mutation = {
                favoriteAlbumUpdate(
                    provider = activeProvider,
                    album = album,
                    favoritedAtIso8601 = favoritedAtIso8601(),
                )
            },
            applyUpdate = applyAlbumUpdate,
        )
    }

    suspend fun setTrackRating(track: Track, rating: Int?): Boolean {
        val activeProvider = provider() ?: return false
        if (!activeProvider.capabilities.supportsTrackRatings) return false
        return runMediaMutation(
            errorMessage = "Could not update rating.",
            mutation = { ratedTrackUpdate(activeProvider, track, rating) },
            applyUpdate = applyTrackUpdate,
        )
    }

    private suspend fun <T> runMediaMutation(
        errorMessage: String,
        mutation: suspend () -> T?,
        applyUpdate: (T) -> Unit,
    ): Boolean {
        var updated = false
        runCatching { mutation() }
            .onSuccess { value ->
                value?.let {
                    applyUpdate(it)
                    updated = true
                }
            }
            .onFailure { error -> setStatus(error.message ?: errorMessage) }
        return updated
    }

    private fun missingItem(message: String): Boolean {
        setStatus(message)
        return false
    }
}
