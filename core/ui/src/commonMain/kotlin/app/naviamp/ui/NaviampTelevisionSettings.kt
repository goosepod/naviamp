package app.naviamp.ui

import app.naviamp.ui.generated.resources.settings_keep_screen_awake
import app.naviamp.ui.generated.resources.settings_keep_screen_awake_description
import app.naviamp.ui.generated.resources.settings_keep_screen_awake_failed

import app.naviamp.ui.generated.resources.connect_setup_code_consent
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.domain.settings.InterfaceFontSize
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import app.naviamp.ui.generated.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.AuroraTone
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import app.naviamp.domain.settings.LyricsDisplayPreference
import app.naviamp.domain.settings.LyricsTimingPreference
import app.naviamp.domain.settings.MaxAlbumBlurRadiusDp
import app.naviamp.domain.settings.MaxWaveformBucketCount
import app.naviamp.domain.settings.MinAlbumBlurRadiusDp
import app.naviamp.domain.settings.MinWaveformBucketCount
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.SampleRateMatching
import app.naviamp.domain.settings.homeSectionPresentation

internal enum class TelevisionSettingsCategory(
    val label: org.jetbrains.compose.resources.StringResource,
    val subtitle: org.jetbrains.compose.resources.StringResource,
    val icon: ImageVector,
) {
    Sources(Res.string.tv_sources, Res.string.tv_servers_and_music_libraries, NaviampIcons.Library),
    Home(Res.string.home_music_title, Res.string.tv_sections_visibility_and_order, NaviampIcons.Home),
    Display(Res.string.tv_display, Res.string.tv_background_and_now_playing, NaviampIcons.Experience),
    Playback(Res.string.settings_category_playback_title, Res.string.tv_audio_and_queue_behavior, NaviampTransportIcons.Play),
    Lyrics(Res.string.settings_lyrics_title, Res.string.tv_sources_and_synchronization, NaviampTransportIcons.Lyrics),
    Controllers(Res.string.tv_controllers, Res.string.tv_trusted_naviamp_remotes, NaviampIcons.Player),
    Diagnostics(Res.string.tv_diagnostics, Res.string.tv_connection_and_local_health, NaviampIcons.Bug),
    About(Res.string.settings_category_about_title, Res.string.tv_version_and_build_information, NaviampIcons.AppMark),
}

internal fun televisionSettingsCategories(controllersAvailable: Boolean): List<TelevisionSettingsCategory> =
    TelevisionSettingsCategory.entries.filter { category ->
        category != TelevisionSettingsCategory.Controllers || controllersAvailable
    }

private enum class TelevisionSettingsChoicePage {
    Language,
    GeneralFontSize,
    NowPlayingFontSize,
    Background,
    AlbumBlurAmount,
    SingleColor,
    AuroraTone,
    AuroraColorSteps,
    AuroraAngle,
    AlbumSortOrder,
    ReplayGain,
    SampleRateMatching,
    Crossfade,
    WaveformDensity,
    LyricsDownloadTiming,
    LyricsDisplayTiming,
}

private sealed interface TelevisionSettingsPage {
    data object Root : TelevisionSettingsPage
    data class Category(val category: TelevisionSettingsCategory) : TelevisionSettingsPage
    data class Choice(val choice: TelevisionSettingsChoicePage) : TelevisionSettingsPage
}

private sealed interface TelevisionSettingsReturnFocus {
    data class Category(val category: TelevisionSettingsCategory) : TelevisionSettingsReturnFocus
    data class Choice(val choice: TelevisionSettingsChoicePage) : TelevisionSettingsReturnFocus
}

private data class TelevisionChoiceUi(
    val label: String,
    val subtitle: String = "",
    val selected: Boolean,
    val onSelected: () -> Unit,
)

