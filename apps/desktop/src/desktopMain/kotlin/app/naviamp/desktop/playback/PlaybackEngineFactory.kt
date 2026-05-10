package app.naviamp.desktop.playback

object PlaybackEngineFactory {
    fun createDefault(): PlaybackEngine {
        val mpv = MpvExecutableResolver().resolve()
        return if (mpv != null) {
            if (experimentalCrossfadeEnabled()) {
                ExperimentalCrossfadeMpvPlaybackEngine(mpv.absolutePath)
            } else {
                MpvProcessPlaybackEngine(mpv.absolutePath)
            }
        } else {
            JLayerPlaybackEngine()
        }
    }

    private fun experimentalCrossfadeEnabled(): Boolean =
        System.getProperty("naviamp.playback.engine") == "mpv-crossfade-prototype" ||
            System.getenv("NAVIAMP_PLAYBACK_ENGINE") == "mpv-crossfade-prototype"
}
