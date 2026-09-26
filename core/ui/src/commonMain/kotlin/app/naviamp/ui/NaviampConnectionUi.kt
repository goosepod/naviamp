package app.naviamp.ui
import org.jetbrains.compose.resources.stringResource
import app.naviamp.ui.generated.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.selectProvider
import app.naviamp.domain.source.ConnectionPasswordRequiredStatus
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.DefaultSingleColorHex
import app.naviamp.domain.provider.NaviampProviderCatalog
import app.naviamp.domain.provider.ProviderAvailability
import app.naviamp.domain.provider.ProviderConnectionIcon
import app.naviamp.domain.provider.ProviderDescriptor
import app.naviamp.domain.provider.providerDescriptor

@Composable
private fun localizedConnectionStatus(status: String): String =
    if (status == ConnectionPasswordRequiredStatus) {
        stringResource(Res.string.connection_password_required)
    } else {
        status
    }

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
        Text(stringResource(Res.string.connection_restoring_connection), color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    connectionStatusIsError: Boolean = false,
    settingsSyncStatus: String? = null,
    availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
    musicFoldersStatus: String? = null,
    capabilities: NaviampConnectionCapabilitiesUi = NaviampConnectionCapabilitiesUi(),
    allowLocalFileInputs: Boolean = true,
    allowFallbackUrls: Boolean = true,
    modifier: Modifier = Modifier,
    onFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onImportSettingsSyncFile: (() -> Unit)? = null,
    onCancel: (() -> Unit)?,
) {
    var advancedVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val advancedFocusRequester = remember { FocusRequester() }
    val connectFocusRequester = remember { FocusRequester() }
    val focusNext: () -> Unit = { focusManager.moveFocus(FocusDirection.Next) }

    NaviampSystemBackHandler(enabled = onCancel != null && !isConnecting) {
        onCancel?.invoke()
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        onCancel?.let { cancel ->
            ConnectionFormTextAction(
                label = stringResource(Res.string.common_back),
                colors = colors,
                enabled = !isConnecting,
                modifier = Modifier.testTag(ConnectionBackButtonTestTag),
                onClick = cancel,
            )
        }
        if (connectionStatusIsError && !connectionStatus.isNullOrBlank()) {
            ConnectionErrorCard(localizedConnectionStatus(connectionStatus))
        }
        SettingsSectionTitle(stringResource(Res.string.connection_connection_details), colors)
        ProviderSelector(
            selectedProviderId = form.providerId,
            colors = colors,
            enabled = !isConnecting,
            onProviderSelected = { providerId -> onFormChanged(form.selectProvider(providerId)) },
        )
        Text(
            providerDescriptor(form.providerId).connectionGuidance,
            color = colors.mutedText,
            fontSize = 11.sp,
        )
        if (isReconnect) {
            Text(
                stringResource(Res.string.connection_saved_credentials_loaded_leave_password_blank_to_reuse_them),
                color = colors.mutedText,
                fontSize = 11.sp,
            )
        }
        onImportSettingsSyncFile?.takeIf { allowLocalFileInputs }?.let { importSettings ->
            ConnectionFormTextAction(
                label = stringResource(Res.string.settings_sync_import_provider),
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
            label = stringResource(Res.string.connection_connection_name_optional),
            colors = colors,
            imeAction = ImeAction.Next,
            onImeAction = focusNext,
            modifier = Modifier.testTag(ConnectionNameFieldTestTag),
        )
        NaviampTextField(
            value = form.serverUrl,
            onValueChange = { onFormChanged(form.copy(serverUrl = it)) },
            label = stringResource(Res.string.connection_server_url),
            colors = colors,
            inputKind = NaviampTextInputKind.Url,
            imeAction = ImeAction.Next,
            onImeAction = focusNext,
            modifier = Modifier.testTag(ConnectionServerUrlFieldTestTag),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NaviampTextField(
                value = form.username,
                onValueChange = { onFormChanged(form.copy(username = it)) },
                label = stringResource(Res.string.connection_username),
                colors = colors,
                inputKind = NaviampTextInputKind.Technical,
                modifier = Modifier.weight(1f).testTag(ConnectionUsernameFieldTestTag),
                imeAction = ImeAction.Next,
                onImeAction = focusNext,
            )
            NaviampTextField(
                value = form.password,
                onValueChange = { onFormChanged(form.copy(password = it)) },
                label = stringResource(Res.string.connection_password),
                colors = colors,
                isPassword = true,
                forceFloatingLabel = isReconnect,
                modifier = Modifier.weight(1f).testTag(ConnectionPasswordFieldTestTag),
                imeAction = ImeAction.Done,
                onImeAction = {
                    softwareKeyboardController?.hide()
                    connectFocusRequester.requestFocus()
                },
            )
        }
        ConnectionFormTextAction(
            label = if (advancedVisible) stringResource(Res.string.connection_hide_advanced) else stringResource(Res.string.connection_show_advanced),
            colors = colors,
            modifier = Modifier
                .focusRequester(advancedFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (!advancedVisible && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        connectFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                }
                .testTag(ConnectionAdvancedActionTestTag),
            onClick = { advancedVisible = !advancedVisible },
        )
        if (advancedVisible) {
            val customServerCertificatesVisible = allowLocalFileInputs && capabilities.customServerCertificates
            val clientCertificatesVisible = allowLocalFileInputs && capabilities.clientCertificates
            if (capabilities.insecureServerVerification || customServerCertificatesVisible) {
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
                    Text(stringResource(Res.string.connection_skip_tls_certificate_verification), color = colors.secondaryText, fontSize = 13.sp)
                }
            }
            if (customServerCertificatesVisible) {
                NaviampTextField(
                    value = form.customCertificatePath,
                    onValueChange = { onFormChanged(form.copy(customCertificatePath = it)) },
                    label = stringResource(Res.string.connection_trusted_certificate_or_ca_file),
                    colors = colors,
                    enabled = !form.skipTlsVerification,
                    inputKind = NaviampTextInputKind.Technical,
                )
            }
            if (clientCertificatesVisible) {
                SettingsSectionTitle("mTLS", colors)
                NaviampTextField(
                    value = form.clientCertificatePath,
                    onValueChange = { onFormChanged(form.copy(clientCertificatePath = it)) },
                    label = stringResource(Res.string.connection_client_certificate_pkcs12_file),
                    colors = colors,
                    inputKind = NaviampTextInputKind.Technical,
                )
                NaviampTextField(
                    value = form.clientCertificatePassword,
                    onValueChange = { onFormChanged(form.copy(clientCertificatePassword = it)) },
                    label = stringResource(Res.string.connection_client_certificate_password),
                    colors = colors,
                    isPassword = true,
                )
            }
            if (allowFallbackUrls) {
                SettingsSectionTitle(stringResource(Res.string.connection_fallback_urls), colors)
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
                            inputKind = NaviampTextInputKind.Url,
                            modifier = Modifier.weight(1f),
                        )
                        NaviampTextField(
                            value = entry.label,
                            onValueChange = { value ->
                                onFormChanged(form.copy(
                                    secondaryUrls = form.secondaryUrls.updateAt(index, entry.copy(label = value)),
                                ))
                            },
                            label = stringResource(Res.string.connection_label),
                            colors = colors,
                            modifier = Modifier.weight(0.65f),
                        )
                        TextButton(
                            onClick = {
                                onFormChanged(form.copy(secondaryUrls = form.secondaryUrls.removeAt(index)))
                            },
                        ) {
                            Text(stringResource(Res.string.mix_remove), color = colors.secondaryText)
                        }
                    }
                }
                ConnectionFormTextAction(
                    label = stringResource(Res.string.connection_add_fallback_url),
                    colors = colors,
                    onClick = {
                        onFormChanged(form.copy(secondaryUrls = form.secondaryUrls + ConnectionFormSecondaryUrl()))
                    },
                )
            }
            SettingsSectionTitle(stringResource(Res.string.connection_headers), colors)
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
                            label = stringResource(Res.string.connection_header_name),
                            colors = colors,
                            inputKind = NaviampTextInputKind.Technical,
                            modifier = Modifier.weight(1f),
                        )
                        NaviampTextField(
                            value = header.value,
                            onValueChange = { value ->
                                onFormChanged(form.copy(
                                    customHeaders = form.customHeaders.updateAt(index, header.copy(value = value)),
                                ))
                            },
                            label = stringResource(Res.string.connection_header_value),
                            colors = colors,
                            isPassword = header.valueIsSecret,
                            inputKind = NaviampTextInputKind.Technical,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                onFormChanged(form.copy(customHeaders = form.customHeaders.removeAt(index)))
                            },
                        ) {
                            Text(stringResource(Res.string.mix_remove), color = colors.secondaryText)
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
                        Text(stringResource(Res.string.connection_treat_value_as_secret_do_not_sync_it), color = colors.secondaryText, fontSize = 12.sp)
                    }
                }
            }
            ConnectionFormTextAction(
                label = stringResource(Res.string.connection_add_header),
                colors = colors,
                onClick = {
                    onFormChanged(form.copy(customHeaders = form.customHeaders + ConnectionFormHeader()))
                },
            )
        }
        connectionStatus?.takeUnless { connectionStatusIsError }?.let {
            Text(localizedConnectionStatus(it), color = colors.secondaryText, fontSize = 11.sp)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(
                label = if (isConnecting) stringResource(Res.string.common_connecting) else if (isReconnect) stringResource(Res.string.connection_save_and_connect) else stringResource(Res.string.common_connect),
                colors = colors,
                enabled = !isConnecting,
                modifier = Modifier
                    .focusRequester(connectFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (!advancedVisible && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            advancedFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag(ConnectionConnectButtonTestTag),
                onClick = onConnect,
            )
            onCancel?.let {
                TextButton(enabled = !isConnecting, onClick = it) {
                    Text(stringResource(Res.string.common_cancel), color = colors.secondaryText)
                }
            }
        }
    }
}

