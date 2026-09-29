package app.naviamp.android

import android.app.SearchManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import app.naviamp.presentation.NaviampCoreApp
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.externalPlaybackBridge
import app.naviamp.presentation.voiceFailure
import app.naviamp.presentation.systemBackCommand
import app.naviamp.ui.LocalNaviampSystemBackDispatcher
import app.naviamp.ui.NaviampApplicationSurface
import app.naviamp.ui.NaviampAndroidRasterHost
import app.naviamp.ui.NaviampSystemBackDispatcher
import app.naviamp.ui.NaviampVoiceFailure
import app.naviamp.ui.naviampVoiceMessage

/** Thin Android window and intent/permission boundary for the process-owned Core app. */
class MainActivity : FragmentActivity() {
    private var openNowPlayingRequest by mutableIntStateOf(0)
    private var settingsImportRequest by mutableStateOf<String?>(null)
    private var mediaSearchIntent by mutableStateOf<Intent?>(null)

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
            LaunchedEffect(runtime, mediaSearchIntent) {
                val request = mediaSearchIntent ?: return@LaunchedEffect
                val query = request.getStringExtra(SearchManager.QUERY).orEmpty()
                val bridge = runtime.core.externalPlaybackBridge()
                val appAction = isAndroidNamedMediaAppAction(request.action)
                val named = if (appAction) {
                    androidAppActionNamedMediaRequest(request.action, request.extras)
                } else {
                    androidNamedMediaRequest(query, request.extras)
                }
                if (named == null) {
                    if (appAction) {
                        Toast.makeText(
                            this@MainActivity,
                            naviampVoiceMessage(NaviampVoiceFailure.NoMatch),
                            Toast.LENGTH_LONG,
                        ).show()
                    } else if (query.isBlank()) bridge.play() else bridge.playSearch(query)
                } else {
                    bridge.playNamedMedia(named).status.voiceFailure()?.let { failure ->
                        Toast.makeText(this@MainActivity, naviampVoiceMessage(failure), Toast.LENGTH_LONG).show()
                    }
                }
                if (mediaSearchIntent === request) mediaSearchIntent = null
            }
            AndroidNaviampPlaybackLifecycle(runtime.core)
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides systemBackDispatcher) {
                NaviampAndroidRasterHost {
                    Box {
                        NaviampCoreApp(
                            core = runtime.core,
                            modifier = Modifier.safeDrawingPadding().imePadding(),
                            applicationSurface = naviampApplicationSurface(),
                            screenAwakeEffect = remember(window) { AndroidScreenAwakeEffect(window) },
                            applicationUpdateChecker = runtime.applicationUpdateChecker,
                        )
                        if (runtime.core.castAvailable) {
                            AndroidView(
                                factory = { context ->
                                    MediaRouteButton(context).also { button ->
                                        CastButtonFactory.setUpMediaRouteButton(context, button)
                                        AndroidNaviampCastRoutePickerEffect.attach(button)
                                    }
                                },
                                modifier = Modifier.size(1.dp).alpha(0f),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ||
            isAndroidNamedMediaAppAction(intent?.action)
        ) {
            mediaSearchIntent = intent
        }
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

/** Android's UI-mode configuration is the concrete OS boundary selecting the shared TV surface. */
private fun ComponentActivity.naviampApplicationSurface(): NaviampApplicationSurface =
    if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) {
        NaviampApplicationSurface.Television
    } else {
        NaviampApplicationSurface.Standard
    }

@Composable
private fun rememberAndroidNaviampRuntime(): AndroidNaviampApplicationRuntime {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidNaviampApplicationRuntime.get(context) }
}

const val IntentExtraOpenNowPlaying = "app.naviamp.android.OPEN_NOW_PLAYING"
