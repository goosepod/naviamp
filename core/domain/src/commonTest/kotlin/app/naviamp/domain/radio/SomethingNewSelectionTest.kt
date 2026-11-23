package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SomethingNewSelectionTest {
    @Test
    fun picksTheFirstSampledTrackWithNoPlayCountOrPendingListen() = runTest {
        val batches = ArrayDeque(listOf(
            listOf(track("played", playCount = 3), track("pending"), track("recent", lastPlayed = "2026-01-01")),
            listOf(track("played-2", playCount = 1), track("fresh"), track("fresh-2")),
        ))
        val requested = mutableListOf<Int>()

        val pick = pick(randomTracks = { limit -> requested += limit; batches.removeFirst() },
            pendingListens = { setOf(TrackId("pending")) })

        assertEquals(SomethingNewPick.Found(track("fresh"), fromDownloads = false), pick)
        assertEquals(listOf(SomethingNewBatchSize, SomethingNewBatchSize), requested)
    }

    @Test
    fun aFullyPlayedLibraryStopsAtTheBatchBoundWithoutAPick() = runTest {
        var calls = 0

        val pick = pick(randomTracks = { calls++; listOf(track("a", playCount = 1), track("b", playCount = 9)) })

        assertEquals(SomethingNewPick.NothingUnplayed(offline = false), pick)
        assertEquals(SomethingNewMaxBatches, calls)
    }

    @Test
    fun anEmptyLibraryStopsAfterTheFirstEmptyBatch() = runTest {
        var calls = 0

        assertEquals(SomethingNewPick.NothingUnplayed(offline = false), pick(randomTracks = { calls++; emptyList() }))
        assertEquals(1, calls)
    }

    @Test
    fun tracksOutsideTheSelectedLibrariesAreNeverChosen() = runTest {
        val pick = pick(
            randomTracks = { listOf(track("hidden", folder = "2"), track("visible", folder = "1")) },
            inSelectedLibraries = { tracks -> tracks.filter { it.musicFolderId == "1" } },
        )

        assertEquals("visible", (pick as SomethingNewPick.Found).track.id.value)
    }

    @Test
    fun offlinePicksOnlyFromDownloadedTracksAndNeverAsksTheServer() = runTest {
        var serverCalls = 0
        val pick = pick(
            offline = true,
            randomTracks = { serverCalls++; listOf(track("streamed")) },
            downloadedTracks = { listOf(track("downloaded-played", playCount = 2), track("downloaded-new")) },
        )

        assertEquals(SomethingNewPick.Found(track("downloaded-new"), fromDownloads = true), pick)
        assertEquals(0, serverCalls)
    }

    @Test
    fun offlineReportsMissingOrFullyPlayedDownloads() = runTest {
        assertEquals(SomethingNewPick.NothingDownloaded, pick(offline = true, downloadedTracks = { emptyList() }))
        assertEquals(SomethingNewPick.NothingDownloaded, pick(offline = true,
            downloadedTracks = { listOf(track("hidden", folder = "2")) },
            inSelectedLibraries = { tracks -> tracks.filter { it.musicFolderId == "1" } }))
        assertEquals(SomethingNewPick.NothingUnplayed(offline = true), pick(offline = true,
            downloadedTracks = { listOf(track("played", playCount = 1)) }))
    }

    @Test
    fun aProviderWithoutPlayCountsIsReportedInsteadOfGuessing() = runTest {
        var calls = 0

        assertEquals(SomethingNewPick.PlayCountsUnavailable,
            pick(playCountsAvailable = false, randomTracks = { calls++; listOf(track("a")) }))
        assertEquals(0, calls)
    }

    @Test
    fun providerErrorsBecomeAFailedPick() = runTest {
        val pick = pick(randomTracks = { error("server down") })

        assertEquals("server down", assertIs<SomethingNewPick.Failed>(pick).error.message)
    }

    @Test
    fun aSupersededPickStopsSamplingBetweenBatches() = runTest {
        var calls = 0
        val pick = pick(randomTracks = { calls++; listOf(track("played", playCount = 1)) }, isCurrent = { calls < 2 })

        assertEquals(SomethingNewPick.Superseded, pick)
        assertEquals(2, calls)
    }

    @Test
    fun excludedAndDiscoveryPreviewTracksAreSkipped() = runTest {
        val pick = pick(
            randomTracks = { listOf(track("now-playing"), track("discovery-preview:1"), track("next")) },
            excluded = setOf(TrackId("now-playing")),
        )

        assertEquals("next", (pick as SomethingNewPick.Found).track.id.value)
    }

    private suspend fun pick(
        playCountsAvailable: Boolean = true,
        offline: Boolean = false,
        randomTracks: suspend (Int) -> List<Track> = { emptyList() },
        downloadedTracks: suspend () -> List<Track> = { emptyList() },
        inSelectedLibraries: (List<Track>) -> List<Track> = { it },
        pendingListens: suspend () -> Set<TrackId> = { emptySet() },
        excluded: Set<TrackId> = emptySet(),
        isCurrent: () -> Boolean = { true },
    ) = pickSomethingNew(
        playCountsAvailable = playCountsAvailable,
        offline = offline,
        randomTracks = randomTracks,
        downloadedTracks = downloadedTracks,
        inSelectedLibraries = inSelectedLibraries,
        pendingListens = pendingListens,
        excluded = excluded,
        isCurrent = isCurrent,
        shuffle = { it },
    )

    private fun track(id: String, playCount: Int? = null, lastPlayed: String? = null, folder: String? = null) = Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
        playCount = playCount,
        lastPlayedAtIso8601 = lastPlayed,
        musicFolderId = folder,
    )
}
