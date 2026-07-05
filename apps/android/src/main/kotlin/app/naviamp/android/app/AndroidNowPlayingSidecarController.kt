package app.naviamp.android

import android.util.Log
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.lyrics.LyricsOffsetController
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.SidecarTypeLyrics
import app.naviamp.domain.playback.lyricsLoadingStatus
import app.naviamp.domain.playback.lyricsUnavailableStatus
import app.naviamp.domain.playback.recordSidecarFailure
import app.naviamp.domain.playback.recordSidecarSuccess
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class AndroidNowPlayingSidecarController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val lyricsSidecarService: LyricsSidecarService,
    private val audioMetadataSidecarService: AudioMetadataSidecarService,
    private val sidecarStatusRepository: SidecarStatusRepository,
    private val lyricsOffsetRepository: LyricsOffsetRepository,
    private val cacheAudioTrack: suspend (String, NavidromeProvider, Track, StreamQuality) -> File?,
    private val currentStreamQuality: () -> StreamQuality,
) {
    private val lyricsOffsetController = LyricsOffsetController(lyricsOffsetRepository)

    fun loadLyrics(track: Track) {
        val activeProvider = state.provider ?: return
        if (
            state.lyricsByTrackId.containsKey(track.id.value) ||
            state.lyricsStatusByTrackId[track.id.value] != null
        ) {
            return
        }
        state.lyricsStatusByTrackId =
            state.lyricsStatusByTrackId + (track.id.value to lyricsLoadingStatus(state.playbackSettings.lrclibLyricsEnabled))
        scope.launch {
            val sourceId = state.activeSourceId
            val quality = currentStreamQuality()
            runCatching {
                withContext(Dispatchers.IO) {
                    lyricsSidecarService.loadLyrics(
                        sourceId = sourceId,
                        provider = activeProvider,
                        track = track,
                        quality = quality,
                        audioCachingEnabled = true,
                        onlineLyricsEnabled = state.playbackSettings.lrclibLyricsEnabled,
                    ).lyrics?.let { lyrics -> lyricsOffsetController.withSavedOffset(sourceId, track, lyrics) }
                }
            }
                .onSuccess { lyrics ->
                    state.lyricsByTrackId = state.lyricsByTrackId + (track.id.value to lyrics)
                    state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (track.id.value to null)
                    sourceId?.let { activeSourceId ->
                        sidecarStatusRepository.recordSidecarSuccess(
                            sourceId = activeSourceId,
                            trackId = track.id,
                            quality = StreamQuality.Original,
                            sidecarType = SidecarTypeLyrics,
                        )
                    }
                }
                .onFailure { error ->
                    val message = lyricsUnavailableStatus(error)
                    Log.w(LyricsLogTag, "Lyrics load failed for track ${track.id.value}: $message", error)
                    if (state.lyricsByTrackId[track.id.value] != null) {
                        state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (track.id.value to null)
                    } else {
                        state.lyricsByTrackId = state.lyricsByTrackId + (track.id.value to null)
                        state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (track.id.value to message)
                    }
                    sourceId?.let { activeSourceId ->
                        sidecarStatusRepository.recordSidecarFailure(
                            sourceId = activeSourceId,
                            trackId = track.id,
                            quality = StreamQuality.Original,
                            sidecarType = SidecarTypeLyrics,
                            errorMessage = message,
                        )
                    }
                }
        }
    }

    fun handleLyricsOffsetChanged(offsetMillis: Int) {
        val sourceId = state.activeSourceId ?: return
        val track = state.nowPlaying ?: return
        state.lyricsByTrackId = state.lyricsByTrackId + (
            track.id.value to lyricsOffsetController.saveOffset(
                sourceId = sourceId,
                track = track,
                lyrics = state.lyricsByTrackId[track.id.value],
                offsetMillis = offsetMillis,
            )
            )
    }

    fun reloadVisibleLyrics() {
        state.lyricsByTrackId = emptyMap()
        state.lyricsStatusByTrackId = emptyMap()
        if ((state.lyricsVisible || state.selectedVisualizer == NaviampVisualizer.LyricMirrorTunnel) && state.nowPlayingOpen) {
            state.nowPlaying?.let(::loadLyrics)
        }
    }

    fun loadAudioTags(track: Track) {
        if (state.audioTagsByTrackId.containsKey(track.id.value)) return
        val activeProvider = state.provider ?: return
        val sourceId = state.activeSourceId ?: return
        scope.launch {
            val tags = withContext(Dispatchers.IO) {
                runCatching {
                    val quality = currentStreamQuality()
                    cacheAudioTrack(sourceId, activeProvider, track, quality)
                    audioMetadataSidecarService.audioTagsForTrack(
                        sourceId = sourceId,
                        track = track,
                        quality = quality,
                        audioCachingEnabled = true,
                    )
                }.getOrElse { emptyList() }
            }
            state.audioTagsByTrackId = state.audioTagsByTrackId + (track.id.value to tags)
        }
    }
}

private const val LyricsLogTag = "NaviampLyrics"
