package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Shows this device's code while composed. Being on screen is the consent: entering composition
 * starts the offer and leaving it ends the offer.
 */
@Composable
internal fun NaviampConnectShowCodePanel(
    connect: NaviampConnectSettingsUi,
    colors: NaviampColors,
    actions: NaviampConnectSettingsActions,
    modifier: Modifier = Modifier,
) {
    val start by rememberUpdatedState(actions.onStartPairingMode)
    val stop by rememberUpdatedState(actions.onStopPairingMode)
    // The offer lives only while the panel is shown in a visible app: Home or a hidden window ends it,
    // and returning starts a fresh code.
    NaviampWhileVisibleEffect(onVisible = { start() }, onHidden = { stop() })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        // A used code is replaced at once, so the panel keeps showing the next one.
        connect.connectedControllerName?.let { controllerName ->
            Text(stringResource(Res.string.connect_status_connected_to_device, controllerName), color = colors.primaryText)
        }
        Text(stringResource(if (connect.pairingLink != null) Res.string.connect_show_code_or_scan_instructions
            else Res.string.connect_show_code_instructions), color = colors.secondaryText)
        Text(connect.localDeviceName, color = colors.primaryText, fontWeight = FontWeight.Bold)
        // The QR and the code are one offer; the code stays visible for devices without a camera.
        connect.pairingLink?.takeIf { connect.pairingCode != null }?.let { link ->
            NaviampConnectQrCode(link, stringResource(Res.string.connect_qr_description, connect.localDeviceName))
        }
        connect.pairingCode?.let { code ->
            Text(
                formatNaviampConnectPairingCode(code),
                color = colors.primaryText,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("connect-code"),
            )
            connect.pairingCodeExpiresAtEpochMillis?.let { expiresAt -> NaviampConnectCodeCountdown(expiresAt, colors) }
        }
        connect.recovery?.let { recovery ->
            NaviampConnectRecoveryPanel(recovery, colors, actions.onRetryConnection,
                actions.onOpenPermissionSettings, television = false)
        }
        connect.listeningPort?.let { port ->
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = !open }) { Text(stringResource(Res.string.connect_cant_connect)) }
            if (open) Text(stringResource(Res.string.connect_port, port), color = colors.secondaryText)
        }
    }
}

/** Once-per-second text only while the code is on screen; no continuous animation. */
@Composable
private fun NaviampConnectCodeCountdown(expiresAtEpochMillis: Long, colors: NaviampColors) {
    var now by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(expiresAtEpochMillis) {
        while (true) {
            now = currentTimeMillis()
            delay(1_000L)
        }
    }
    val seconds = ((expiresAtEpochMillis - now).coerceAtLeast(0L) / 1_000L)
    val remaining = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    Text(stringResource(Res.string.connect_code_refreshes_in, remaining), color = colors.secondaryText, fontSize = 12.sp)
}