@Composable
internal fun TelevisionSettingsSheet(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampAppShellActions,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf<TelevisionSettingsPage>(TelevisionSettingsPage.Root) }
    var returnFocus by remember { mutableStateOf<TelevisionSettingsReturnFocus?>(null) }
    var movingHomeSectionId by remember { mutableStateOf<String?>(null) }
    var homeSectionOrderDraft by remember { mutableStateOf<List<HomeScreenSectionOption>?>(null) }
    val firstFocusRequester = remember { FocusRequester() }
    val returnFocusRequester = remember { FocusRequester() }
    val dismissOrGoBack = {
        if (movingHomeSectionId != null) {
            movingHomeSectionId = null
            homeSectionOrderDraft = null
        } else {
            page = when (val current = page) {
                TelevisionSettingsPage.Root -> {
                    onDismiss()
                    TelevisionSettingsPage.Root
                }
                is TelevisionSettingsPage.Category -> {
                    returnFocus = TelevisionSettingsReturnFocus.Category(current.category)
                    TelevisionSettingsPage.Root
                }
                is TelevisionSettingsPage.Choice -> {
                    returnFocus = TelevisionSettingsReturnFocus.Choice(current.choice)
                    televisionSettingsCategoryFor(current.choice).let(TelevisionSettingsPage::Category)
                }
            }
        }
    }
    LaunchedEffect(page, returnFocus) {
        withFrameNanos { }
        (if (returnFocus == null) firstFocusRequester else returnFocusRequester).requestFocus()
    }
    NaviampPopupPresence()
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = dismissOrGoBack,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = TelevisionSettingsBackdropDimAlpha))
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        if (event.type == KeyEventType.KeyUp) dismissOrGoBack()
                        true
                    } else false
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.43f)
                    .widthIn(min = 430.dp)
                    .background(colors.background.copy(alpha = 0.98f))
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                TelevisionSettingsHeader(
                    title = televisionSettingsPageTitle(page),
                    colors = colors,
                )
                Spacer(Modifier.size(12.dp))
                when (val current = page) {
                    TelevisionSettingsPage.Root -> TelevisionSettingsRoot(
                        uiState = uiState,
                        colors = colors,
                        firstFocusRequester = firstFocusRequester,
                        returnFocusRequester = returnFocusRequester,
                        returnCategory = (returnFocus as? TelevisionSettingsReturnFocus.Category)?.category,
                        onCategorySelected = {
                            returnFocus = null
                            page = TelevisionSettingsPage.Category(it)
                        },
                    )
                    is TelevisionSettingsPage.Category -> TelevisionSettingsCategoryPage(
                        category = current.category,
                        uiState = uiState,
                        colors = colors,
                        actions = actions,
                        firstFocusRequester = firstFocusRequester,
                        returnFocusRequester = returnFocusRequester,
                        returnChoice = (returnFocus as? TelevisionSettingsReturnFocus.Choice)?.choice,
                        onChoiceSelected = {
                            returnFocus = null
                            page = TelevisionSettingsPage.Choice(it)
                        },
                        movingHomeSectionId = movingHomeSectionId,
                        homeSectionOrderDraft = homeSectionOrderDraft,
                        onHomeMoveStarted = { sectionId, sections ->
                            movingHomeSectionId = sectionId
                            homeSectionOrderDraft = sections
                        },
                        onHomeMoveChanged = { homeSectionOrderDraft = it },
                        onHomeMoveCommitted = {
                            homeSectionOrderDraft?.let { sections ->
                                actions.valueActions.onInterfaceSettingsChanged(
                                    uiState.general.interfaceSettings.withOrderedTelevisionHomeSections(sections),
                                )
                            }
                            movingHomeSectionId = null
                            homeSectionOrderDraft = null
                        },
                    )
                    is TelevisionSettingsPage.Choice -> when (current.choice) {
                        TelevisionSettingsChoicePage.Background -> TelevisionBackgroundSettings(
                            uiState = uiState,
                            colors = colors,
                            actions = actions.valueActions,
                            firstFocusRequester = firstFocusRequester,
                        )
                        TelevisionSettingsChoicePage.AlbumBlurAmount -> TelevisionAlbumBlurAmountSettings(
                            uiState = uiState,
                            colors = colors,
                            actions = actions.valueActions,
                            firstFocusRequester = firstFocusRequester,
                        )
                        TelevisionSettingsChoicePage.SingleColor -> TelevisionSingleColorSettings(
                            uiState = uiState,
                            colors = colors,
                            actions = actions.valueActions,
                            firstFocusRequester = firstFocusRequester,
                        )
                        else -> TelevisionSettingsChoiceList(
                            page = current.choice,
                            uiState = uiState,
                            colors = colors,
                            actions = actions,
                            firstFocusRequester = firstFocusRequester,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelevisionSettingsHeader(
    title: String,
    colors: NaviampColors,
) {
    Text(
        text = title,
        color = colors.primaryText,
        fontSize = 28.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun TelevisionSettingsRoot(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    firstFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester,
    returnCategory: TelevisionSettingsCategory?,
    onCategorySelected: (TelevisionSettingsCategory) -> Unit,
) {
    val categories = televisionSettingsCategories(
        controllersAvailable = uiState.connect.available,
    )
    TelevisionSettingsList {
        items(categories, key = { it.name }) { category ->
            TelevisionSettingsRow(
                title = stringResource(category.label),
                subtitle = stringResource(category.subtitle),
                value = televisionSettingsCategoryValue(category, uiState),
                icon = category.icon,
                disclosure = true,
                colors = colors,
                onClick = { onCategorySelected(category) },
                modifier = when {
                    category == returnCategory -> Modifier.focusRequester(returnFocusRequester)
                    category == categories.first() -> Modifier.focusRequester(firstFocusRequester)
                    else -> Modifier
                },
            )
        }
    }
}

@Composable
private fun TelevisionSettingsCategoryPage(
    category: TelevisionSettingsCategory,
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampAppShellActions,
    firstFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester,
    returnChoice: TelevisionSettingsChoicePage?,
    onChoiceSelected: (TelevisionSettingsChoicePage) -> Unit,
    movingHomeSectionId: String?,
    homeSectionOrderDraft: List<HomeScreenSectionOption>?,
    onHomeMoveStarted: (String, List<HomeScreenSectionOption>) -> Unit,
    onHomeMoveChanged: (List<HomeScreenSectionOption>) -> Unit,
    onHomeMoveCommitted: () -> Unit,
) {
    when (category) {
        TelevisionSettingsCategory.Sources -> TelevisionSourcesSettings(
            uiState,
            colors,
            actions.connectionActions,
            firstFocusRequester,
        )
        TelevisionSettingsCategory.Home -> TelevisionHomeSettings(
            uiState = uiState,
            colors = colors,
            actions = actions.valueActions,
            firstFocusRequester = firstFocusRequester,
            movingSectionId = movingHomeSectionId,
            orderDraft = homeSectionOrderDraft,
            onMoveStarted = onHomeMoveStarted,
            onMoveChanged = onHomeMoveChanged,
            onMoveCommitted = onHomeMoveCommitted,
        )
        TelevisionSettingsCategory.Playback -> TelevisionPlaybackSettings(
            uiState,
            colors,
            actions.valueActions,
            firstFocusRequester,
            returnFocusRequester,
            returnChoice,
            onChoiceSelected,
        )
        TelevisionSettingsCategory.Lyrics -> TelevisionLyricsSettings(
            uiState,
            colors,
            actions.valueActions,
            firstFocusRequester,
            returnFocusRequester,
            returnChoice,
            onChoiceSelected,
        )
        TelevisionSettingsCategory.Display -> TelevisionDisplaySettings(
            uiState,
            colors,
            actions.valueActions,
            firstFocusRequester,
            returnFocusRequester,
            returnChoice,
            onChoiceSelected,
        )
        TelevisionSettingsCategory.Diagnostics -> TelevisionDiagnosticsSettings(
            uiState,
            colors,
            actions.maintenanceActions,
            firstFocusRequester,
        )
        TelevisionSettingsCategory.About -> TelevisionAboutSettings(uiState, colors, firstFocusRequester)
        TelevisionSettingsCategory.Controllers -> TelevisionControllersSettings(
            connect = uiState.connect,
            actions = actions.connectActions,
            colors = colors,
            firstFocusRequester = firstFocusRequester,
        )
    }
}

@Composable
private fun TelevisionControllersSettings(
    connect: NaviampConnectSettingsUi,
    actions: NaviampConnectSettingsActions?,
    colors: NaviampColors,
    firstFocusRequester: FocusRequester,
) {
    if (!connect.available || actions == null) {
        TelevisionSettingsMessage(stringResource(Res.string.tv_naviamp_connect_is_unavailable_on_this_device), colors, firstFocusRequester)
        return
    }
    var renameOpen by remember { mutableStateOf(false) }
    var deviceName by remember(connect.localDeviceName) { mutableStateOf(connect.localDeviceName) }
    if (renameOpen) {
        NaviampPopupPresence()
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(Res.string.tv_name_this_device)) },
            text = {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { if (it.length <= 64) deviceName = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.tv_friendly_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = deviceName.trim().isNotEmpty(),
                    onClick = {
                        actions.onLocalDeviceNameChanged(deviceName)
                        renameOpen = false
                    },
                ) { Text(stringResource(Res.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    TelevisionSettingsList {
        item(key = "device-name") {
            TelevisionSettingsRow(
                title = stringResource(Res.string.tv_device_name),
                subtitle = stringResource(Res.string.tv_the_name_other_naviamp_devices_see_when_connecting),
                value = connect.localDeviceName,
                icon = NaviampIcons.Player,
                disclosure = true,
                colors = colors,
                onClick = { renameOpen = true },
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
        connect.recovery?.let { recovery ->
            item(key = "connect-recovery") {
                NaviampConnectRecoveryPanel(recovery, colors, actions.onRetryConnection,
                    actions.onOpenPermissionSettings, television = true)
            }
        }
        if (connect.canAdvertise) {
            item(key = "pairing-mode") {
                TelevisionSettingsRow(
                    title = if (connect.pairingActive) stringResource(Res.string.tv_stop_pairing) else stringResource(Res.string.tv_pair_a_controller),
                    subtitle = stringResource(Res.string.connect_setup_code_consent),
                    value = connect.pairingCode?.let(::formatNaviampConnectPairingCode),
                    icon = NaviampIcons.Player,
                    selected = connect.pairingActive,
                    colors = colors,
                    onClick = if (connect.pairingActive) actions.onStopPairingMode else actions.onStartPairingMode,
                )
            }
            if (connect.pairingPhase == NaviampConnectPairingUiPhase.AwaitingApproval) {
                item(key = "approve-controller") {
                    TelevisionSettingsRow(
                        title = stringResource(Res.string.tv_approve_named_controller, connect.pendingControllerName ?: stringResource(Res.string.tv_controller_fallback)),
                        subtitle = stringResource(Res.string.tv_only_approve_if_you_started_pairing_on_this_device),
                        value = stringResource(Res.string.tv_approve),
                        icon = NaviampIcons.Player,
                        disclosure = true,
                        colors = colors,
                        onClick = actions.onApproveController,
                    )
                }
                item(key = "reject-controller") {
                    TelevisionSettingsRow(
                        title = stringResource(Res.string.tv_reject_request),
                        subtitle = stringResource(Res.string.tv_close_this_connection_without_creating_trust),
                        value = stringResource(Res.string.tv_reject),
                        icon = NaviampIcons.Close,
                        colors = colors,
                        onClick = actions.onRejectController,
                    )
                }
            }
        }
        if (connect.canDiscover) {
            connect.connectedTargetName?.let { targetName ->
                item(key = "stop-controlling") {
                    TelevisionSettingsRow(
                        title = stringResource(Res.string.tv_stop_controlling_named, targetName),
                        subtitle = stringResource(Res.string.tv_disconnect_this_controller_while_playback_continues_on_the_target),
                        value = stringResource(Res.string.tv_disconnect),
                        icon = NaviampIcons.Close,
                        colors = colors,
                        onClick = actions.onStopControlling,
                    )
                }
            }
            item(key = "refresh-targets") {
                TelevisionSettingsRow(
                    title = stringResource(Res.string.tv_find_naviamp_targets),
                    subtitle = connect.displayStatus() ?: stringResource(Res.string.tv_search_this_local_network_for_naviamp_devices_ready_to_pair),
                    value = connect.discoveredTargets.size.takeIf { it > 0 }?.toString(),
                    icon = NaviampIcons.Refresh,
                    colors = colors,
                    onClick = actions.onRefreshTargets,
                )
            }
            items(connect.discoveredTargets, key = { "target-${it.instanceId}" }) { target ->
                TelevisionSettingsRow(
                    title = target.displayName,
                    subtitle = target.detail,
                    value = if (target.compatible) stringResource(Res.string.tv_pair) else stringResource(Res.string.tv_update_required),
                    icon = NaviampIcons.Player,
                    disclosure = target.compatible,
                    enabled = target.compatible,
                    colors = colors,
                    onClick = { actions.onTargetSelected(target) },
                )
            }
        }
        if (connect.pendingProvisioningConnectionName != null) {
            item(key = "approve-provisioning") {
                TelevisionSettingsRow(
                    title = stringResource(Res.string.tv_set_up_named_source, connect.pendingProvisioningConnectionName.orEmpty()),
                    subtitle = stringResource(Res.string.tv_provisioning_requested_by, connect.pendingProvisioningControllerName ?: stringResource(Res.string.tv_paired_controller_fallback)),
                    value = stringResource(Res.string.tv_approve),
                    icon = NaviampIcons.Library,
                    disclosure = true,
                    colors = colors,
                    onClick = actions.onApproveProvisioning,
                )
            }
            item(key = "reject-provisioning") {
                TelevisionSettingsRow(
                    title = stringResource(Res.string.tv_reject_server_setup),
                    subtitle = stringResource(Res.string.tv_discard_the_transferred_credential_without_saving_a_connection),
                    value = stringResource(Res.string.tv_reject),
                    icon = NaviampIcons.Close,
                    colors = colors,
                    onClick = actions.onRejectProvisioning,
                )
            }
        }
        items(connect.trustedDevices, key = { "trusted-${it.deviceId}" }) { device ->
            TelevisionSettingsRow(
                title = device.displayName,
                subtitle = stringResource(Res.string.tv_paired_device),
                value = if (device.reconnectAvailable) stringResource(Res.string.common_reconnect) else stringResource(Res.string.tv_trusted),
                icon = NaviampIcons.Player,
                enabled = device.reconnectAvailable,
                disclosure = device.reconnectAvailable,
                colors = colors,
                onClick = { actions.onTrustedDeviceSelected(device) },
            )
        }
    }
}

internal fun formatNaviampConnectPairingCode(code: String): String =
    code.filter(Char::isDigit).chunked(3).joinToString(" ")

@Composable
private fun TelevisionHomeSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsValueActions,
    firstFocusRequester: FocusRequester,
    movingSectionId: String?,
    orderDraft: List<HomeScreenSectionOption>?,
    onMoveStarted: (String, List<HomeScreenSectionOption>) -> Unit,
    onMoveChanged: (List<HomeScreenSectionOption>) -> Unit,
    onMoveCommitted: () -> Unit,
) {
    val settings = uiState.general.interfaceSettings
    val sections = orderDraft ?: settings.televisionHomeSectionOptions()
    val listState = rememberLazyListState()
    LaunchedEffect(movingSectionId, sections.map { it.id }) {
        val movingIndex = sections.indexOfFirst { it.id == movingSectionId }
        if (movingIndex < 0) return@LaunchedEffect
        withFrameNanos { }
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        televisionHomeMoveScrollAnchor(
            movingIndex = movingIndex,
            visibleIndices = visibleItems.map { it.index },
        )?.let { firstVisibleIndex ->
            listState.animateScrollToItem(firstVisibleIndex)
        }
    }
    TelevisionSettingsList(state = listState) {
        items(sections, key = { it.id }) { section ->
            val visible = settings.homeSectionPresentation(section.id).visible
            val moving = movingSectionId == section.id
            val controlsEnabled = movingSectionId == null || moving
            TelevisionHomeSectionRow(
                section = section,
                visible = visible,
                moving = moving,
                controlsEnabled = controlsEnabled,
                colors = colors,
                onVisibilityClick = {
                    actions.onInterfaceSettingsChanged(
                        settings.withHomeScreenSectionVisible(section.id, !visible),
                    )
                },
                onMoveClick = {
                    if (moving) onMoveCommitted() else onMoveStarted(section.id, sections)
                },
                onMove = { direction ->
                    val currentIndex = sections.indexOfFirst { it.id == section.id }
                    val targetIndex = (currentIndex + direction).coerceIn(sections.indices)
                    if (currentIndex >= 0 && currentIndex != targetIndex) {
                        onMoveChanged(sections.moveHomeSectionItem(currentIndex, targetIndex))
                    }
                },
                onCommit = onMoveCommitted,
                firstFocusRequester = if (section == sections.firstOrNull()) firstFocusRequester else null,
            )
        }
    }
}

@Composable
private fun TelevisionHomeSectionRow(
    section: HomeScreenSectionOption,
    visible: Boolean,
    moving: Boolean,
    controlsEnabled: Boolean,
    colors: NaviampColors,
    onVisibilityClick: () -> Unit,
    onMoveClick: () -> Unit,
    onMove: (Int) -> Unit,
    onCommit: () -> Unit,
    firstFocusRequester: FocusRequester?,
) {
    val sectionTitle = section.titleResource?.let { org.jetbrains.compose.resources.stringResource(it) } ?: section.title
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (moving) colors.accent.copy(alpha = 0.2f) else colors.controlSurface.copy(alpha = 0.72f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 15.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                sectionTitle,
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (moving) stringResource(Res.string.tv_moving_use_up_down_then_press_select_or_right) else if (visible) stringResource(Res.string.home_settings_visible) else stringResource(Res.string.home_settings_hidden),
                color = if (!visible && !moving) colors.secondaryText.copy(alpha = 0.68f) else colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        TelevisionHomeSectionIconButton(
            icon = if (visible) NaviampIcons.VisibilityOn else NaviampIcons.VisibilityOff,
            contentDescription = if (visible) stringResource(Res.string.tv_hide_section, sectionTitle) else stringResource(Res.string.tv_show_section, sectionTitle),
            selected = !visible,
            enabled = controlsEnabled && !moving,
            colors = colors,
            onClick = onVisibilityClick,
            modifier = firstFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        )
        TelevisionHomeSectionIconButton(
            icon = NaviampIcons.MoveVertical,
            contentDescription = if (moving) stringResource(Res.string.tv_place_section, sectionTitle) else stringResource(Res.string.tv_move_section, sectionTitle),
            selected = moving,
            enabled = controlsEnabled,
            colors = colors,
            onClick = onMoveClick,
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (!moving) return@onPreviewKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionUp -> -1
                    Key.DirectionDown -> 1
                    Key.DirectionRight -> 0
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    if (direction == 0) onCommit() else onMove(direction)
                }
                true
            },
        )
    }
}

@Composable
private fun TelevisionHomeSectionIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) colors.accent.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.2f),
            contentColor = colors.primaryText,
            disabledContainerColor = Color.Black.copy(alpha = 0.1f),
            disabledContentColor = colors.secondaryText.copy(alpha = 0.35f),
        ),
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .size(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) TelevisionSettingsFocusedBorderWidth else 0.dp,
                color = if (focused) TelevisionSettingsFocusedBorderColor else Color.Transparent,
                shape = shape,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun TelevisionSourcesSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampConnectionSettingsActions,
    firstFocusRequester: FocusRequester,
) {
    val connection = uiState.connectionSettings.connection
    val saved = connection.savedConnections
    TelevisionSettingsList {
        items(saved, key = { it.id }) { source ->
            TelevisionSettingsRow(
                title = source.displayName,
                subtitle = listOf(source.providerId, source.username).filter(String::isNotBlank).joinToString(" • "),
                value = if (source.current) stringResource(Res.string.common_current) else source.selectedLibrarySummary,
                icon = NaviampIcons.Library,
                selected = source.current,
                colors = colors,
                onClick = { if (!source.current) actions.onConnectSavedConnection(source) },
                modifier = if (source == saved.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier,
            )
        }
        item(key = "edit-current-source") {
            TelevisionSettingsRow(
                title = stringResource(Res.string.tv_edit_current_source),
                subtitle = stringResource(Res.string.tv_server_account_and_libraries),
                icon = NaviampIcons.Edit,
                enabled = connection.connected,
                colors = colors,
                onClick = actions.onEditCurrentConnection,
                modifier = if (saved.isEmpty() && connection.connected) {
                    Modifier.focusRequester(firstFocusRequester)
                } else {
                    Modifier
                },
            )
        }
        item(key = "add-source") {
            TelevisionSettingsRow(
                title = stringResource(Res.string.tv_add_source),
                subtitle = stringResource(Res.string.tv_connect_another_media_server),
                icon = NaviampIcons.Plus,
                colors = colors,
                onClick = actions.onNewConnection,
                modifier = if (saved.isEmpty() && !connection.connected) {
                    Modifier.focusRequester(firstFocusRequester)
                } else {
                    Modifier
                },
            )
        }
        connection.status?.let { status ->
            item(key = "source-status") { TelevisionSettingsInfo(stringResource(Res.string.tv_status), status, colors) }
        }
    }
}

@Composable
private fun TelevisionPlaybackSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsValueActions,
    firstFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester,
    returnChoice: TelevisionSettingsChoicePage?,
    onChoiceSelected: (TelevisionSettingsChoicePage) -> Unit,
) {
    val capability = uiState.playback
    val settings = capability.settings
    TelevisionSettingsList {
        if (capability.replayGainAvailable) {
            item(key = "replay-gain") {
                TelevisionSettingsRow(
                    stringResource(Res.string.tv_replaygain),
                    stringResource(Res.string.tv_normalize_loudness_between_tracks_and_albums),
                    televisionReplayGainLabel(settings.replayGainMode),
                    NaviampIcons.Experience,
                    disclosure = true,
                    colors = colors,
                    onClick = { onChoiceSelected(TelevisionSettingsChoicePage.ReplayGain) },
                    modifier = Modifier.focusRequester(
                        if (returnChoice == TelevisionSettingsChoicePage.ReplayGain) {
                            returnFocusRequester
                        } else {
                            firstFocusRequester
                        },
                    ),
                )
            }
        }
        if (capability.gaplessAvailable) {
            item(key = "gapless") {
                TelevisionSettingsToggleRow(
                    stringResource(Res.string.tv_gapless_playback),
                    stringResource(Res.string.tv_keep_continuous_albums_seamless),
                    settings.gaplessEnabled,
                    colors,
                    {
                        actions.onPlaybackSettingsChanged(
                            televisionPlaybackSettingsWithGapless(settings, !settings.gaplessEnabled),
                        )
                    },
                    if (!capability.replayGainAvailable) Modifier.focusRequester(firstFocusRequester) else Modifier,
                )
            }
        }
        if (capability.crossfadeAvailable) {
            item(key = "crossfade") {
                TelevisionSettingsRow(
                    stringResource(Res.string.settings_crossfade_title),
                    stringResource(Res.string.tv_blend_the_end_and_beginning_of_tracks),
                    televisionCrossfadeLabel(settings.crossfadeDurationSeconds),
                    NaviampIcons.Experience,
                    disclosure = true,
                    colors = colors,
                    onClick = { onChoiceSelected(TelevisionSettingsChoicePage.Crossfade) },
                    modifier = if (returnChoice == TelevisionSettingsChoicePage.Crossfade) {
                        Modifier.focusRequester(returnFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
        item(key = "sample-rate") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_sample_rate_matching),
                stringResource(Res.string.tv_adjust_the_output_rate_for_playback),
                televisionSampleRateLabel(settings.sampleRateMatching),
                NaviampIcons.Experience,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.SampleRateMatching) },
                modifier = when {
                    returnChoice == TelevisionSettingsChoicePage.SampleRateMatching ->
                        Modifier.focusRequester(returnFocusRequester)
                    !capability.replayGainAvailable && !capability.gaplessAvailable ->
                        Modifier.focusRequester(firstFocusRequester)
                    else -> Modifier
                },
            )
        }
        item(key = "downmix") {
            TelevisionSettingsToggleRow(
                stringResource(Res.string.tv_stereo_downmix),
                stringResource(Res.string.tv_mix_multichannel_audio_to_stereo),
                settings.stereoDownmixEnabled,
                colors,
                { actions.onPlaybackSettingsChanged(settings.copy(stereoDownmixEnabled = !settings.stereoDownmixEnabled)) },
            )
        }
        item(key = "remove-played") {
            TelevisionSettingsToggleRow(
                stringResource(Res.string.tv_remove_played_tracks),
                stringResource(Res.string.tv_keep_the_queue_focused_on_what_is_next),
                settings.removePlayedTracksFromQueue,
                colors,
                {
                    actions.onPlaybackSettingsChanged(
                        settings.copy(removePlayedTracksFromQueue = !settings.removePlayedTracksFromQueue),
                    )
                },
            )
        }
    }
}

@Composable
private fun TelevisionLyricsSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsValueActions,
    firstFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester,
    returnChoice: TelevisionSettingsChoicePage?,
    onChoiceSelected: (TelevisionSettingsChoicePage) -> Unit,
) {
    val settings = uiState.playback.settings
    TelevisionSettingsList {
        item(key = "online-lyrics") {
            TelevisionSettingsToggleRow(
                stringResource(Res.string.tv_online_lyrics),
                stringResource(Res.string.tv_use_online_providers_when_server_and_embedded_lyrics_are_unavailable),
                settings.lrclibLyricsEnabled,
                colors,
                { actions.onPlaybackSettingsChanged(settings.copy(lrclibLyricsEnabled = !settings.lrclibLyricsEnabled)) },
                Modifier.focusRequester(firstFocusRequester),
            )
        }
        item(key = "lyrics-download-timing") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_preferred_lyrics),
                stringResource(Res.string.tv_timing_requested_while_loading_lyrics),
                televisionLyricsTimingLabel(settings.lyricsTimingPreference),
                NaviampTransportIcons.Lyrics,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.LyricsDownloadTiming) },
                modifier = if (returnChoice == TelevisionSettingsChoicePage.LyricsDownloadTiming) {
                    Modifier.focusRequester(returnFocusRequester)
                } else {
                    Modifier
                },
            )
        }
        item(key = "lyrics-display-timing") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_lyrics_display),
                stringResource(Res.string.tv_timing_used_in_now_playing),
                televisionLyricsDisplayLabel(settings.lyricsDisplayPreference),
                NaviampTransportIcons.Lyrics,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.LyricsDisplayTiming) },
                modifier = if (returnChoice == TelevisionSettingsChoicePage.LyricsDisplayTiming) {
                    Modifier.focusRequester(returnFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun TelevisionDisplaySettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsValueActions,
    firstFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester,
    returnChoice: TelevisionSettingsChoicePage?,
    onChoiceSelected: (TelevisionSettingsChoicePage) -> Unit,
) {
    val settings = uiState.general.interfaceSettings
    val nowPlaying = settings.nowPlaying
    val screenAwake = LocalNaviampScreenAwakeUi.current
    TelevisionSettingsList {
        item(key = "language") {
            TelevisionSettingsRow(
                title = stringResource(Res.string.settings_language_title),
                subtitle = stringResource(Res.string.settings_language_subtitle),
                value = naviampLanguagePack(settings.language).languageTitle(settings.language),
                icon = NaviampIcons.Experience,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.Language) },
                modifier = Modifier.focusRequester(
                    if (returnChoice == TelevisionSettingsChoicePage.Language) returnFocusRequester else firstFocusRequester,
                ),
            )
        }
        item(key = "general-font-size") {
            TelevisionSettingsRow(
                stringResource(Res.string.settings_font_size_general_title),
                stringResource(Res.string.settings_font_size_subtitle),
                settings.generalFontSize.label(),
                NaviampIcons.Experience,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.GeneralFontSize) },
                modifier = if (returnChoice == TelevisionSettingsChoicePage.GeneralFontSize) {
                    Modifier.focusRequester(returnFocusRequester)
                } else Modifier,
            )
        }
        item(key = "now-playing-font-size") {
            TelevisionSettingsRow(
                stringResource(Res.string.settings_font_size_now_playing_title),
                stringResource(Res.string.settings_font_size_subtitle),
                settings.nowPlayingFontSize.label(),
                NaviampIcons.Player,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.NowPlayingFontSize) },
                modifier = if (returnChoice == TelevisionSettingsChoicePage.NowPlayingFontSize) {
                    Modifier.focusRequester(returnFocusRequester)
                } else Modifier,
            )
        }
        if (screenAwake.available) {
            item(key = "keep-screen-awake") {
                TelevisionSettingsToggleRow(
                    title = stringResource(Res.string.settings_keep_screen_awake),
                    subtitle = stringResource(if (screenAwake.failed) Res.string.settings_keep_screen_awake_failed
                        else Res.string.settings_keep_screen_awake_description),
                    checked = settings.keepScreenAwake,
                    subtitleMaxLines = Int.MAX_VALUE,
                    colors = colors,
                    onClick = { actions.onInterfaceSettingsChanged(settings.copy(keepScreenAwake = !settings.keepScreenAwake)) },
                )
            }
        }
        item(key = "background") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_background),
                stringResource(Res.string.tv_choose_the_living_room_backdrop),
                televisionBackgroundLabel(settings.appBackgroundStyle),
                NaviampIcons.Experience,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.Background) },
                modifier = if (returnChoice == TelevisionSettingsChoicePage.Background) {
                    Modifier.focusRequester(returnFocusRequester)
                } else Modifier,
            )
        }
        item(key = "album-sort-order") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_album_sort_order),
                stringResource(Res.string.tv_album_sort_order_description),
                televisionAlbumSortOrderLabel(settings.albumSortOrder),
                NaviampIcons.Library,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.AlbumSortOrder) },
                modifier = if (returnChoice == TelevisionSettingsChoicePage.AlbumSortOrder) {
                    Modifier.focusRequester(returnFocusRequester)
                } else Modifier,
            )
        }
        item(key = "album-release-grouping") {
            TelevisionSettingsToggleRow(
                stringResource(Res.string.tv_group_albums_by_release_type),
                stringResource(Res.string.tv_group_albums_by_release_type_description),
                settings.groupAlbumsByReleaseType,
                colors,
                {
                    actions.onInterfaceSettingsChanged(
                        settings.copy(groupAlbumsByReleaseType = !settings.groupAlbumsByReleaseType),
                    )
                },
            )
        }
        item(key = "waveform-density") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_waveform_density),
                stringResource(Res.string.tv_choose_the_detail_level_of_the_now_playing_waveform),
                televisionWaveformDensityLabel(uiState.cache.settings.waveformBucketCount),
                NaviampIcons.Experience,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.WaveformDensity) },
                modifier = if (returnChoice == TelevisionSettingsChoicePage.WaveformDensity) {
                    Modifier.focusRequester(returnFocusRequester)
                } else {
                    Modifier
                },
            )
        }
        item(key = "album-year") {
            TelevisionSettingsToggleRow(
                stringResource(Res.string.settings_now_playing_show_album_year),
                stringResource(Res.string.tv_include_release_context_in_now_playing),
                nowPlaying.showAlbumYear,
                colors,
                {
                    actions.onInterfaceSettingsChanged(
                        settings.copy(nowPlaying = nowPlaying.copy(showAlbumYear = !nowPlaying.showAlbumYear)),
                    )
                },
            )
        }
        item(key = "audio-info") {
            TelevisionSettingsToggleRow(
                stringResource(Res.string.tv_show_audio_information),
                stringResource(Res.string.tv_display_codec_and_playback_quality),
                nowPlaying.showAudioInfo,
                colors,
                {
                    actions.onInterfaceSettingsChanged(
                        settings.copy(nowPlaying = nowPlaying.copy(showAudioInfo = !nowPlaying.showAudioInfo)),
                    )
                },
            )
        }
        item(key = "track-cover") {
            TelevisionSettingsToggleRow(
                stringResource(Res.string.tv_prefer_track_artwork),
                stringResource(Res.string.tv_use_track_specific_art_when_available),
                nowPlaying.showTrackCover,
                colors,
                {
                    actions.onInterfaceSettingsChanged(
                        settings.copy(nowPlaying = nowPlaying.copy(showTrackCover = !nowPlaying.showTrackCover)),
                    )
                },
            )
        }

    }
}

