package app.naviamp.presentation

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.recentPlaylistIdsAfterPlayed
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistPreview
import app.naviamp.domain.smartplaylist.previewSmartPlaylist
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.SharedMediaItemUi

/** Audio-engine boundary used only after Core resolves the complete playback transaction. */
fun interface NaviampCorePlaylistPlaybackPort {
    suspend fun play(playlist: Playlist, tracks: List<Track>, shuffle: Boolean)
}

/** Common queue-owner boundary; composition delegates this to Naviamp's shared queue coordinator. */
fun interface NaviampCorePlaylistQueuePort {
    suspend fun addToQueue(playlist: Playlist, tracks: List<Track>)
}

/** Common download-owner boundary; composition delegates this to Naviamp's shared coordinator. */
fun interface NaviampCorePlaylistDownloadPort {
    suspend fun download(playlist: Playlist, tracks: List<Track>, option: String?)
}

fun interface NaviampCorePlaylistHistoryPort {
    suspend fun recordPlayed(currentIds: List<String>, playlistId: String): List<String>
}

/** Core-owned recency policy with an injected persistence effect. */
fun naviampCorePlaylistHistoryPort(
    persist: (List<String>) -> Unit = {},
): NaviampCorePlaylistHistoryPort = NaviampCorePlaylistHistoryPort { current, playlistId ->
    recentPlaylistIdsAfterPlayed(current, playlistId, limit = 50).also(persist)
}

fun interface NaviampCoreSmartPlaylistPreviewPort {
    suspend fun preview(definition: SmartPlaylistDefinition): SmartPlaylistPreview
}

fun naviampCoreSmartPlaylistPreviewPort(
    sourceId: () -> String?,
    libraryIndex: LocalLibraryIndexRepository,
    nowEpochMillis: () -> Long,
): NaviampCoreSmartPlaylistPreviewPort = NaviampCoreSmartPlaylistPreviewPort { definition ->
    val activeSourceId = sourceId()
        ?: return@NaviampCoreSmartPlaylistPreviewPort SmartPlaylistPreview(
            message = "Connect to a library to preview.",
        )
    previewSmartPlaylist(
        definition = definition,
        tracks = libraryIndex.libraryTracksForSmartPlaylistPreview(activeSourceId),
        nowEpochMillis = nowEpochMillis(),
    )
}

