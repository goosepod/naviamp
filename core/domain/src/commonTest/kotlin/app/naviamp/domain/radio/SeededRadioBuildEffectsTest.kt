package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.RecentRadioKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SeededRadioBuildEffectsTest {
    @Test
    fun readyResultRemembersRecentAndAppliesOnlyTracksAfterSeed() {
        val seed = track("seed")
        val next = track("next")
        val recent = RecentRadioStream("radio", "Radio", RecentRadioKind.Track)
        val calls = mutableListOf<String>()

        val applied = applySeededRadioBuildResult(
            result = SeededRadioBuildResult.Ready(listOf(seed, next), recent),
            requestIsCurrent = true,
            buildingStatus = "Building radio...",
            failureStatus = "Failed",
            applier = SeededRadioBuildEffectApplier(
                rememberRecentRadioStream = { calls += "recent:${it.id}" },
                appendFetchedTracks = { calls += "tracks:${it.map { track -> track.id.value }}" },
                setStatus = { calls += "status:$it" },
            ),
        )

        assertEquals(true, applied)
        assertEquals(listOf("recent:radio", "tracks:[next]", "status:Building radio..."), calls)
    }

    @Test
    fun staleReadyResultOnlyPreservesRecentMetadata() {
        val recent = RecentRadioStream("radio", "Radio", RecentRadioKind.Track)
        val calls = mutableListOf<String>()

        val applied = applySeededRadioBuildResult(
            result = SeededRadioBuildResult.Ready(listOf(track("seed")), recent),
            requestIsCurrent = false,
            buildingStatus = "Building",
            failureStatus = "Failed",
            applier = SeededRadioBuildEffectApplier(
                rememberRecentRadioStream = { calls += "recent" },
                appendFetchedTracks = { calls += "tracks" },
                setStatus = { calls += "status" },
            ),
        )

        assertEquals(false, applied)
        assertEquals(listOf("recent"), calls)
    }

    @Test
    fun currentExpansionAppliesFetchedTracks() {
        val calls = mutableListOf<String>()

        val applied = applySeededRadioExpansionResult(
            result = SeededRadioExpansionResult.Ready(listOf(track("next"))),
            requestIsCurrent = true,
            failureStatus = "Failed",
            appendFetchedTracks = { calls += "tracks:${it.map { track -> track.id.value }}" },
            setStatus = { calls += "status:$it" },
        )

        assertEquals(true, applied)
        assertEquals(listOf("tracks:[next]"), calls)
    }

    @Test
    fun staleExpansionDoesNotApplyFetchedTracks() {
        val calls = mutableListOf<String>()

        val applied = applySeededRadioExpansionResult(
            result = SeededRadioExpansionResult.Ready(listOf(track("next"))),
            requestIsCurrent = false,
            failureStatus = "Failed",
            appendFetchedTracks = { calls += "tracks" },
            setStatus = { calls += "status" },
        )

        assertEquals(false, applied)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun currentExpansionFailureUsesErrorOrFallbackStatus() {
        val errorCalls = mutableListOf<String>()
        val fallbackCalls = mutableListOf<String>()

        applySeededRadioExpansionResult(
            result = SeededRadioExpansionResult.Failed(IllegalStateException("Provider failed")),
            requestIsCurrent = true,
            failureStatus = "Failed",
            appendFetchedTracks = {},
            setStatus = errorCalls::add,
        )
        applySeededRadioExpansionResult(
            result = SeededRadioExpansionResult.Failed(IllegalStateException()),
            requestIsCurrent = true,
            failureStatus = "Failed",
            appendFetchedTracks = {},
            setStatus = fallbackCalls::add,
        )

        assertEquals(listOf("Provider failed"), errorCalls)
        assertEquals(listOf("Failed"), fallbackCalls)
    }

    private fun track(id: String) = Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}
