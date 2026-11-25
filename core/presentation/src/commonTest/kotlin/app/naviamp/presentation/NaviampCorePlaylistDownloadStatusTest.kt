package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.DownloadStorageLimitExceededMessage
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.toSharedMediaItemUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Playlists header must not keep announcing a download that already finished. */
@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCorePlaylistDownloadStatusTest {
    private val starting = "Starting download for Core Playlist..."

    private class Snapshots : app.naviamp.domain.cache.OfflinePlaylistRepository by
        app.naviamp.domain.cache.EmptyOfflinePlaylistRepository {
        val saved = mutableMapOf<String, app.naviamp.domain.cache.OfflinePlaylistSnapshot>()
        override fun saveOfflinePlaylist(snapshot: app.naviamp.domain.cache.OfflinePlaylistSnapshot) {
            saved[snapshot.playlist.id] = snapshot
        }
    }

    private val snapshots = Snapshots()

    private fun TestScope.core(
        provider: MediaProvider = FakeCoreMediaProvider(),
        mobileData: Boolean = false,
        saved: List<Track> = emptyList(),
        transfer: NaviampCoreDownloadTransferPort,
    ): NaviampCore {
        val defaults = fakeCoreServices(provider)
        val storage = object : NaviampCoreDownloadStoragePort by defaults.downloads.storage {
            override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot(
                downloads = saved.map { NaviampCoreDownloadedTrack(it.id.value, it, sizeBytes = 1) })
        }
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "source"),
                product = state.product.copy(shell = state.product.shell.copy(
                    connectionSettings = state.product.shell.connectionSettings.copy(currentSourceId = "source"))),
            )
        }
        return NaviampCore.create(this, defaults.copy(downloads = defaults.downloads.copy(storage = storage, transfer = transfer,
            network = NaviampCoreMobileNetworkPort { mobileData }, offlinePlaylists = snapshots)), initial,
            onAsyncFailure = { command, cause -> throw IllegalStateException("Core command failed: $command", cause) })
    }

    private suspend fun NaviampCore.download(provider: FakeCoreMediaProvider = FakeCoreMediaProvider()) = execute(
        NaviampCoreCommand.Playlists.Detail(NaviampPlaylistDetailActionRequest(
            provider.playlist.toSharedMediaItemUi(coverArtUrl = { null }), NaviampPlaylistDetailCommand.Download(null))))

    @Test fun completedDownloadClearsTheStartingStatus() = runTest {
        val gate = CompletableDeferred<Unit>()
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, update ->
            gate.await()
            update(DownloadJobUpdate.Completed)
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        runCurrent()
        assertEquals(starting, core.state.value.shell.playlists.status)

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(core.state.value.shell.playlists.status)
        core.close()
    }

    @Test fun failedDownloadReportsTheFailureForThePlaylist() = runTest {
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, _ -> error("Disk full") })
        core.download()
        advanceUntilIdle()
        assertEquals("Could not download Core Playlist.", core.state.value.shell.playlists.status)
        assertEquals("Could not download Core Playlist.", core.state.value.shell.playlistDetail.status)
        core.close()
    }

    @Test fun storageLimitFailureNamesTheReason() = runTest {
        val core = core(transfer = NaviampCoreDownloadTransferPort { request, _, update ->
            update(DownloadJobUpdate.Started)
            update(DownloadJobUpdate.Failed(request.tracks.first().id.value, DownloadStorageLimitExceededMessage))
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        advanceUntilIdle()
        val expected = "Could not download Core Playlist. The download storage limit was reached."
        assertEquals(expected, core.state.value.shell.playlists.status)
        assertEquals(expected, core.state.value.shell.playlistDetail.status)
        core.close()
    }

    @Test fun failureStaysOnThePlaylistPageWhenItIsOpenedLater() = runTest {
        val core = core(transfer = NaviampCoreDownloadTransferPort { request, _, update ->
            update(DownloadJobUpdate.Failed(request.tracks.first().id.value, DownloadStorageLimitExceededMessage))
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        advanceUntilIdle()
        val item = FakeCoreMediaProvider().playlist.toSharedMediaItemUi(coverArtUrl = { null })
        core.execute(NaviampCoreCommand.Media.ItemAction(app.naviamp.ui.NaviampMediaItemActionRequest(item,
            app.naviamp.ui.NaviampMediaItemCommand.Playlist(app.naviamp.ui.NaviampPlaylistMediaCommand.Select))))
        advanceUntilIdle()

        assertEquals("Could not download Core Playlist. The download storage limit was reached.",
            core.state.value.shell.playlistDetail.status)
        core.close()
    }

    @Test fun blockedStartFromTheListReplacesAnEarlierFailureAndSavesNoSnapshot() = runTest {
        val core = core(mobileData = true, transfer = NaviampCoreDownloadTransferPort { _, _, _ -> error("Started") })
        core.stateStore.updateShell { it.copy(playlists = it.playlists.copy(status = "Could not download Folk Music.")) }
        val item = FakeCoreMediaProvider().playlist.toSharedMediaItemUi(coverArtUrl = { null })
        core.execute(NaviampCoreCommand.Media.ItemAction(app.naviamp.ui.NaviampMediaItemActionRequest(item,
            app.naviamp.ui.NaviampMediaItemCommand.Playlist(app.naviamp.ui.NaviampPlaylistMediaCommand.Detail(
                NaviampPlaylistDetailCommand.Download(null))))))
        advanceUntilIdle()

        val expected = "Could not download Core Playlist. Downloads over mobile data are turned off."
        assertEquals(expected, core.state.value.shell.playlists.status)
        assertEquals(expected, core.state.value.shell.playlistDetail.status)
        assertEquals(emptyMap(), snapshots.saved, "Nothing was downloaded, so no offline copy may appear")
        core.close()
    }

    @Test fun startedDownloadSavesTheSnapshot() = runTest {
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, update ->
            update(DownloadJobUpdate.Completed)
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        advanceUntilIdle()
        assertEquals(setOf("core-playlist"), snapshots.saved.keys)
        core.close()
    }

    @Test fun alreadyDownloadedPlaylistSaysSoInsteadOfClearingSilently() = runTest {
        var transfers = 0
        val core = core(saved = listOf(FakeCoreMediaProvider().track), mobileData = true,
            transfer = NaviampCoreDownloadTransferPort { _, _, _ ->
                transfers++
                NaviampCoreDownloadTransferResult(refreshDownloads = false)
            })
        core.download()
        advanceUntilIdle()

        val expected = "All tracks in Core Playlist are already downloaded."
        assertEquals(expected, core.state.value.shell.playlists.status)
        assertEquals(expected, core.state.value.shell.playlistDetail.status)
        assertEquals(0, transfers)
        assertEquals(emptyList(), core.state.value.shell.downloads.jobs)
        assertEquals(setOf("core-playlist"), snapshots.saved.keys, "The complete copy stays available offline")
        core.close()
    }

    @Test fun blockedStartReportsTheFailureInsteadOfStarting() = runTest {
        val core = core(mobileData = true, transfer = NaviampCoreDownloadTransferPort { _, _, _ -> error("Started") })
        core.download()
        advanceUntilIdle()
        assertEquals("Could not download Core Playlist. Downloads over mobile data are turned off.",
            core.state.value.shell.playlists.status)
        assertEquals(emptyList(), core.state.value.shell.downloads.jobs)
        core.close()
    }

    @Test fun aNewerStatusIsNeverReplacedByTheOutcome() = runTest {
        val gate = CompletableDeferred<Unit>()
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, _ ->
            gate.await()
            error("Disk full")
        })
        core.download()
        runCurrent()
        core.execute(NaviampCoreCommand.Playlists.Refresh)
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(core.state.value.shell.playlists.status)
        core.close()
    }

    @Test fun cancelledDownloadClearsTheStartingStatus() = runTest {
        val never = CompletableDeferred<Unit>()
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, _ ->
            never.await()
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        runCurrent()
        val job = core.state.value.shell.downloads.jobs.single()
        core.execute(NaviampCoreCommand.Downloads.CancelJob(job.id))
        advanceUntilIdle()
        assertNull(core.state.value.shell.playlists.status)
        core.close()
    }

    @Test fun unreadablePlaylistNeverLeavesTheStartingStatus() = runTest {
        val provider = object : MediaProvider by FakeCoreMediaProvider() {
            override suspend fun playlistTracks(playlistId: String): List<Track> = error("Connection timed out")
            override suspend fun playlists(limit: Int): List<Playlist> = emptyList()
        }
        val core = core(provider, transfer = NaviampCoreDownloadTransferPort { _, _, _ -> error("Not started") })
        core.download()
        advanceUntilIdle()
        assertNull(core.state.value.shell.playlists.status)
        assertEquals("Connection timed out", core.state.value.shell.playlistDetail.status)
        core.close()
    }
}
