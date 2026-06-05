package app.naviamp.desktop.playback

interface DesktopPlaybackEngineDiagnostics {
    fun statsRows(): List<Pair<String, String>>
}
