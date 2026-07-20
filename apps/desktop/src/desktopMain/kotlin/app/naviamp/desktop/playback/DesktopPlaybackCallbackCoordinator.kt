package app.naviamp.desktop.playback

import app.naviamp.desktop.DesktopSystemClock

import app.naviamp.domain.app.NaviampRoute

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackProgressEffectApplier
import app.naviamp.domain.playback.PlaybackTrackStartEffectApplier
import app.naviamp.domain.playback.applyPlaybackProgressEffects
import app.naviamp.domain.playback.applyPlaybackTrackStartEffects
import app.naviamp.domain.playback.planPlaybackProgressUpdate
import app.naviamp.domain.playback.planPlaybackTrackStart
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.desktop.PlaybackProgressUiUpdateIntervalMillis
import app.naviamp.desktop.PlaybackProgressUiUpdateThresholdSeconds

fun desktopPlaylistCallbacks(
    provider: () -> MediaProvider?,
    appRoute: () -> NaviampRoute,
    setAppRoute: (NaviampRoute) -> Unit,
    openPlayerOnTrackStart: () -> Boolean,
    nowPlayingTrack: () -> Track?,
    setNowPlayingTrack: (Track?) -> Unit,
    setNowPlayingCoverArtUrl: (String?) -> Unit,
    setNowPlayingWaveform: (AudioWaveform?) -> Unit,
    setNowPlayingWaveformStatus: (String) -> Unit,
    setNowPlayingAudioTags: (List<AudioTag>?) -> Unit,
    setNowPlayingLyrics: (Lyrics?) -> Unit,
    setNowPlayingLyricsStatus: (String?) -> Unit,
    setNowPlayingInternetRadioStation: (app.naviamp.domain.InternetRadioStation?) -> Unit,
    setNowPlayingStreamMetadata: (PlaybackStreamMetadata) -> Unit,
    incrementPlayReportSessionId: () -> Unit,
    incrementNowPlayingWaveformReloadToken: () -> Unit,
    reportNowPlaying: (Track) -> Unit,
    maybeReportPlaybackState: (PlaybackState, PlaybackProgress) -> Unit,
    clearShuffleSnapshot: () -> Unit,
    refillRadioIfNeeded: (PlaybackQueue) -> Unit,
    activeQueue: () -> PlaybackQueue,
    setPlaybackQueue: (PlaybackQueue) -> Unit,
    savePlaybackSession: (PlaybackQueue, Double?) -> Unit,
    playbackProgress: () -> PlaybackProgress,
    setPlaybackProgress: (PlaybackProgress) -> Unit,
    setPlaybackState: (PlaybackState) -> Unit,
    pendingSeekPositionSeconds: () -> Double?,
    setPendingSeekPositionSeconds: (Double?) -> Unit,
    pendingSeekIssuedAtMillis: () -> Long?,
    setPendingSeekIssuedAtMillis: (Long?) -> Unit,
    lastPlaybackProgressUiUpdateMillis: () -> Long,
    setLastPlaybackProgressUiUpdateMillis: (Long) -> Unit,
    maybeSavePlaybackPosition: (PlaybackProgress) -> Unit,
): PlaylistCallbacks =
    PlaylistCallbacks(
        onTrackStarted = { track, coverArtUrl ->
            val effectsPlan = planPlaybackTrackStart(
                previousTrack = nowPlayingTrack(),
                track = track,
                openNowPlaying = openPlayerOnTrackStart(),
                nowPlayingOpen = appRoute() == NaviampRoute.Player,
                lyricsVisible = false,
                supportsTrackFavorites = provider()?.capabilities?.supportsTrackFavorites == true,
                keepRadioQueueActive = true,
            )
            applyPlaybackTrackStartEffects(
                track = track,
                coverArtUrl = coverArtUrl,
                effects = effectsPlan,
                applier = PlaybackTrackStartEffectApplier(
                    clearShuffleSnapshot = clearShuffleSnapshot,
                    clearInternetRadioNowPlaying = { setNowPlayingInternetRadioStation(null) },
                    resetStreamMetadata = { setNowPlayingStreamMetadata(PlaybackStreamMetadata()) },
                    setNowPlayingTrack = { startedTrack -> setNowPlayingTrack(startedTrack) },
                    setNowPlayingCoverArtUrl = setNowPlayingCoverArtUrl,
                    incrementPlayReportSession = incrementPlayReportSessionId,
                    openNowPlaying = { setAppRoute(NaviampRoute.Player) },
                    reportNowPlaying = reportNowPlaying,
                    resetSidecars = {
                        setNowPlayingWaveform(null)
                        setNowPlayingWaveformStatus("Waiting")
                        setNowPlayingAudioTags(null)
                        setNowPlayingLyrics(null)
                        setNowPlayingLyricsStatus(null)
                        incrementNowPlayingWaveformReloadToken()
                    },
                    resetProgress = { setPlaybackProgress(PlaybackProgress.Unknown) },
                    refillRadioQueue = { refillRadioIfNeeded(activeQueue()) },
                ),
            )
        },
        onQueueChanged = { queue ->
            setPlaybackQueue(queue)
            savePlaybackSession(queue, playbackProgress().positionSeconds)
        },
        onPlaybackStateChanged = { state ->
            setPlaybackState(state)
            maybeReportPlaybackState(state, playbackProgress())
        },
        onPlaybackProgressChanged = progressChanged@{ progress ->
            val pendingSeek = pendingSeekPositionSeconds()
            val now = DesktopSystemClock.nowEpochMillis()
            val plan = planPlaybackProgressUpdate(
                sessionToken = 1,
                activeSessionToken = 1,
                incomingProgress = progress,
                currentProgress = playbackProgress(),
                pendingSeekPositionSeconds = pendingSeek,
                pendingSeekIssuedAtMillis = pendingSeekIssuedAtMillis(),
                pendingRestoreStartPositionSeconds = null,
                nowMillis = now,
                lastExternalProgressPublishAtMillis = 0,
                externalProgressPublishIntervalMillis = Long.MAX_VALUE,
                resetUnknownProgress = false,
                keepPreviousOnLargeBackwardProgressJump = true,
                savePlaybackPosition = true,
                prepareNext = false,
                lastUiUpdateMillis = lastPlaybackProgressUiUpdateMillis(),
                positionThresholdSeconds = PlaybackProgressUiUpdateThresholdSeconds,
                uiUpdateIntervalMillis = PlaybackProgressUiUpdateIntervalMillis,
            )
            val result = applyPlaybackProgressEffects(
                plan = plan,
                updateProgress = plan.shouldUpdateUi,
                applier = PlaybackProgressEffectApplier(
                    clearPendingSeek = {
                        setPendingSeekPositionSeconds(null)
                        setPendingSeekIssuedAtMillis(null)
                    },
                    savePlaybackPosition = maybeSavePlaybackPosition,
                    reportPlaybackProgress = { mergedProgress ->
                        maybeReportPlaybackState(PlaybackState.Playing, mergedProgress)
                    },
                    updateProgress = setPlaybackProgress,
                ),
            )
            if (result.ignored || result.progress == null) return@progressChanged
            if (plan.shouldUpdateUi) {
                setLastPlaybackProgressUiUpdateMillis(now)
            }
        },
        onMetadataChanged = { metadata ->
            setNowPlayingStreamMetadata(metadata)
        },
        onCurrentTrackSidecarsReady = { track ->
            if (nowPlayingTrack()?.id == track.id) {
                incrementNowPlayingWaveformReloadToken()
            }
        },
    )
