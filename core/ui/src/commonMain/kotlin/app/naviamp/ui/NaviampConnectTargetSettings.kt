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
    val targetPairingActive = connect.pairingCode != null || connect.pendingControllerName != null ||
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
        }
        if (connect.pairingPhase == NaviampConnectPairingUiPhase.AwaitingApproval &&
            connect.pendingControllerName != null
        ) {
            Text(stringResource(Res.string.tv_only_approve_if_you_started_pairing_on_this_device),
                color = colors.secondaryText)
            PrimaryButton(stringResource(Res.string.tv_approve_named_controller, connect.pendingControllerName),
                colors, enabled = true, onClick = actions.onApproveController,
                modifier = Modifier.testTag("connect-target-approve"))
            PrimaryButton(stringResource(Res.string.tv_reject_request), colors, enabled = true,
                onClick = actions.onRejectController, modifier = Modifier.testTag("connect-target-reject"))
        }
        connect.pendingProvisioningConnectionName?.let { source ->
            Text(stringResource(Res.string.tv_provisioning_requested_by,
                connect.pendingProvisioningControllerName ?: stringResource(Res.string.tv_paired_controller_fallback)),
                color = colors.secondaryText)
            PrimaryButton(stringResource(Res.string.tv_set_up_named_source, source), colors,
                enabled = true, onClick = actions.onApproveProvisioning,
                modifier = Modifier.testTag("connect-target-approve-setup"))
            PrimaryButton(stringResource(Res.string.tv_reject_server_setup), colors,
                enabled = true, onClick = actions.onRejectProvisioning,
                modifier = Modifier.testTag("connect-target-reject-setup"))
        }
    }
}