@Composable
private fun TelevisionDiagnosticsSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsMaintenanceActions,
    firstFocusRequester: FocusRequester,
) {
    val connection = uiState.connectionSettings.connection
    TelevisionSettingsList {
        item(key = "stats") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_stats_for_nerds),
                stringResource(Res.string.tv_playback_connection_and_cache_details),
                icon = NaviampIcons.Bug,
                colors = colors,
                onClick = actions.onOpenStatsForNerds,
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
        item(key = "refresh-library") {
            TelevisionSettingsRow(
                stringResource(Res.string.tv_refresh_library),
                stringResource(Res.string.tv_reload_provider_content_for_this_tv),
                icon = NaviampIcons.Refresh,
                colors = colors,
                onClick = actions.onRefreshLibrary,
            )
        }
        connection.serverVersion?.let { version ->
            item(key = "server-version") { TelevisionSettingsInfo(stringResource(Res.string.tv_server_version), version, colors) }
        }
        connection.status?.let { status ->
            item(key = "connection-status") { TelevisionSettingsInfo(stringResource(Res.string.tv_connection), status, colors) }
        }
        uiState.cache.diagnostics.sections.forEachIndexed { sectionIndex, section ->
            items(section.rows, key = { row -> "$sectionIndex:${row.first}" }) { row ->
                TelevisionSettingsInfo(
                    localizedDiagnosticText(row.first),
                    localizedDiagnosticText(row.second),
                    colors,
                )
            }
        }
    }
}

