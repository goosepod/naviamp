package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Standard phone/Desktop presentation of the shared playback-target actions. */
@Composable
internal fun NaviampConnectTargetSettings(
    connect: NaviampConnectSettingsUi,
    colors: NaviampColors,
    actions: NaviampConnectSettingsActions,
) {
    if (!connect.canAdvertise) return
    // The shared phase also describes outgoing pairing on bidirectional devices.
    val targetPairingActive = connect.pairingCode != null ||
        (connect.selectedTargetId == null && connect.pairingPhase in setOf(
            NaviampConnectPairingUiPhase.Starting, NaviampConnectPairingUiPhase.Handshaking,
        ))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.connect_setup_code_consent), color = colors.secondaryText)
        PrimaryButton(
            stringResource(if (targetPairingActive) Res.string.tv_stop_pairing else Res.string.tv_show_pairing_code),
            colors,
            enabled = targetPairingActive || connect.pairingPhase != NaviampConnectPairingUiPhase.Handshaking,
            onClick = if (targetPairingActive) actions.onStopPairingMode else actions.onStartPairingMode,
            modifier = Modifier.testTag("connect-target-pairing"),
        )
        connect.pairingCode?.let { code ->
            Text(stringResource(Res.string.connect_pairing_code), color = colors.secondaryText)
            Text(formatNaviampConnectPairingCode(code), color = colors.primaryText,
                fontSize = 32.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("connect-target-code"))
            connect.listeningPort?.let { port ->
                Text(stringResource(Res.string.connect_manual_port, port), color = colors.secondaryText)
            }
        }
    }
}
