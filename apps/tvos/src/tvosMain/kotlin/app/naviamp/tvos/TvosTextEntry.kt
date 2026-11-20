package app.naviamp.tvos

import app.naviamp.ui.NaviampTelevisionTextEntry
import app.naviamp.ui.NaviampTelevisionTextEntryRequest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UITextField
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

/** UIKit owns the editor's responder chain while it presents its system keyboard. */
@OptIn(ExperimentalForeignApi::class)
internal class TvosTextEntry(
    private val presentingController: () -> UIViewController?,
) : NaviampTelevisionTextEntry {
    override suspend fun enter(request: NaviampTelevisionTextEntryRequest): String? {
        val presenter = presentingController() ?: return null
        if (presenter.viewIfLoaded?.window == null || presenter.presentedViewController != null) return null
        val editor = UIAlertController.alertControllerWithTitle(
            request.label, message = null, preferredStyle = UIAlertControllerStyleAlert,
        )
        var completed = false
        return try {
            suspendCancellableCoroutine { continuation ->
                fun complete(value: String?) {
                    if (completed || !continuation.isActive) return
                    completed = true
                    continuation.resume(value)
                }
                editor.addTextFieldWithConfigurationHandler { field ->
                    field?.text = request.value
                }
                val field = editor.textFields?.firstOrNull() as? UITextField
                editor.addAction(UIAlertAction.actionWithTitle(request.submitLabel, UIAlertActionStyleDefault) {
                    complete(field?.text.orEmpty())
                })
                editor.addAction(UIAlertAction.actionWithTitle(request.cancelLabel, UIAlertActionStyleCancel) {
                    complete(null)
                })
                presenter.presentViewController(editor, animated = true) { field?.becomeFirstResponder() }
            }
        } finally {
            if (!completed && presenter.presentedViewController == editor) {
                editor.dismissViewControllerAnimated(false, completion = null)
            }
        }
    }
}
