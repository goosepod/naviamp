package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.domain.Track
import app.naviamp.domain.media.resolveTrackArtistNavigation
import app.naviamp.domain.media.favoriteTrackUpdate
import app.naviamp.domain.media.ratedTrackUpdate
import app.naviamp.domain.radio.RadioService
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingDisplayAction
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingItemTarget
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.StationRowActionRequest
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.resolveAction

/** Owns Now Playing display, current-track, selection, and row-action product behavior. */
class NaviampCoreNowPlayingMediaController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val effects: NaviampCorePlaybackEffectPort,
    private val presenter: NaviampCoreNowPlayingPresenter,
    private val playbackController: NaviampCorePlaybackController,
    private val settings: NaviampCorePlaybackSettingsPort,
    private val visualizerSettings: NaviampCoreVisualizerSettingsPort,
    private val sidecars: NaviampCoreNowPlayingSidecarPort,
    private val downloads: NaviampCoreDownloadsController,
    private val mediaDetails: NaviampCoreMediaDetailController,
    private val navigation: NaviampCoreNavigationController,
    private val radio: NaviampCoreInternetRadioController,
    private val favoritedAtIso8601: () -> String,
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
) : NaviampCoreCommandController {
    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.NowPlaying.Display,
        is NaviampCoreCommand.NowPlaying.CurrentTrack,
        is NaviampCoreCommand.NowPlaying.Selection,
        is NaviampCoreCommand.NowPlaying.QueueItem,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.NowPlaying.Display -> display(command.request)
            is NaviampCoreCommand.NowPlaying.CurrentTrack -> currentTrack(command.request)
            is NaviampCoreCommand.NowPlaying.Selection -> selection(command.request)
            is NaviampCoreCommand.NowPlaying.QueueItem -> queueItem(command.request)
            else -> return null
        }
        presenter.publish(playbackController.currentDisplay())
        return NaviampCoreCommandResult.Completed
    }

    suspend fun onTrackChanged(track: Track?) {
        playback.updateCurrentTrack(track)
        if (track != null) sidecars.loadForTrack(track)
        presenter.publish(playbackController.currentDisplay())
    }

    private suspend fun display(request: NowPlayingDisplayActionRequest) {
        when (request.action) {
            NowPlayingDisplayAction.ToggleLyrics -> {
                val track = currentTrackOrPublish() ?: return
                val visible = !playbackController.currentDisplay().lyricsVisible
                playbackController.updateDisplay { it.copy(lyricsVisible = visible) }
                if (visible) sidecars.loadLyrics(track)
            }
            NowPlayingDisplayAction.ChangeLyricsOffset -> {
                val track = currentTrackOrPublish() ?: return
                request.lyricsOffsetMillis?.let { sidecars.changeLyricsOffset(track, it) }
                    ?: publishStatus("Lyrics offset is missing.")
            }
            NowPlayingDisplayAction.ToggleVisualizer ->
                playbackController.updateDisplay { it.copy(visualizerVisible = !it.visualizerVisible) }
            NowPlayingDisplayAction.SelectVisualizer -> {
                val visualizer = request.visualizer
                if (visualizer == null) {
                    publishStatus("Visualizer selection is missing.")
                } else {
                    selectVisualizer(visualizer)
                    if (visualizer == NaviampVisualizer.LyricMirrorTunnel) currentTrack()?.let { sidecars.loadLyrics(it) }
                }
            }
            NowPlayingDisplayAction.SelectRadioDj -> selectRadioDj(request.radioDjId)
            NowPlayingDisplayAction.Collapse ->
                navigation.dispatch(NaviampCoreCommand.Navigation.CloseNowPlaying)
        }
    }

    private suspend fun currentTrack(request: NowPlayingCurrentTrackUiActionRequest) {
        val track = currentTrackOrPublish() ?: return
        when (request.action) {
            NowPlayingCurrentTrackAction.StartRadio -> startTrackRadio(track)
            NowPlayingCurrentTrackAction.AddToPlaylist -> addToPlaylist(track, request.playlistChoice?.id)
            NowPlayingCurrentTrackAction.CreatePlaylistAndAdd -> createPlaylist(track, request.playlistName)
            NowPlayingCurrentTrackAction.Download -> downloads.downloadTracks(track.title, listOf(track), includeCompletedCount = false)
            NowPlayingCurrentTrackAction.GoToAlbum -> openAlbum(track)
            NowPlayingCurrentTrackAction.GoToArtist -> openArtist(track, request.artistId, request.artistName)
            NowPlayingCurrentTrackAction.ToggleFavorite -> toggleFavorite(track)
            NowPlayingCurrentTrackAction.SetRating -> setRating(track, request.rating)
        }
    }

    private suspend fun selection(request: NowPlayingSelectionActionRequest) {
        when (request.action) {
            NowPlayingSelectionAction.SelectQueueItem -> {
                val index = nowPlayingQueueIndex(request.item)
                if (index == null || index !in playback.state.value.queue.tracks.indices) {
                    publishStatus("Queue item is no longer available.")
                } else {
                    val update = queue.selectIndex(index)
                    if (update.changed) {
                        playback.updateCurrentTrack(update.queue.current)
                        effects.playQueueSelection(update.queue, update.queue.currentIndex)
                    }
                }
            }
            NowPlayingSelectionAction.SelectRelatedItem -> {
                val related = sidecars.snapshot().relatedTracks
                val action = app.naviamp.ui.nowPlayingRelatedIndex(request.item)
                if (action == null || action !in related.indices) publishStatus("Related track is no longer available.")
                else startQueue(related, action)
            }
            NowPlayingSelectionAction.SelectRadioStation ->
                radio.execute(
                    NaviampCoreCommand.Radio.StationAction(
                        StationRowActionRequest(
                            SharedMediaItemUi(request.item.id, request.item.title, request.item.subtitle),
                            StationRowAction.Select,
                        ),
                    ),
                )
        }
    }

    private suspend fun queueItem(request: NowPlayingItemActionRequest) {
        val live = playback.state.value
        val related = sidecars.snapshot().relatedTracks
        val resolved = request.resolveAction(live.queue.tracks, related)
        val track = resolved.track
        when (resolved.action) {
            NowPlayingItemAction.StartRadio -> track?.let { startTrackRadio(it) } ?: staleTrack()
            NowPlayingItemAction.PlayTrackRadioNext -> track?.let { addTrackRadio(it, playNext = true) } ?: staleTrack()
            NowPlayingItemAction.AddTrackRadioToQueue -> track?.let { addTrackRadio(it, playNext = false) } ?: staleTrack()
            NowPlayingItemAction.PlayNext -> when (val target = request.target) {
                is NowPlayingItemTarget.QueueIndex -> applyQueueMutation(queue.moveToNext(target.index))
                else -> track?.let { applyQueueUpdate(queue.playNextTracks(listOf(it), "track")) } ?: staleTrack()
            }
            NowPlayingItemAction.AddToQueue ->
                track?.let { applyQueueUpdate(queue.appendTracks(listOf(it), "track")) } ?: staleTrack()
            NowPlayingItemAction.AddToPlaylist -> track?.let { addToPlaylist(it, resolved.playlistChoice?.id) } ?: staleTrack()
            NowPlayingItemAction.CreatePlaylistAndAdd -> track?.let { createPlaylist(it, resolved.playlistName) } ?: staleTrack()
            NowPlayingItemAction.Download -> track?.let {
                downloads.downloadTracks(it.title, listOf(it), includeCompletedCount = false)
            } ?: staleTrack()
            NowPlayingItemAction.GoToAlbum -> track?.let { openAlbum(it) } ?: staleTrack()
            NowPlayingItemAction.GoToArtist -> track?.let { openArtist(it, null, null) } ?: staleTrack()
            NowPlayingItemAction.ToggleFavorite -> track?.let { toggleFavorite(it) } ?: staleTrack()
            NowPlayingItemAction.RemoveFromQueue -> {
                val index = (request.target as? NowPlayingItemTarget.QueueIndex)?.index
                if (index == null) staleTrack() else applyQueueMutation(queue.removeAt(index))
            }
        }
    }

    private suspend fun startTrackRadio(seed: Track) {
        val provider = providerOrPublish() ?: return
        publishStatus("Building track radio...")
        runCatching {
            val settings = stateStore.state.value.shell.playback.settings
            RadioService(provider, tuning = settings.radioTuning)
                .trackRadio(seed, settings.sonicSimilarityEnabled)
        }.onSuccess { fetched ->
            if (fetched.isEmpty()) publishStatus("Track radio did not return any tracks.")
            else {
                val update = queue.replaceGeneratedRadioUpcomingTracks(
                    currentTrack = seed,
                    fetchedTracks = fetched,
                    requestIsCurrent = playback.state.value.currentTrack?.id == seed.id,
                )
                applyQueueMutation(update)
                publishStatus("Playing track radio.")
            }
        }.onFailure { publishStatus(it.message ?: "Could not build track radio.") }
    }

    private suspend fun addTrackRadio(seed: Track, playNext: Boolean) {
        val provider = providerOrPublish() ?: return
        runCatching {
            val settings = stateStore.state.value.shell.playback.settings
            RadioService(provider, tuning = settings.radioTuning)
                .trackRadio(seed, settings.sonicSimilarityEnabled)
        }.onSuccess { tracks ->
            if (playNext) applyQueueUpdate(queue.playNextTracks(tracks, "radio tracks"))
            else applyQueueUpdate(queue.appendTracks(tracks, "radio tracks"))
        }.onFailure { publishStatus(it.message ?: "Could not load track radio.") }
    }

    private suspend fun selectRadioDj(id: String?) {
        val current = stateStore.state.value.shell.playback.settings
        val selected = id?.let { requested -> current.radioDjs.firstOrNull { it.id == requested } }
        if (id != null && selected == null) {
            publishStatus("Radio DJ is no longer available.")
            return
        }
        val updated = current.copy(
            radioTuning = selected?.tuning ?: app.naviamp.domain.radio.RadioTuningSettings(),
            activeRadioDjId = selected?.id,
        )
        settings.apply(updated, redownload = false)
        stateStore.updateShell { shell -> shell.copy(playback = shell.playback.copy(settings = updated)) }
        currentTrack()?.let { startTrackRadio(it) }
        publishStatus(selected?.let { "Selected ${it.name} DJ." } ?: "Default radio selected.")
    }

    private fun selectVisualizer(visualizer: NaviampVisualizer) {
        visualizerSettings.save(visualizer)
        stateStore.updateShell { shell -> shell.copy(shellChrome = shell.shellChrome.copy(selectedVisualizer = visualizer)) }
    }

    private suspend fun addToPlaylist(track: Track, playlistId: String?) {
        val provider = providerOrPublish() ?: return
        if (playlistId.isNullOrBlank()) {
            publishPlaylistStatus("Choose a playlist first.")
            return
        }
        runCatching { provider.addTracksToPlaylist(playlistId, listOf(track.id)) }
            .onSuccess { publishPlaylistStatus("Added ${track.title} to playlist.") }
            .onFailure { publishPlaylistStatus(it.message ?: "Could not add track to playlist.") }
    }

    private suspend fun createPlaylist(track: Track, requestedName: String?) {
        val provider = providerOrPublish() ?: return
        val name = requestedName?.trim().orEmpty()
        if (name.isEmpty()) {
            publishPlaylistStatus("Playlist name cannot be blank.")
            return
        }
        runCatching { provider.createPlaylist(name, listOf(track.id)) }
            .onSuccess { publishPlaylistStatus("Created $name.") }
            .onFailure { publishPlaylistStatus(it.message ?: "Could not create playlist.") }
    }

    private suspend fun toggleFavorite(track: Track) {
        val provider = providerOrPublish() ?: return
        runCatching { favoriteTrackUpdate(provider, track, favoritedAtIso8601()) }
            .onSuccess { updated ->
                if (updated == null) publishStatus("Track favorites are not supported.") else {
                    mediaRegistry.updateTrack(updated)
                    replaceTrack(updated)
                }
            }
            .onFailure { publishStatus(it.message ?: "Could not update favorite.") }
    }

    private suspend fun setRating(track: Track, rating: Int?) {
        val provider = providerOrPublish() ?: return
        val normalized = rating?.coerceIn(1, 5)
        runCatching { ratedTrackUpdate(provider, track, normalized) }
            .onSuccess { updated ->
                if (updated == null) publishStatus("Track ratings are not supported.") else replaceTrack(updated)
            }
            .onFailure { publishStatus(it.message ?: "Could not update rating.") }
    }

    private suspend fun openAlbum(track: Track) {
        val albumId = track.albumId ?: run { publishStatus("Album is not available for this track."); return }
        mediaDetails.execute(
            NaviampCoreCommand.Media.ItemAction(
                app.naviamp.ui.NaviampMediaItemActionRequest(
                    SharedMediaItemUi(albumId.value, track.albumTitle ?: "Album", track.artistName),
                    app.naviamp.ui.NaviampMediaItemCommand.Album(app.naviamp.ui.NaviampArtistAlbumCommand.Select),
                ),
            ),
        )
    }

    private suspend fun openArtist(track: Track, requestedId: String?, requestedName: String?) {
        val artist = resolveTrackArtistNavigation(track, requestedId, requestedName) { query, limit ->
            providerSource.current()?.search(query, limit)?.artists.orEmpty()
        }
        if (artist == null) {
            publishStatus("Artist is not available for this track.")
            return
        }
        mediaDetails.execute(
            NaviampCoreCommand.Media.ItemAction(
                app.naviamp.ui.NaviampMediaItemActionRequest(
                    SharedMediaItemUi(artist.id.value, artist.name, ""),
                    app.naviamp.ui.NaviampMediaItemCommand.Artist(app.naviamp.ui.NaviampArtistMediaCommand.Select),
                ),
            ),
        )
    }

    private fun replaceTrack(updated: Track) {
        playback.updateCurrentTrack(updated)
        val mutation = queue.updateTrack(updated)
        if (mutation.changed) effects.applyQueue(mutation.queue, mutation.clearPreparedNext)
        presenter.publish(playbackController.currentDisplay())
    }

    private fun startQueue(tracks: List<Track>, index: Int) {
        val update = queue.startQueue(tracks, index)
        if (update.changed) effects.applyQueue(update.queue, update.clearPreparedNext)
        playback.updateCurrentTrack(update.queue.current)
        effects.playQueueSelection(update.queue, update.queue.currentIndex)
    }

    private fun applyQueueMutation(update: app.naviamp.domain.playback.PlaybackQueueMutationUpdate) {
        if (update.changed) effects.applyQueue(update.queue, update.clearPreparedNext)
    }

    private fun applyQueueUpdate(update: app.naviamp.domain.playback.PlaybackQueueUpdate) {
        publishStatus(update.status)
        if (update.tracksChanged) effects.applyQueue(update.queue, clearPreparedNext = true)
    }

    private fun currentTrack(): Track? = playback.state.value.currentTrack ?: playback.state.value.queue.current

    private fun currentTrackOrPublish(): Track? = currentTrack().also {
        if (it == null) publishStatus("No track is currently selected.")
    }

    private fun providerOrPublish() = providerSource.current().also {
        if (it == null) publishStatus("Connect to Navidrome to use this action.")
    }

    private fun staleTrack() = publishStatus("Track is no longer available.")

    private fun publishPlaylistStatus(message: String) {
        playbackController.updateDisplay { it.copy(playlistActionStatus = message) }
        publishStatus(message)
    }

    private fun publishStatus(message: String) {
        stateStore.update { state -> state.copy(overlays = state.overlays.copy(status = message)) }
    }
}
