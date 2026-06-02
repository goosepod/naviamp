package app.naviamp.desktop

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track
import app.naviamp.domain.media.favoriteTrackUpdate
import app.naviamp.domain.media.ratedTrackUpdate
import app.naviamp.domain.media.trackPlaybackSelection
import app.naviamp.domain.media.withUpdatedTrack
import app.naviamp.domain.cache.TrackMetadataRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.queueAppendPlan
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

class DesktopMediaActionsController(
    private val scope: CoroutineScope,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: PlaylistEngine,
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
    private val selectedAlbumDetails: () -> AlbumDetails?,
    private val setSelectedAlbumDetails: (AlbumDetails?) -> Unit,
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

    fun playRelatedTrack(index: Int) {
        playTracks(relatedTracks(), index = index)
    }

    fun playPopularTracks(tracks: List<Track>, index: Int = 0) {
        playTracks(tracks, index = index)
    }

    fun addPopularTracksToQueue(tracks: List<Track>) {
        val plan = queueAppendPlan(
            tracks = tracks,
            label = "popular tracks",
            existingTracks = playlistEngine.queue.tracks,
            deduplicateExisting = true,
        )
        if (tracks.isEmpty()) return
        if (plan.tracks.isNotEmpty()) {
            playlistEngine.appendTracks(plan.tracks)
        }
        setConnectionStatus(plan.status)
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        setNowPlayingTrack(
            nowPlayingTrack().withUpdatedTrack(updatedTrack),
        )
        setSearchResults(
            searchResults().withUpdatedTrack(updatedTrack),
        )
        setSelectedAlbumDetails(
            selectedAlbumDetails()?.withUpdatedTrack(updatedTrack),
        )
        playlistEngine.updateTrack(updatedTrack)
        trackMetadataRepository.updateTrack(updatedTrack)
    }

    fun toggleTrackFavorite(track: Track) {
        val provider = provider() ?: return
        if (!provider.capabilities.supportsTrackFavorites) return

        scope.launch {
            try {
                favoriteTrackUpdate(provider, track, favoritedAtIso8601 = Instant.now().toString())
                    ?.let(::applyTrackMetadataUpdate)
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not update favorite.")
            }
        }
    }

    fun setTrackRating(track: Track, rating: Int?) {
        val provider = provider() ?: return
        if (!provider.capabilities.supportsTrackRatings) return

        scope.launch {
            try {
                ratedTrackUpdate(provider, track, rating)
                    ?.let(::applyTrackMetadataUpdate)
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not update rating.")
            }
        }
    }

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
            callbacks = playlistCallbacks(),
        )
    }
}