/** Owns playlist playback intent, mutations, smart-playlist policy, and result publication. */
class NaviampCorePlaylistTransactionController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val browseController: NaviampCorePlaylistBrowseController,
    private val playback: NaviampCorePlaylistPlaybackPort,
    private val queue: NaviampCorePlaylistQueuePort,
    private val downloads: NaviampCorePlaylistDownloadPort,
    private val history: NaviampCorePlaylistHistoryPort = naviampCorePlaylistHistoryPort(),
    private val sessionPort: NaviampCoreProviderSessionPort,
    private val preview: NaviampCoreSmartPlaylistPreviewPort = NaviampCoreSmartPlaylistPreviewPort {
        SmartPlaylistPreview(message = "Preview is not available for this connection.")
    },
    private val openNowPlaying: () -> Unit = {},
    private val onPlaylistTracksChanged: suspend (String) -> Unit = {},
) : NaviampCoreCommandController {
    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.Playlists.Detail,
        is NaviampCoreCommand.Playlists.UpdateTracks,
        is NaviampCoreCommand.SmartPlaylist,
        -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Media.ItemAction -> {
            val playlist = command.request.command as? NaviampMediaItemCommand.Playlist
            if (playlist?.command is NaviampPlaylistMediaCommand.Detail) {
                NaviampCoreImmediateCommandResult.Deferred
            } else {
                NaviampCoreImmediateCommandResult.Unhandled
            }
        }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? = when (command) {
        is NaviampCoreCommand.Playlists.Detail -> {
            executeDetail(command.request)
            NaviampCoreCommandResult.Completed
        }
        is NaviampCoreCommand.Playlists.UpdateTracks -> {
            updateTracks(command.playlist, command.tracks.map { TrackId(it.id) })
            NaviampCoreCommandResult.Completed
        }
        is NaviampCoreCommand.SmartPlaylist.Save -> {
            saveSmartPlaylist(command.definition, command.password)
            NaviampCoreCommandResult.Completed
        }
        is NaviampCoreCommand.SmartPlaylist.Preview ->
            NaviampCoreCommandResult.SmartPlaylistPreviewed(preview.preview(command.definition))
        is NaviampCoreCommand.SmartPlaylist.Update -> {
            updateSmartPlaylist(command.playlist, command.definition, command.password)
            NaviampCoreCommandResult.Completed
        }
        is NaviampCoreCommand.SmartPlaylist.Load ->
            NaviampCoreCommandResult.SmartPlaylistLoaded(loadSmartPlaylist(command.playlist, command.password))
        is NaviampCoreCommand.Media.ItemAction -> {
            val playlistCommand = command.request.command as? NaviampMediaItemCommand.Playlist ?: return null
            val detail = playlistCommand.command as? NaviampPlaylistMediaCommand.Detail ?: return null
            executeDetail(NaviampPlaylistDetailActionRequest(command.request.item, detail.command))
            NaviampCoreCommandResult.Completed
        }
        else -> null
    }

    private suspend fun executeDetail(request: NaviampPlaylistDetailActionRequest) {
        val provider = providerOrPublish() ?: return
        val playlist = browseController.resolvePlaylist(request.playlist)
        publishStatus("Loading ${playlist.name}...")
        runCatching {
            when (val command = request.command) {
                is NaviampPlaylistDetailCommand.Play -> {
                    val tracks = provider.playlistTracks(playlist.id).requireNotEmpty(playlist)
                    playback.play(playlist, tracks, command.shuffle)
                    openNowPlaying()
                    recordPlayed(playlist.id)
                }
                NaviampPlaylistDetailCommand.AddToQueue ->
                    queue.addToQueue(playlist, provider.playlistTracks(playlist.id))
                is NaviampPlaylistDetailCommand.Download -> {
                    publishListStatus("Starting download for ${playlist.name}...")
                    publishStatus("Download started.")
                    downloads.download(playlist, provider.playlistTracks(playlist.id), command.value)
                    return
                }
                is NaviampPlaylistDetailCommand.AddToPlaylist -> {
                    val tracks = provider.playlistTracks(playlist.id)
                    provider.addTracksToPlaylist(command.choice.id, tracks.map(Track::id))
                    onPlaylistTracksChanged(command.choice.id)
                    browseController.refreshAfterMutation("Added ${tracks.size} tracks to ${command.choice.name}.")
                    return
                }
                is NaviampPlaylistDetailCommand.CreatePlaylistAndAdd -> {
                    val tracks = provider.playlistTracks(playlist.id)
                    provider.createPlaylist(requireName(command.name), tracks.map(Track::id))
                    browseController.refreshAfterMutation("Created ${command.name.trim()}.")
                    return
                }
                is NaviampPlaylistDetailCommand.Copy -> {
                    val tracks = provider.playlistTracks(playlist.id)
                    val copiedTracks = if (command.deduplicate) tracks.distinctBy { it.id } else tracks
                    provider.createPlaylist(requireName(command.name), copiedTracks.map(Track::id))
                    browseController.refreshAfterMutation("Copied ${playlist.name}.")
                    return
                }
                is NaviampPlaylistDetailCommand.Rename -> {
                    val name = requireName(command.name)
                    provider.renamePlaylist(playlist.id, name)
                    browseController.refreshAfterMutation("Renamed playlist.")
                    updateSelectedPlaylistName(playlist.id, name)
                    return
                }
                NaviampPlaylistDetailCommand.Delete -> {
                    provider.deletePlaylist(playlist.id)
                    browseController.refreshAfterMutation("Deleted playlist.")
                    clearDeletedSelection(playlist.id)
                    return
                }
            }
            publishStatus("Connected.")
        }.onFailure { cause ->
            publishStatus(cause.message ?: "Playlist action failed.")
        }
    }

    private suspend fun updateTracks(item: SharedMediaItemUi, requestedTrackIds: List<TrackId>) {
        val provider = providerOrPublish() ?: return
        val playlist = browseController.resolvePlaylist(item)
        publishStatus("Updating ${playlist.name}...")
        try {
            val currentTrackIds = provider.playlistTracks(playlist.id).map(Track::id)
            provider.replacePlaylistTracks(
                playlistId = playlist.id,
                currentTrackIds = currentTrackIds,
                trackIds = requestedTrackIds,
            )
            onPlaylistTracksChanged(playlist.id)
            browseController.refreshAfterMutation("Updated playlist.")
            publishStatus("Updated playlist.")
        } catch (cause: Throwable) {
            publishStatus(cause.message ?: "Could not update playlist.")
            throw cause
        }
    }

    private suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition, password: String?) {
        val provider = smartProvider(password, "save")
        publishListStatus("Saving ${definition.name}...")
        try {
            provider.createSmartPlaylist(definition)
            sessionPort.persistActiveSession()
            browseController.refreshAfterMutation("Saved smart playlist ${definition.name}.")
        } catch (cause: Throwable) {
            sessionPort.persistActiveSession()
            publishListStatus(cause.message ?: "Could not save smart playlist.")
            throw cause
        }
    }

    private suspend fun updateSmartPlaylist(
        item: SharedMediaItemUi,
        definition: SmartPlaylistDefinition,
        password: String?,
    ) {
        val provider = smartProvider(password, "update")
        val playlist = browseController.resolvePlaylist(item)
        publishListStatus("Updating ${definition.name}...")
        try {
            provider.updateSmartPlaylist(playlist.id, definition)
            sessionPort.persistActiveSession()
            browseController.refreshAfterMutation("Updated smart playlist ${definition.name}.")
        } catch (cause: Throwable) {
            sessionPort.persistActiveSession()
            publishListStatus(cause.message ?: "Could not update smart playlist.")
            throw cause
        }
    }

    private suspend fun loadSmartPlaylist(
        item: SharedMediaItemUi,
        password: String?,
    ): SmartPlaylistDefinition {
        val provider = smartProvider(password, "load")
        val playlist = browseController.resolvePlaylist(item)
        publishListStatus("Loading ${playlist.name} rules...")
        return try {
            provider.smartPlaylistDefinition(playlist.id).also {
                sessionPort.persistActiveSession()
                publishListStatus(null)
            }
        } catch (cause: Throwable) {
            sessionPort.persistActiveSession()
            publishListStatus(cause.message ?: "Could not load smart playlist rules.")
            throw cause
        }
    }

    private fun providerOrPublish(): MediaProvider? = providerSource.current().also { provider ->
        if (provider == null) publishStatus("Connect to Navidrome to use playlists.")
    }

    private suspend fun smartProvider(password: String?, action: String): MediaProvider {
        val provider = sessionPort.smartPlaylistProvider(password)
        if (provider == null) {
            val message = "Connect to Navidrome before attempting to $action a smart playlist."
            publishListStatus(message)
            throw IllegalStateException(message)
        }
        if (!provider.capabilities.supportsSmartPlaylists) {
            val message = "The connected server does not support smart playlists."
            publishListStatus(message)
            throw UnsupportedOperationException(message)
        }
        return provider
    }

    private suspend fun recordPlayed(playlistId: String) {
        val current = stateStore.state.value.shell.playlists.recentPlaylistIds
        val updated = history.recordPlayed(current, playlistId)
        stateStore.updateShell { shell ->
            shell.copy(playlists = shell.playlists.copy(recentPlaylistIds = updated))
        }
    }

    private fun List<Track>.requireNotEmpty(playlist: Playlist): List<Track> =
        takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("${playlist.name} did not return any tracks.")

    private fun requireName(name: String): String =
        name.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Playlist name cannot be blank.")

    private fun updateSelectedPlaylistName(playlistId: String, name: String) {
        stateStore.updateShell { shell ->
            val selected = shell.playlistDetail.selectedPlaylist
            if (selected?.id != playlistId) return@updateShell shell
            val renamed = selected.copy(title = name)
            shell.copy(
                playlistDetail = shell.playlistDetail.copy(
                    selectedPlaylist = renamed,
                    detail = shell.playlistDetail.detail?.copy(playlist = renamed),
                ),
            )
        }
    }

    private fun clearDeletedSelection(playlistId: String) {
        stateStore.updateShell { shell ->
            if (shell.playlistDetail.selectedPlaylist?.id != playlistId) return@updateShell shell
            shell.copy(playlistDetail = app.naviamp.ui.NaviampPlaylistDetailScreenUi())
        }
    }

    private fun publishStatus(status: String?) {
        stateStore.updateShell { shell ->
            shell.copy(playlistDetail = shell.playlistDetail.copy(status = status))
        }
    }

    private fun publishListStatus(status: String?) {
        stateStore.updateShell { shell ->
            shell.copy(playlists = shell.playlists.copy(status = status))
        }
    }
}
