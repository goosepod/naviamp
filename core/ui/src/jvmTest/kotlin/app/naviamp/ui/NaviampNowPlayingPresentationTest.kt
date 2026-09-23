package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.lyrics.LyricsTiming
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.PlaybackQueueGroup
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampNowPlayingPresentationTest {
    @Test
    fun waveformSeekFractionUsesTheVisibleScrubberBounds() {
        assertEquals(0f, waveformSeekFraction(x = -20f, width = 200))
        assertEquals(0.5f, waveformSeekFraction(x = 100f, width = 200))
        assertEquals(1f, waveformSeekFraction(x = 240f, width = 200))
        assertEquals(0.5f, waveformSeekFraction(x = 98f, width = 196f))
    }

    @Test
    fun lyricsTimingSelectorExposesOnlyModesSupportedByLoadedLyrics() {
        assertTrue(lyricsDisplayTimingAvailable(LyricsTiming.Plain, LyricsTiming.WordSynced))
        assertTrue(lyricsDisplayTimingAvailable(LyricsTiming.LineSynced, LyricsTiming.WordSynced))
        assertTrue(lyricsDisplayTimingAvailable(LyricsTiming.WordSynced, LyricsTiming.WordSynced))
        assertTrue(lyricsDisplayTimingAvailable(LyricsTiming.Plain, LyricsTiming.LineSynced))
        assertTrue(lyricsDisplayTimingAvailable(LyricsTiming.LineSynced, LyricsTiming.LineSynced))
        assertFalse(lyricsDisplayTimingAvailable(LyricsTiming.WordSynced, LyricsTiming.LineSynced))
        assertFalse(lyricsDisplayTimingAvailable(LyricsTiming.Plain, null))
    }

    @Test
    fun downloadedTranscodeQualityOverridesTheLibraryFilesOriginalQuality() {
        val track = track("hi-res").copy(
            audioInfo = AudioInfo(
                codec = "FLAC",
                bitrateKbps = 2_800,
                contentType = "audio/flac",
                bitDepth = 24,
                samplingRateHz = 96_000,
            ),
        )

        assertEquals(
            "OPUS  128 kbps",
            track.nowPlayingAudioInfoLabel(
                streamQuality = StreamQuality.Transcoded(AudioCodec.Opus, 128),
            ),
        )
    }

    @Test
    fun trackPresentationCarriesQueueAndPlaybackStateThroughTheSharedContract() {
        val current = track("current")
        val next = track("next")

        val presentation = input(
            track = current,
            queue = PlaybackQueue(tracks = listOf(current, next), currentIndex = 0),
            playbackState = PlaybackState.Playing,
        ).toPresentationUi()

        assertEquals("current", presentation.nowPlaying.id)
        assertTrue(presentation.nowPlaying.isPlaying)
        assertEquals(listOf(nowPlayingQueueItemId(1)), presentation.nowPlaying.upNext.map { it.id })
        assertEquals(NaviampVisualizer.AudioSphere, presentation.selectedVisualizer)
        val miniNowPlaying = assertNotNull(presentation.miniNowPlaying)
        assertEquals("current", miniNowPlaying.id)
        assertTrue(miniNowPlaying.isPlaying)
        assertTrue(miniNowPlaying.hasNext)
    }

    @Test
    fun queueContextFollowsTheCurrentGroupAndIgnoresMissingOrInternalLabels() {
        val first = track("first")
        val second = track("second")
        val playlist = PlaybackQueueGroup(
            id = "internal-playlist-id",
            target = PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, "provider-id"),
            label = "Road Trip",
            startIndex = 0,
            endIndexExclusive = 1,
        )
        val album = PlaybackQueueGroup(
            id = "internal-album-id",
            target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-id"),
            label = "Evening Songs",
            startIndex = 1,
            endIndexExclusive = 2,
        )
        val restored = PlaybackQueue(listOf(first, second), currentIndex = 0, groups = listOf(playlist, album))
        fun context(track: Track, queue: PlaybackQueue) = input(track, queue)
            .copy(displaySettings = NowPlayingDisplaySettings(showPlaybackSource = true))
            .toPresentationUi().nowPlaying.queueContext

        assertNull(input(first, restored).toPresentationUi().nowPlaying.queueContext)

        assertEquals(
            NowPlayingQueueContextUi(PlaybackProfileTargetType.Playlist, "Road Trip"),
            context(first, restored),
        )
        assertEquals(
            NowPlayingQueueContextUi(PlaybackProfileTargetType.Album, "Evening Songs"),
            context(second, restored.copy(currentIndex = 1)),
        )
        assertNull(context(second, restored.copy(currentIndex = 1, groups = listOf(playlist))))
        assertNull(context(first, restored.copy(groups = listOf(playlist.copy(label = "  ")))))
        assertNull(context(first, restored.copy(groups = listOf(playlist.copy(label = "provider-id")))))
    }

    @Test
    fun emptyQueueInvitesPlaybackWithoutClaimingTheSourceIsDisconnected() {
        val presentation = input(track = null).toPresentationUi()

        assertEquals("Choose something to play", presentation.nowPlaying.title)
        assertEquals("Nothing Playing", presentation.nowPlaying.subtitle)
    }

    @Test
    fun failedPlaybackCanBeRetriedFromNowPlaying() {
        val capabilities = nowPlayingTrackCapabilities(
            isLiveStream = false,
            playbackState = PlaybackState.Error("No route to host"),
        )

        assertTrue(capabilities.canPlayPause)
    }

    @Test
    fun remoteOutputKeepsTheNowPlayingActionMenuAvailableForDisconnect() {
        val nowPlaying = input(track = null).toPresentationUi().nowPlaying.copy(
            menuEnabled = false,
            remoteOutputDeviceName = "Living Room TV",
        )

        assertTrue(nowPlayingActionMenuEnabled(nowPlaying))
    }

    @Test
    fun radioPresentationUsesStreamMetadataAndDisablesSeek() {
        val station = InternetRadioStation("radio", "Station", "https://example.test/radio")

        val presentation = input(
            track = app.naviamp.domain.radio.internetRadioTrack(station),
            stations = listOf(station),
            currentStationId = station.id,
            streamMetadata = PlaybackStreamMetadata(title = "Live Track"),
            playbackState = PlaybackState.Playing,
        ).toPresentationUi()

        assertEquals("Live Track", presentation.nowPlaying.title)
        assertEquals("Station", presentation.nowPlaying.subtitle)
        assertTrue(presentation.nowPlaying.isLive)
        assertFalse(presentation.nowPlaying.canSeek)
    }

    @Test
    fun hostSpecificDurationAndPlaylistPresentationArePreserved() {
        val base = input(track("current").copy(durationSeconds = null))
        val presentation = base.copy(
            content = base.content.copy(
                durationSeconds = 360.0,
                playlistChoices = listOf(NaviampPlaylistChoiceUi("playlist", "Road Trip")),
                playlistActionStatus = "Ready",
            ),
        ).toPresentationUi()

        assertEquals(360.0, presentation.nowPlaying.durationSeconds)
        assertEquals(listOf("Road Trip"), presentation.nowPlaying.playlistChoices.map { it.name })
        assertEquals("Ready", presentation.nowPlaying.playlistActionStatus)
    }

    private fun input(
        track: Track?,
        queue: PlaybackQueue = PlaybackQueue(),
        stations: List<InternetRadioStation> = emptyList(),
        currentStationId: String? = null,
        streamMetadata: PlaybackStreamMetadata = PlaybackStreamMetadata(),
        playbackState: PlaybackState = PlaybackState.Paused,
    ): NaviampNowPlayingPresentationInput = NaviampNowPlayingPresentationInput(
        content = NaviampNowPlayingContentInput(
            stateLabel = playbackState.toString(),
            playbackEngineName = "Test engine",
            capabilities = nowPlayingTrackCapabilities(
                isLiveStream = currentStationId != null,
                playbackState = playbackState,
                supportsSeek = true,
                supportsSoftwareVolume = true,
                supportsTrackRadio = true,
                supportsTrackFavorites = true,
                supportsTrackRatings = true,
                canRepeatQueue = true,
                canSaveQueueAsPlaylist = true,
            ),
            nowPlayingTrack = track,
            nowPlayingWaveform = null,
            nowPlayingAudioTags = null,
            nowPlayingLyrics = null,
            nowPlayingLyricsStatus = null,
            nowPlayingStreamMetadata = streamMetadata,
            lyricsVisible = false,
            visualizerAvailable = false,
            visualizerVisible = false,
            coverArtUrl = null,
            playbackQueue = queue,
            internetRadioStations = stations,
            currentInternetRadioStationId = currentStationId,
            radioTrackArtworkByKey = emptyMap(),
            relatedTracks = emptyList(),
            relatedTracksSource = RelatedTracksSource.LocalLibrary,
            relatedSimilarityByTrackId = emptyMap(),
            coverArtUrlForTrack = { null },
            hasPrevious = false,
            hasNext = queue.currentIndex < queue.tracks.lastIndex,
            shuffleActive = false,
            repeatMode = RepeatMode.Off,
            playbackState = playbackState,
            playbackProgress = PlaybackProgress(10.0, 125.0),
            volumePercent = 75,
            sleepTimer = NaviampSleepTimerUi(),
            streamQuality = StreamQuality.Original,
            replayGainInspectorEnabled = false,
            replayGainMode = ReplayGainMode.Off,
            sonicSimilarityEnabled = false,
            radioDjs = emptyList(),
            activeRadioDjId = null,
        ),
        displaySettings = NowPlayingDisplaySettings(),
        visualizerFrame = null,
        selectedVisualizer = NaviampVisualizer.AudioSphere,
        visualizerColors = NaviampPlayerColors.solid(Color.Black),
    )

    private fun track(id: String): Track = Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 125,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}
