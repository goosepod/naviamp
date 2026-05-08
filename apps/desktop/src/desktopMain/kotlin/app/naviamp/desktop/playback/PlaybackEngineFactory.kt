package app.naviamp.desktop.playback

object PlaybackEngineFactory {
    fun createDefault(): PlaybackEngine {
        val mpv = MpvExecutableResolver().resolve()
        return if (mpv != null) {
            MpvProcessPlaybackEngine(mpv.absolutePath)
        } else {
            JLayerPlaybackEngine()
        }
    }
}
