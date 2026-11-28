package app.naviamp.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.naviamp.ui.NaviampQrScanner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * Android camera boundary for Connect QR scanning (#715): the camera feature check, the CAMERA runtime
 * permission prompt and a ZXing camera preview. When to scan and what a scanned link does are shared.
 */
class AndroidNaviampQrScanner(context: Context) : NaviampQrScanner {
    private val appContext = context.applicationContext
    private var granted by mutableStateOf(permissionGranted())

    override val available: Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    private fun permissionGranted() =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Composable
    override fun hasCameraPermission(): Boolean {
        // Access can change in system settings while Naviamp is in the background.
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) granted = permissionGranted()
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        return granted
    }

    @Composable
    override fun rememberCameraPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val result by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            granted = allowed
            result(allowed)
        }
        return remember(launcher) { { launcher.launch(Manifest.permission.CAMERA) } }
    }

    @Composable
    override fun Viewfinder(onScanned: (String) -> Unit, modifier: Modifier) {
        val scanned by rememberUpdatedState(onScanned)
        val context = LocalContext.current
        val owner = LocalLifecycleOwner.current
        val view = remember(context) {
            BarcodeView(context).apply {
                decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                decodeContinuous { result -> result.text?.let { scanned(it) } }
            }
        }
        // The camera runs only while the preview is composed in a resumed activity.
        DisposableEffect(owner, view) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.resume()
                    Lifecycle.Event.ON_PAUSE -> view.pause()
                    else -> Unit
                }
            }
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
            owner.lifecycle.addObserver(observer)
            onDispose {
                owner.lifecycle.removeObserver(observer)
                view.pause()
            }
        }
        AndroidView(factory = { view }, modifier = modifier)
    }
}
