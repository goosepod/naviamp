package app.naviamp.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Sources("Sources", "Servers and music libraries", NaviampIcons.Library),
    Home("Home", "Sections, visibility, and order", NaviampIcons.Home),
    Playback("Playback", "Audio and queue behavior", NaviampTransportIcons.Play),
    Lyrics("Lyrics", "Sources and synchronization", NaviampTransportIcons.Lyrics),
    Controllers("Controllers", "Trusted Naviamp remotes", NaviampIcons.Player),
    Display("Display", "Background and Now Playing", NaviampIcons.Experience),
    Diagnostics("Diagnostics", "Connection and local health", NaviampIcons.Bug),
    About("About", "Version and build information", NaviampIcons.AppMark),
}

internal fun televisionSettingsCategories(controllersAvailable: Boolean): List<TelevisionSettingsCategory> =
    TelevisionSettingsCategory.entries.filter { category ->
        category != TelevisionSettingsCategory.Controllers || controllersAvailable
    }

private enum class TelevisionSettingsChoicePage {
    Background,
    AlbumBlurAmount,
    SingleColor,
    AuroraTone,
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
                .background(Color.Black.copy(alpha = TelevisionSettingsBackdropDimAlpha)),
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
private fun TelevisionSettingsRoot(
    uiState: NaviampAppShellUiState,
    colors: NaviampColors,
    firstFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester,
    returnCategory: TelevisionSettingsCategory?,
    onCategorySelected: (TelevisionSettingsCategory) -> Unit,
) {
    val categories = televisionSettingsCategories(controllersAvailable = false)
    TelevisionSettingsList {
        items(categories, key = { it.name }) { category ->
            TelevisionSettingsRow(
                title = category.label,
                subtitle = category.subtitle,
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
        TelevisionSettingsCategory.Controllers -> TelevisionSettingsMessage(
            "Naviamp Connect controllers will appear here after the shared pairing protocol is available.",
            colors,
            firstFocusRequester,
        )
    }
}

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
                section.title,
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (moving) "MOVING — use Up/Down, then press Select or Right" else if (visible) "Visible" else "Hidden",
                color = if (!visible && !moving) colors.secondaryText.copy(alpha = 0.68f) else colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        TelevisionHomeSectionIconButton(
            icon = if (visible) NaviampIcons.VisibilityOn else NaviampIcons.VisibilityOff,
            contentDescription = if (visible) "Hide ${section.title}" else "Show ${section.title}",
            selected = !visible,
            enabled = controlsEnabled && !moving,
            colors = colors,
            onClick = onVisibilityClick,
            modifier = firstFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        )
        TelevisionHomeSectionIconButton(
            icon = NaviampIcons.MoveVertical,
            contentDescription = if (moving) "Set ${section.title} position" else "Move ${section.title}",
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
                value = if (source.current) "Current" else source.selectedLibrarySummary,
                icon = NaviampIcons.Library,
                selected = source.current,
                colors = colors,
                onClick = { if (!source.current) actions.onConnectSavedConnection(source) },
                modifier = if (source == saved.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier,
            )
        }
        item(key = "edit-current-source") {
            TelevisionSettingsRow(
                title = "Edit current source",
                subtitle = "Server, account, and libraries",
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
                title = "Add source",
                subtitle = "Connect another media server",
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
            item(key = "source-status") { TelevisionSettingsInfo("Status", status, colors) }
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
                    "ReplayGain",
                    "Normalize loudness between tracks and albums",
                    settings.replayGainMode.displayName,
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
                    "Gapless playback",
                    "Keep continuous albums seamless",
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
                    "Crossfade",
                    "Blend the end and beginning of tracks",
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
                "Sample-rate matching",
                "Adjust the output rate for playback",
                settings.sampleRateMatching.label,
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
                "Stereo downmix",
                "Mix multichannel audio to stereo",
                settings.stereoDownmixEnabled,
                colors,
                { actions.onPlaybackSettingsChanged(settings.copy(stereoDownmixEnabled = !settings.stereoDownmixEnabled)) },
            )
        }
        item(key = "remove-played") {
            TelevisionSettingsToggleRow(
                "Remove played tracks",
                "Keep the queue focused on what is next",
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
                "Online lyrics",
                "Use online providers when server and embedded lyrics are unavailable",
                settings.lrclibLyricsEnabled,
                colors,
                { actions.onPlaybackSettingsChanged(settings.copy(lrclibLyricsEnabled = !settings.lrclibLyricsEnabled)) },
                Modifier.focusRequester(firstFocusRequester),
            )
        }
        item(key = "lyrics-download-timing") {
            TelevisionSettingsRow(
                "Preferred lyrics",
                "Timing requested while loading lyrics",
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
                "Lyrics display",
                "Timing used in Now Playing",
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
    TelevisionSettingsList {
        item(key = "background") {
            TelevisionSettingsRow(
                "Background",
                "Choose the living-room backdrop",
                settings.appBackgroundStyle.label,
                NaviampIcons.Experience,
                disclosure = true,
                colors = colors,
                onClick = { onChoiceSelected(TelevisionSettingsChoicePage.Background) },
                modifier = Modifier.focusRequester(
                    if (returnChoice == TelevisionSettingsChoicePage.Background) {
                        returnFocusRequester
                    } else {
                        firstFocusRequester
                    },
                ),
            )
        }
        if (settings.appBackgroundStyle == AppBackgroundStyle.Aurora) {
            item(key = "aurora-tone") {
                TelevisionSettingsRow(
                    "Aurora tone",
                    "Tune artwork-derived colors for the room",
                    settings.auroraTone.label,
                    NaviampIcons.Experience,
                    disclosure = true,
                    colors = colors,
                    onClick = { onChoiceSelected(TelevisionSettingsChoicePage.AuroraTone) },
                    modifier = if (returnChoice == TelevisionSettingsChoicePage.AuroraTone) {
                        Modifier.focusRequester(returnFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (settings.appBackgroundStyle == AppBackgroundStyle.AlbumBlur) {
            item(key = "album-blur-amount") {
                TelevisionSettingsRow(
                    "Blur amount",
                    "Adjust how strongly the album artwork is softened",
                    "${settings.albumBlurRadiusDp}dp",
                    NaviampIcons.Experience,
                    disclosure = true,
                    colors = colors,
                    onClick = { onChoiceSelected(TelevisionSettingsChoicePage.AlbumBlurAmount) },
                    modifier = if (returnChoice == TelevisionSettingsChoicePage.AlbumBlurAmount) {
                        Modifier.focusRequester(returnFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (settings.appBackgroundStyle == AppBackgroundStyle.SingleColor) {
            item(key = "single-color") {
                TelevisionSettingsRow(
                    "Single color",
                    "Adjust hue, saturation, and brightness",
                    settings.singleColorHex,
                    NaviampIcons.Experience,
                    disclosure = true,
                    colors = colors,
                    onClick = { onChoiceSelected(TelevisionSettingsChoicePage.SingleColor) },
                    modifier = if (returnChoice == TelevisionSettingsChoicePage.SingleColor) {
                        Modifier.focusRequester(returnFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
        item(key = "waveform-density") {
            TelevisionSettingsRow(
                "Waveform density",
                "Choose the detail level of the Now Playing waveform",
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
                "Show album year",
                "Include release context in Now Playing",
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
                "Show audio information",
                "Display codec and playback quality",
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
                "Prefer track artwork",
                "Use track-specific art when available",
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
                "Stats for Nerds",
                "Playback, connection, and cache details",
                icon = NaviampIcons.Bug,
                colors = colors,
                onClick = actions.onOpenStatsForNerds,
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
        item(key = "refresh-library") {
            TelevisionSettingsRow(
                "Refresh library",
                "Reload provider content for this TV",
                icon = NaviampIcons.Refresh,
                colors = colors,
                onClick = actions.onRefreshLibrary,
            )
        }
        connection.serverVersion?.let { version ->
            item(key = "server-version") { TelevisionSettingsInfo("Server version", version, colors) }
        }
        connection.status?.let { status ->
            item(key = "connection-status") { TelevisionSettingsInfo("Connection", status, colors) }
        }
        uiState.cache.diagnostics.sections.forEachIndexed { sectionIndex, section ->
            items(section.rows, key = { row -> "$sectionIndex:${row.first}" }) { row ->
                TelevisionSettingsInfo(row.first, row.second, colors)
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
                "Shared music player for every screen",
                about.version,
                NaviampIcons.AppMark,
                colors = colors,
                onClick = {},
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
        if (about.buildNumber.isNotBlank()) {
            item(key = "build") { TelevisionSettingsInfo("Build", about.buildNumber, colors) }
        }
        item(key = "libraries") { TelevisionSettingsInfo("Open-source libraries", about.libraries.size.toString(), colors) }
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
                label = "Blur amount",
                value = settings.albumBlurRadiusDp.toFloat(),
                valueRange = MinAlbumBlurRadiusDp.toFloat()..MaxAlbumBlurRadiusDp.toFloat(),
                step = 2f,
                valueText = "${settings.albumBlurRadiusDp}dp",
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
                label = "Hue",
                value = hsv[0] * 360f,
                valueRange = 0f..360f,
                step = 10f,
                valueText = "${(hsv[0] * 360f).toInt()}°",
                colors = colors,
                onValueChange = { hue -> updateColor(hue / 360f, hsv[1], hsv[2]) },
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
        }
        item(key = "color-saturation") {
            TelevisionSettingsSliderRow(
                label = "Saturation",
                value = hsv[1] * 100f,
                valueRange = 0f..100f,
                step = 5f,
                valueText = "${(hsv[1] * 100f).toInt()}%",
                colors = colors,
                onValueChange = { saturation -> updateColor(hsv[0], saturation / 100f, hsv[2]) },
            )
        }
        item(key = "color-brightness") {
            TelevisionSettingsSliderRow(
                label = "Brightness",
                value = hsv[2] * 100f,
                valueRange = 8f..70f,
                step = 5f,
                valueText = "${(hsv[2] * 100f).toInt()}%",
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
            Text("Selected color", color = colors.primaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
        items(choices, key = { it.label }) { choice ->
            TelevisionSettingsRow(
                title = choice.label,
                subtitle = choice.subtitle,
                value = if (choice.selected) "✓" else null,
                selected = choice.selected,
                colors = colors,
                onClick = choice.onSelected,
                modifier = if (choice == choices.first()) Modifier.focusRequester(firstFocusRequester) else Modifier,
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
        TelevisionSettingsChoicePage.Background -> AppBackgroundStyle.entries.map { value ->
            TelevisionChoiceUi(value.label, selected = value == interfaceSettings.appBackgroundStyle) {
                actions.valueActions.onInterfaceSettingsChanged(interfaceSettings.copy(appBackgroundStyle = value))
            }
        }
        TelevisionSettingsChoicePage.AuroraTone -> AuroraTone.entries.map { value ->
            TelevisionChoiceUi(value.label, selected = value == interfaceSettings.auroraTone) {
                actions.valueActions.onInterfaceSettingsChanged(interfaceSettings.copy(auroraTone = value))
            }
        }
        TelevisionSettingsChoicePage.ReplayGain -> ReplayGainMode.entries.map { value ->
            TelevisionChoiceUi(value.displayName, selected = value == playback.replayGainMode) {
                actions.valueActions.onPlaybackSettingsChanged(playback.copy(replayGainMode = value))
            }
        }
        TelevisionSettingsChoicePage.SampleRateMatching -> SampleRateMatching.entries.map { value ->
            TelevisionChoiceUi(value.label, value.subtitle, value == playback.sampleRateMatching) {
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
) {
    TelevisionSettingsRow(
        title = title,
        subtitle = subtitle,
        value = if (checked) "On" else "Off",
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
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 2,
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

private fun televisionSettingsCategoryValue(
    category: TelevisionSettingsCategory,
    uiState: NaviampAppShellUiState,
): String? = when (category) {
    TelevisionSettingsCategory.Sources -> uiState.connectionSettings.connection.savedConnections
        .firstOrNull { it.current }?.displayName
    TelevisionSettingsCategory.Home -> uiState.general.interfaceSettings.let { settings ->
        val sections = settings.televisionHomeSectionOptions()
        val visible = sections.count { settings.homeSectionPresentation(it.id).visible }
        "$visible of ${sections.size} visible"
    }
    TelevisionSettingsCategory.Playback -> if (uiState.playback.settings.gaplessEnabled) "Gapless" else null
    TelevisionSettingsCategory.Lyrics -> if (uiState.playback.settings.lrclibLyricsEnabled) "Online on" else "Server + tags"
    TelevisionSettingsCategory.Controllers -> null
    TelevisionSettingsCategory.Display -> uiState.general.interfaceSettings.appBackgroundStyle.label
    TelevisionSettingsCategory.Diagnostics -> uiState.connectionSettings.connection.serverVersion
    TelevisionSettingsCategory.About -> uiState.general.about.version
}

private fun televisionSettingsPageTitle(page: TelevisionSettingsPage): String = when (page) {
    TelevisionSettingsPage.Root -> "Settings"
    is TelevisionSettingsPage.Category -> page.category.label
    is TelevisionSettingsPage.Choice -> when (page.choice) {
        TelevisionSettingsChoicePage.Background -> "Background"
        TelevisionSettingsChoicePage.AlbumBlurAmount -> "Blur amount"
        TelevisionSettingsChoicePage.SingleColor -> "Single color"
        TelevisionSettingsChoicePage.AuroraTone -> "Aurora tone"
        TelevisionSettingsChoicePage.ReplayGain -> "ReplayGain"
        TelevisionSettingsChoicePage.SampleRateMatching -> "Sample-rate matching"
        TelevisionSettingsChoicePage.Crossfade -> "Crossfade"
        TelevisionSettingsChoicePage.WaveformDensity -> "Waveform density"
        TelevisionSettingsChoicePage.LyricsDownloadTiming -> "Preferred lyrics"
        TelevisionSettingsChoicePage.LyricsDisplayTiming -> "Lyrics display"
    }
}

private fun televisionSettingsCategoryFor(page: TelevisionSettingsChoicePage): TelevisionSettingsCategory = when (page) {
    TelevisionSettingsChoicePage.Background,
    TelevisionSettingsChoicePage.AlbumBlurAmount,
    TelevisionSettingsChoicePage.SingleColor,
    TelevisionSettingsChoicePage.AuroraTone,
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

internal fun televisionCrossfadeLabel(seconds: Int): String = if (seconds <= 0) "Off" else "$seconds seconds"

internal fun televisionWaveformDensityLabel(bucketCount: Int): String = "$bucketCount steps"

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

internal fun televisionLyricsTimingLabel(value: LyricsTimingPreference): String = when (value) {
    LyricsTimingPreference.FirstAvailable -> "First available"
    LyricsTimingPreference.Plain -> "Plain"
    LyricsTimingPreference.LineSynced -> "Line synced"
    LyricsTimingPreference.WordSynced -> "Word synced"
}

internal fun televisionLyricsDisplayLabel(value: LyricsDisplayPreference): String = when (value) {
    LyricsDisplayPreference.MatchDownload -> "Match preferred lyrics"
    LyricsDisplayPreference.Plain -> "Plain"
    LyricsDisplayPreference.LineSynced -> "Line synced"
    LyricsDisplayPreference.WordSynced -> "Word synced"
}

private val TelevisionSettingsFocusOverflow = 12.dp
private val TelevisionSettingsFocusedBorderWidth = 3.dp
private val TelevisionSettingsFocusedBorderColor = Color(0xFFBDEBFF)