@Composable
private fun TelevisionAboutSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    firstFocusRequester: FocusRequester,
) {
    val about = uiState.general.about
    TelevisionSettingsList {
        item(key = "version") {
            TelevisionSettingsRow(
                "Naviamp",
                stringResource(Res.string.tv_shared_music_player_for_every_screen),
                about.version,
                NaviampIcons.AppMark,
                colors = colors,
                onClick = {},
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
        if (about.buildNumber.isNotBlank()) {
            item(key = "build") { TelevisionSettingsInfo(stringResource(Res.string.tv_build), about.buildNumber, colors) }
        }
        item(key = "libraries") { TelevisionSettingsInfo(stringResource(Res.string.tv_open_source_libraries), about.libraries.size.toString(), colors) }
    }
}

@Composable
private fun TelevisionSettingsMessage(
    message: String,
    colors: NaviampColors,
    firstFocusRequester: FocusRequester,
) {
    Text(
        message,
        color = colors.secondaryText,
        fontSize = 18.sp,
        lineHeight = 25.sp,
        modifier = Modifier.focusRequester(firstFocusRequester),
    )
}

@Composable
internal fun TelevisionBackgroundSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsValueActions,
    firstFocusRequester: FocusRequester,
) {
    val settings = uiState.general.interfaceSettings
    val selectedColor = naviampColorFromHex(settings.singleColorHex) ?: colors.background
    val hsv = naviampColorToHsv(selectedColor)
    val updateColor: (Float, Float, Float) -> Unit = { hue, saturation, brightness ->
        actions.onInterfaceSettingsChanged(
            settings.copy(
                singleColorHex = naviampColorToHex(naviampColorFromHsv(hue, saturation, brightness)),
            ).normalized(),
        )
    }
    TelevisionSettingsList {
        itemsIndexed(AppBackgroundStyle.entries, key = { _, style -> "background-style:${style.name}" }) { index, style ->
            TelevisionSettingsRow(
                title = televisionBackgroundLabel(style),
                value = if (settings.appBackgroundStyle == style) "✓" else null,
                selected = settings.appBackgroundStyle == style,
                colors = colors,
                onClick = {
                    actions.onInterfaceSettingsChanged(settings.copy(appBackgroundStyle = style).normalized())
                },
                modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
            )
        }
        item(key = "background-options-divider") {
            HorizontalDivider(
                color = colors.border.copy(alpha = 0.78f),
                modifier = Modifier.padding(vertical = 9.dp),
            )
        }
        when (settings.appBackgroundStyle) {
            AppBackgroundStyle.Aurora -> {
                items(AuroraTone.entries, key = { tone -> "aurora-tone:${tone.name}" }) { tone ->
                    TelevisionSettingsRow(
                        title = televisionAuroraToneLabel(tone),
                        subtitle = stringResource(when (tone) {
                            AuroraTone.Dark -> Res.string.aurora_tone_balanced_description
                            AuroraTone.Light -> Res.string.aurora_tone_light_description
                            AuroraTone.DeepDark -> Res.string.aurora_tone_dark_description
                        }),
                        value = if (settings.auroraTone == tone) "✓" else null,
                        selected = settings.auroraTone == tone,
                        colors = colors,
                        onClick = {
                            actions.onInterfaceSettingsChanged(settings.copy(auroraTone = tone).normalized())
                        },
                    )
                }
                item(key = "aurora-color-steps") {
                    TelevisionSettingsSliderRow(
                        label = stringResource(Res.string.aurora_color_steps),
                        value = settings.auroraColorSteps.toFloat(),
                        valueRange = 2f..5f,
                        step = 1f,
                        valueText = stringResource(Res.string.aurora_color_count, settings.auroraColorSteps),
                        colors = colors,
                        onValueChange = { value ->
                            actions.onInterfaceSettingsChanged(
                                settings.copy(auroraColorSteps = value.toInt()).normalized(),
                            )
                        },
                    )
                }
                item(key = "aurora-angle") {
                    TelevisionSettingsSliderRow(
                        label = stringResource(Res.string.aurora_gradient_angle),
                        value = settings.auroraAngleDegrees.toFloat(),
                        valueRange = 0f..180f,
                        step = 15f,
                        valueText = stringResource(Res.string.aurora_angle_value, settings.auroraAngleDegrees),
                        colors = colors,
                        onValueChange = { value ->
                            actions.onInterfaceSettingsChanged(
                                settings.copy(auroraAngleDegrees = value.toInt()).normalized(),
                            )
                        },
                    )
                }
            }
            AppBackgroundStyle.AlbumBlur -> item(key = "album-blur-amount") {
                TelevisionSettingsSliderRow(
                    label = stringResource(Res.string.tv_blur_amount),
                    value = settings.albumBlurRadiusDp.toFloat(),
                    valueRange = MinAlbumBlurRadiusDp.toFloat()..MaxAlbumBlurRadiusDp.toFloat(),
                    step = 2f,
                    valueText = stringResource(Res.string.tv_blur_value, settings.albumBlurRadiusDp),
                    colors = colors,
                    onValueChange = { value ->
                        actions.onInterfaceSettingsChanged(
                            settings.copy(albumBlurRadiusDp = value.toInt()).normalized(),
                        )
                    },
                )
            }
            AppBackgroundStyle.SingleColor -> {
                item(key = "color-preview") {
                    TelevisionSettingsColorPreview(settings.singleColorHex, selectedColor, colors)
                }
                item(key = "color-hue") {
                    TelevisionSettingsSliderRow(
                        label = stringResource(Res.string.tv_hue),
                        value = hsv[0] * 360f,
                        valueRange = 0f..360f,
                        step = 10f,
                        valueText = stringResource(Res.string.aurora_angle_value, (hsv[0] * 360f).toInt()),
                        colors = colors,
                        onValueChange = { hue -> updateColor(hue / 360f, hsv[1], hsv[2]) },
                    )
                }
                item(key = "color-saturation") {
                    TelevisionSettingsSliderRow(
                        label = stringResource(Res.string.tv_saturation),
                        value = hsv[1] * 100f,
                        valueRange = 0f..100f,
                        step = 5f,
                        valueText = stringResource(Res.string.tv_percentage_value, (hsv[1] * 100f).toInt()),
                        colors = colors,
                        onValueChange = { saturation -> updateColor(hsv[0], saturation / 100f, hsv[2]) },
                    )
                }
                item(key = "color-brightness") {
                    TelevisionSettingsSliderRow(
                        label = stringResource(Res.string.tv_brightness),
                        value = hsv[2] * 100f,
                        valueRange = 8f..70f,
                        step = 5f,
                        valueText = stringResource(Res.string.tv_percentage_value, (hsv[2] * 100f).toInt()),
                        colors = colors,
                        onValueChange = { brightness -> updateColor(hsv[0], hsv[1], brightness / 100f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TelevisionAlbumBlurAmountSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsValueActions,
    firstFocusRequester: FocusRequester,
) {
    val settings = uiState.general.interfaceSettings
    TelevisionSettingsList {
        item(key = "blur-slider") {
            TelevisionSettingsSliderRow(
                label = stringResource(Res.string.tv_blur_amount),
                value = settings.albumBlurRadiusDp.toFloat(),
                valueRange = MinAlbumBlurRadiusDp.toFloat()..MaxAlbumBlurRadiusDp.toFloat(),
                step = 2f,
                valueText = stringResource(Res.string.tv_blur_value, settings.albumBlurRadiusDp),
                colors = colors,
                onValueChange = { value ->
                    actions.onInterfaceSettingsChanged(
                        settings.copy(albumBlurRadiusDp = value.toInt()).normalized(),
                    )
                },
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
    }
}

@Composable
private fun TelevisionSingleColorSettings(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampSettingsValueActions,
    firstFocusRequester: FocusRequester,
) {
    val settings = uiState.general.interfaceSettings
    val selectedColor = naviampColorFromHex(settings.singleColorHex) ?: colors.background
    val hsv = naviampColorToHsv(selectedColor)
    val updateColor: (Float, Float, Float) -> Unit = { hue, saturation, brightness ->
        actions.onInterfaceSettingsChanged(
            settings.copy(
                singleColorHex = naviampColorToHex(naviampColorFromHsv(hue, saturation, brightness)),
            ).normalized(),
        )
    }
    TelevisionSettingsList {
        item(key = "color-preview") {
            TelevisionSettingsColorPreview(settings.singleColorHex, selectedColor, colors)
        }
        item(key = "color-hue") {
            TelevisionSettingsSliderRow(
                label = stringResource(Res.string.tv_hue),
                value = hsv[0] * 360f,
                valueRange = 0f..360f,
                step = 10f,
                valueText = stringResource(Res.string.aurora_angle_value, (hsv[0] * 360f).toInt()),
                colors = colors,
                onValueChange = { hue -> updateColor(hue / 360f, hsv[1], hsv[2]) },
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
        item(key = "color-saturation") {
            TelevisionSettingsSliderRow(
                label = stringResource(Res.string.tv_saturation),
                value = hsv[1] * 100f,
                valueRange = 0f..100f,
                step = 5f,
                valueText = stringResource(Res.string.tv_percentage_value, (hsv[1] * 100f).toInt()),
                colors = colors,
                onValueChange = { saturation -> updateColor(hsv[0], saturation / 100f, hsv[2]) },
            )
        }
        item(key = "color-brightness") {
            TelevisionSettingsSliderRow(
                label = stringResource(Res.string.tv_brightness),
                value = hsv[2] * 100f,
                valueRange = 8f..70f,
                step = 5f,
                valueText = stringResource(Res.string.tv_percentage_value, (hsv[2] * 100f).toInt()),
                colors = colors,
                onValueChange = { brightness -> updateColor(hsv[0], hsv[1], brightness / 100f) },
            )
        }
    }
}

@Composable
private fun TelevisionSettingsColorPreview(
    hex: String,
    previewColor: Color,
    colors: NaviampColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlSurface.copy(alpha = 0.46f), RoundedCornerShape(12.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(previewColor, RoundedCornerShape(10.dp))
                .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.tv_selected_color), color = colors.primaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(hex, color = colors.secondaryText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TelevisionSettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueText: String,
    colors: NaviampColors,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val adjustedValue: (Boolean) -> Unit = { increase ->
        onValueChange(televisionSteppedSettingsValue(value, step, valueRange, increase))
    }
    Button(
        onClick = { adjustedValue(true) },
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.controlSurface.copy(alpha = 0.72f),
            contentColor = colors.primaryText,
        ),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp),
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                val increase = when (event.key) {
                    Key.DirectionLeft -> false
                    Key.DirectionRight -> true
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) adjustedValue(increase)
                true
            }
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) TelevisionSettingsFocusedBorderWidth else 0.dp,
                color = if (focused) TelevisionSettingsFocusedBorderColor else Color.Transparent,
                shape = shape,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(valueText, color = colors.secondaryText, fontSize = 15.sp)
            }
            val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                .coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val centerY = size.height / 2f
                val trackInset = 7.dp.toPx()
                val trackStart = trackInset
                val trackEnd = (size.width - trackInset).coerceAtLeast(trackStart)
                drawLine(
                    color = colors.secondaryText.copy(alpha = 0.3f),
                    start = Offset(trackStart, centerY),
                    end = Offset(trackEnd, centerY),
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val thumbX = trackStart + (trackEnd - trackStart) * fraction
                drawLine(
                    color = colors.primaryText.copy(alpha = if (focused) 1f else 0.76f),
                    start = Offset(trackStart, centerY),
                    end = Offset(thumbX, centerY),
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = if (focused) colors.primaryText else colors.secondaryText,
                    radius = 7.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
            }
        }
    }
}

@Composable
private fun TelevisionSettingsChoiceList(
    page: TelevisionSettingsChoicePage,
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    actions: NaviampAppShellActions,
    firstFocusRequester: FocusRequester,
) {
    val choices = televisionSettingsChoices(page, uiState, actions)
    TelevisionSettingsList {
        // Option order is fixed within each page; translated labels must never identify focus nodes.
        itemsIndexed(choices, key = { index, _ -> "${page.name}:$index" }) { index, choice ->
            TelevisionSettingsRow(
                title = choice.label,
                subtitle = choice.subtitle,
                value = if (choice.selected) "✓" else null,
                selected = choice.selected,
                colors = colors,
                onClick = choice.onSelected,
                modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun TelevisionSettingsList(
    state: LazyListState? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val resolvedState = state ?: rememberLazyListState()
    LazyColumn(
        state = resolvedState,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(
            start = TelevisionSettingsFocusOverflow,
            top = TelevisionSettingsFocusOverflow,
            end = TelevisionSettingsFocusOverflow,
            bottom = TelevisionSettingsFocusOverflow,
        ),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

@Composable
private fun televisionSettingsChoices(
    page: TelevisionSettingsChoicePage,
    uiState: NaviampAppShellUiState,
    actions: NaviampAppShellActions,
): List<TelevisionChoiceUi> {
    val interfaceSettings = uiState.general.interfaceSettings
    val playback = uiState.playback.settings
    return when (page) {
        TelevisionSettingsChoicePage.AlbumBlurAmount,
        TelevisionSettingsChoicePage.SingleColor,
        -> emptyList()
        TelevisionSettingsChoicePage.Language -> InterfaceLanguage.entries.map { value ->
            val pack = naviampLanguagePack(interfaceSettings.language)
            TelevisionChoiceUi(pack.languageTitle(value), pack.languageSubtitle(value), value == interfaceSettings.language) {
                actions.valueActions.onInterfaceSettingsChanged(interfaceSettings.copy(language = value).normalized())
            }
        }
        TelevisionSettingsChoicePage.GeneralFontSize -> InterfaceFontSize.entries.map { value ->
            TelevisionChoiceUi(
                value.label(),
                value.subtitle(),
                value == interfaceSettings.generalFontSize,
            ) {
                actions.valueActions.onInterfaceSettingsChanged(
                    interfaceSettings.copy(generalFontSize = value).normalized(),
                )
            }
        }
        TelevisionSettingsChoicePage.NowPlayingFontSize -> InterfaceFontSize.entries.map { value ->
            TelevisionChoiceUi(
                value.label(),
                value.subtitle(),
                value == interfaceSettings.nowPlayingFontSize,
            ) {
                actions.valueActions.onInterfaceSettingsChanged(
                    interfaceSettings.copy(nowPlayingFontSize = value).normalized(),
                )
            }
        }
        TelevisionSettingsChoicePage.Background -> AppBackgroundStyle.entries.map { value ->
            TelevisionChoiceUi(televisionBackgroundLabel(value), selected = value == interfaceSettings.appBackgroundStyle) {
                actions.valueActions.onInterfaceSettingsChanged(interfaceSettings.copy(appBackgroundStyle = value))
            }
        }
        TelevisionSettingsChoicePage.AuroraTone -> AuroraTone.entries.map { value ->
            TelevisionChoiceUi(televisionAuroraToneLabel(value), selected = value == interfaceSettings.auroraTone) {
                actions.valueActions.onInterfaceSettingsChanged(interfaceSettings.copy(auroraTone = value))
            }
        }
        TelevisionSettingsChoicePage.AuroraColorSteps -> (2..5).map { value ->
            TelevisionChoiceUi(
                stringResource(Res.string.aurora_color_count, value),
                selected = value == interfaceSettings.auroraColorSteps,
            ) {
                actions.valueActions.onInterfaceSettingsChanged(
                    interfaceSettings.copy(auroraColorSteps = value).normalized(),
                )
            }
        }
        TelevisionSettingsChoicePage.AuroraAngle -> listOf(0, 30, 45, 60, 90, 120, 135, 150, 180).map { value ->
            TelevisionChoiceUi(
                stringResource(Res.string.aurora_angle_value, value),
                selected = value == interfaceSettings.auroraAngleDegrees,
            ) {
                actions.valueActions.onInterfaceSettingsChanged(
                    interfaceSettings.copy(auroraAngleDegrees = value).normalized(),
                )
            }
        }
        TelevisionSettingsChoicePage.AlbumSortOrder -> AlbumSortOrder.entries.map { value ->
            TelevisionChoiceUi(
                televisionAlbumSortOrderLabel(value),
                selected = value == interfaceSettings.albumSortOrder,
            ) {
                actions.valueActions.onInterfaceSettingsChanged(interfaceSettings.copy(albumSortOrder = value))
            }
        }
        TelevisionSettingsChoicePage.ReplayGain -> ReplayGainMode.entries.map { value ->
            TelevisionChoiceUi(televisionReplayGainLabel(value), selected = value == playback.replayGainMode) {
                actions.valueActions.onPlaybackSettingsChanged(playback.copy(replayGainMode = value))
            }
        }
        TelevisionSettingsChoicePage.SampleRateMatching -> SampleRateMatching.entries.map { value ->
            TelevisionChoiceUi(televisionSampleRateLabel(value), televisionSampleRateSubtitle(value), value == playback.sampleRateMatching) {
                actions.valueActions.onPlaybackSettingsChanged(playback.copy(sampleRateMatching = value))
            }
        }
        TelevisionSettingsChoicePage.Crossfade -> listOf(0, 3, 5, 8, 12).map { seconds ->
            TelevisionChoiceUi(televisionCrossfadeLabel(seconds), selected = seconds == playback.crossfadeDurationSeconds) {
                actions.valueActions.onPlaybackSettingsChanged(
                    televisionPlaybackSettingsWithCrossfade(playback, seconds),
                )
            }
        }
        TelevisionSettingsChoicePage.WaveformDensity -> TelevisionWaveformBucketCountOptions.map { count ->
            TelevisionChoiceUi(
                televisionWaveformDensityLabel(count),
                selected = count == uiState.cache.settings.waveformBucketCount,
            ) {
                actions.valueActions.onCacheSettingsChanged(
                    uiState.cache.settings.copy(waveformBucketCount = count).normalized(),
                )
            }
        }
        TelevisionSettingsChoicePage.LyricsDownloadTiming -> LyricsTimingPreference.entries.map { value ->
            TelevisionChoiceUi(televisionLyricsTimingLabel(value), selected = value == playback.lyricsTimingPreference) {
                actions.valueActions.onPlaybackSettingsChanged(playback.copy(lyricsTimingPreference = value))
            }
        }
        TelevisionSettingsChoicePage.LyricsDisplayTiming -> LyricsDisplayPreference.entries.map { value ->
            TelevisionChoiceUi(televisionLyricsDisplayLabel(value), selected = value == playback.lyricsDisplayPreference) {
                actions.valueActions.onPlaybackSettingsChanged(playback.copy(lyricsDisplayPreference = value))
            }
        }
    }
}

@Composable
private fun TelevisionSettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 2,
) {
    TelevisionSettingsRow(
        title = title,
        subtitle = subtitle,
        subtitleMaxLines = subtitleMaxLines,
        value = if (checked) stringResource(Res.string.tv_on) else stringResource(Res.string.common_off),
        selected = checked,
        colors = colors,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun TelevisionSettingsRow(
    title: String,
    subtitle: String = "",
    value: String? = null,
    icon: ImageVector? = null,
    disclosure: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 2,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) colors.accent.copy(alpha = 0.22f) else colors.controlSurface.copy(alpha = 0.72f),
            contentColor = colors.primaryText,
            disabledContainerColor = colors.controlSurface.copy(alpha = 0.46f),
            disabledContentColor = colors.secondaryText,
        ),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp),
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) TelevisionSettingsFocusedBorderWidth else 0.dp,
                color = if (focused) TelevisionSettingsFocusedBorderColor else Color.Transparent,
                shape = shape,
            ),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(25.dp))
            Spacer(Modifier.size(13.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        value?.takeIf(String::isNotBlank)?.let {
            Text(it, color = if (selected) colors.primaryText else colors.secondaryText, fontSize = 14.sp, maxLines = 1)
        }
        if (disclosure) {
            Spacer(Modifier.size(9.dp))
            Icon(NaviampIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TelevisionSettingsInfo(label: String, value: String, colors: NaviampColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlSurface.copy(alpha = 0.46f), RoundedCornerShape(10.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Text(label, color = colors.secondaryText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = colors.primaryText, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun televisionSettingsCategoryValue(
    category: TelevisionSettingsCategory,
    uiState: NaviampAppShellUiState,
): String? = when (category) {
    TelevisionSettingsCategory.Sources -> uiState.connectionSettings.connection.savedConnections
        .firstOrNull { it.current }?.displayName
    TelevisionSettingsCategory.Home -> uiState.general.interfaceSettings.let { settings ->
        val sections = settings.televisionHomeSectionOptions()
        val visible = sections.count { settings.homeSectionPresentation(it.id).visible }
        stringResource(Res.string.tv_sections_visible, visible, sections.size)
    }
    TelevisionSettingsCategory.Playback -> if (uiState.playback.settings.gaplessEnabled) stringResource(Res.string.settings_gapless_title) else null
    TelevisionSettingsCategory.Lyrics -> if (uiState.playback.settings.lrclibLyricsEnabled) stringResource(Res.string.tv_online_on) else stringResource(Res.string.tv_server_tags)
    TelevisionSettingsCategory.Controllers -> when {
        uiState.connect.pairingCode != null -> formatNaviampConnectPairingCode(uiState.connect.pairingCode)
        uiState.connect.trustedDevices.isNotEmpty() -> pluralStringResource(Res.plurals.tv_trusted_devices_count, uiState.connect.trustedDevices.size, uiState.connect.trustedDevices.size)
        else -> null
    }
    TelevisionSettingsCategory.Display -> televisionBackgroundLabel(uiState.general.interfaceSettings.appBackgroundStyle)
    TelevisionSettingsCategory.Diagnostics -> uiState.connectionSettings.connection.serverVersion
    TelevisionSettingsCategory.About -> uiState.general.about.version
}

@Composable
private fun televisionSettingsPageTitle(page: TelevisionSettingsPage): String = when (page) {
    TelevisionSettingsPage.Root -> stringResource(Res.string.nav_settings)
    is TelevisionSettingsPage.Category -> stringResource(page.category.label)
    is TelevisionSettingsPage.Choice -> when (page.choice) {
        TelevisionSettingsChoicePage.Language -> stringResource(Res.string.settings_language_title)
        TelevisionSettingsChoicePage.GeneralFontSize -> stringResource(Res.string.settings_font_size_general_title)
        TelevisionSettingsChoicePage.NowPlayingFontSize -> stringResource(Res.string.settings_font_size_now_playing_title)
        TelevisionSettingsChoicePage.Background -> stringResource(Res.string.tv_background)
        TelevisionSettingsChoicePage.AlbumBlurAmount -> stringResource(Res.string.tv_blur_amount)
        TelevisionSettingsChoicePage.SingleColor -> stringResource(Res.string.tv_single_color)
        TelevisionSettingsChoicePage.AuroraTone -> stringResource(Res.string.aurora_tone)
        TelevisionSettingsChoicePage.AuroraColorSteps -> stringResource(Res.string.aurora_color_steps)
        TelevisionSettingsChoicePage.AuroraAngle -> stringResource(Res.string.aurora_gradient_angle)
        TelevisionSettingsChoicePage.AlbumSortOrder -> stringResource(Res.string.tv_album_sort_order)
        TelevisionSettingsChoicePage.ReplayGain -> stringResource(Res.string.tv_replaygain)
        TelevisionSettingsChoicePage.SampleRateMatching -> stringResource(Res.string.tv_sample_rate_matching)
        TelevisionSettingsChoicePage.Crossfade -> stringResource(Res.string.settings_crossfade_title)
        TelevisionSettingsChoicePage.WaveformDensity -> stringResource(Res.string.tv_waveform_density)
        TelevisionSettingsChoicePage.LyricsDownloadTiming -> stringResource(Res.string.tv_preferred_lyrics)
        TelevisionSettingsChoicePage.LyricsDisplayTiming -> stringResource(Res.string.tv_lyrics_display)
    }
}

private fun televisionSettingsCategoryFor(page: TelevisionSettingsChoicePage): TelevisionSettingsCategory = when (page) {
    TelevisionSettingsChoicePage.Language,
    TelevisionSettingsChoicePage.GeneralFontSize,
    TelevisionSettingsChoicePage.NowPlayingFontSize,
    TelevisionSettingsChoicePage.Background,
    TelevisionSettingsChoicePage.AlbumBlurAmount,
    TelevisionSettingsChoicePage.SingleColor,
    TelevisionSettingsChoicePage.AuroraTone,
    TelevisionSettingsChoicePage.AuroraColorSteps,
    TelevisionSettingsChoicePage.AuroraAngle,
    TelevisionSettingsChoicePage.AlbumSortOrder,
    TelevisionSettingsChoicePage.WaveformDensity,
    -> TelevisionSettingsCategory.Display
    TelevisionSettingsChoicePage.ReplayGain,
    TelevisionSettingsChoicePage.SampleRateMatching,
    TelevisionSettingsChoicePage.Crossfade,
        -> TelevisionSettingsCategory.Playback
    TelevisionSettingsChoicePage.LyricsDownloadTiming,
    TelevisionSettingsChoicePage.LyricsDisplayTiming,
    -> TelevisionSettingsCategory.Lyrics
}

@Composable
internal fun televisionCrossfadeLabel(seconds: Int): String = if (seconds <= 0) stringResource(Res.string.common_off) else pluralStringResource(Res.plurals.tv_seconds, seconds, seconds)

@Composable
internal fun televisionWaveformDensityLabel(bucketCount: Int): String = pluralStringResource(Res.plurals.tv_waveform_steps, bucketCount, bucketCount)

@Composable
internal fun televisionAlbumSortOrderLabel(order: AlbumSortOrder): String = when (order) {
    AlbumSortOrder.ReleaseYearAscending -> stringResource(Res.string.tv_album_sort_oldest_first)
    AlbumSortOrder.ReleaseYearDescending -> stringResource(Res.string.tv_album_sort_newest_first)
    AlbumSortOrder.Title -> stringResource(Res.string.tv_album_sort_title)
}

internal fun televisionSteppedSettingsValue(
    value: Float,
    step: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    increase: Boolean,
): Float = (value + if (increase) step else -step).coerceIn(valueRange.start, valueRange.endInclusive)

internal const val TelevisionSettingsBackdropDimAlpha = 0.03f

internal fun televisionPlaybackSettingsWithGapless(
    settings: PlaybackSettings,
    enabled: Boolean,
): PlaybackSettings = settings.copy(
    gaplessEnabled = enabled,
    crossfadeDurationSeconds = if (enabled) 0 else settings.crossfadeDurationSeconds,
)

internal fun televisionPlaybackSettingsWithCrossfade(
    settings: PlaybackSettings,
    seconds: Int,
): PlaybackSettings = settings.copy(
    crossfadeDurationSeconds = seconds,
    gaplessEnabled = if (seconds > 0) false else settings.gaplessEnabled,
)

private val TelevisionWaveformBucketCountOptions = listOf(
    MinWaveformBucketCount,
    DefaultWaveformBucketCount,
    250,
    320,
    400,
    MaxWaveformBucketCount,
)

@Composable
internal fun televisionLyricsTimingLabel(value: LyricsTimingPreference): String = when (value) {
    LyricsTimingPreference.FirstAvailable -> stringResource(Res.string.tv_first_available)
    LyricsTimingPreference.Plain -> stringResource(Res.string.settings_lyrics_timing_plain)
    LyricsTimingPreference.LineSynced -> stringResource(Res.string.tv_line_synced)
    LyricsTimingPreference.WordSynced -> stringResource(Res.string.tv_word_synced)
}

@Composable
internal fun televisionLyricsDisplayLabel(value: LyricsDisplayPreference): String = when (value) {
    LyricsDisplayPreference.MatchDownload -> stringResource(Res.string.tv_match_preferred_lyrics)
    LyricsDisplayPreference.Plain -> stringResource(Res.string.settings_lyrics_timing_plain)
    LyricsDisplayPreference.LineSynced -> stringResource(Res.string.tv_line_synced)
    LyricsDisplayPreference.WordSynced -> stringResource(Res.string.tv_word_synced)
}

private val TelevisionSettingsFocusOverflow = 12.dp
private val TelevisionSettingsFocusedBorderWidth = 3.dp
private val TelevisionSettingsFocusedBorderColor = Color(0xFFBDEBFF)
