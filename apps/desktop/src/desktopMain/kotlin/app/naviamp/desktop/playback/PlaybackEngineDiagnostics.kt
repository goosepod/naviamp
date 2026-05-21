package app.naviamp.desktop.playback

interface PlaybackEngineDiagnostics {
    fun statsRows(): List<Pair<String, String>>
}
