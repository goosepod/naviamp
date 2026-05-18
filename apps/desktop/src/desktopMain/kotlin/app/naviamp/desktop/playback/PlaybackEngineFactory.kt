package app.naviamp.desktop.playback

import app.naviamp.desktop.playback.bass.BassPlaybackEngine
import app.naviamp.desktop.playback.bass.BassNative
import app.naviamp.domain.playback.PlaybackEngine

object PlaybackEngineFactory {
    fun createDefault(): PlaybackEngine {
        val requestedEngine = requestedEngine()
        if (requestedEngine == "bass") {
            return BassPlaybackEngine()
        }
        if (requestedEngine.isNullOrBlank()) {
            val bass = BassNative.load()
            if (bass.isSuccess) {
                return BassPlaybackEngine(bass)
            }
        }
        val mpv = MpvExecutableResolver().resolve()
        return if (mpv != null) {
            if (requestedEngine == "mpv-crossfade-prototype") {
                ExperimentalCrossfadeMpvPlaybackEngine(mpv.absolutePath)
            } else {
                MpvProcessPlaybackEngine(mpv.absolutePath)
            }
        } else {
            JLayerPlaybackEngine()
        }
    }

    private fun requestedEngine(): String? =
        System.getProperty("naviamp.playback.engine")
            ?: System.getenv("NAVIAMP_PLAYBACK_ENGINE")
}
