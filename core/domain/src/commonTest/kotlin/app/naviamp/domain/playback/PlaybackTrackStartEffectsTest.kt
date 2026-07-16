package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTrackStartEffectsTest {
    @Test
    fun appliesEnabledTrackStartEffectsInSharedOrder() {
        val calls = mutableListOf<String>()
        val track = track("one", favorited = true)
        val presentation = planPlaybackTrackStarted(
            previousTrack = null,
            track = track,
            openNowPlaying = true,
            nowPlayingOpen = false,
            lyricsVisible = true,
            supportsTrackFavorites = true,
        )
        val effects = planPlaybackTrackStartEffects(
            track = track,
            presentation = presentation,
            keepRadioQueueActive = false,
        )

        applyPlaybackTrackStartEffects(
            track = track,
            coverArtUrl = "cover",
            effects = effects,
            applier = PlaybackTrackStartEffectApplier(
                clearShuffleSnapshot = { calls += "clear-shuffle" },
                clearRadioContinuation = { calls += "clear-radio" },
                clearInternetRadioNowPlaying = { calls += "clear-station" },
                resetStreamMetadata = { calls += "reset-stream" },
                setNowPlayingTrack = { calls += "set-track:${it.id.value}" },
                setNowPlayingCoverArtUrl = { calls += "set-cover:$it" },
                applyFavoriteState = { canFavorite, isFavorite -> calls += "favorite:$canFavorite:$isFavorite" },
                incrementPlayReportSession = { calls += "increment-report" },
                savePlaybackSession = { calls += "save-session" },
                openNowPlaying = { calls += "open-now-playing" },
                reportNowPlaying = { calls += "report:${it.id.value}" },
                resetSidecars = { calls += "reset-sidecars" },
                resetProgress = { calls += "reset-progress" },
                refillRadioQueue = { calls += "refill-radio" },
                loadRelatedTracks = { calls += "related:${it.id.value}" },
                loadAudioTags = { calls += "tags:${it.id.value}" },
                loadLyrics = { calls += "lyrics:${it.id.value}" },
                startAudioPrefetch = { calls += "prefetch" },
                startSidecarPrep = { calls += "sidecars" },
                updateNotificationMetadata = { title, subtitle, cover -> calls += "notification:$title:$subtitle:$cover" },
            ),
        )

        assertEquals(
            listOf(
                "clear-shuffle",
                "clear-radio",
                "clear-station",
                "reset-stream",
                "set-track:one",
                "set-cover:cover",
                "favorite:true:true",
                "increment-report",
                "save-session",
                "open-now-playing",
                "report:one",
                "reset-sidecars",
                "reset-progress",
                "refill-radio",
                "related:one",
                "tags:one",
                "lyrics:one",
                "prefetch",
                "sidecars",
                "notification:Track one:Artist:cover",
            ),
            calls,
        )
    }

    @Test
    fun skipsDisabledTrackStartEffects() {
        val calls = mutableListOf<String>()
        val track = track("one")
        val presentation = planPlaybackTrackStarted(
            previousTrack = track,
            track = track,
            openNowPlaying = false,
            nowPlayingOpen = false,
            lyricsVisible = true,
            supportsTrackFavorites = false,
        )
        val effects = planPlaybackTrackStartEffects(
            track = track,
            presentation = presentation,
            keepRadioQueueActive = true,
        )

        applyPlaybackTrackStartEffects(
            track = track,
            coverArtUrl = null,
            effects = effects,
            applier = PlaybackTrackStartEffectApplier(
                clearShuffleSnapshot = { calls += "clear-shuffle" },
                clearRadioContinuation = { calls += "clear-radio" },
                setNowPlayingTrack = { calls += "set-track" },
                applyFavoriteState = { canFavorite, isFavorite -> calls += "favorite:$canFavorite:$isFavorite" },
                openNowPlaying = { calls += "open" },
                resetSidecars = { calls += "sidecars" },
                loadAudioTags = { calls += "tags" },
                loadLyrics = { calls += "lyrics" },
            ),
        )

        assertEquals(
            listOf(
                "set-track",
                "favorite:false:false",
                "tags",
            ),
            calls,
        )
    }

    private fun track(id: String, favorited: Boolean = false): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
            favoritedAtIso8601 = if (favorited) "2026-05-30T00:00:00Z" else null,
        )
}
