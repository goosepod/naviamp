package app.naviamp.android.playback

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AndroidPlaybackRuntime private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val bassLoadReport: AndroidBassLoadReport = AndroidBassNativeLoader.loadBundledLibraries()
    private val bassJni: AndroidBassJni = AndroidBassJni.load().fold(
        onSuccess = { it },
        onFailure = { throw IllegalStateException("BASS is required for Android playback.", it) },
    )
    val bassAudioBackend: AndroidBassAudioBackend = AndroidBassAudioBackend(bassJni)
    val playbackEngine: AndroidPlaybackEngine = AndroidBassPlaybackEngine(appContext, bassAudioBackend)
    val waveformAnalyzer: AndroidAudioWaveformAnalyzer = AndroidAudioWaveformAnalyzer(appContext, bassAudioBackend)

    companion object {
        @Volatile
        private var instance: AndroidPlaybackRuntime? = null

        fun get(context: Context): AndroidPlaybackRuntime =
            instance ?: synchronized(this) {
                instance ?: AndroidPlaybackRuntime(context.applicationContext).also { instance = it }
            }
    }
}
