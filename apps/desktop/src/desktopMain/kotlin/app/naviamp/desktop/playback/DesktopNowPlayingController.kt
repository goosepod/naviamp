package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.lyrics.LyricsOffsetController
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.coverArtPreloadUrls
import app.naviamp.domain.playback.lyricsLoadingStatus
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.ui.preloadJvmPlatformCoverArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopNowPlayingController(
    private val audioWaveformService: AudioWaveformService,
    private val lyricsSidecarService: LyricsSidecarService,
    private val audioMetadataSidecarService: AudioMetadataSidecarService,
    private val localLibraryIndexRepository: LocalLibraryIndexRepository,
    lyricsOffsetRepository: LyricsOffsetRepository,
    private val playbackAudioAssets: PlaybackAudioAssetRepository,
    private val playbackEngine: PlaybackEngine,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val playbackSettings: () -> PlaybackSettings,
    private val cacheSettings: () -> CacheSettings,
    private val appRoute: () -> DesktopAppRoute,
    private val lyricsVisible: () -> Boolean,
    private val playbackQueue: () -> PlaybackQueue,
    private val nowPlayingTrack: () -> Track?,
    private val nowPlayingCoverArtUrl: () -> String?,
) {
    private val lyricsOffsetController = LyricsOffsetController(lyricsOffsetRepository)

    var waveform by mutableStateOf<AudioWaveform?>(null)
        private set
    var waveformStatus by mutableStateOf("No track")
        private set
    var waveformReloadToken by mutableStateOf(0)
        private set
    var audioTags by mutableStateOf<List<AudioTag>?>(null)
        private set
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var lyricsStatus by mutableStateOf<String?>(null)
        private set
    var relatedTracks by mutableStateOf<List<Track>>(emptyList())
        private set

    fun updateWaveform(waveform: AudioWaveform?) {
        this.waveform = waveform
    }

    fun updateWaveformStatus(status: String) {
        waveformStatus = status
    }

    fun updateAudioTags(tags: List<AudioTag>?) {
        audioTags = tags
    }

    fun updateLyricsStatus(status: String?) {
        lyricsStatus = status
    }

    fun incrementWaveformReloadToken() {
        waveformReloadToken += 1
    }

    fun clearLyricsAndReloadAnalysis() {
        lyrics = null
        lyricsStatus = null
        incrementWaveformReloadToken()
    }

    fun resetAnalysis(status: String) {
        waveform = null
        waveformStatus = status
        audioTags = null
        setNowPlayingLyricsWithSavedOffset(null)
        lyricsStatus = null
    }

    fun setNowPlayingLyricsWithSavedOffset(lyrics: Lyrics?) {
        this.lyrics = lyricsOffsetController.withSavedOffset(
            sourceId = sourceId(),
            track = nowPlayingTrack(),
            lyrics = lyrics,
        )
    }

    fun handleLyricsOffsetChanged(offsetMillis: Int) {
        lyrics = lyricsOffsetController.saveOffset(
            sourceId = sourceId(),
            track = nowPlayingTrack(),
            lyrics = lyrics,
            offsetMillis = offsetMillis,
        )
    }

    suspend fun loadNowPlayingAnalysis() {
        val lyricsVisibleForWork = lyricsVisible() && appRoute() == DesktopAppRoute.Player
        val track = nowPlayingTrack() ?: run {
            waveform = null
            waveformStatus = "No track"
            lyricsStatus = null
            return
        }
        if (track.isInternetRadioTrack()) {
            waveform = null
            waveformStatus = "Internet radio"
            audioTags = null
            setNowPlayingLyricsWithSavedOffset(null)
            lyricsStatus = null
            return
        }
        val activeSourceId = sourceId() ?: run {
            waveform = null
            waveformStatus = "No source"
            lyricsStatus = null
            return
        }
        val activeProvider = provider() ?: run {
            waveform = null
            waveformStatus = "No provider"
            lyricsStatus = null
            return
        }
        val activePlaybackSettings = playbackSettings()
        val activeCacheSettings = cacheSettings()
        val quality = activePlaybackSettings.streamQuality(playbackEngine)
        waveformStatus = "Loading"
        lyricsStatus = if (lyricsVisibleForWork) lyricsLoadingStatus(activePlaybackSettings.lrclibLyricsEnabled) else null

        val analysis = withContext(Dispatchers.IO) {
            runCatching {
                val waveformResult = audioWaveformService.loadOrCreateWaveform(
                    sourceId = activeSourceId,
                    provider = activeProvider,
                    track = track,
                    quality = quality,
                    audioCachingEnabled = activeCacheSettings.audioCachingEnabled,
                )
                val audioPath = waveformResult.localAudio ?: resolvePlaybackAudioSource(
                        sourceId = activeSourceId,
                        track = track,
                        quality = quality,
                        audioCachingEnabled = activeCacheSettings.audioCachingEnabled,
                        audioAssets = playbackAudioAssets,
                    ).localAudio
                val waveform = waveformResult.waveform
                val status = waveformResult.status(activeCacheSettings.audioCachingEnabled)
                val tags = audioPath
                    ?.let { audio -> audioMetadataSidecarService.audioTags(audio) }
                    .orEmpty()
                val lyrics = if (lyricsVisibleForWork) {
                    lyricsSidecarService.loadLyrics(
                        sourceId = activeSourceId,
                        provider = activeProvider,
                        track = track,
                        quality = quality,
                        audioCachingEnabled = activeCacheSettings.audioCachingEnabled,
                        onlineLyricsEnabled = activePlaybackSettings.lrclibLyricsEnabled,
                    ).lyrics
                } else {
                    null
                }
                DesktopNowPlayingAnalysis(waveform, status, tags, lyrics)
            }.getOrNull()
        }
        waveform = analysis?.waveform
        waveformStatus = analysis?.waveformStatus ?: "Unavailable"
        audioTags = analysis?.audioTags
        setNowPlayingLyricsWithSavedOffset(analysis?.lyrics)
        lyricsStatus = null
    }

    suspend fun loadRelatedTracks() {
        val track = nowPlayingTrack()
        val activeSourceId = sourceId()
        if (track == null || activeSourceId == null || track.isInternetRadioTrack()) {
            relatedTracks = emptyList()
            return
        }
        val activeProvider = provider()
        if (
            playbackSettings().sonicSimilarityEnabled &&
            activeProvider?.capabilities?.supportsSonicSimilarity == true
        ) {
            relatedTracks = withContext(Dispatchers.IO) {
                activeProvider.sonicSimilarTracks(track.id, count = RelatedTracksLimit.toInt())
            }
            if (relatedTracks.isNotEmpty()) return
        }
        relatedTracks = withContext(Dispatchers.IO) {
            localLibraryIndexRepository.relatedLibraryTracks(activeSourceId, track, limit = RelatedTracksLimit)
        }
    }

    suspend fun preloadCoverArt() {
        val activeProvider = provider() ?: return
        val urls = coverArtPreloadUrls(
            queue = playbackQueue(),
            currentCoverArtUrl = nowPlayingCoverArtUrl(),
            historyLimit = CoverArtPreloadHistoryLimit,
            upcomingLimit = CoverArtPreloadUpcomingLimit,
            coverArtUrl = activeProvider::coverArtUrl,
        )
        withContext(Dispatchers.IO) {
            preloadJvmPlatformCoverArt(urls)
        }
    }
}
