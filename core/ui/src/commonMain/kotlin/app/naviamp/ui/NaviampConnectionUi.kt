package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.DefaultSingleColorHex
import app.naviamp.domain.settings.toggleSelectedMusicFolderId

@Composable
internal fun RestoringConnectionCard(
    status: String,
    colors: NaviampColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.controlSurface.copy(alpha = 0.72f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Restoring connection", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(status, color = colors.secondaryText, fontSize = 13.sp)
    }
}

@Composable
fun NaviampConnectionForm(
    form: ConnectionFormState,
    colors: NaviampColors,
    isReconnect: Boolean,
    isConnecting: Boolean = false,
    connectionStatus: String? = null,
    settingsSyncStatus: String? = null,
    availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
    musicFoldersStatus: String? = null,
    capabilities: NaviampConnectionCapabilitiesUi = NaviampConnectionCapabilitiesUi(),
    modifier: Modifier = Modifier,
    onFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onImportSettingsSyncFile: (() -> Unit)? = null,
    onCancel: (() -> Unit)?,
) {
    var advancedVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle("Connection Details", colors)
        if (isReconnect) {
            Text(
                "Saved credentials loaded. Leave password blank to reuse them.",
                color = colors.mutedText,
                fontSize = 11.sp,
            )
        }
        onImportSettingsSyncFile?.let { importSettings ->
            ConnectionFormTextAction(
                label = "Import provider settings",
                colors = colors,
                enabled = !isConnecting,
                onClick = importSettings,
            )
            settingsSyncStatus?.let {
                Text(it, color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        NaviampTextField(
            value = form.displayName,
            onValueChange = { onFormChanged(form.copy(displayName = it)) },
            label = "Connection name (optional)",
            colors = colors,
        )
        NaviampTextField(
            value = form.serverUrl,
            onValueChange = { onFormChanged(form.copy(serverUrl = it)) },
            label = "Server URL",
            colors = colors,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NaviampTextField(
                value = form.username,
                onValueChange = { onFormChanged(form.copy(username = it)) },
                label = "Username",
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            NaviampTextField(
                value = form.password,
                onValueChange = { onFormChanged(form.copy(password = it)) },
                label = "Password",
                colors = colors,
                isPassword = true,
                forceFloatingLabel = isReconnect,
                modifier = Modifier.weight(1f),
            )
        }
        ConnectionFormTextAction(
            label = if (advancedVisible) "Hide Advanced" else "Show Advanced",
            colors = colors,
            onClick = { advancedVisible = !advancedVisible },
        )
        if (advancedVisible) {
            SettingsSectionTitle("Libraries", colors)
            MusicFolderMultiSelect(
                selectedIds = form.selectedMusicFolderIds,
                availableFolders = availableMusicFolders,
                status = musicFoldersStatus,
                colors = colors,
                onSelectedIdsChanged = { ids ->
                    onFormChanged(form.copy(selectedMusicFolderIds = ids))
                },
            )
            if (capabilities.insecureServerVerification || capabilities.customServerCertificates) {
                SettingsSectionTitle("TLS", colors)
            }
            if (capabilities.insecureServerVerification) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = form.skipTlsVerification,
                        onCheckedChange = { onFormChanged(form.copy(skipTlsVerification = it)) },
                    )
                    Text("Skip TLS certificate verification", color = colors.secondaryText, fontSize = 13.sp)
                }
            }
            if (capabilities.customServerCertificates) {
                NaviampTextField(
                    value = form.customCertificatePath,
                    onValueChange = { onFormChanged(form.copy(customCertificatePath = it)) },
                    label = "Trusted certificate or CA file",
                    colors = colors,
                    enabled = !form.skipTlsVerification,
                )
            }
            if (capabilities.clientCertificates) {
                SettingsSectionTitle("mTLS", colors)
                NaviampTextField(
                    value = form.clientCertificatePath,
                    onValueChange = { onFormChanged(form.copy(clientCertificatePath = it)) },
                    label = "Client certificate PKCS12 file",
                    colors = colors,
                )
                NaviampTextField(
                    value = form.clientCertificatePassword,
                    onValueChange = { onFormChanged(form.copy(clientCertificatePassword = it)) },
                    label = "Client certificate password",
                    colors = colors,
                    isPassword = true,
                )
            }
            SettingsSectionTitle("Fallback URLs", colors)
            form.secondaryUrls.forEachIndexed { index, entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NaviampTextField(
                        value = entry.url,
                        onValueChange = { value ->
                            onFormChanged(form.copy(
                                secondaryUrls = form.secondaryUrls.updateAt(index, entry.copy(url = value)),
                            ))
                        },
                        label = "URL",
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                    NaviampTextField(
                        value = entry.label,
                        onValueChange = { value ->
                            onFormChanged(form.copy(
                                secondaryUrls = form.secondaryUrls.updateAt(index, entry.copy(label = value)),
                            ))
                        },
                        label = "Label",
                        colors = colors,
                        modifier = Modifier.weight(0.65f),
                    )
                    TextButton(
                        onClick = {
                            onFormChanged(form.copy(secondaryUrls = form.secondaryUrls.removeAt(index)))
                        },
                    ) {
                        Text("Remove", color = colors.secondaryText)
                    }
                }
            }
            ConnectionFormTextAction(
                label = "Add fallback URL",
                colors = colors,
                onClick = {
                    onFormChanged(form.copy(secondaryUrls = form.secondaryUrls + ConnectionFormSecondaryUrl()))
                },
            )
            SettingsSectionTitle("Headers", colors)
            form.customHeaders.forEachIndexed { index, header ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NaviampTextField(
                            value = header.name,
                            onValueChange = { value ->
                                onFormChanged(form.copy(
                                    customHeaders = form.customHeaders.updateAt(index, header.copy(name = value)),
                                ))
                            },
                            label = "Header name",
                            colors = colors,
                            modifier = Modifier.weight(1f),
                        )
                        NaviampTextField(
                            value = header.value,
                            onValueChange = { value ->
                                onFormChanged(form.copy(
                                    customHeaders = form.customHeaders.updateAt(index, header.copy(value = value)),
                                ))
                            },
                            label = "Header value",
                            colors = colors,
                            isPassword = header.valueIsSecret,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                onFormChanged(form.copy(customHeaders = form.customHeaders.removeAt(index)))
                            },
                        ) {
                            Text("Remove", color = colors.secondaryText)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = header.valueIsSecret,
                            onCheckedChange = { checked ->
                                onFormChanged(form.copy(
                                    customHeaders = form.customHeaders.updateAt(index, header.copy(valueIsSecret = checked)),
                                ))
                            },
                        )
                        Text("Treat value as secret; do not sync it", color = colors.secondaryText, fontSize = 12.sp)
                    }
                }
            }
            ConnectionFormTextAction(
                label = "Add header",
                colors = colors,
                onClick = {
                    onFormChanged(form.copy(customHeaders = form.customHeaders + ConnectionFormHeader()))
                },
            )
        }
        connectionStatus?.let {
            Text(it, color = colors.secondaryText, fontSize = 11.sp)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(
                label = if (isConnecting) "Connecting" else if (isReconnect) "Save and connect" else "Connect",
                colors = colors,
                enabled = !isConnecting,
                onClick = onConnect,
            )
            onCancel?.let {
                TextButton(enabled = !isConnecting, onClick = it) {
                    Text("Cancel", color = colors.secondaryText)
                }
            }
        }
    }
}

