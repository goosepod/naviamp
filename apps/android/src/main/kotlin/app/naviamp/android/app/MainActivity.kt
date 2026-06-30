package app.naviamp.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private var openNowPlayingRequest by mutableStateOf(0)
    private var autoPlayMediaIdRequest by mutableStateOf<String?>(null)
    private var autoCommandRequest by mutableStateOf<String?>(null)
    private var settingsSyncImportUriRequest by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            NaviampAndroidApp(
                openNowPlayingRequest = openNowPlayingRequest,
                autoPlayMediaIdRequest = autoPlayMediaIdRequest,
                onAutoPlayMediaIdConsumed = { autoPlayMediaIdRequest = null },
                autoCommandRequest = autoCommandRequest,
                onAutoCommandConsumed = { autoCommandRequest = null },
                settingsSyncImportUriRequest = settingsSyncImportUriRequest,
                onSettingsSyncImportUriConsumed = { settingsSyncImportUriRequest = null },
                modifier = Modifier
                    .safeDrawingPadding()
                    .imePadding(),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ExtraOpenNowPlaying, false) == true) {
            openNowPlayingRequest += 1
            android.util.Log.i("NaviampAutoCommand", "Received open-now-playing request")
        }
        intent?.getStringExtra(ExtraAutoPlayMediaId)?.takeIf { it.isNotBlank() }?.let { mediaId ->
            autoPlayMediaIdRequest = mediaId
            android.util.Log.i("NaviampAutoCommand", "Received Auto mediaId=$mediaId")
        }
        intent?.getStringExtra(ExtraAutoCommand)?.takeIf { it.isNotBlank() }?.let { command ->
            autoCommandRequest = command
            android.util.Log.i("NaviampAutoCommand", "Received Auto command=$command")
        }
        settingsSyncImportUri(intent)?.let { uri ->
            settingsSyncImportUriRequest = uri
            android.util.Log.i("NaviampSettingsSync", "Received settings sync import uri=$uri")
        }
    }

    @Suppress("DEPRECATION")
    private fun settingsSyncImportUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
    }

    companion object {
        const val ExtraOpenNowPlaying = "app.naviamp.android.OPEN_NOW_PLAYING"
        const val ExtraAutoPlayMediaId = "app.naviamp.android.AUTO_PLAY_MEDIA_ID"
        const val ExtraAutoCommand = "app.naviamp.android.AUTO_COMMAND"
    }
}
