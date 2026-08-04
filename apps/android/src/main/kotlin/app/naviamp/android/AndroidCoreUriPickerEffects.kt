package app.naviamp.android

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

data class AndroidCoreUriPickers(
    val directory: AndroidCoreUriPicker,
    val document: AndroidCoreUriPicker,
)

/** Activity-result bridge for Core's opaque URI picker effects. */
@Composable
fun rememberAndroidCoreUriPickers(): AndroidCoreUriPickers {
    val context = LocalContext.current
    val directoryState = AndroidCoreUriPickerRegistry.directoryState
    val documentState = AndroidCoreUriPickerRegistry.documentState
    val directoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { selected ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selected,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        directoryState.complete(uri?.toString())
    }
    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { selected ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selected,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        documentState.complete(uri?.toString())
    }
    directoryState.launch = { currentUri ->
        directoryLauncher.launch(currentUri?.let(Uri::parse))
    }
    documentState.launch = {
        documentLauncher.launch(AndroidSettingsDocumentMimeTypes)
    }
    DisposableEffect(Unit) {
        onDispose {
            directoryState.cancel()
            documentState.cancel()
        }
    }
    return AndroidCoreUriPickerRegistry.pickers
}

/** Process-owned picker ports; the current Activity supplies only launcher effects. */
object AndroidCoreUriPickerRegistry {
    internal val directoryState = AndroidCoreUriPickerState()
    internal val documentState = AndroidCoreUriPickerState()
    val pickers = AndroidCoreUriPickers(
        directory = AndroidCoreUriPicker(directoryState::choose),
        document = AndroidCoreUriPicker(documentState::choose),
    )
}

internal class AndroidCoreUriPickerState {
    var launch: (String?) -> Unit = {}
    private var pending: CancellableContinuation<String?>? = null

    suspend fun choose(currentUri: String?, title: String): String? =
        suspendCancellableCoroutine { continuation ->
            check(pending == null) { "A document picker is already open." }
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            launch(currentUri)
        }

    fun complete(uri: String?) {
        pending?.also { continuation ->
            pending = null
            if (continuation.isActive) continuation.resume(uri)
        }
    }

    fun cancel() {
        pending?.also { continuation ->
            pending = null
            continuation.cancel()
        }
    }
}

private val AndroidSettingsDocumentMimeTypes = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
    "application/octet-stream",
)
