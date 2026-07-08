package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.cache.TrackMetadataRepository
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.media.MediaMetadataMutationController
import app.naviamp.domain.media.MediaTrackLookupSources
import app.naviamp.domain.media.mediaMetadataMutationController
import app.naviamp.domain.media.trackPlaybackSelection
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

class DesktopMediaActionsController(
    private val scope: CoroutineScope,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: DesktopPlaylistEngine,
    private val provider: () -> NavidromeProvider?,
    private val playbackSettings: () -> PlaybackSettings,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val albumTracks: () -> List<Track>,
    private val searchTracks: () -> List<Track>,
    private val relatedTracks: () -> List<Track>,
    private val nowPlayingTrack: () -> Track?,
    private val setNowPlayingTrack: (Track?) -> Unit,
    private val searchResults: () -> MediaSearchResults,
    private val setSearchResults: (MediaSearchResults) -> Unit,
    private val homeContent: () -> HomeContent,
    private val setHomeContent: (HomeContent) -> Unit,
    private val selectedAlbumDetails: () -> AlbumDetails?,
    private val setSelectedAlbumDetails: (AlbumDetails?) -> Unit,
    private val selectedArtistDetails: () -> ArtistDetails?,
    private val setSelectedArtistDetails: (ArtistDetails?) -> Unit,
    private val setArtistMixSelectedArtists: ((Artist) -> Unit)? = null,
    private val setArtistMixSuggestions: ((Artist) -> Unit)? = null,
    private val setAlbumMixSelectedAlbums: ((Album) -> Unit)? = null,
    private val setAlbumMixSuggestions: ((Album) -> Unit)? = null,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
    private val setConnectionStatus: (String) -> Unit,
) {
    fun playAlbumDetails(shuffle: Boolean = false, index: Int = 0) {
        playTracks(albumTracks(), index = index, shuffle = shuffle)
    }

    fun playSearchTrack(index: Int) {
        playTracks(searchTracks(), index = index)
    }

    fun searchTrackAt(index: Int): Track? =
        searchTracks().getOrNull(index)

    fun playRelatedTrack(index: Int) {
        playTracks(relatedTracks(), index = index)
    }

    fun playPopularTracks(tracks: List<Track>, index: Int = 0) {
        playTracks(tracks, index = index)
    }

    fun addPopularTracksToQueue(tracks: List<Track>) {
        val update = PlaybackQueueManager().appendTracks(
            currentQueue = playlistEngine.queue,
            tracksToAdd = tracks,
            label = "popular tracks",
            existingTracks = playlistEngine.queue.tracks,
            deduplicateExisting = true,
        )
        applyPlaybackQueueUpdate(
            update = update,
            setStatus = setConnectionStatus,
            replaceQueue = playlistEngine::replaceQueue,
        )
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        metadataMutationController().applyTrackUpdateResult(updatedTrack)
    }

    fun applyArtistMetadataUpdate(updatedArtist: Artist) {
        metadataMutationController().applyArtistUpdateResult(updatedArtist)
    }

    fun applyAlbumMetadataUpdate(updatedAlbum: Album) {
        metadataMutationController().applyAlbumUpdateResult(updatedAlbum)
    }

    fun toggleTrackFavorite(track: Track) {
        scope.launch {
            metadataMutationController().toggleTrackFavorite(track)
        }
    }

    fun toggleArtistFavorite(artist: Artist) {
        scope.launch {
            metadataMutationController().toggleArtistFavorite(artist)
        }
    }

    fun toggleAlbumFavorite(album: Album) {
        scope.launch {
            metadataMutationController().toggleAlbumFavorite(album)
        }
    }

    fun setTrackRating(track: Track, rating: Int?) {
        scope.launch {
            metadataMutationController().setTrackRating(track, rating)
        }
    }

    private fun metadataMutationController(): MediaMetadataMutationController =
        mediaMetadataMutationController(
            provider = provider,
            favoritedAtIso8601 = { Instant.now().toString() },
            setStatus = setConnectionStatus,
            trackLookupSources = {
                MediaTrackLookupSources(
                    primaryTracks = playlistEngine.queue.tracks,
                    extraTracks = albumTracks() + searchTracks() + relatedTracks(),
                )
            },
            homeContent = homeContent,
            setHomeContent = setHomeContent,
            searchResults = searchResults,
            setSearchResults = setSearchResults,
            albumDetails = selectedAlbumDetails,
            setAlbumDetails = setSelectedAlbumDetails,
            artistDetails = selectedArtistDetails,
            setArtistDetails = setSelectedArtistDetails,
            nowPlayingTrack = nowPlayingTrack,
            setNowPlayingTrack = setNowPlayingTrack,
            updateExtraArtistCollections = { artist ->
                setArtistMixSelectedArtists?.invoke(artist)
                setArtistMixSuggestions?.invoke(artist)
            },
            updateExtraAlbumCollections = { album ->
                setAlbumMixSelectedAlbums?.invoke(album)
                setAlbumMixSuggestions?.invoke(album)
            },
            afterTrackUpdate = { updatedTrack, _ ->
                playlistEngine.updateTrack(updatedTrack)
                trackMetadataRepository.updateTrack(updatedTrack)
            },
        )

    private fun playTracks(
        tracks: List<Track>,
        index: Int,
        shuffle: Boolean = false,
    ) {
        val provider = provider() ?: return
        val selection = trackPlaybackSelection(tracks, index = index, shuffle = shuffle) ?: return
        stopRadioContinuation()
        clearShuffleSnapshot()
        setOpenPlayerOnTrackStart(true)
        playlistEngine.playFrom(
            scope = scope,
            provider = provider,
            tracks = selection.tracks,
            index = selection.index,
            quality = playbackSettings().streamQuality(playbackEngine),
            replayGainMode = playbackSettings().replayGainMode,
            replayGainPreampDb = playbackSettings().replayGainPreampDb,
            callbacks = playlistCallbacks(),
        )
    }
}
