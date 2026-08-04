package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioQueueRulesTest {
    @Test
    fun appendPlanSkipsAlreadyQueuedTracks() {
        val seed = track("seed")
        val one = track("one")
        val two = track("two")

        assertEquals(
            listOf(two),
            generatedRadioTracksToAppend(
                seedTrack = seed,
                fetchedTracks = listOf(one, two),
                queuedTracks = listOf(seed, one),
            ),
        )
    }

    @Test
    fun upcomingReplacementPlanUsesGeneratedTracksAfterCurrentTrack() {
        val current = track("current")
        val one = track("one")
        val two = track("two")

        assertEquals(
            listOf(one, two),
            generatedRadioUpcomingTracks(
                currentTrack = current,
                fetchedTracks = listOf(one, current, two),
            ),
        )
    }

    @Test
    fun upcomingAppendPlanFiltersQueuedTracksAndFinishRequiresCurrentSession() {
        val current = track("current")
        val one = track("one")
        val two = track("two")

        assertEquals(
            listOf(two),
            generatedRadioUpcomingTracksToAppend(
                currentTrack = current,
                fetchedTracks = listOf(current, one, two),
                queuedTracks = listOf(current, one),
            ),
        )
        assertEquals(true, shouldFinishRadioRefillForSession(radioSession = 4, currentRadioSession = 4))
        assertEquals(false, shouldFinishRadioRefillForSession(radioSession = 3, currentRadioSession = 4))
    }

    @Test
    fun refillSeedUsesLastQueuedTrackOnlyWhenRepeatIsOffAndNearEnd() {
        val current = track("current")
        val middle = track("middle")
        val tail = track("tail")
        val queue = PlaybackQueue(
            tracks = listOf(current, middle, tail),
            currentIndex = 1,
        )

        assertEquals(
            tail,
            radioRefillSeedTrack(
                queue = queue,
                refillThreshold = 2,
                repeatMode = RepeatMode.Off,
                isActive = true,
                isRefilling = false,
                lastRefillSeedTrackId = null,
            ),
        )
        assertEquals(
            null,
            radioRefillSeedTrack(
                queue = queue,
                refillThreshold = 2,
                repeatMode = RepeatMode.Queue,
                isActive = true,
                isRefilling = false,
                lastRefillSeedTrackId = null,
            ),
        )
        assertEquals(
            null,
            radioRefillSeedTrack(
                queue = queue,
                refillThreshold = 2,
                repeatMode = RepeatMode.Off,
                isActive = true,
                isRefilling = false,
                lastRefillSeedTrackId = tail.id,
            ),
        )
        assertEquals(
            null,
            radioRefillSeedTrack(
                queue = PlaybackQueue(
                    tracks = listOf(current, middle, tail, track("four"), track("five")),
                    currentIndex = 0,
                ),
                refillThreshold = 2,
                repeatMode = RepeatMode.Off,
                isActive = true,
                isRefilling = false,
                lastRefillSeedTrackId = null,
            ),
        )
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
