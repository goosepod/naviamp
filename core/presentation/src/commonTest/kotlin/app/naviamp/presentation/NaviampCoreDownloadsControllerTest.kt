package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.NaviampConnectionSettingsUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreDownloadsControllerTest {
    @Test
    fun refreshMapsStoragePoliciesAndPlaybackIntoAuthoritativeCoreState() = runTest {
        val fixture = fixture(this)

        fixture.controller.execute(NaviampCoreCommand.Downloads.Refresh)
        val screen = fixture.store.state.value.shell.downloads

        assertEquals(listOf("file-one", "file-two"), screen.downloads.map { it.id })
        assertEquals(3_000L, screen.downloadBytes)
        assertEquals(4L, screen.offlineDashboard.audioCacheCount)
        assertEquals(8_000L, screen.offlineDashboard.audioCacheBytes)
        assertEquals(3L, screen.offlineDashboard.pendingProviderActionCount)
        assertTrue(screen.keepFavoritesDownloaded)
        assertEquals("Downloads are up to date.", screen.status)

        fixture.controller.execute(
            NaviampCoreCommand.Downloads.TrackAction(
                DownloadedTrackActionRequest(screen.downloads[1], DownloadedTrackAction.Select),
            ),
        )
        assertEquals(listOf("one,two:1"), fixture.played)
    }

    @Test
    fun downloadedTrackMutationsAndPlaylistActionsAreCoreTransactions() = runTest {
        val fixture = fixture(this)
        fixture.controller.refresh(reconcile = false)
        val first = fixture.store.state.value.shell.downloads.downloads.first()

        fixture.controller.execute(
            NaviampCoreCommand.Downloads.TrackAction(
                DownloadedTrackActionRequest(
                    first,
                    DownloadedTrackAction.AddToPlaylist,
                    playlistChoice = NaviampPlaylistChoiceUi("playlist", "Playlist"),
                ),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.Downloads.TrackAction(
                DownloadedTrackActionRequest(
                    first,
                    DownloadedTrackAction.CreatePlaylistAndAdd,
                    playlistName = "Created",
                ),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.Downloads.TrackAction(
                DownloadedTrackActionRequest(first, DownloadedTrackAction.Remove),
            ),
        )

        assertEquals(listOf("playlist:one"), fixture.provider.added)
        assertEquals(listOf("Created:one"), fixture.provider.created)
        assertEquals(listOf("one"), fixture.storage.removed)
        assertEquals(listOf("two"), fixture.store.state.value.shell.downloads.downloads.map { it.track.id })

        fixture.controller.execute(NaviampCoreCommand.Downloads.DeleteAll)
        assertTrue(fixture.store.state.value.shell.downloads.downloads.isEmpty())
        assertEquals("Deleted 1 download.", fixture.store.state.value.shell.downloads.status)
    }

    @Test
    fun failedJobsRemainRetryableAndRetryUsesTheUnfinishedCoreJob() = runTest {
        val fixture = fixture(this)
        fixture.transfer.failNext = true

        fixture.controller.downloadTracks("selection", listOf(downloadTrack("one"), downloadTrack("two")))
        advanceUntilIdle()

        val failed = fixture.store.state.value.shell.downloads.jobs.single()
        assertTrue(failed.canRetry)
        assertEquals("Failed - 0 of 2 saved", failed.statusLabel)

        fixture.controller.execute(NaviampCoreCommand.Downloads.RetryJob(failed.id))
        advanceUntilIdle()

        assertEquals(2, fixture.transfer.requests.size)
        assertEquals(listOf("one", "two"), fixture.transfer.requests.last().tracks.map { it.id.value })
        assertTrue(fixture.store.state.value.shell.downloads.jobs.isEmpty())
    }

    @Test
    fun favoritesPolicyReconciliationAndDownloadLaunchAreOwnedByCore() = runTest {
        val fixture = fixture(this, initialFavoritesPolicy = false)

        fixture.controller.execute(NaviampCoreCommand.Downloads.ToggleKeepFavorites)
        advanceUntilIdle()

        assertEquals(listOf("favorite"), fixture.keep.reconciledTracks.map { it.id.value })
        assertEquals("Keeping Favorite tracks downloaded", fixture.transfer.requests.single().label)
        assertTrue(fixture.store.state.value.shell.downloads.keepFavoritesDownloaded)
    }

    @Test
    fun playlistKeepDownloadedOptionIsInterpretedByCore() = runTest {
        val fixture = fixture(this, initialFavoritesPolicy = false)

        fixture.controller.downloadPlaylist(
            playlist = Playlist("road-trip", "Road Trip", 2),
            tracks = listOf(downloadTrack("one"), downloadTrack("two")),
            option = app.naviamp.ui.KeepDownloadedActionValue,
        )
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), fixture.keep.reconciledTracks.map { it.id.value })
        assertEquals("Keeping Road Trip downloaded", fixture.transfer.requests.single().label)
        assertTrue(fixture.keep.policies("source").any { it.collectionId == "road-trip" })
    }

    @Test
    fun disconnectedAndStaleActionsProduceVisibleCoreFailures() = runTest {
        val fixture = fixture(this, connected = false)

        fixture.controller.execute(NaviampCoreCommand.Downloads.Refresh)
        assertEquals("Connect to Navidrome to use downloads.", fixture.store.state.value.shell.downloads.status)

        fixture.controller.execute(
            NaviampCoreCommand.Downloads.TrackAction(
                DownloadedTrackActionRequest(
                    downloadTrack("missing").let {
                        app.naviamp.ui.NaviampDownloadedTrackUi(
                            id = "missing",
                            track = app.naviamp.ui.SharedTrackRowUi(it.id.value, it.title, ""),
                            sizeBytes = 1,
                        )
                    },
                    DownloadedTrackAction.Select,
                ),
            ),
        )
        assertEquals("Downloaded track is no longer available.", fixture.store.state.value.shell.downloads.status)
    }
}

