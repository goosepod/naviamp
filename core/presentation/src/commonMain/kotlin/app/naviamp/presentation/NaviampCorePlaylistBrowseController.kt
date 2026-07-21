package app.naviamp.presentation

import app.naviamp.domain.Playlist
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedTrackRowUi

data class NaviampCorePlaylistBrowseSupplement(
    val recentPlaylistIds: List<String> = emptyList(),
    val keepDownloadedPlaylistIds: Set<String> = emptySet(),
)

fun interface NaviampCorePlaylistBrowseSupplementSource {
    fun current(): NaviampCorePlaylistBrowseSupplement
}

/** Owns playlist list/detail browsing; mutations and playback are separate Core transactions. */
class NaviampCorePlaylistBrowseController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val navigationController: NaviampCoreNavigationController,
    private val supplementSource: NaviampCorePlaylistBrowseSupplementSource =
        NaviampCorePlaylistBrowseSupplementSource { NaviampCorePlaylistBrowseSupplement() },
    private val playlistLimit: Int = 500,
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
) : NaviampCoreCommandController {
    private var listGeneration = 0L
    private var detailGeneration = 0L
    private var playlistsById = emptyMap<String, Playlist>()

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        NaviampCoreCommand.Playlists.Refresh -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Playlists.ChangeSort -> {
            stateStore.updateShell { shell ->
                shell.copy(playlists = shell.playlists.copy(sortMode = command.sortMode))
            }
            NaviampCoreImmediateCommandResult.Handled()
        }
        is NaviampCoreCommand.Media.SelectPlaylist -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Media.ItemAction -> {
            val playlistCommand = command.request.command as? NaviampMediaItemCommand.Playlist
            if (playlistCommand?.command == NaviampPlaylistMediaCommand.Select) {
                NaviampCoreImmediateCommandResult.Deferred
            } else {
                NaviampCoreImmediateCommandResult.Unhandled
            }
        }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            NaviampCoreCommand.Playlists.Refresh -> refresh()
            is NaviampCoreCommand.Media.SelectPlaylist -> open(command.playlist)
            is NaviampCoreCommand.Media.ItemAction -> {
                val playlistCommand = command.request.command as? NaviampMediaItemCommand.Playlist ?: return null
                if (playlistCommand.command != NaviampPlaylistMediaCommand.Select) return null
                open(command.request.item)
            }
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun refresh(finalStatus: String? = null) {
        val generation = ++listGeneration
        stateStore.updateShell { shell ->
            shell.copy(playlists = shell.playlists.copy(refreshing = true, status = "Loading playlists..."))
        }
        val provider = providerSource.current()
        if (provider == null) {
            publishListFailure("Connect to Navidrome to load playlists.")
            return
        }
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        runCatching { provider.playlists(playlistLimit) }
            .onSuccess { playlists ->
                if (generation != listGeneration) return@onSuccess
                playlistsById = playlists.associateBy(Playlist::id)
                mediaRegistry.updatePlaylists(playlists)
                val supplement = supplementSource.current()
                stateStore.updateShell { shell ->
                    shell.copy(
                        playlists = shell.playlists.copy(
                            playlists = playlists.map { playlist ->
                                playlist.toSharedMediaItemUi(
                                    coverArtUrl = coverArtUrl,
                                    keepDownloadedActive = playlist.id in supplement.keepDownloadedPlaylistIds,
                                )
                            },
                            recentPlaylistIds = supplement.recentPlaylistIds,
                            status = finalStatus,
                            refreshing = false,
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == listGeneration) {
                    publishListFailure(cause.message ?: "Could not load playlists.")
                }
            }
    }

    internal suspend fun refreshAfterMutation(status: String) {
        refresh(finalStatus = status)
    }

    internal fun resolvePlaylist(item: SharedMediaItemUi): Playlist =
        playlistsById[item.id] ?: Playlist(
            id = item.id,
            name = item.title,
            trackCount = item.trackCount ?: 0,
            isSmart = item.isSmartPlaylist,
        )

    private suspend fun open(item: SharedMediaItemUi) {
        val generation = ++detailGeneration
        mediaRegistry.updateSelectedPlaylist(null, emptyList())
        navigationController.openPlaylistDetail()
        stateStore.updateShell { shell ->
            shell.copy(
                playlistDetail = shell.playlistDetail.copy(
                    selectedPlaylist = item,
                    detail = null,
                    status = "Loading ${item.title}...",
                ),
            )
        }
        val provider = providerSource.current()
        if (provider == null) {
            publishDetailFailure(item, "Connect to Navidrome to load a playlist.")
            return
        }
        val playlist = resolvePlaylist(item)
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        runCatching { provider.playlistTracks(playlist.id) }
            .onSuccess { tracks ->
                if (generation != detailGeneration) return@onSuccess
                val resolvedPlaylist = playlist.copy(trackCount = tracks.size)
                mediaRegistry.updateSelectedPlaylist(resolvedPlaylist, tracks)
                val mappedPlaylist = resolvedPlaylist.toSharedMediaItemUi(
                    coverArtUrl = coverArtUrl,
                    tracks = tracks,
                    keepDownloadedActive = playlist.id in supplementSource.current().keepDownloadedPlaylistIds,
                )
                stateStore.updateShell { shell ->
                    shell.copy(
                        playlistDetail = shell.playlistDetail.copy(
                            selectedPlaylist = mappedPlaylist,
                            detail = SharedPlaylistDetailUi(
                                playlist = mappedPlaylist,
                                tracks = tracks.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
                            ),
                            status = "Connected.",
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == detailGeneration) {
                    publishDetailFailure(item, cause.message ?: "Playlist failed to load.")
                }
            }
    }

    private fun publishListFailure(status: String) {
        stateStore.updateShell { shell ->
            shell.copy(playlists = shell.playlists.copy(refreshing = false, status = status))
        }
    }

    private fun publishDetailFailure(item: SharedMediaItemUi, status: String) {
        stateStore.updateShell { shell ->
            shell.copy(
                playlistDetail = shell.playlistDetail.copy(
                    selectedPlaylist = item,
                    detail = null,
                    status = status,
                ),
            )
        }
    }
}
