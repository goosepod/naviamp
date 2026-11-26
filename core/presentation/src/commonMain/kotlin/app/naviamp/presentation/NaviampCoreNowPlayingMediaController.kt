package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import app.naviamp.domain.Track
import app.naviamp.domain.media.resolveTrackArtistNavigation
import app.naviamp.domain.media.favoriteTrackUpdate
import app.naviamp.domain.media.ratedTrackUpdate
import app.naviamp.domain.settings.LyricsDisplayPreference
import app.naviamp.ui.NaviampStatusMessage
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.cycleNaviampVisualizer
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
    private val generatedRadio: NaviampCoreTrackRadioTransactions,
    private val favoritedAtIso8601: () -> String,
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
    onPlaylistCreated: (app.naviamp.domain.Playlist) -> Unit = {},
    onPlaylistContentsReconciled: (String, List<Track>) -> Unit = { _, _ -> },
    membershipCoordinator: NaviampCorePlaylistMembershipCoordinator? = null,
    private val queueUndo: NaviampCoreQueueUndoController? = null,
) : NaviampCoreCommandController {
    private val membership = membershipCoordinator ?: NaviampCorePlaylistMembershipCoordinator(
        providerSource = providerSource,
        currentEditor = { stateStore.state.value.shell.playlistMembership },
        publish = { editor -> stateStore.updateShell { it.copy(playlistMembership = editor) } },
        onPlaylistChanged = downloads::playlistTracksChanged,
        onContentsReconciled = onPlaylistContentsReconciled,
        onPlaylistCreated = onPlaylistCreated,
    )

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.NowPlaying.Display ->
            if (command.request.action == NowPlayingDisplayAction.Collapse) {
                navigation.dispatch(NaviampCoreCommand.Navigation.CloseNowPlaying)
                NaviampCoreImmediateCommandResult.Handled()
            } else {
                NaviampCoreImmediateCommandResult.Deferred
            }
        is NaviampCoreCommand.NowPlaying.CurrentTrack,
        is NaviampCoreCommand.NowPlaying.Selection,
        is NaviampCoreCommand.NowPlaying.QueueItem,
        is NaviampCoreCommand.NowPlaying.TogglePlaylistMembership,
        is NaviampCoreCommand.NowPlaying.CreateMembershipPlaylist,
        NaviampCoreCommand.NowPlaying.RetryPlaylistMembership,
        NaviampCoreCommand.NowPlaying.ApplyPlaylistMembership,
        NaviampCoreCommand.NowPlaying.DismissPlaylistMembership,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.NowPlaying.Display -> display(command.request)
            is NaviampCoreCommand.NowPlaying.CurrentTrack -> return currentTrack(command.request)
            is NaviampCoreCommand.NowPlaying.Selection -> selection(command.request)
            is NaviampCoreCommand.NowPlaying.QueueItem -> queueItem(command.request)
            is NaviampCoreCommand.NowPlaying.TogglePlaylistMembership -> membership.toggle(command.playlistId)
            is NaviampCoreCommand.NowPlaying.CreateMembershipPlaylist -> membership.create(command.name)
            NaviampCoreCommand.NowPlaying.RetryPlaylistMembership -> membership.retry()
            NaviampCoreCommand.NowPlaying.ApplyPlaylistMembership -> membership.apply()
            NaviampCoreCommand.NowPlaying.DismissPlaylistMembership -> membership.dismiss()
            else -> return null
        }
        presenter.publish(playbackController.currentDisplay())
        return NaviampCoreCommandResult.Completed
    }

    suspend fun onTrackChanged(track: Track?) {
        playback.updateCurrentTrack(track)
        if (track != null) {
            sidecars.loadForTrack(track)
            val lyricsNeeded = playbackController.currentDisplay().lyricsVisible ||
                stateStore.state.value.shell.shellChrome.selectedVisualizer == NaviampVisualizer.LyricMirrorTunnel
            if (lyricsNeeded) sidecars.loadLyrics(track)
        }
        presenter.publish(playbackController.currentDisplay())
    }

    /** Applies an absolute favorite value requested by a trusted Connect controller. */
    internal suspend fun setConnectFavorite(mediaId: String, favorite: Boolean): Boolean {
        val track = playback.state.value.queue.tracks.firstOrNull { it.id.value == mediaId }
            ?: currentTrack()?.takeIf { it.id.value == mediaId }
            ?: return false
        if ((track.favoritedAtIso8601 != null) == favorite) return true
        val provider = providerSource.current() ?: return false
        val updated = runCatching {
            favoriteTrackUpdate(provider, track, favoritedAtIso8601())
        }.getOrNull() ?: return false
        mediaRegistry.updateTrack(updated)
        replaceTrack(updated)
        return (updated.favoritedAtIso8601 != null) == favorite
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
            }
            NowPlayingDisplayAction.SelectLyricsDisplayTiming -> {
                val preference = request.lyricsDisplayPreference
                if (preference != null && preference != LyricsDisplayPreference.MatchDownload) {
                    val current = stateStore.state.value.shell.playback.settings
                    val updated = settings.apply(
                        current.copy(lyricsDisplayPreference = preference),
                        redownload = false,
                    )
                    stateStore.updateShell { shell ->
                        shell.copy(playback = shell.playback.copy(settings = updated))
                    }
                    currentTrack()?.let { sidecars.loadLyrics(it) }
                }
            }
            NowPlayingDisplayAction.ToggleVisualizer ->
                playbackController.updateDisplay { it.copy(visualizerVisible = !it.visualizerVisible) }
            NowPlayingDisplayAction.SelectVisualizer -> {
                val visualizer = request.visualizer
                if (visualizer != null) {
                    selectVisualizer(visualizer)
                    if (visualizer == NaviampVisualizer.LyricMirrorTunnel) currentTrack()?.let { sidecars.loadLyrics(it) }
                }
            }
            NowPlayingDisplayAction.CycleVisualizer -> {
                val shell = stateStore.state.value.shell
                val nowPlaying = shell.nowPlaying
                if (nowPlaying?.visualizerAvailable == true && nowPlaying.visualizerVisible) {
                    request.visualizerDirection?.let { direction ->
                        cycleNaviampVisualizer(shell.shellChrome.selectedVisualizer, direction)?.let { visualizer ->
                            selectVisualizer(visualizer)
                            if (visualizer == NaviampVisualizer.LyricMirrorTunnel) {
                                currentTrack()?.let { sidecars.loadLyrics(it) }
                            }
                        }
                    }
                }
            }
            NowPlayingDisplayAction.SelectRadioDj -> selectRadioDj(request.radioDjId)
            NowPlayingDisplayAction.Collapse ->
                navigation.dispatch(NaviampCoreCommand.Navigation.CloseNowPlaying)
        }
    }

    private suspend fun currentTrack(request: NowPlayingCurrentTrackUiActionRequest): NaviampCoreCommandResult {
        val track = currentTrackOrPublish() ?: return NaviampCoreCommandResult.PlaybackActionApplied(false)
        request.target?.let { target ->
            if (target.trackId != track.id.value ||
                target.sourceId != stateStore.state.value.shell.connectionSettings.currentSourceId ||
                stateStore.state.value.shell.connect.remotePlaybackAuthorityActive ||
                target.queue?.let { it != playback.state.value.queue } == true) return NaviampCoreCommandResult.PlaybackActionApplied(false)
        }
        when (request.action) {
            NowPlayingCurrentTrackAction.StartRadio -> return NaviampCoreCommandResult.PlaybackActionApplied(generatedRadio.startTrackRadio(track))
            NowPlayingCurrentTrackAction.AddToPlaylist -> request.playlistChoice?.id?.let { addToPlaylist(track, it) }
                ?: membership.open(track)
            NowPlayingCurrentTrackAction.CreatePlaylistAndAdd -> createPlaylist(track, request.playlistName)
            NowPlayingCurrentTrackAction.Download -> downloads.downloadTracks(track.title, listOf(track), includeCompletedCount = false)
            NowPlayingCurrentTrackAction.GoToAlbum -> openAlbum(track)
            NowPlayingCurrentTrackAction.GoToArtist -> openArtist(track, request.artistId, request.artistName)
            NowPlayingCurrentTrackAction.ToggleFavorite -> return NaviampCoreCommandResult.PlaybackActionApplied(toggleFavorite(track))
            NowPlayingCurrentTrackAction.SetRating -> setRating(track, request.rating)
        }
        presenter.publish(playbackController.currentDisplay())
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun selection(request: NowPlayingSelectionActionRequest) {
        when (request.action) {
            NowPlayingSelectionAction.SelectQueueItem -> {
                val index = nowPlayingQueueIndex(request.item)
                if (index == null || index !in playback.state.value.queue.tracks.indices) {
                    publishStatus(NaviampStatusMessage.TrackUnavailable)
                } else {
                    val update = queue.selectIndex(index)
                    if (update.changed) {
                        playback.updateCurrentTrack(update.queue.current)
                        effects.selectExistingQueueItem(update.queue, update.queue.currentIndex)
                    }
                }
            }
            NowPlayingSelectionAction.SelectRelatedItem -> {
                val related = sidecars.snapshot().relatedTracks
                val action = app.naviamp.ui.nowPlayingRelatedIndex(request.item)
                if (action == null || action !in related.indices) publishStatus(NaviampStatusMessage.TrackUnavailable)
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
            NowPlayingItemAction.StartRadio -> track?.let { generatedRadio.startTrackRadio(it) } ?: staleTrack()
            NowPlayingItemAction.PlayTrackRadioNext ->
                track?.let { generatedRadio.addTrackRadio(it, playNext = true) } ?: staleTrack()
            NowPlayingItemAction.AddTrackRadioToQueue ->
                track?.let { generatedRadio.addTrackRadio(it, playNext = false) } ?: staleTrack()
            NowPlayingItemAction.PlayNext -> when (val target = request.target) {
                is NowPlayingItemTarget.QueueIndex -> applyQueueMutation(queue.moveToPlayNext(target.index))
                is NowPlayingItemTarget.QueueOccurrence -> applyQueueMutation(queue.moveToPlayNext(target.renderedIndex))
                else -> track?.let { applyQueueUpdate(queue.playNextTracks(listOf(it), "track")) } ?: staleTrack()
            }
            NowPlayingItemAction.PlayNextTrack -> when (val target = request.target) {
                is NowPlayingItemTarget.QueueIndex -> applyQueueMutation(queue.moveToNext(target.index))
                is NowPlayingItemTarget.QueueOccurrence -> applyQueueMutation(queue.moveToNext(target.renderedIndex))
                else -> track?.let { applyQueueUpdate(queue.playNextTrack(it)) } ?: staleTrack()
            }
            NowPlayingItemAction.AddToQueue ->
                track?.let { applyQueueUpdate(queue.appendTracks(listOf(it), "track")) } ?: staleTrack()
            NowPlayingItemAction.AddToPlaylist -> track?.let { selected ->
                resolved.playlistChoice?.id?.let { addToPlaylist(selected, it) } ?: membership.open(selected)
            } ?: staleTrack()
            NowPlayingItemAction.CreatePlaylistAndAdd -> track?.let { createPlaylist(it, resolved.playlistName) } ?: staleTrack()
            NowPlayingItemAction.Download -> track?.let {
                downloads.downloadTracks(it.title, listOf(it), includeCompletedCount = false)
            } ?: staleTrack()
            NowPlayingItemAction.GoToAlbum -> track?.let { openAlbum(it) } ?: staleTrack()
            NowPlayingItemAction.GoToArtist -> track?.let { openArtist(it, null, null) } ?: staleTrack()
            NowPlayingItemAction.ToggleFavorite -> track?.let { toggleFavorite(it) } ?: staleTrack()
            NowPlayingItemAction.RemoveFromQueue -> {
                val index = (request.target as? NowPlayingItemTarget.QueueIndex)?.index
                if (index == null) staleTrack() else {
                    val capture = queueUndo?.capture()?.takeIf { index != live.queue.currentIndex }
                    val update = queue.removeAt(index)
                    if (update.changed) queueUndo?.record(capture, app.naviamp.ui.NaviampQueueUndoKind.RemovedTrack,
                        update.queue, live.queue.tracks.getOrNull(index)?.title)
                    applyQueueMutation(update)
                }
            }
        }
    }

    private suspend fun selectRadioDj(id: String?) {
        val current = stateStore.state.value.shell.playback.settings
        val selected = id?.let { requested -> current.radioDjs.firstOrNull { it.id == requested } }
        if (id != null && selected == null) {
            publishStatus(NaviampStatusMessage.RadioDjUnavailable)
            return
        }
        val updated = current.copy(
            radioTuning = selected?.tuning ?: app.naviamp.domain.radio.RadioTuningSettings(),
            activeRadioDjId = selected?.id,
        )
        settings.apply(updated, redownload = false)
        stateStore.updateShell { shell -> shell.copy(playback = shell.playback.copy(settings = updated)) }
        currentTrack()?.let { generatedRadio.startTrackRadio(it) }
    }

    private fun selectVisualizer(visualizer: NaviampVisualizer) {
        visualizerSettings.save(visualizer)
        stateStore.updateShell { shell -> shell.copy(shellChrome = shell.shellChrome.copy(selectedVisualizer = visualizer)) }
    }

    private suspend fun addToPlaylist(track: Track, playlistId: String?) {
        val provider = providerOrPublish() ?: return
        if (playlistId.isNullOrBlank()) {
            publishPlaylistStatus(NaviampStatusMessage.ChoosePlaylist, "Choose a playlist first.")
            return
        }
        runCatching { provider.addTracksToPlaylist(playlistId, listOf(track.id)) }
            .onSuccess {
                downloads.playlistTracksChanged(playlistId)
                publishPlaylistStatus(NaviampStatusMessage.AddedToAPlaylist, "Added ${track.title} to playlist.")
            }
            .onFailure { publishPlaylistStatus(NaviampStatusMessage.PlaylistFailed, it.message ?: "Could not add track to playlist.") }
    }

    private suspend fun createPlaylist(track: Track, requestedName: String?) {
        val provider = providerOrPublish() ?: return
        val name = requestedName?.trim().orEmpty()
        if (name.isEmpty()) {
            publishPlaylistStatus(NaviampStatusMessage.PlaylistNameBlank, "Playlist name cannot be blank.")
            return
        }
        runCatching { provider.createPlaylist(name, listOf(track.id)) }
            .onSuccess { publishPlaylistStatus(NaviampStatusMessage.PlaylistCreated(name), "Created $name.") }
            .onFailure { publishPlaylistStatus(NaviampStatusMessage.PlaylistFailed, it.message ?: "Could not create playlist.") }
    }

    private suspend fun toggleFavorite(track: Track): Boolean {
        val provider = providerOrPublish() ?: return false
        val source = stateStore.state.value.vehicleSourceKey()
        try {
            val updated = favoriteTrackUpdate(provider, track, favoritedAtIso8601())
            currentCoroutineContext().ensureActive()
            if (source != stateStore.state.value.vehicleSourceKey() || !providerSource.isCurrent(provider)) return false
            if (updated == null) {
                publishStatus(NaviampStatusMessage.FavoritesUnsupported)
                return false
            }
            mediaRegistry.updateTrack(updated)
            // A delayed favorite must never replace a newly selected song.
            if (currentTrack()?.id == updated.id) playback.updateCurrentTrack(updated)
            val mutation = queue.updateTrack(updated)
            if (mutation.changed) effects.applyQueue(mutation.queue, mutation.clearPreparedNext)
            presenter.publish(playbackController.currentDisplay())
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (source == stateStore.state.value.vehicleSourceKey()) publishStatus(NaviampStatusMessage.FavoriteFailed)
            return false
        }
    }

    private suspend fun setRating(track: Track, rating: Int?) {
        val provider = providerOrPublish() ?: return
        val normalized = rating?.coerceIn(1, 5)
        runCatching { ratedTrackUpdate(provider, track, normalized) }
            .onSuccess { updated ->
                if (updated == null) publishStatus(NaviampStatusMessage.RatingsUnsupported) else replaceTrack(updated)
            }
            .onFailure { publishStatus(NaviampStatusMessage.RatingFailed) }
    }

    private suspend fun openAlbum(track: Track) {
        val albumId = track.albumId ?: run { publishStatus(NaviampStatusMessage.AlbumUnavailable); return }
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
            publishStatus(NaviampStatusMessage.ArtistUnavailable)
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
        update.outcome?.let { publishStatus(it.statusMessage()) }
        if (update.tracksChanged) effects.applyQueue(update.queue, clearPreparedNext = true)
    }

    private fun currentTrack(): Track? = playback.state.value.currentTrack ?: playback.state.value.queue.current

    private fun currentTrackOrPublish(): Track? = currentTrack().also {
        if (it == null) publishStatus(NaviampStatusMessage.TrackUnavailable)
    }

    private fun providerOrPublish() = providerSource.current().also {
        if (it == null) publishStatus(NaviampStatusMessage.ConnectionRequired)
    }

    private fun staleTrack() = publishStatus(NaviampStatusMessage.TrackUnavailable)

    /** [dialogText] keeps the playlist dialog's existing in-place line; the notice is the translated outcome. */
    private fun publishPlaylistStatus(message: NaviampStatusMessage, dialogText: String) {
        playbackController.updateDisplay { it.copy(playlistActionStatus = dialogText) }
        publishStatus(message)
    }

    private fun publishStatus(message: NaviampStatusMessage) = stateStore.publishStatus(message)
}