internal const val ConnectionNameFieldTestTag = "connection-name"
internal const val ConnectionBackButtonTestTag = "connection-back"
internal const val ConnectionServerUrlFieldTestTag = "connection-server-url"
internal const val ConnectionUsernameFieldTestTag = "connection-username"
internal const val ConnectionPasswordFieldTestTag = "connection-password"
internal const val ConnectionAdvancedActionTestTag = "connection-advanced-action"
internal const val ConnectionConnectButtonTestTag = "connection-connect"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderSelector(
    selectedProviderId: String,
    colors: NaviampColors,
    enabled: Boolean,
    onProviderSelected: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NaviampProviderCatalog.forEach { provider ->
            ProviderSelectionTile(
                provider = provider,
                selected = provider.id == selectedProviderId,
                enabled = enabled && provider.selectable,
                colors = colors,
                onClick = { onProviderSelected(provider.id) },
            )
        }
    }
}

@Composable
private fun ProviderSelectionTile(
    provider: ProviderDescriptor,
    selected: Boolean,
    enabled: Boolean,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    val contentColor = when {
        selected -> colors.primaryText
        enabled -> colors.secondaryText
        else -> colors.mutedText
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .widthIn(min = 126.dp, max = 180.dp)
            .clip(shape)
            .background(
                if (selected) colors.accent.copy(alpha = 0.28f)
                else colors.controlSurface.copy(alpha = 0.45f),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else colors.border.copy(alpha = 0.7f),
                shape = shape,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = provider.icon.toImageVector(),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            provider.displayName,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (provider.availability == ProviderAvailability.ComingSoon) {
            Text(stringResource(Res.string.connection_coming_soon), color = colors.mutedText, fontSize = 10.sp, maxLines = 1)
        }
    }
}

private fun ProviderConnectionIcon.toImageVector() = when (this) {
    ProviderConnectionIcon.Navidrome -> NaviampIcons.Turntable
    ProviderConnectionIcon.Subsonic -> NaviampIcons.Globe
    ProviderConnectionIcon.Jellyfin -> NaviampIcons.Player
    ProviderConnectionIcon.Bandcamp -> NaviampIcons.Library
}

@Composable
private fun ConnectionErrorCard(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = stringResource(Res.string.connection_connection_error),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConnectionFormTextAction(
    label: String,
    colors: NaviampColors,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

private fun <T> List<T>.updateAt(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    filterIndexed { itemIndex, _ -> itemIndex != index }
