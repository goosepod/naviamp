package app.naviamp.android

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.SidecarTypeLyrics
import app.naviamp.domain.playback.lyricsLoadingStatus
import app.naviamp.domain.playback.lyricsUnavailableStatus
import app.naviamp.domain.playback.recordSidecarFailure
import app.naviamp.domain.playback.recordSidecarSuccess
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
                lyricsSidecarService.loadLyrics(
                    sourceId = sourceId,
                    provider = activeProvider,
                    track = track,
                    quality = quality,
                    audioCachingEnabled = true,
                    onlineLyricsEnabled = state.playbackSettings.lrclibLyricsEnabled,
                ).lyrics?.copy(
                    offsetMillis = sourceId?.let { lyricsOffsetRepository.lyricsOffsetMillis(it, track.id) } ?: 0,
                )
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
                    state.lyricsByTrackId = state.lyricsByTrackId + (track.id.value to null)
                    val message = lyricsUnavailableStatus(error)
                    state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (track.id.value to message)
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
        lyricsOffsetRepository.saveLyricsOffsetMillis(sourceId, track.id, offsetMillis)
        state.lyricsByTrackId = state.lyricsByTrackId + (
            track.id.value to state.lyricsByTrackId[track.id.value]?.copy(offsetMillis = offsetMillis)
            )
    }

    fun reloadVisibleLyrics() {
        state.lyricsByTrackId = emptyMap()
        state.lyricsStatusByTrackId = emptyMap()
        if (state.lyricsVisible && state.nowPlayingOpen) {
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
