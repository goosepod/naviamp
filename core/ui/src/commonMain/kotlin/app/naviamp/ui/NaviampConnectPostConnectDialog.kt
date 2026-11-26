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
        onDismissRequest = { if (enabled) actions.onPostConnectDone() },
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
                        Text(stringResource(Res.string.connect_post_sign_in))
                    }
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
