package app.naviamp.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.naviamp.android.playback.AndroidAudioWaveformAnalyzer
import app.naviamp.android.playback.AndroidBassAudioBackend
import app.naviamp.android.playback.AndroidBassJni
import app.naviamp.android.playback.AndroidFocusedBassPlaybackEngine
import app.naviamp.presentation.NaviampCore
import app.naviamp.presentation.createNaviampCore
import app.naviamp.presentation.externalPlaybackBridge
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.defaultNaviampApplicationUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-owned Android resource graph. The Activity renders this Core instance and the playback
 * service keeps using it after the Activity is destroyed.
 */
class AndroidNaviampApplicationRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bassJni = AndroidBassJni.load().getOrElse { error ->
        throw IllegalStateException("BASS is required for Android playback.", error)
    }
    private val bass = AndroidBassAudioBackend(bassJni)
    private val engine = AndroidFocusedBassPlaybackEngine(appContext, bass)
    private val catalog = AndroidNaviampCoreCatalog.create(
        context = appContext,
        scope = scope,
        playbackEngine = engine,
        waveformAnalyzer = AndroidAudioWaveformAnalyzer(bass),
        directoryPicker = AndroidCoreUriPickerRegistry.pickers.directory,
        documentPicker = AndroidCoreUriPickerRegistry.pickers.document,
        isMobileData = ::isMobileData,
        prepareWaveformAnalysis = { bass.init().getOrThrow() },
        waveformWorkContext = Dispatchers.IO,
    )

    val core: NaviampCore = createNaviampCore(scope, catalog.environment)
    val externalPlayback = core.externalPlaybackBridge()
    val applicationUpdateChecker: NaviampApplicationUpdateChecker? = defaultNaviampApplicationUpdateChecker()

    init {
        AndroidNaviampPlaybackRuntime.install(externalPlayback)
        if (core.state.value.shell.connectionSettings.currentSourceId != null) {
            scope.launch { core.maintainProviderSession() }
        }
    }

    private fun isMobileData(): Boolean {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    companion object {
        @Volatile
        private var instance: AndroidNaviampApplicationRuntime? = null

        fun get(context: Context): AndroidNaviampApplicationRuntime =
            instance ?: synchronized(this) {
                instance ?: AndroidNaviampApplicationRuntime(context).also { instance = it }
            }
    }
}
