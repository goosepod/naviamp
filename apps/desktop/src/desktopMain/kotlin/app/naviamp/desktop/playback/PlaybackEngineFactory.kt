package app.naviamp.desktop.playback

import app.naviamp.desktop.playback.bass.BassPlaybackEngine
import app.naviamp.desktop.playback.bass.BassNative
import app.naviamp.domain.playback.PlaybackEngine

object PlaybackEngineFactory {
    fun createDefault(): PlaybackEngine {
        val requestedEngine = requestedEngine()
        if (requestedEngine.isNullOrBlank() || requestedEngine == "bass") {
            return BassPlaybackEngine(BassNative.load())
        }
        return BassPlaybackEngine(BassNative.load())
    }

    private fun requestedEngine(): String? =
        System.getProperty("naviamp.playback.engine")
            ?: System.getenv("NAVIAMP_PLAYBACK_ENGINE")
}
