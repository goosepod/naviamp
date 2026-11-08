package app.naviamp.domain.playback

/** Name/signature contract invoked by the shared JNI BASS end-sync adapter. */
class BassEndSyncCallback(private val callback: (Int) -> Unit) {
    fun onEndSync(stream: Int) = callback(stream)
}
