package app.naviamp.domain.playback

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NowPlayingSidecarsTest {
    @Test
    fun onlineLyricsLoadOnlyWhenEnabledAndNoSyncedLocalLyricsExist() {
        val synced = Lyrics(LyricsSource.Provider, synced = true, lines = listOf(LyricLine(1_000, "line")))
        val unsynced = Lyrics(LyricsSource.Provider, synced = false, lines = listOf(LyricLine(null, "line")))

        assertTrue(shouldLoadOnlineLyrics(true, providerLyrics = null, embeddedLyrics = null))
        assertTrue(shouldLoadOnlineLyrics(true, providerLyrics = unsynced, embeddedLyrics = null))
        assertFalse(shouldLoadOnlineLyrics(false, providerLyrics = null, embeddedLyrics = null))
        assertFalse(shouldLoadOnlineLyrics(true, providerLyrics = synced, embeddedLyrics = null))
    }

    @Test
    fun waveformStatusReflectsCacheAudioAndGenerationState() {
        assertEquals("Cached", waveformStatus(true, false, true, true))
        assertEquals("Generated", waveformStatus(false, true, true, true))
        assertEquals("Cache disabled", waveformStatus(false, false, false, false))
        assertEquals("Preparing", waveformStatus(false, false, false, true))
        assertEquals("Unavailable", waveformStatus(false, false, true, true))
    }

    @Test
    fun sidecarFailureStatusUsesMessageOrTypeFallback() {
        assertEquals("No waveform", sidecarFailureStatus(IllegalStateException("No waveform")))
        assertEquals("IllegalStateException", sidecarFailureStatus(IllegalStateException()))
    }

    @Test
    fun sidecarStatusHelpersRecordSuccessAndFailureRows() {
        val repository = RecordingSidecarStatusRepository()
        val trackId = TrackId("track")

        repository.recordSidecarSuccess(
            sourceId = "source",
            trackId = trackId,
            quality = StreamQuality.Original,
            sidecarType = SidecarTypeWaveform,
        )
        repository.recordSidecarFailure(
            sourceId = "source",
            trackId = trackId,
            quality = StreamQuality.Original,
            sidecarType = SidecarTypeLyrics,
            errorMessage = "Lyrics unavailable",
        )

        assertEquals(
            listOf(
                RecordedSidecarStatus("source", trackId, SidecarTypeWaveform, success = true, errorMessage = null),
                RecordedSidecarStatus("source", trackId, SidecarTypeLyrics, success = false, errorMessage = "Lyrics unavailable"),
            ),
            repository.records,
        )
    }

    @Test
    fun sidecarPrepTracksUseCurrentQueueWindowAndSkipRadioTracks() {
        val one = track("one")
        val radio = track(internetRadioTrackId("station").value)
        val two = track("two")
        val queue = PlaybackQueue(
            tracks = listOf(one, radio, two),
            currentIndex = 0,
        )

        assertEquals(listOf(one, two), sidecarPrepTracks(queue, depth = 3))
        assertEquals(listOf(one), sidecarPrepTracks(queue, depth = 1))
    }

    @Test
    fun audioPrefetchTracksCanIncludeOrSkipCurrentTrackAndSkipRadioTracks() {
        val one = track("one")
        val radio = track(internetRadioTrackId("station").value)
        val two = track("two")
        val three = track("three")
        val queue = PlaybackQueue(
            tracks = listOf(one, radio, two, three),
            currentIndex = 0,
        )

        assertEquals(
            listOf(one, two),
            audioPrefetchTracks(queue, depth = 3, includeCurrentTrack = true),
        )
        assertEquals(
            listOf(two, three),
            audioPrefetchTracks(queue, depth = 3, includeCurrentTrack = false),
        )
        assertEquals(
            emptyList(),
            audioPrefetchTracks(queue, depth = 0, includeCurrentTrack = true),
        )
    }

    @Test
    fun sidecarPrepPlanIncludesLyricsDecision() {
        val one = track("one")
        val queue = PlaybackQueue(tracks = listOf(one), currentIndex = 0)

        assertEquals(
            SidecarPrepPlan(tracks = listOf(one), loadLyrics = false),
            sidecarPrepPlan(queue, depth = 1, onlineLyricsEnabled = false, lyricsVisible = false),
        )
        assertEquals(
            SidecarPrepPlan(tracks = listOf(one), loadLyrics = true),
            sidecarPrepPlan(queue, depth = 1, onlineLyricsEnabled = true, lyricsVisible = false),
        )
    }

    @Test
    fun currentTrackSidecarWorkBuildsSharedWork() {
        val one = track("one")
        val work = currentTrackSidecarWork(
            sourceId = "source",
            provider = "provider",
            queue = PlaybackQueue(tracks = listOf(one), currentIndex = 0),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = false,
            lyricsVisible = true,
        )

        requireNotNull(work)
        assertEquals("source", work.sourceId)
        assertEquals("provider", work.provider)
        assertEquals(one, work.track)
        assertEquals(StreamQuality.Original, work.quality)
        assertTrue(work.audioCachingEnabled)
        assertFalse(work.onlineLyricsEnabled)
        assertTrue(work.loadLyrics)
    }

    @Test
    fun currentTrackSidecarWorkSkipsMissingOrRadioTracks() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)
        val radioQueue = PlaybackQueue(tracks = listOf(track(internetRadioTrackId("station").value)), currentIndex = 0)

        assertEquals(
            null,
            currentTrackSidecarWork(
                sourceId = "source",
                provider = null,
                queue = queue,
                quality = StreamQuality.Original,
                audioCachingEnabled = true,
                onlineLyricsEnabled = false,
                lyricsVisible = false,
            ),
        )
        assertEquals(
            null,
            currentTrackSidecarWork(
                sourceId = "source",
                provider = "provider",
                queue = queue,
                quality = null,
                audioCachingEnabled = true,
                onlineLyricsEnabled = false,
                lyricsVisible = false,
            ),
        )
        assertEquals(
            null,
            currentTrackSidecarWork(
                sourceId = "source",
                provider = "provider",
                queue = PlaybackQueue(),
                quality = StreamQuality.Original,
                audioCachingEnabled = true,
                onlineLyricsEnabled = false,
                lyricsVisible = false,
            ),
        )
        assertEquals(
            null,
            currentTrackSidecarWork(
                sourceId = "source",
                provider = "provider",
                queue = radioQueue,
                quality = StreamQuality.Original,
                audioCachingEnabled = true,
                onlineLyricsEnabled = false,
                lyricsVisible = false,
            ),
        )
    }

    @Test
    fun runCurrentTrackSidecarsRunsSharedSequence() = runTest {
        val events = mutableListOf<String>()
        val work = currentSidecarWork(loadLyrics = true)

        runCurrentTrackSidecars(
            work = work,
            isActive = { true },
            prepareAudio = {
                events += "audio"
            },
            prepareWaveform = {
                events += "waveform"
                AudioWaveform(listOf(0.1f))
            },
            prepareAudioTags = {
                events += "tags"
                listOf(AudioTag("Title", "Track one"))
            },
            prepareLyrics = {
                events += "lyrics"
                Lyrics(LyricsSource.Provider, synced = false, lines = emptyList())
            },
            onWaveformReady = {
                events += "waveform-ready"
            },
            onAudioTagsReady = {
                events += "tags-ready:${it.size}"
            },
            onLyricsReady = {
                events += "lyrics-ready"
            },
        )

        assertEquals(
            listOf("waveform", "waveform-ready", "audio", "tags", "tags-ready:1", "lyrics", "lyrics-ready"),
            events,
        )
    }

    @Test
    fun runCurrentTrackSidecarsSkipsLyricsWhenWorkDoesNotLoadLyrics() = runTest {
        val events = mutableListOf<String>()

        runCurrentTrackSidecars(
            work = currentSidecarWork(loadLyrics = false),
            isActive = { true },
            prepareWaveform = {
                events += "waveform"
                null
            },
            prepareLyrics = {
                events += "lyrics"
                null
            },
            onWaveformReady = {
                events += "waveform-ready"
            },
        )

        assertEquals(listOf("waveform", "waveform-ready"), events)
    }

    @Test
    fun runCurrentTrackSidecarsStopsWhenInactiveOrAudioPrepFailsAfterWaveform() = runTest {
        val inactiveEvents = mutableListOf<String>()
        runCurrentTrackSidecars(
            work = currentSidecarWork(loadLyrics = true),
            isActive = { false },
            prepareWaveform = {
                inactiveEvents += "waveform"
                null
            },
        )
        assertEquals(emptyList(), inactiveEvents)

        val failedAudioEvents = mutableListOf<String>()
        runCurrentTrackSidecars(
            work = currentSidecarWork(loadLyrics = true),
            isActive = { true },
            prepareAudio = {
                failedAudioEvents += "audio"
                error("cache failed")
            },
            prepareWaveform = {
                failedAudioEvents += "waveform"
                null
            },
            onWaveformReady = {
                failedAudioEvents += "waveform-ready"
            },
            prepareAudioTags = {
                failedAudioEvents += "tags"
                emptyList()
            },
        )
        assertEquals(listOf("waveform", "waveform-ready", "audio"), failedAudioEvents)
    }

    @Test
    fun coverArtPreloadUrlsIncludeCurrentHistoryAndUpcomingWindows() {
        val one = track("one", coverArtId = "art-one")
        val two = track("two", coverArtId = "art-two")
        val three = track("three", coverArtId = "art-three")
        val four = track("four", coverArtId = "art-four")
        val queue = PlaybackQueue(
            tracks = listOf(one, two, three, four),
            currentIndex = 2,
        )

        assertEquals(
            listOf("current", "cover://art-two", "cover://art-four"),
            coverArtPreloadUrls(
                queue = queue,
                currentCoverArtUrl = "current",
                historyLimit = 1,
                upcomingLimit = 1,
                coverArtUrl = { coverArtId -> "cover://$coverArtId" },
            ),
        )
    }

    private fun track(
        id: String,
        coverArtId: String? = null,
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = coverArtId,
            audioInfo = null,
            replayGain = null,
        )

    private fun currentSidecarWork(loadLyrics: Boolean): CurrentTrackSidecarWork<String> =
        CurrentTrackSidecarWork(
            sourceId = "source",
            provider = "provider",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = loadLyrics,
            loadLyrics = loadLyrics,
        )
}

private data class RecordedSidecarStatus(
    val sourceId: String,
    val trackId: TrackId,
    val sidecarType: String,
    val success: Boolean,
    val errorMessage: String?,
)

private class RecordingSidecarStatusRepository : SidecarStatusRepository {
    val records = mutableListOf<RecordedSidecarStatus>()

    override fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
        errorMessage: String?,
    ) {
        records += RecordedSidecarStatus(sourceId, trackId, sidecarType, success, errorMessage)
    }
}