private fun fixture(
    scope: kotlinx.coroutines.CoroutineScope,
    connected: Boolean = true,
    initialFavoritesPolicy: Boolean = true,
): DownloadsFixture {
    val provider = DownloadsTestProvider()
    val store = NaviampCoreStateStore()
    if (connected) {
        store.updateShell { shell ->
            shell.copy(connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"))
        }
    }
    val storage = DownloadsTestStorage()
    val transfer = DownloadsTestTransfer(storage)
    val keep = DownloadsTestKeep(initialFavoritesPolicy)
    val played = mutableListOf<String>()
    return DownloadsFixture(
        store = store,
        provider = provider,
        storage = storage,
        transfer = transfer,
        keep = keep,
        controller = NaviampCoreDownloadsController(
            scope = scope,
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider.takeIf { connected } },
            storage = storage,
            transfer = transfer,
            keepDownloaded = keep,
            playback = NaviampCoreDownloadedPlaybackPort { tracks, index ->
                played += "${tracks.joinToString(",") { it.id.value }}:$index"
            },
        ),
        played = played,
    )
}

private data class DownloadsFixture(
    val store: NaviampCoreStateStore,
    val provider: DownloadsTestProvider,
    val storage: DownloadsTestStorage,
    val transfer: DownloadsTestTransfer,
    val keep: DownloadsTestKeep,
    val controller: NaviampCoreDownloadsController,
    val played: List<String>,
)

private class DownloadsTestStorage : NaviampCoreDownloadStoragePort {
    val downloads = mutableListOf(
        NaviampCoreDownloadedTrack("file-one", downloadTrack("one"), 1_000, "FLAC"),
        NaviampCoreDownloadedTrack("file-two", downloadTrack("two"), 2_000, "MP3"),
    )
    val removed = mutableListOf<String>()

    override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot(downloads, 4, 8_000, 3)
    override suspend fun pruneMissing(sourceId: String) = 0
    override suspend fun remove(sourceId: String, track: Track) {
        removed += track.id.value
        downloads.removeAll { it.track.id == track.id }
    }
    override suspend fun deleteAll(sourceId: String): Int = downloads.size.also { downloads.clear() }
}

private class DownloadsTestTransfer(
    private val storage: DownloadsTestStorage,
) : NaviampCoreDownloadTransferPort {
    val requests = mutableListOf<NaviampCoreDownloadTransferRequest>()
    var failNext = false

    override suspend fun transfer(
        request: NaviampCoreDownloadTransferRequest,
        onStatus: (String) -> Unit,
        onJobUpdate: (DownloadJobUpdate) -> Unit,
    ): NaviampCoreDownloadTransferResult {
        requests += request
        onJobUpdate(DownloadJobUpdate.Started)
        if (failNext) {
            failNext = false
            onJobUpdate(DownloadJobUpdate.Failed(request.tracks.first().id.value, "network"))
            onStatus("network")
            return NaviampCoreDownloadTransferResult(false)
        }
        request.tracks.forEach { track -> onJobUpdate(DownloadJobUpdate.TrackCompleted(track.id.value)) }
        onJobUpdate(DownloadJobUpdate.Completed)
        onStatus("Downloaded ${request.label}.")
        return NaviampCoreDownloadTransferResult(true)
    }
}

private class DownloadsTestKeep(initialFavoritesPolicy: Boolean) : NaviampCoreKeepDownloadedPort {
    private val policies = mutableListOf<KeepDownloadedCollectionPolicy>()
    var reconciledTracks = emptyList<Track>()
    init {
        if (initialFavoritesPolicy) policies += favoritePolicy()
    }

    override fun policies(sourceId: String) = policies.toList()
    override fun toggle(policy: KeepDownloadedCollectionPolicy): NaviampKeepDownloadedToggleResult {
        val existing = policies.removeAll { it.kind == policy.kind && it.collectionId == policy.collectionId }
        return if (existing) NaviampKeepDownloadedToggleResult.Disabled else NaviampKeepDownloadedToggleResult.Enable
    }
    override fun reconcile(
        policy: KeepDownloadedCollectionPolicy,
        tracks: List<Track>,
    ): NaviampKeepDownloadedReconciliationApplication {
        if (policies.none { it.kind == policy.kind && it.collectionId == policy.collectionId }) policies += policy
        reconciledTracks = tracks
        return NaviampKeepDownloadedReconciliationApplication(
            tracksToDownload = tracks,
            downloadLabel = "Keeping ${policy.name} downloaded",
            status = null,
            refreshDownloads = false,
        )
    }
}

private class DownloadsTestProvider : MediaProvider {
    override val id = ProviderId("downloads")
    override val displayName = "Downloads"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    val added = mutableListOf<String>()
    val created = mutableListOf<String>()

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun favoriteTracks(limit: Int) = listOf(downloadTrack("favorite"))
    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        added += "$playlistId:${trackIds.joinToString(",") { it.value }}"
    }
    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        created += "$name:${trackIds.joinToString(",") { it.value }}"
        return Playlist("created", name, trackIds.size)
    }
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"
}

private fun favoritePolicy() = KeepDownloadedCollectionPolicy(
    sourceId = "source",
    kind = KeepDownloadedCollectionKind.Favorites,
    collectionId = "favorite-tracks",
    name = "Favorite tracks",
)

private fun downloadTrack(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = id,
    audioInfo = null,
    replayGain = null,
)
