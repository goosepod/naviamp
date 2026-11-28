package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** One choice right after a code connection: sign the other device in, play on it, or neither. */
@Composable
internal fun NaviampConnectPostConnectDialog(
    postConnect: NaviampConnectPostConnectUi,
    colors: NaviampColors,
    connect: NaviampConnectSettingsUi,
    actions: NaviampConnectSettingsActions,
) {
    val enabled = !postConnect.busy
    NaviampAlertDialog(
        // Back, Escape or tapping outside closes without submitting the ticked options.
        onDismissRequest = { if (enabled) actions.onPostConnectDismiss() },
        title = { Text(stringResource(Res.string.connect_post_title, postConnect.deviceName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (postConnect.canSignIn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .toggleable(
                                value = postConnect.signInSelected,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = actions.onPostConnectSignInChanged,
                            )
                            .testTag("connect-post-sign-in"),
                    ) {
                        Checkbox(checked = postConnect.signInSelected, onCheckedChange = null, enabled = enabled)
                        Text(if (postConnect.pull) stringResource(Res.string.connect_post_sign_in_from, postConnect.deviceName)
                            else stringResource(Res.string.connect_post_sign_in))
                    }
                    if (postConnect.canChoose && postConnect.signInSelected && postConnect.results.isEmpty()) {
                        TextButton(onClick = actions.onPostConnectChoose, enabled = enabled,
                            modifier = Modifier.testTag("connect-post-choose")) {
                            Text(stringResource(Res.string.connect_post_choose))
                        }
                    }
                    if (postConnect.choosing && postConnect.results.isEmpty()) {
                        NaviampConnectTransferChoices(postConnect, colors, actions, enabled)
                    }
                }
                postConnect.results.forEach { result ->
                    Text(
                        "${result.displayName} · ${stringResource(result.outcome.label())}",
                        color = colors.secondaryText,
                        modifier = Modifier.testTag("connect-post-result"),
                    )
                }
                // The title already says the device is connected; show only later progress or problems.
                connect.displayStatus()?.takeIf { connect.statusMessage?.text != NaviampConnectStatusText.ConnectedToDevice }
                    ?.let { Text(it, color = colors.secondaryText) }
                if (connect.needsProvisioningCredential) {
                    var password by remember { mutableStateOf("") }
                    Text(stringResource(Res.string.connect_setup_password_required), color = colors.secondaryText)
                    OutlinedTextField(
                        colors = naviampOutlinedTextFieldColors(colors),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.connection_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(stringResource(Res.string.common_connect), colors,
                        enabled = password.isNotBlank() && enabled,
                        onClick = {
                            val submitted = password
                            password = ""
                            actions.onSubmitProvisioningCredential(submitted)
                        })
                }
            }
        },
        confirmButton = {
            if (postConnect.canPlayOn) {
                TextButton(onClick = actions.onPostConnectPlayOnDevice, enabled = enabled) {
                    Text(stringResource(Res.string.connect_post_play_on, postConnect.deviceName))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onPostConnectDone, enabled = enabled) {
                Text(stringResource(Res.string.connect_done))
            }
        },
    )
}

/** "Choose accounts and settings": each account with its status, and the settings group. */
@Composable
private fun NaviampConnectTransferChoices(
    postConnect: NaviampConnectPostConnectUi,
    colors: NaviampColors,
    actions: NaviampConnectSettingsActions,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("connect-post-choices")) {
        postConnect.accounts.forEach { account ->
            val sendable = account.status != NaviampConnectTransferAccountStatus.SetUpOnDevice
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .toggleable(
                        value = account.selected && sendable,
                        enabled = enabled && sendable,
                        role = Role.Checkbox,
                        onValueChange = { actions.onPostConnectAccountSelected(account.id, it) },
                    )
                    .testTag("connect-post-account-${account.id}"),
            ) {
                Checkbox(checked = account.selected && sendable, onCheckedChange = null, enabled = enabled && sendable)
                Column {
                    Text(account.displayName)
                    when (account.status) {
                        NaviampConnectTransferAccountStatus.NeedsPassword ->
                            Text(stringResource(Res.string.connect_account_needs_password), color = colors.secondaryText)
                        NaviampConnectTransferAccountStatus.SetUpOnDevice ->
                            Text(stringResource(Res.string.connect_account_set_up_on_device), color = colors.secondaryText)
                        NaviampConnectTransferAccountStatus.Ready -> Unit
                    }
                }
            }
            if (account.status == NaviampConnectTransferAccountStatus.NeedsPassword && account.selected) {
                var password by remember(account.id) { mutableStateOf("") }
                OutlinedTextField(
                    colors = naviampOutlinedTextFieldColors(colors),
                    value = password,
                    onValueChange = {
                        password = it
                        actions.onPostConnectAccountPassword(account.id, it)
                    },
                    label = { Text(stringResource(Res.string.connect_account_password_for, account.displayName)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("connect-post-password-${account.id}"),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .toggleable(
                    value = postConnect.includeSettings,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = actions.onPostConnectSettingsChanged,
                )
                .testTag("connect-post-settings"),
        ) {
            Checkbox(checked = postConnect.includeSettings, onCheckedChange = null, enabled = enabled)
            Text(stringResource(Res.string.connect_post_include_settings))
        }
    }
}

private fun NaviampConnectTransferOutcome.label() = when (this) {
    NaviampConnectTransferOutcome.Added -> Res.string.connect_result_added
    NaviampConnectTransferOutcome.Updated -> Res.string.connect_result_updated
    NaviampConnectTransferOutcome.NeedsPassword -> Res.string.connect_account_needs_password
    NaviampConnectTransferOutcome.SetUpOnDevice -> Res.string.connect_account_set_up_on_device
    NaviampConnectTransferOutcome.Failed -> Res.string.connect_result_failed
}

/** A missing or rejected password outside the post-connect sheet (for example after a trusted reconnect). */
internal fun shouldShowConnectCredentialPrompt(connect: NaviampConnectSettingsUi): Boolean =
    connect.needsProvisioningCredential && connect.postConnect == null

@Composable
internal fun NaviampConnectLinkStatusDialog(
    status: NaviampConnectStatusMessage,
    busy: Boolean,
    colors: NaviampColors,
    actions: NaviampConnectSettingsActions,
) {
    NaviampAlertDialog(
        onDismissRequest = actions.onDismissLinkStatus,
        text = {
            Text(status.localized(), color = colors.secondaryText, modifier = Modifier.testTag("connect-link-status"))
        },
        confirmButton = {
            TextButton(onClick = actions.onDismissLinkStatus, enabled = !busy,
                modifier = Modifier.testTag("connect-link-status-close")) {
                Text(stringResource(Res.string.common_close))
            }
        },
    )
}

@Composable
internal fun NaviampConnectCredentialDialog(
    connect: NaviampConnectSettingsUi,
    colors: NaviampColors,
    actions: NaviampConnectSettingsActions,
) {
    var password by remember { mutableStateOf("") }
    val enabled = !connect.provisioningBusy
    NaviampAlertDialog(
        onDismissRequest = { password = ""; actions.onCancelProvisioningCredential() },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.connect_setup_password_required), color = colors.secondaryText)
                OutlinedTextField(
                    colors = naviampOutlinedTextFieldColors(colors),
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.connection_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("connect-credential-password"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val submitted = password
                    password = ""
                    actions.onSubmitProvisioningCredential(submitted)
                },
                enabled = enabled && password.isNotBlank(),
                modifier = Modifier.testTag("connect-credential-submit"),
            ) { Text(stringResource(Res.string.common_connect)) }
        },
        dismissButton = {
            TextButton(onClick = { password = ""; actions.onCancelProvisioningCredential() }) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}
