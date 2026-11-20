package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.*
import org.jetbrains.compose.resources.stringResource
import app.naviamp.ui.generated.resources.*

enum class NaviampRecoveryFailureUi { Capacity, Unreadable, Conflict, Unavailable }

/** Shared, remote-focusable recovery UI; native hosts never invent failure/reset behavior. */
@Composable
fun NaviampRecoveryFailureScreen(
    failure: NaviampRecoveryFailureUi?, starting: Boolean, overlay: Boolean, confirmReset: Boolean,
    onRetry: () -> Unit, onDismiss: () -> Unit, onRequestReset: () -> Unit,
    onConfirmReset: () -> Unit, onCancelReset: () -> Unit,
    resetAvailable: Boolean = true,
) {
    val colors = if (LocalNaviampApplicationSurface.current == NaviampApplicationSurface.Television) NaviampTelevisionColors else NaviampColors.Dark
    MaterialTheme(colorScheme = darkColorScheme(background = colors.background, surface = colors.controlSurface,
        primary = colors.accent, onPrimary = colors.onAccent, onBackground = colors.primaryText, onSurface = colors.primaryText),
        typography = rememberNaviampTypography()) {
        RecoveryFailureContent(failure, starting, overlay, confirmReset && resetAvailable, resetAvailable,
            onRetry, onDismiss, onRequestReset, onConfirmReset, onCancelReset)
    }
}

@Composable
private fun RecoveryFailureContent(
    failure: NaviampRecoveryFailureUi?, starting: Boolean, overlay: Boolean, confirmReset: Boolean,
    resetAvailable: Boolean,
    onRetry: () -> Unit, onDismiss: () -> Unit, onRequestReset: () -> Unit,
    onConfirmReset: () -> Unit, onCancelReset: () -> Unit,
) {
    val television = LocalNaviampApplicationSurface.current == NaviampApplicationSurface.Television
    val first = remember { FocusRequester() }
    val second = remember { FocusRequester() }
    fun Modifier.focusAction(requester: FocusRequester, target: FocusRequester, direction: Key) =
        focusRequester(requester).onPreviewKeyEvent { event ->
            if (event.key != direction) false else {
                if (event.type == KeyEventType.KeyDown) target.requestFocus()
                true
            }
        }
    val title = stringResource(if (confirmReset) Res.string.storage_recovery_reset_title else Res.string.storage_recovery_title)
    val message = stringResource(when {
        confirmReset -> Res.string.storage_recovery_reset_message
        failure == NaviampRecoveryFailureUi.Capacity -> Res.string.storage_recovery_capacity
        failure == NaviampRecoveryFailureUi.Unreadable && resetAvailable -> Res.string.storage_recovery_unreadable
        failure == NaviampRecoveryFailureUi.Conflict -> Res.string.storage_recovery_conflict
        else -> Res.string.storage_recovery_unavailable
    })
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (confirmReset) {
                RecoveryAction(stringResource(Res.string.common_cancel), onCancelReset, Modifier.focusAction(first, second, Key.DirectionRight).testTag("storage-recovery-cancel"))
                RecoveryAction(stringResource(Res.string.common_reset), onConfirmReset, Modifier.focusAction(second, first, Key.DirectionLeft).testTag("storage-recovery-confirm"))
            } else {
                if (resetAvailable) {
                    RecoveryAction(stringResource(Res.string.library_sources_retry), onRetry, Modifier.focusAction(first, second, Key.DirectionRight).testTag("storage-recovery-retry"))
                    RecoveryAction(stringResource(Res.string.common_reset), onRequestReset, Modifier.focusAction(second, first, Key.DirectionLeft).testTag("storage-recovery-reset"))
                } else {
                    RecoveryAction(stringResource(Res.string.library_sources_retry), onRetry, Modifier.focusRequester(first).testTag("storage-recovery-retry"))
                }
            }
        }
        LaunchedEffect(confirmReset) { withFrameNanos { }; first.requestFocus() }
    }
    if (overlay) {
        NaviampAlertDialog(
            onDismissRequest = if (confirmReset) onCancelReset else onDismiss,
            title = { Text(title) }, text = { Text(message) }, confirmButton = actions,
        )
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(48.dp)) {
                if (starting) CircularProgressIndicator() else Column(
                    modifier = Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    Text(message, style = MaterialTheme.typography.bodyLarge.let { if (television) it.copy(fontSize = 24.sp) else it })
                    actions()
                }
            }
        }
    }
}

@Composable
private fun RecoveryAction(label: String, onClick: () -> Unit, modifier: Modifier) {
    if (LocalNaviampApplicationSurface.current == NaviampApplicationSurface.Television) {
        TelevisionTextButton(label, NaviampTelevisionColors, calmFocus = true, fontSize = 24.sp, onClick = onClick, modifier = modifier)
    } else Button(onClick = onClick, modifier = modifier) { Text(label) }
}