/** Enter a code from another device; discovery runs only while this panel is composed. */
@Composable
internal fun NaviampConnectEnterCodePanel(
    connect: NaviampConnectSettingsUi,
    colors: NaviampColors,
    actions: NaviampConnectSettingsActions,
    modifier: Modifier = Modifier,
) {
    val visibility by rememberUpdatedState(actions.onConnectScreenVisibilityChanged)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    DisposableEffect(Unit) {
        visibility(true)
        onDispose { visibility(false) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        NaviampConnectScanSection(connect, colors, actions)
        Text(stringResource(Res.string.connect_enter_code_instructions), color = colors.secondaryText)
        OutlinedTextField(
            colors = naviampOutlinedTextFieldColors(colors),
            value = connect.enteredPairingCode,
            onValueChange = { value ->
                val digits = value.filter(Char::isDigit).take(6)
                actions.onPairingCodeChanged(digits)
                // The sixth digit submits; close the keyboard so the result and Nearby stay visible.
                if (digits.length == 6) focusManager.clearFocus()
            },
            singleLine = true,
            enabled = !connect.codeEntryBusy,
            label = { Text(stringResource(Res.string.connect_code_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp),
            modifier = Modifier.fillMaxWidth().testTag("connect-code-entry"),
        )
        connect.displayStatus()?.takeIf { connect.recovery == null }?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        connect.recovery?.let { recovery ->
            NaviampConnectRecoveryPanel(recovery, colors, actions.onRetryConnection,
                actions.onOpenPermissionSettings, television = false)
        } ?: run {
            SettingsSectionTitle(stringResource(Res.string.connect_nearby), colors)
            if (connect.discoveredTargets.isEmpty()) {
                Text(stringResource(Res.string.connect_nearby_empty), color = colors.secondaryText,
                    modifier = Modifier.testTag("connect-nearby-empty"))
            }
            connect.discoveredTargets.forEach { target ->
                PrimaryButton(
                    label = target.displayName,
                    colors = colors,
                    enabled = target.compatible,
                    onClick = { actions.onTargetSelected(target) },
                    modifier = Modifier.fillMaxWidth().testTag("connect-nearby-${target.instanceId}"),
                )
            }
        }
        var manualOpen by remember { mutableStateOf(false) }
        var manualAddress by rememberSaveable { mutableStateOf("") }
        TextButton(onClick = { manualOpen = !manualOpen }) { Text(stringResource(Res.string.connect_cant_find_it)) }
        if (manualOpen) {
            Text(stringResource(Res.string.connect_address_hint), color = colors.secondaryText, fontSize = 12.sp)
            OutlinedTextField(
                colors = naviampOutlinedTextFieldColors(colors),
                value = manualAddress,
                onValueChange = { manualAddress = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.connect_address_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(stringResource(Res.string.common_connect), colors,
                enabled = manualAddress.isNotBlank(),
                onClick = { actions.onManualEndpointSelected(manualAddress) })
        }
    }
}

/**
 * Scanning a QR code (#715): the camera opens only when asked, asks permission on first use, and
 * falls back to code entry when there is no camera or access is denied.
 */
@Composable
private fun NaviampConnectScanSection(
    connect: NaviampConnectSettingsUi,
    colors: NaviampColors,
    actions: NaviampConnectSettingsActions,
) {
    val scanner = LocalNaviampQrScanner.current?.takeIf { it.available } ?: return
    var scanning by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val granted = scanner.hasCameraPermission()
    val requestPermission = scanner.rememberCameraPermissionRequest { allowed ->
        denied = !allowed
        scanning = allowed
    }
    val onScanned by rememberUpdatedState(actions.onConnectLinkScanned)
    // A scan that is connecting closes the camera; the result follows in the status line.
    LaunchedEffect(connect.codeEntryBusy) { if (connect.codeEntryBusy) scanning = false }
    if (scanning && granted) {
        Text(stringResource(Res.string.connect_scan_qr_instructions), color = colors.secondaryText)
        scanner.Viewfinder(
            onScanned = { text ->
                scanning = false
                onScanned(text)
            },
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(1f).testTag("connect-qr-viewfinder"),
        )
        TextButton(onClick = { scanning = false }, modifier = Modifier.testTag("connect-qr-stop")) {
            Text(stringResource(Res.string.connect_stop_scanning))
        }
    } else {
        PrimaryButton(
            label = stringResource(Res.string.connect_scan_qr),
            colors = colors,
            enabled = !connect.codeEntryBusy,
            onClick = {
                if (granted) {
                    denied = false
                    scanning = true
                } else {
                    requestPermission()
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("connect-qr-scan"),
        )
        if (denied && !granted) {
            Text(stringResource(Res.string.connect_camera_denied), color = colors.secondaryText, fontSize = 12.sp,
                modifier = Modifier.testTag("connect-qr-denied"))
        }
    }
}

/**
 * Runs [onVisible] while this composition is on screen in a started (visible) app and [onHidden]
 * when the app stops or the composition leaves. Built from the lifecycle APIs allowed in common code.
 */
@Composable
internal fun NaviampWhileVisibleEffect(onVisible: () -> Unit, onHidden: () -> Unit) {
    val visible by rememberUpdatedState(onVisible)
    val hidden by rememberUpdatedState(onHidden)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var active = false
        fun show() { if (!active) { active = true; visible() } }
        fun hide() { if (active) { active = false; hidden() } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> show()
                Lifecycle.Event.ON_STOP -> hide()
                else -> Unit
            }
        }
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) show()
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            hide()
        }
    }
}
