package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.OfflinePlaylistRepository
import app.naviamp.domain.cache.OfflinePlaylistRetention
import app.naviamp.domain.cache.OfflinePlaylistSnapshot
import app.naviamp.domain.cache.OfflinePlaylistSummary
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.ui.KeepDownloadedActionValue
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampOfflineAvailabilityUi
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.toSharedMediaItemUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cold launch, browsing, playback, reconnection and removal of downloaded playlists through Core. */
@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreOfflinePlaylistsTest {
    private val record = NaviampCoreSavedConnectionRecord("source", "Home", "https://music.example", "demo")
    private fun track(id: String) = FakeCoreMediaProvider().track.copy(id = TrackId(id), title = "Track $id")
    private val roadTrip = Playlist("road", "Road Trip", trackCount = 4)
    private val unplayable = Playlist("quiet", "Quiet", trackCount = 1)

    private class MemoryOfflinePlaylists : OfflinePlaylistRepository {
        val saved = linkedMapOf<Pair<String, String>, OfflinePlaylistSnapshot>()
        override fun offlinePlaylistSummaries(sourceId: String) = saved.values.filter { it.sourceId == sourceId }
            .map { OfflinePlaylistSummary(it.sourceId, it.playlist, it.tracks.map { track -> track.id.value }, it.retention) }
        override fun offlinePlaylist(sourceId: String, playlistId: String) = saved[sourceId to playlistId]
        override fun saveOfflinePlaylist(snapshot: OfflinePlaylistSnapshot) {
            saved[snapshot.sourceId to snapshot.playlist.id] = snapshot
        }
        override fun updateOfflinePlaylistDetails(sourceId: String, playlist: Playlist) {
            saved[sourceId to playlist.id]?.let { saved[sourceId to playlist.id] = it.copy(playlist = playlist) }
        }
        override fun deleteOfflinePlaylist(sourceId: String, playlistId: String) {
            saved.remove(sourceId to playlistId)
        }
        override fun pruneOrphanedOfflinePlaylists(sourceId: String, removedTrackId: String?) = Unit
    }

    private class Fixture(
        val core: NaviampCore,
        val snapshots: MemoryOfflinePlaylists,
        val effects: FakeCorePlaybackEffects,
        val server: Server,
        val keep: KeepPolicies,
    )

    private class Server(var reachable: Boolean, var playlists: List<Playlist>, val tracks: MutableMap<String, List<Track>>) {
        var requests = 0
        var connects = 0
        var network: app.naviamp.domain.network.NetworkMetering = app.naviamp.domain.network.NetworkMetering.Unmetered
    }

    private class KeepPolicies(var policies: List<KeepDownloadedCollectionPolicy>) : NaviampCoreKeepDownloadedPort {
        override fun policies(sourceId: String) = policies.filter { it.sourceId == sourceId }
        override fun toggle(policy: KeepDownloadedCollectionPolicy): NaviampKeepDownloadedToggleResult =
            if (policies.any { it.collectionId == policy.collectionId }) {
                policies = policies.filterNot { it.collectionId == policy.collectionId }
                NaviampKeepDownloadedToggleResult.Disabled
            } else {
                policies = policies + policy
                NaviampKeepDownloadedToggleResult.Enable
            }
        override fun reconcile(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) =
            NaviampKeepDownloadedReconciliationApplication(emptyList(), null, null, false)
    }

    private fun TestScope.fixture(
        reachable: Boolean,
        local: Set<String> = setOf("a", "c"),
        policies: List<KeepDownloadedCollectionPolicy> = emptyList(),
        /** Null: the host cannot observe the network (Desktop). */
        initialNetwork: app.naviamp.domain.network.NetworkMetering? = null,
    ): Fixture {
        val snapshots = MemoryOfflinePlaylists().apply {
            saveOfflinePlaylist(OfflinePlaylistSnapshot("source", roadTrip, listOf("a", "b", "a", "c").map(::track),
                OfflinePlaylistRetention.KeepDownloaded))
            saveOfflinePlaylist(OfflinePlaylistSnapshot("source", unplayable, listOf(track("x"))))
        }
        val server = Server(reachable, listOf(roadTrip, unplayable), mutableMapOf())
        initialNetwork?.let { server.network = it }
        val fake = FakeCoreMediaProvider()
        val provider = object : MediaProvider by fake {
            override suspend fun playlists(limit: Int): List<Playlist> {
                server.requests++
                check(server.reachable) { "Failed to connect to server" }
                return server.playlists
            }
            override suspend fun playlistTracks(playlistId: String): List<Track> {
                server.requests++
                check(server.reachable) { "Failed to connect to server" }
                return server.tracks.getValue(playlistId)
            }
        }
        val effects = FakeCorePlaybackEffects()
        val keep = KeepPolicies(policies)
        val defaults = fakeCoreServices(provider, playbackEffects = effects)
        val services = defaults.copy(
            connection = object : NaviampCoreProviderSessionPort by defaults.connection {
                override suspend fun connect(
                    request: NaviampCoreConnectionRequest,
                    plan: app.naviamp.app.NaviampConnectionAttemptPlan,
                ): NaviampCoreConnectedSession {
                    server.connects++
                    check(server.reachable) { "Failed to connect to server" }
                    return NaviampCoreConnectedSession("source", "Home",
                        inventory = NaviampCoreConnectionInventory(listOf(record), "source"))
                }
            },
            downloads = defaults.downloads.copy(
                storage = object : NaviampCoreDownloadStoragePort {
                    override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot()
                    override suspend fun availableDownloads(sourceId: String) =
                        local.map { NaviampCoreDownloadedTrack(it, track(it), 100) }
                    override suspend fun pruneMissing(sourceId: String) = 0
                    override suspend fun remove(sourceId: String, track: Track) = Unit
                    override suspend fun deleteAll(sourceId: String) = 0
                },
                keepDownloaded = keep,
                offlinePlaylists = snapshots,
            ),
            // The one path every host uses: the network fact supplied to the playback catalog.
            playback = defaults.playback.copy(networkMetering = app.naviamp.domain.network.NetworkMeteringSource {
                server.network
            }.takeIf { initialNetwork != null }),
        )
        val core = NaviampCore.create(this, services, NaviampCoreInitialState(
            connectionInventory = NaviampCoreConnectionInventory(listOf(record), "source"),
        ), onAsyncFailure = { command, cause -> throw IllegalStateException("Core command failed: $command", cause) })
        return Fixture(core, snapshots, effects, server, keep)
    }

    private fun Fixture.listed(id: String): SharedMediaItemUi = core.state.value.shell.playlists.playlists.single { it.id == id }

    private suspend fun Fixture.open(item: SharedMediaItemUi) = core.execute(NaviampCoreCommand.Media.ItemAction(
        NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Playlist(NaviampPlaylistMediaCommand.Select))))

    private suspend fun Fixture.detail(item: SharedMediaItemUi, command: NaviampPlaylistDetailCommand) =
        core.execute(NaviampCoreCommand.Playlists.Detail(NaviampPlaylistDetailActionRequest(item, command)))

    @Test fun anAirplaneModeColdStartSkipsTheServerAndReconnectsOnceTheNetworkReturns() = runTest {
        val fixture = fixture(reachable = true, initialNetwork = app.naviamp.domain.network.NetworkMetering.Disconnected)
        runCurrent()

        assertEquals(0, fixture.server.connects, "No connection attempt while the OS reports no network")
        assertEquals(0L, testScheduler.currentTime, "Offline content is not held behind a timeout")
        assertTrue(fixture.core.state.value.shell.connectionSettings.connection.offline)
        assertEquals(NaviampOfflineAvailabilityUi(3, 4), fixture.listed("road").offlineAvailability)

        fixture.server.network = app.naviamp.domain.network.NetworkMetering.Unmetered
        fixture.core.startRuntimeServices()
        // Runtime recovery starts with the network already back, so the first backoff step applies.
        advanceTimeBy(NaviampCoreReconnectSchedule().backoffMillis.first() + 1); runCurrent()

        assertEquals(1, fixture.server.connects)
        assertFalse(fixture.core.state.value.shell.connectionSettings.connection.offline)
        assertNull(fixture.listed("road").offlineAvailability, "Reconnecting returns to server browsing")
        fixture.core.close()
    }

    @Test fun coldLaunchWithoutServerListsAndOpensDownloadedPlaylistsInSavedOrder() = runTest {
        val fixture = fixture(reachable = false)
        advanceUntilIdle()

        val shell = fixture.core.state.value.shell
        assertTrue(shell.connectionSettings.connection.offline)
        assertEquals(setOf("Road Trip", "Quiet"), shell.playlists.playlists.map { it.title }.toSet())
        assertEquals(NaviampOfflineAvailabilityUi(3, 4), fixture.listed("road").offlineAvailability)
        assertEquals(NaviampOfflineAvailabilityUi(0, 1), fixture.listed("quiet").offlineAvailability)
        assertEquals(false, fixture.listed("road").canEditPlaylist)
        assertEquals(0, fixture.server.requests, "Offline browsing must not wait on the unreachable server")

        fixture.open(fixture.listed("road"))
        val detail = checkNotNull(fixture.core.state.value.shell.playlistDetail.detail)
        assertEquals(listOf("a", "b", "a", "c"), detail.tracks.map { it.id })
        assertEquals(listOf(false, true, false, false), detail.tracks.map { it.unavailableOffline })
        assertEquals(NaviampOfflineAvailabilityUi(3, 4), detail.playlist.offlineAvailability)
        assertEquals(0, fixture.server.requests)
        fixture.core.close()
    }

    @Test fun offlinePlayQueuesOnlyLocalTracksInPlaylistOrder() = runTest {
        val fixture = fixture(reachable = false)
        advanceUntilIdle()
        fixture.open(fixture.listed("road"))

        fixture.detail(fixture.listed("road"), NaviampPlaylistDetailCommand.Play(shuffle = false))

        assertEquals(listOf("a", "a", "c"), fixture.effects.selections.single().tracks.map { it.id.value })
        assertTrue(fixture.core.state.value.shell.shellChrome.nowPlayingOpen)
        assertEquals(0, fixture.server.requests)
        fixture.core.close()
    }

    @Test fun tappingAnUnavailableRowExplainsWhyAndDoesNotPlay() = runTest {
        val fixture = fixture(reachable = false)
        advanceUntilIdle()
        fixture.open(fixture.listed("road"))
        val unavailable = checkNotNull(fixture.core.state.value.shell.playlistDetail.detail).tracks[1]

        fixture.core.execute(NaviampCoreCommand.Detail.PlaylistTrack(
            app.naviamp.ui.SharedTrackRowActionRequest(unavailable, app.naviamp.ui.SharedTrackRowAction.Select)))
        advanceUntilIdle()

        // The playlist page renders and announces its status line; the overlay status is never shown.
        assertEquals("This track is not downloaded. Reconnect to play it.",
            fixture.core.state.value.shell.playlistDetail.status)
        assertTrue(fixture.effects.selections.isEmpty())
        assertEquals(0, fixture.server.requests)
        fixture.core.close()
    }

    @Test fun reconnectRefreshesAChangedKeptPlaylistAndReturnsToServerBrowsing() = runTest {
        val policy = KeepDownloadedCollectionPolicy("source", KeepDownloadedCollectionKind.Playlist, "road", "Road Trip")
        val fixture = fixture(reachable = false, policies = listOf(policy))
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "a", "c"), fixture.snapshots.saved.getValue("source" to "road").tracks.map { it.id.value })

        fixture.server.reachable = true
        fixture.server.tracks["road"] = listOf("c", "a", "c").map(::track)
        fixture.core.execute(NaviampCoreCommand.Connection.ConnectSaved(
            NaviampSavedConnectionUi(record.id, record.displayName, record.serverUrl, record.username)))
        advanceUntilIdle()

        val refreshed = fixture.snapshots.saved.getValue("source" to "road")
        assertEquals(listOf("c", "a", "c"), refreshed.tracks.map { it.id.value })
        assertEquals(OfflinePlaylistRetention.KeepDownloaded, refreshed.retention)
        assertEquals(listOf("x"), fixture.snapshots.saved.getValue("source" to "quiet").tracks.map { it.id.value })
        assertNull(fixture.listed("road").offlineAvailability)
        fixture.core.close()
    }

    @Test fun failedRefreshKeepsTheLastSavedMembership() = runTest {
        val policy = KeepDownloadedCollectionPolicy("source", KeepDownloadedCollectionKind.Playlist, "road", "Road Trip")
        val fixture = fixture(reachable = true, policies = listOf(policy))
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "a", "c"), fixture.snapshots.saved.getValue("source" to "road").tracks.map { it.id.value })
        fixture.core.close()
    }

    @Test fun downloadingSavesTheServerOrderAndStoppingKeepDownloadedRemovesTheSnapshot() = runTest {
        val fixture = fixture(reachable = true)
        fixture.server.tracks["fresh"] = listOf("b", "a", "b").map(::track)
        advanceUntilIdle()
        val fresh = Playlist("fresh", "Fresh", trackCount = 3).toSharedMediaItemUi(coverArtUrl = { null })

        fixture.detail(fresh, NaviampPlaylistDetailCommand.Download(null))
        advanceUntilIdle()
        val manual = fixture.snapshots.saved.getValue("source" to "fresh")
        assertEquals(listOf("b", "a", "b"), manual.tracks.map { it.id.value })
        assertEquals(OfflinePlaylistRetention.Manual, manual.retention)

        fixture.detail(fresh, NaviampPlaylistDetailCommand.Download(KeepDownloadedActionValue))
        advanceUntilIdle()
        assertEquals(OfflinePlaylistRetention.KeepDownloaded, fixture.snapshots.saved.getValue("source" to "fresh").retention)

        fixture.detail(fresh, NaviampPlaylistDetailCommand.Download(KeepDownloadedActionValue))
        advanceUntilIdle()
        assertNull(fixture.snapshots.saved["source" to "fresh"])
        fixture.core.close()
    }
}
