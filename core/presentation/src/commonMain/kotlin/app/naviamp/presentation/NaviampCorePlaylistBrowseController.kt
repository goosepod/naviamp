package app.naviamp.presentation

import app.naviamp.domain.Playlist
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.navibeat.navibeatMixOrNull
import app.naviamp.domain.smartplaylist.SmartPlaylistGenreOption
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedTrackRowUi
import app.naviamp.ui.toPlaylistChoiceUi

data class NaviampCorePlaylistBrowseSupplement(
    val recentPlaylistIds: List<String> = emptyList(),
    val keepDownloadedPlaylistIds: Set<String> = emptySet(),
    val genreCatalog: List<SmartPlaylistGenreOption> = emptyList(),
)

fun interface NaviampCorePlaylistBrowseSupplementSource {
    fun current(): NaviampCorePlaylistBrowseSupplement
}

fun naviampCorePlaylistBrowseSupplementSource(
    recentPlaylistIds: () -> List<String>,
    sourceId: () -> String?,
    keepDownloadedRepository: KeepDownloadedRepository,
    genreCatalog: () -> List<SmartPlaylistGenreOption> = { emptyList() },
): NaviampCorePlaylistBrowseSupplementSource = NaviampCorePlaylistBrowseSupplementSource {
    NaviampCorePlaylistBrowseSupplement(
        recentPlaylistIds = recentPlaylistIds(),
        keepDownloadedPlaylistIds = sourceId()
            ?.let(keepDownloadedRepository::keepDownloadedPolicies)
            .orEmpty()
            .filter { policy ->
                policy.kind == KeepDownloadedCollectionKind.Playlist ||
                    policy.kind == KeepDownloadedCollectionKind.SmartPlaylist
            }
            .mapTo(mutableSetOf()) { it.collectionId },
        genreCatalog = genreCatalog(),
    )
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
    private val playbackProfiles: NaviampCorePlaybackProfileController =
        NaviampCorePlaybackProfileController(stateStore),
) : NaviampCoreCommandController {
    // Mutations capture this separately from ordinary list/detail refresh generations.
    internal var sourceGeneration = 0L
        private set
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
        val supplement = supplementSource.current()
        stateStore.updateShell { shell ->
            shell.copy(
                playlists = shell.playlists.copy(
                    refreshing = true,
                    status = "Loading playlists...",
                    genreCatalog = supplement.genreCatalog,
                ),
            )
        }
        val provider = providerSource.current()
        if (provider == null) {
            publishListFailure("Connect to Navidrome to load playlists.")
            return
        }
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        runCatching { provider.playlists(playlistLimit) }
            .onSuccess { playlists ->
                if (generation != listGeneration || !providerSource.isCurrent(provider)) return@onSuccess
                playlistsById = playlists.associateBy(Playlist::id)
                mediaRegistry.updatePlaylists(playlists)
                val visiblePlaylists = playlists.filter { it.navibeatMixOrNull() == null }
                stateStore.updateShell { shell ->
                    shell.copy(
                        playlists = shell.playlists.copy(
                            playlists = visiblePlaylists.map { playlist ->
                                playlist.toSharedMediaItemUi(
                                    coverArtUrl = coverArtUrl,
                                    keepDownloadedActive = playlist.id in supplement.keepDownloadedPlaylistIds,
                                )
                            },
                            recentPlaylistIds = supplement.recentPlaylistIds,
                            status = finalStatus,
                            refreshing = false,
                        ),
                        playlistChoices = visiblePlaylists
                            .filterNot(Playlist::isSmart)
                            .filter(Playlist::canEdit)
                            .map(Playlist::toPlaylistChoiceUi),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == listGeneration && providerSource.isCurrent(provider)) {
                    publishListFailure(cause.message ?: "Could not load playlists.")
                }
            }
    }

    fun resetForSourceChange() {
        sourceGeneration++
        listGeneration++
        detailGeneration++
        playlistsById = emptyMap()
        mediaRegistry.updatePlaylists(emptyList())
        mediaRegistry.updateSelectedPlaylist(null, emptyList())
        stateStore.updateShell { shell -> shell.copy(
            playlists = app.naviamp.ui.NaviampPlaylistsScreenUi(sortMode = shell.playlists.sortMode),
            playlistChoices = emptyList(),
            playlistDetail = app.naviamp.ui.NaviampPlaylistDetailScreenUi(),
        ) }
    }

    suspend fun refreshAfterConnection() {
        resetForSourceChange()
        refresh()
    }

    internal suspend fun refreshAfterMutation(status: String) {
        refresh(finalStatus = status)
    }

    internal fun publishCreated(playlist: Playlist) {
        val provider = providerSource.current() ?: return
        ++listGeneration // A refresh begun before creation must not remove the new playlist.
        playlistsById = playlistsById + (playlist.id to playlist)
        mediaRegistry.updatePlaylists(playlistsById.values.toList())
        val mapped = playlist.toSharedMediaItemUi(coverArtUrl = { id -> id?.let(provider::coverArtUrl) })
        stateStore.updateShell { shell ->
            shell.copy(
                playlists = shell.playlists.copy(
                    playlists = shell.playlists.playlists.filterNot { it.id == playlist.id } + mapped,
                    refreshing = false,
                    status = null,
                ),
                playlistChoices = shell.playlistChoices.filterNot { it.id == playlist.id } + playlist.toPlaylistChoiceUi(),
            )
        }
    }

    internal fun reconcileContents(playlistId: String, tracks: List<app.naviamp.domain.Track>) {
        val provider = providerSource.current() ?: return
        val selected = stateStore.state.value.shell.playlistDetail.selectedPlaylist
        val playlist = playlistsById[playlistId]
            ?: selected?.takeIf { it.id == playlistId }?.let(::resolvePlaylist)
            ?: return
        val updated = playlist.copy(trackCount = tracks.size)
        ++listGeneration // An older list response cannot restore pre-mutation counts.
        playlistsById = playlistsById + (playlistId to updated)
        mediaRegistry.updatePlaylists(playlistsById.values.toList())
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        val mapped = updated.toSharedMediaItemUi(
            coverArtUrl = coverArtUrl,
            tracks = tracks,
            keepDownloadedActive = playlistId in supplementSource.current().keepDownloadedPlaylistIds,
        )
        if (selected?.id == playlistId) {
            ++detailGeneration // An older detail load cannot restore pre-mutation contents.
            mediaRegistry.updateSelectedPlaylist(updated, tracks)
        }
        stateStore.updateShell { shell ->
            shell.copy(
                playlists = shell.playlists.copy(
                    playlists = shell.playlists.playlists.map {
                        if (it.id == playlistId) mapped else it
                    },
                    refreshing = false,
                    status = null,
                ),
                playlistChoices = shell.playlistChoices.map {
                    if (it.id == playlistId) updated.toPlaylistChoiceUi() else it
                },
                playlistDetail = if (shell.playlistDetail.selectedPlaylist?.id == playlistId) {
                    shell.playlistDetail.copy(
                        selectedPlaylist = mapped,
                        detail = SharedPlaylistDetailUi(mapped, tracks.map { it.toSharedTrackRowUi(coverArtUrl) }),
                        status = null,
                    )
                } else shell.playlistDetail,
            )
        }
    }

    internal fun resolvePlaylist(item: SharedMediaItemUi): Playlist =
        playlistsById[item.id] ?: Playlist(
            id = item.id,
            name = item.title,
            trackCount = item.trackCount ?: 0,
            isSmart = item.isSmartPlaylist,
            canEdit = item.canEditPlaylist,
        )

    private suspend fun open(item: SharedMediaItemUi) {
        val generation = ++detailGeneration
        mediaRegistry.updateSelectedPlaylist(null, emptyList())
        navigationController.openPlaylistDetail()
        stateStore.updateShell { shell ->
            shell.copy(
                albumDetail = NaviampAlbumDetailScreenUi(),
                artistDetail = NaviampArtistDetailScreenUi(),
                playlistDetail = shell.playlistDetail.copy(
                    selectedPlaylist = item,
                    detail = null,
                    status = "Loading ${item.title}...",
                    genreCatalog = supplementSource.current().genreCatalog,
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
                if (generation != detailGeneration || !providerSource.isCurrent(provider)) return@onSuccess
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
                            playbackProfile = playbackProfiles.playlistProfile(resolvedPlaylist.id),
                            playbackProfileStatus = null,
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == detailGeneration && providerSource.isCurrent(provider)) {
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