@Composable
private fun ConnectionFormTextAction(
    label: String,
    colors: NaviampColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.primaryText,
            containerColor = colors.controlSurface.copy(alpha = 0.42f),
            disabledContentColor = colors.secondaryText.copy(alpha = 0.78f),
            disabledContainerColor = colors.controlSurface.copy(alpha = 0.18f),
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

private fun <T> List<T>.updateAt(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    filterIndexed { itemIndex, _ -> itemIndex != index }

@Composable
private fun MusicFolderMultiSelect(
    selectedIds: List<String>,
    availableFolders: List<ConnectionFormMusicFolder>,
    status: String?,
    colors: NaviampColors,
    onSelectedIdsChanged: (List<String>) -> Unit,
) {
    val selectedSet = selectedIds.toSet()
    val knownIds = availableFolders.map { it.id }.toSet()
    val unknownSelected = selectedIds
        .filterNot { it in knownIds }
        .map { id -> ConnectionFormMusicFolder(id = id, name = id) }
    val choices = availableFolders + unknownSelected

    status?.let {
        Text(it, color = colors.mutedText, fontSize = 11.sp)
    }
    if (choices.isEmpty()) {
        Text(
            "Connect or enter credentials to load available libraries.",
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        choices.forEach { folder ->
            val checked = folder.id in selectedSet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onSelectedIdsChanged(
                            selectedIds.toggleSelectedMusicFolderId(
                                id = folder.id,
                                requireOne = choices.isNotEmpty(),
                            ),
                        )
                    }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        color = colors.primaryText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (folder.defaultSelected) "Default library" else "ID: ${folder.id}",
                        color = colors.mutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
