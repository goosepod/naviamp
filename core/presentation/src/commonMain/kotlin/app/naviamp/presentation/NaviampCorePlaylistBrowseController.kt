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
    private val offlinePlaylists: NaviampCoreOfflinePlaylistSource = NaviampCoreOfflinePlaylistSource(),
) : NaviampCoreCommandController {
    // Mutations capture this separately from ordinary list/detail refresh generations.
    internal var sourceGeneration = 0L
        private set
    private var listGeneration = 0L
    private var detailGeneration = 0L
    private var playlistsById = emptyMap<String, Playlist>()
    // Playlists currently presented from saved snapshots; their actions use only local audio.
    private var offlinePlaylistIds = emptySet<String>()
    private var offlineUnavailableTrackIds = emptySet<String>()
    // The last failed download per playlist; opening its page must show it, not a generic status.
    private var downloadFailures = emptyMap<String, String>()

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
        if (provider == null || connectionOffline()) {
            if (publishOfflineList(generation, provider, supplement)) return
            if (provider == null) {
                publishListFailure("Connect to Navidrome to load playlists.")
                return
            }
        }
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        loadLibraries(provider)
        runCatching { provider.playlists(playlistLimit) }
            .onSuccess { playlists ->
                if (generation != listGeneration || !providerSource.isCurrent(provider)) return@onSuccess
                playlistsById = playlists.associateBy(Playlist::id)
                offlinePlaylistIds = emptySet()
                mediaRegistry.updatePlaylists(playlists)
                currentSourceId()?.let { sourceId -> runCatching { offlinePlaylists.refreshDetails(sourceId, playlists) } }
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
                if (generation == listGeneration && providerSource.isCurrent(provider) &&
                    !publishOfflineList(generation, provider, supplement)) {
                    publishListFailure(cause.message ?: "Could not load playlists.")
                }
            }
    }

    internal fun isPresentedOffline(playlistId: String): Boolean = playlistId in offlinePlaylistIds

    /** Records how the latest download of [playlistId] ended; null clears an earlier failure. */
    internal fun recordDownloadFailure(playlistId: String, failure: String?) {
        downloadFailures = if (failure == null) downloadFailures - playlistId else downloadFailures + (playlistId to failure)
    }

    /** Shows a notice on the open playlist page, whose status line is rendered and announced. */
    internal fun publishDetailNotice(message: String) {
        stateStore.updateShell { shell -> shell.copy(playlistDetail = shell.playlistDetail.copy(status = message)) }
    }

    /** True for a row of the open saved copy whose audio is not on this device. */
    internal fun isUnavailableOffline(trackId: String): Boolean = trackId in offlineUnavailableTrackIds

    /** Re-reads availability so a file removed since the page opened is never queued. */
    internal suspend fun offlinePlayableTracks(playlistId: String): List<app.naviamp.domain.Track> {
        val sourceId = currentSourceId() ?: return emptyList()
        return offlinePlaylists.playlist(sourceId, playlistId)?.availability?.playableTracks.orEmpty()
    }

    private fun currentSourceId(): String? = stateStore.state.value.shell.connectionSettings.currentSourceId

    private fun connectionOffline(): Boolean = stateStore.state.value.shell.connectionSettings.connection.offline

    private fun offlineCoverArtUrl(provider: app.naviamp.domain.provider.MediaProvider?): (String?) -> String? =
        { id -> id?.let { provider?.coverArtUrl(it) } }

    /** Returns false when nothing is saved, so the caller keeps its ordinary failure. */
    private suspend fun publishOfflineList(
        generation: Long,
        provider: app.naviamp.domain.provider.MediaProvider?,
        supplement: NaviampCorePlaylistBrowseSupplement,
    ): Boolean {
        val sourceId = currentSourceId() ?: return false
        val listed = runCatching { offlinePlaylists.playlists(sourceId) }.getOrNull().orEmpty()
        if (listed.isEmpty()) return false
        if (generation != listGeneration || currentSourceId() != sourceId) return true
        val coverArtUrl = offlineCoverArtUrl(provider)
        val playlists = listed.map { it.playlist }
        playlistsById = playlists.associateBy(Playlist::id)
        offlinePlaylistIds = playlistsById.keys
        mediaRegistry.updatePlaylists(playlists)
        stateStore.updateShell { shell ->
            shell.copy(
                playlists = shell.playlists.copy(
                    playlists = listed.filter { it.playlist.navibeatMixOrNull() == null }.map { listing ->
                        listing.playlist.toSharedMediaItemUi(
                            coverArtUrl = coverArtUrl,
                            keepDownloadedActive = listing.playlist.id in supplement.keepDownloadedPlaylistIds,
                        ).copy(offlineAvailability = app.naviamp.ui.NaviampOfflineAvailabilityUi(
                            listing.availableCount, listing.summary.trackIds.size))
                    },
                    recentPlaylistIds = supplement.recentPlaylistIds,
                    status = null,
                    refreshing = false,
                ),
                playlistChoices = emptyList(),
            )
        }
        return true
    }

    private suspend fun publishOfflineDetail(
        generation: Long,
        item: SharedMediaItemUi,
        provider: app.naviamp.domain.provider.MediaProvider?,
    ): Boolean {
        val sourceId = currentSourceId() ?: return false
        val saved = runCatching { offlinePlaylists.playlist(sourceId, item.id) }.getOrNull() ?: return false
        if (generation != detailGeneration || currentSourceId() != sourceId) return true
        if (stateStore.state.value.shell.playlistDetail.selectedPlaylist?.id != item.id) return true
        val playlist = saved.playlist
        val availability = saved.availability
        val coverArtUrl = offlineCoverArtUrl(provider)
        playlistsById = playlistsById + (playlist.id to playlist)
        offlinePlaylistIds = offlinePlaylistIds + playlist.id
        offlineUnavailableTrackIds = availability.entries.filterNot { it.available }.mapTo(mutableSetOf()) { it.track.id.value }
        mediaRegistry.updateSelectedPlaylist(playlist, availability.playableTracks)
        val mapped = playlist.toSharedMediaItemUi(
            coverArtUrl = coverArtUrl,
            tracks = saved.snapshot.tracks,
            keepDownloadedActive = playlist.id in supplementSource.current().keepDownloadedPlaylistIds,
        ).copy(offlineAvailability = app.naviamp.ui.NaviampOfflineAvailabilityUi(
            availability.availableCount, availability.totalCount))
        stateStore.updateShell { shell ->
            shell.copy(
                playlistDetail = shell.playlistDetail.copy(
                    trackCapabilities = app.naviamp.ui.PlaylistTrackCapabilities(canAddToQueue = true),
                    selectedPlaylist = mapped,
                    detail = SharedPlaylistDetailUi(
                        playlist = mapped,
                        tracks = availability.entries.map { entry ->
                            entry.track.toSharedTrackRowUi(coverArtUrl).copy(
                                canToggleFavorite = false,
                                hasAlbum = false,
                                hasArtist = false,
                                canStartRadio = false,
                                unavailableOffline = !entry.available,
                            )
                        },
                    ),
                    status = downloadFailures[playlist.id],
                    playbackProfile = playbackProfiles.playlistProfile(playlist.id),
                    playbackProfileStatus = null,
                ),
            )
        }
        return true
    }

    fun resetForSourceChange() {
        sourceGeneration++
        listGeneration++
        detailGeneration++
        playlistsById = emptyMap()
        offlinePlaylistIds = emptySet()
        offlineUnavailableTrackIds = emptySet()
        downloadFailures = emptyMap()
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

    // Navidrome can expose a newly created smart playlist through OpenSubsonic
    // before its native playlist list identifies it as smart. Keep the creation
    // response's capabilities until the next ordinary refresh catches up.
    internal fun reconcileCreatedSmartPlaylist(created: Playlist) {
        val provider = providerSource.current() ?: return
        val current = playlistsById[created.id]
        if (current?.isSmart == true && current.canManage) return
        val corrected = created.copy(
            trackCount = current?.trackCount ?: created.trackCount,
            durationSeconds = current?.durationSeconds ?: created.durationSeconds,
            coverArtId = current?.coverArtId ?: created.coverArtId,
        )
        ++listGeneration
        playlistsById = playlistsById + (created.id to corrected)
        mediaRegistry.updatePlaylists(playlistsById.values.toList())
        val mapped = corrected.toSharedMediaItemUi(coverArtUrl = { id -> id?.let(provider::coverArtUrl) })
        stateStore.updateShell { shell ->
            shell.copy(
                playlists = shell.playlists.copy(
                    playlists = shell.playlists.playlists.filterNot { it.id == created.id } + mapped,
                ),
                playlistChoices = shell.playlistChoices.filterNot { it.id == created.id },
            )
        }
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
                        detail = SharedPlaylistDetailUi(mapped, tracks.map { it.toSharedTrackRowUi(coverArtUrl).copy(canToggleFavorite = provider.capabilities.supportsTrackFavorites) }),
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
            canManage = item.canManagePlaylist,
        )

    private suspend fun open(item: SharedMediaItemUi) {
        val generation = ++detailGeneration
        mediaRegistry.updateSelectedPlaylist(null, emptyList())
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
        navigationController.openPlaylistDetail()
        val provider = providerSource.current()
        if (provider == null || connectionOffline()) {
            if (publishOfflineDetail(generation, item, provider)) return
            if (provider == null) {
                publishDetailFailure(item, "Connect to Navidrome to load a playlist.")
                return
            }
        }
        val playlist = resolvePlaylist(item)
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        loadLibraries(provider)
        runCatching { provider.playlistTracks(playlist.id) }
            .onSuccess { tracks ->
                if (generation != detailGeneration || !providerSource.isCurrent(provider)) return@onSuccess
                if (stateStore.state.value.shell.playlistDetail.selectedPlaylist?.id != playlist.id) return@onSuccess
                val resolvedPlaylist = playlist.copy(trackCount = tracks.size)
                if (playlist.id in playlistsById) {
                    playlistsById = playlistsById + (playlist.id to resolvedPlaylist)
                    mediaRegistry.updatePlaylists(playlistsById.values.toList())
                }
                offlinePlaylistIds = offlinePlaylistIds - playlist.id
                offlineUnavailableTrackIds = emptySet()
                mediaRegistry.updateSelectedPlaylist(resolvedPlaylist, tracks)
                val mappedPlaylist = resolvedPlaylist.toSharedMediaItemUi(
                    coverArtUrl = coverArtUrl,
                    tracks = tracks,
                    keepDownloadedActive = playlist.id in supplementSource.current().keepDownloadedPlaylistIds,
                )
                stateStore.updateShell { shell ->
                    shell.copy(
                        playlists = shell.playlists.copy(playlists = shell.playlists.playlists.map { card ->
                            if (card.id == playlist.id) {
                                card.copy(trackCount = mappedPlaylist.trackCount, subtitle = mappedPlaylist.subtitle)
                            } else card
                        }),
                        playlistChoices = shell.playlistChoices.map { choice ->
                            if (choice.id == playlist.id) resolvedPlaylist.toPlaylistChoiceUi() else choice
                        },
                        playlistDetail = shell.playlistDetail.copy(
                            trackCapabilities = app.naviamp.ui.PlaylistTrackCapabilities(
                                canStartRadio = provider.capabilities.supportsTrackRadio,
                                canDownload = provider.capabilities.supportsDownloads,
                                canAddToQueue = true,
                                canAddToPlaylist = true,
                            ),
                            selectedPlaylist = mappedPlaylist,
                            detail = SharedPlaylistDetailUi(
                                playlist = mappedPlaylist,
                                tracks = tracks.map { track -> track.toSharedTrackRowUi(coverArtUrl).copy(
                                    canToggleFavorite = provider.capabilities.supportsTrackFavorites,
                                ) },
                            ),
                            status = downloadFailures[playlist.id] ?: "Connected.",
                            playbackProfile = playbackProfiles.playlistProfile(resolvedPlaylist.id),
                            playbackProfileStatus = null,
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == detailGeneration && providerSource.isCurrent(provider) &&
                    !publishOfflineDetail(generation, item, provider)) {
                    publishDetailFailure(item, cause.message ?: "Playlist failed to load.")
                }
            }
    }

    internal suspend fun loadLibraries(provider: app.naviamp.domain.provider.MediaProvider) {
        val generation = sourceGeneration
        val libraries = try {
            provider.libraries().map { app.naviamp.domain.settings.ConnectionFormMusicFolder(it.id, it.name) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed optional name lookup must not prevent browsing or discard known names.
            return
        }
        if (generation != sourceGeneration || !providerSource.isCurrent(provider)) return
        stateStore.updateShell { shell -> shell.copy(
            playlists = shell.playlists.copy(availableLibraries = libraries,
                selectedConnectionLibraryIds = provider.selectedMusicFolderIds),
            playlistDetail = shell.playlistDetail.copy(availableLibraries = libraries,
                selectedConnectionLibraryIds = provider.selectedMusicFolderIds),
        ) }
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
