package app.naviamp.desktop.playback

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.mergeWith
import app.naviamp.domain.playback.planPlaybackTrackStartEffects
import app.naviamp.domain.playback.planPlaybackTrackStarted
import app.naviamp.domain.playback.shouldClearPendingSeek
import app.naviamp.domain.playback.shouldIgnoreProgressForPendingSeek
import app.naviamp.domain.playback.shouldUpdatePlaybackProgressUi
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.desktop.DesktopAppRoute
import app.naviamp.desktop.PlaybackProgressUiUpdateIntervalMillis
import app.naviamp.desktop.PlaybackProgressUiUpdateThresholdSeconds

fun desktopPlaylistCallbacks(
    provider: () -> MediaProvider?,
    appRoute: () -> DesktopAppRoute,
    setAppRoute: (DesktopAppRoute) -> Unit,
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
    clearSubmittedPlayReportSessionId: () -> Unit,
    incrementNowPlayingWaveformReloadToken: () -> Unit,
    reportNowPlaying: (Track) -> Unit,
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
    maybeReportPlayed: (PlaybackProgress) -> Unit,
): PlaylistCallbacks =
    PlaylistCallbacks(
        onTrackStarted = { track, coverArtUrl ->
            val trackStartedPlan = planPlaybackTrackStarted(
                previousTrack = nowPlayingTrack(),
                track = track,
                openNowPlaying = openPlayerOnTrackStart(),
                nowPlayingOpen = appRoute() == DesktopAppRoute.Player,
                lyricsVisible = false,
                supportsTrackFavorites = provider()?.capabilities?.supportsTrackFavorites == true,
            )
            val effectsPlan = planPlaybackTrackStartEffects(
                track = track,
                presentation = trackStartedPlan,
                keepRadioQueueActive = true,
            )
            if (effectsPlan.presentation.clearShuffleSnapshot) {
                clearShuffleSnapshot()
            }
            if (effectsPlan.presentation.clearInternetRadioNowPlaying) setNowPlayingInternetRadioStation(null)
            if (effectsPlan.presentation.resetStreamMetadata) setNowPlayingStreamMetadata(PlaybackStreamMetadata())
            setNowPlayingTrack(track)
            setNowPlayingCoverArtUrl(coverArtUrl)
            incrementPlayReportSessionId()
            clearSubmittedPlayReportSessionId()
            if (effectsPlan.presentation.shouldReportNowPlaying) reportNowPlaying(track)
            if (effectsPlan.presentation.resetSidecars) {
                setNowPlayingWaveform(null)
                setNowPlayingWaveformStatus("Waiting")
                setNowPlayingAudioTags(null)
                setNowPlayingLyrics(null)
                setNowPlayingLyricsStatus(null)
                incrementNowPlayingWaveformReloadToken()
            }
            if (effectsPlan.presentation.resetProgress) setPlaybackProgress(PlaybackProgress.Unknown)
            if (effectsPlan.refillRadioQueue) refillRadioIfNeeded(activeQueue())
            if (effectsPlan.presentation.shouldOpenNowPlaying) {
                setAppRoute(DesktopAppRoute.Player)
            }
        },
        onQueueChanged = { queue ->
            setPlaybackQueue(queue)
            savePlaybackSession(queue, playbackProgress().positionSeconds)
        },
        onPlaybackStateChanged = { state ->
            setPlaybackState(state)
        },
        onPlaybackProgressChanged = progressChanged@{ progress ->
            val pendingSeek = pendingSeekPositionSeconds()
            val pendingSeekIssuedAt = pendingSeekIssuedAtMillis()
            val progressPosition = progress.positionSeconds
            val now = System.currentTimeMillis()
            if (shouldIgnoreProgressForPendingSeek(pendingSeek, pendingSeekIssuedAt, progressPosition, now)) {
                return@progressChanged
            }
            if (shouldClearPendingSeek(pendingSeek, pendingSeekIssuedAt, progressPosition, now)) {
                setPendingSeekPositionSeconds(null)
                setPendingSeekIssuedAtMillis(null)
            }
            val currentProgress = playbackProgress()
            val mergedProgress = progress.mergeWith(currentProgress)
            maybeSavePlaybackPosition(mergedProgress)
            maybeReportPlayed(mergedProgress)
            if (
                shouldUpdatePlaybackProgressUi(
                    pendingSeekPositionSeconds = pendingSeek,
                    currentProgress = currentProgress,
                    mergedProgress = mergedProgress,
                    nowMillis = now,
                    lastUiUpdateMillis = lastPlaybackProgressUiUpdateMillis(),
                    positionThresholdSeconds = PlaybackProgressUiUpdateThresholdSeconds,
                    updateIntervalMillis = PlaybackProgressUiUpdateIntervalMillis,
                )
            ) {
                setPlaybackProgress(mergedProgress)
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
