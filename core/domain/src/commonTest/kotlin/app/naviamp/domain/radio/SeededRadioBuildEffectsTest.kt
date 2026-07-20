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

        applySeededRadioBuildResult(
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

        assertEquals(listOf("recent:radio", "tracks:[next]", "status:Building radio..."), calls)
    }

    @Test
    fun staleReadyResultOnlyPreservesRecentMetadata() {
        val recent = RecentRadioStream("radio", "Radio", RecentRadioKind.Track)
        val calls = mutableListOf<String>()

        applySeededRadioBuildResult(
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

        assertEquals(listOf("recent"), calls)
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
