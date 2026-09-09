package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NaviampConnectRecoveryUi.description(): String = stringResource(when (problem) {
    NaviampConnectRecoveryProblem.LocalNetworkPermission -> Res.string.connect_recovery_permission
    NaviampConnectRecoveryProblem.DiscoveryUnavailable -> Res.string.connect_recovery_discovery
    NaviampConnectRecoveryProblem.AdvertisingUnavailable -> Res.string.connect_recovery_advertising
})

@Composable
internal fun NaviampConnectRecoveryPanel(
    recovery: NaviampConnectRecoveryUi,
    colors: NaviampColors,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    television: Boolean = false,
) {
    val settingsFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth().testTag("connect-recovery"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(recovery.description(), color = colors.primaryText)
        if (recovery.settingsOpenFailed) {
            Text(stringResource(Res.string.connect_recovery_settings_failed), color = colors.secondaryText)
        }
        if (recovery.canOpenSettings) {
            RecoveryButton(stringResource(Res.string.connect_recovery_open_settings), "connect-recovery-settings",
                television, colors, onOpenSettings, Modifier.focusRequester(settingsFocus).onPreviewKeyEvent { event ->
                    if (television && event.key == Key.DirectionDown) {
                        if (event.type == KeyEventType.KeyDown) retryFocus.requestFocus()
                        true
                    } else false
                })
        }
        RecoveryButton(stringResource(Res.string.connect_recovery_retry), "connect-recovery-retry",
            television, colors, onRetry, Modifier.focusRequester(retryFocus).onPreviewKeyEvent { event ->
                if (television && recovery.canOpenSettings && event.key == Key.DirectionUp) {
                    if (event.type == KeyEventType.KeyDown) settingsFocus.requestFocus()
                    true
                } else false
            })
    }
}

@Composable
private fun RecoveryButton(label: String, tag: String, television: Boolean, colors: NaviampColors, action: () -> Unit, modifier: Modifier) {
    if (television) {
        TelevisionTextButton(label, colors, calmFocus = true, onClick = action, modifier = modifier.testTag(tag))
    } else {
        TextButton(onClick = action, modifier = modifier.testTag(tag)) { Text(label) }
    }
}
