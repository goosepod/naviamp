package app.naviamp.android

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.naviamp.presentation.NaviampCoreApp
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.systemBackCommand
import app.naviamp.ui.LocalNaviampSystemBackDispatcher
import app.naviamp.ui.NaviampSystemBackDispatcher

/** Thin Android window and intent/permission boundary for the process-owned Core app. */
class MainActivity : ComponentActivity() {
    private var openNowPlayingRequest by mutableIntStateOf(0)
    private var settingsImportRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            rememberAndroidCoreUriPickers()
            val runtime = rememberAndroidNaviampRuntime()
            val coreState by runtime.core.state.collectAsState()
            val systemBackDispatcher = remember { NaviampSystemBackDispatcher() }
            val sharedUiBackHandler = systemBackDispatcher.currentHandler
            val systemBackCommand = coreState.systemBackCommand()
            BackHandler(enabled = sharedUiBackHandler != null || systemBackCommand != null) {
                sharedUiBackHandler?.invoke() ?: systemBackCommand?.let(runtime.core::dispatch)
            }
            LaunchedEffect(runtime, openNowPlayingRequest) {
                if (openNowPlayingRequest > 0) {
                    runtime.core.dispatch(NaviampCoreCommand.Navigation.OpenNowPlaying)
                }
            }
            LaunchedEffect(runtime, settingsImportRequest) {
                settingsImportRequest?.let { path ->
                    runtime.core.dispatch(NaviampCoreCommand.SettingsSync.ImportFilePath(path))
                    settingsImportRequest = null
                }
            }
            AndroidNaviampPlaybackLifecycle(runtime.core)
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides systemBackDispatcher) {
                NaviampCoreApp(
                    core = runtime.core,
                    modifier = Modifier.safeDrawingPadding().imePadding(),
                    applicationUpdateChecker = runtime.applicationUpdateChecker,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(IntentExtraOpenNowPlaying, false) == true) {
            openNowPlayingRequest += 1
        }
        settingsDocumentUri(intent)?.let { settingsImportRequest = it.toString() }
    }

    @Suppress("DEPRECATION")
    private fun settingsDocumentUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }
}

@Composable
private fun rememberAndroidNaviampRuntime(): AndroidNaviampApplicationRuntime {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidNaviampApplicationRuntime.get(context) }
}

const val IntentExtraOpenNowPlaying = "app.naviamp.android.OPEN_NOW_PLAYING"
