package app.naviamp.desktop.playback

import app.naviamp.desktop.playback.bass.DesktopBassPlaybackEngine
import app.naviamp.desktop.playback.bass.loadDesktopBassAudioBackend
import app.naviamp.domain.playback.PlaybackEngine

object DesktopPlaybackEngineFactory {
    fun createDefault(): PlaybackEngine {
        val requestedEngine = requestedEngine()
        if (requestedEngine.isNullOrBlank() || requestedEngine == "bass") {
            return DesktopBassPlaybackEngine(loadDesktopBassAudioBackend())
        }
        return DesktopBassPlaybackEngine(loadDesktopBassAudioBackend())
    }

    private fun requestedEngine(): String? =
        System.getProperty("naviamp.playback.engine")
            ?: System.getenv("NAVIAMP_PLAYBACK_ENGINE")
}
