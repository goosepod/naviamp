package app.naviamp.ui

import app.naviamp.domain.settings.albumArtworkPreference

import app.naviamp.ui.generated.resources.connect_setup_code_consent
import org.jetbrains.compose.resources.stringResource
import app.naviamp.ui.generated.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.playback.PlaybackProgress
import kotlinx.coroutines.flow.StateFlow

/** Shared ten-foot shell. Android reports a TV display; it does not assemble this product UI. */
@Composable
fun NaviampTelevisionAppShell(
    modifier: Modifier = Modifier,
    uiState: NaviampAppShellUiState,
    settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    playbackProgress: StateFlow<PlaybackProgress>? = null,
    visualizerBandsProvider: () -> List<Float> = { uiState.nowPlaying?.visualizerFrame?.bands.orEmpty() },
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
) {
    NaviampBackNavigation(uiState, actions,
        modifier.naviampTelevisionPlaybackKeys(uiState.nowPlaying, onPlayback = actions.nowPlayingActions.onPlaybackAction),
        remoteKeys = true) { contentModifier ->
        NaviampTelevisionAppShellContent(modifier = contentModifier, uiState = uiState, settingsSync = settingsSync, playbackProgress = playbackProgress, visualizerBandsProvider = visualizerBandsProvider, actions = actions, syncActions = syncActions)
    }
}

@Composable
private fun NaviampTelevisionAppShellContent(
    modifier: Modifier = Modifier,
    uiState: NaviampAppShellUiState,
    settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    playbackProgress: StateFlow<PlaybackProgress>? = null,
    visualizerBandsProvider: () -> List<Float> = { uiState.nowPlaying?.visualizerFrame?.bands.orEmpty() },
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
) {
    val colors = NaviampTelevisionColors
    val connection = uiState.connectionSettings.connection
    val interfaceSettings = uiState.general.interfaceSettings
    val nowPlaying = uiState.nowPlaying?.let { playing ->
        playing.withDisplaySettings(
            interfaceSettings.nowPlaying,
            interfaceSettings.albumArtworkPreference(uiState.connectionSettings.currentSourceId, playing.albumId),
        )
    }
    PreloadNaviampNowPlayingArtwork(nowPlaying)
    val albumPlayerColors = rememberNaviampCoverArtPlayerColors(nowPlaying?.coverArtUrl, colors)
    val appBackground = naviampAppBackgroundUi(
        interfaceSettings = interfaceSettings,
        coverArtUrl = nowPlaying?.coverArtUrl,
        albumPlayerColors = albumPlayerColors,
        colors = colors,
    )
    val backgroundPlayerColors = animatedNaviampPlayerColors(appBackground.targetPlayerColors)
    var nowPlayingPreview by rememberSaveable { mutableStateOf(false) }
    val selectedDestination = naviampSelectedTelevisionDestination(
        selectedRoute = uiState.shellChrome.selectedRoute,
        nowPlayingOpen = uiState.shellChrome.nowPlayingOpen,
        nowPlayingPreview = nowPlayingPreview,
    )
    val navigationFocusRequesters = remember {
        NaviampTelevisionDestination.entries.associateWith { FocusRequester() }
    }
    val accountFocusRequester = remember { FocusRequester() }
    var restoreAccountFocus by remember { mutableStateOf(false) }
    var accountChooserWasOpen by remember { mutableStateOf(false) }
    val accountSelectionGeneration = uiState.connectionSettings.accountSelectionGeneration
    var observedAccountSelection by remember { mutableStateOf(accountSelectionGeneration) }
    val accountSwitcher = uiState.connectionSettings.accountSwitcher
    LaunchedEffect(accountSwitcher.visible, accountSwitcher.addingAccount, connection.connected, accountSelectionGeneration) {
        if (accountChooserWasOpen && !accountSwitcher.visible && !accountSwitcher.addingAccount && connection.connected) {
            if (observedAccountSelection != accountSelectionGeneration) {
                nowPlayingPreview = false
                withFrameNanos { }
                navigationFocusRequesters.getValue(NaviampTelevisionDestination.Home).requestFocus()
            } else {
                restoreAccountFocus = true
                withFrameNanos { }
                accountFocusRequester.requestFocus()
                restoreAccountFocus = false
            }
        }
        observedAccountSelection = accountSelectionGeneration
        accountChooserWasOpen = accountSwitcher.visible || accountSwitcher.addingAccount
    }
    var navigationFocused by remember { mutableStateOf(false) }
    var suppressedFocusActivation by remember { mutableStateOf<NaviampTelevisionDestination?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsBackgroundRoute by rememberSaveable {
        mutableStateOf(televisionSettingsBackgroundRoute(uiState.shellChrome.selectedRoute, SharedRoute.Home))
    }
    var restoreSettingsFocus by remember { mutableStateOf(false) }
    val libraryViewport = androidx.compose.runtime.key(uiState.connectionSettings.currentSourceId) {
        rememberNaviampTelevisionLibraryState()
    }
    var contentEntryDestination by remember { mutableStateOf<NaviampTelevisionDestination?>(null) }
    var contentEntryGeneration by remember { mutableStateOf(0) }
    var returnToNowPlayingFromSearch by rememberSaveable { mutableStateOf(false) }
    var restoreSearchNavigationFocus by remember { mutableStateOf(false) }
    var observedControllerDeviceId by remember {
        mutableStateOf(uiState.connect.connectedControllerDeviceId)
    }
    var observedConnected by remember { mutableStateOf(connection.connected) }
    LaunchedEffect(nowPlaying?.id) {
        if (nowPlaying == null) nowPlayingPreview = false
    }
    val televisionDestinations = naviampTelevisionDestinations(nowPlayingAvailable = nowPlaying != null)
    val navigationFocusDestination = naviampTelevisionNavigationFocusDestination(
        selected = suppressedFocusActivation ?: selectedDestination,
        settingsSelected = settingsOpen || uiState.shellChrome.selectedRoute == SharedRoute.Settings,
        destinations = televisionDestinations,
    )
    val focusNavigation: () -> Unit = {
        navigationFocusRequesters.getValue(navigationFocusDestination).requestFocus()
        Unit
    }
    LaunchedEffect(connection.connected) {
        if (connection.connected && !observedConnected) {
            settingsOpen = false
            nowPlayingPreview = false
        }
        observedConnected = connection.connected
        if (
            connection.connected &&
            !uiState.shellChrome.nowPlayingOpen &&
            uiState.home.collectionPage == null &&
            uiState.albumDetail.selectedAlbum == null &&
            uiState.artistDetail.selectedArtist == null &&
            uiState.playlistDetail.selectedPlaylist == null
        ) {
            repeat(2) { withFrameNanos { } }
            navigationFocusRequesters.getValue(navigationFocusDestination).requestFocus()
        }
    }
    LaunchedEffect(uiState.shellChrome.nowPlayingOpen, suppressedFocusActivation) {
        val destination = suppressedFocusActivation ?: return@LaunchedEffect
        if (!uiState.shellChrome.nowPlayingOpen) {
            withFrameNanos { }
            navigationFocusRequesters.getValue(destination).requestFocus()
        }
    }
    LaunchedEffect(
        uiState.shellChrome.selectedRoute,
        uiState.shellChrome.nowPlayingOpen,
        restoreSearchNavigationFocus,
    ) {
        if (
            restoreSearchNavigationFocus &&
            uiState.shellChrome.selectedRoute == SharedRoute.Search &&
            !uiState.shellChrome.nowPlayingOpen
        ) {
            suppressedFocusActivation = NaviampTelevisionDestination.Search
            withFrameNanos { }
            navigationFocusRequesters.getValue(NaviampTelevisionDestination.Search).requestFocus()
            restoreSearchNavigationFocus = false
        }
    }
    LaunchedEffect(uiState.shellChrome.selectedRoute) {
        if (uiState.shellChrome.selectedRoute == SharedRoute.Settings) {
            settingsOpen = true
            actions.navigationActions.onRouteSelected(settingsBackgroundRoute)
        } else {
            settingsBackgroundRoute = uiState.shellChrome.selectedRoute
        }
    }
    // Playback and media-identity changes must leave the active settings page and focus intact.
    LaunchedEffect(uiState.connect.connectedControllerDeviceId) {
        val connectedControllerDeviceId = uiState.connect.connectedControllerDeviceId
        val dismiss = televisionSettingsShouldDismissForControllerActivity(
            previousControllerDeviceId = observedControllerDeviceId,
            controllerDeviceId = connectedControllerDeviceId,
        )
        observedControllerDeviceId = connectedControllerDeviceId
        if (settingsOpen && dismiss) {
            settingsOpen = false
            restoreSettingsFocus = false
        }
    }
    LaunchedEffect(settingsOpen, restoreSettingsFocus) {
        if (!settingsOpen && restoreSettingsFocus) {
            withFrameNanos { }
            navigationFocusRequesters.getValue(NaviampTelevisionDestination.Settings).requestFocus()
            restoreSettingsFocus = false
        }
    }

    CompositionLocalProvider(LocalSquareAlbumArtwork provides interfaceSettings.squareAlbumArtwork) {
    NaviampFontSizeScope(
        fontSize = interfaceSettings.generalFontSize,
    ) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = colors.background,
            surface = colors.controlSurface,
            primary = colors.accent,
            onPrimary = colors.onAccent,
            onBackground = colors.primaryText,
            onSurface = colors.primaryText,
        ),
        typography = rememberNaviampTypography(interfaceSettings),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            NaviampAppBackground(
                background = appBackground,
                colors = colors,
                playerColors = backgroundPlayerColors,
            )
            TelevisionReadingSurface(colors)
            val contentInsets = Modifier.naviampContentInsets()
            Box(Modifier.fillMaxSize()) {
                when {
                    connection.restoringConnection && !connection.editingConnection -> Box(contentInsets) {
                        TelevisionStatusScreen(
                            title = stringResource(Res.string.tv_restoring_naviamp_tv),
                            message = connection.status.orEmpty(),
                            colors = colors,
                        )
                    }
                    !connection.connected || connection.editingConnection -> Box(contentInsets) {
                        TelevisionConnectionScreen(
                            uiState = uiState,
                            settingsSync = settingsSync,
                            actions = actions,
                            syncActions = syncActions,
                            colors = colors,
                        )
                    }
                    else -> {
                        val transientContentOpen = uiState.shellChrome.nowPlayingOpen ||
                            settingsOpen ||
                            uiState.home.collectionPage != null ||
                            uiState.albumDetail.selectedAlbum != null ||
                            uiState.artistDetail.selectedArtist != null ||
                            uiState.playlistDetail.selectedPlaylist != null
                        val albumDetailOpen = uiState.albumDetail.selectedAlbum != null
                        val artistDetailOpen = uiState.artistDetail.selectedArtist != null
                        val playlistDetailOpen = uiState.playlistDetail.selectedPlaylist != null
                        val homeCollectionOpen = uiState.home.collectionPage != null
                        NaviampSystemBackHandler(
                            enabled = returnToNowPlayingFromSearch || nowPlayingPreview ||
                                albumDetailOpen || artistDetailOpen || playlistDetailOpen || homeCollectionOpen ||
                                (!transientContentOpen && !navigationFocused),
                        ) {
                            if (
                                returnToNowPlayingFromSearch &&
                                uiState.shellChrome.selectedRoute == SharedRoute.Search &&
                                !albumDetailOpen && !artistDetailOpen && !playlistDetailOpen
                            ) {
                                returnToNowPlayingFromSearch = false
                                nowPlayingPreview = false
                                actions.navigationActions.onOpenNowPlaying()
                            } else if (nowPlayingPreview) {
                                nowPlayingPreview = false
                                val underlyingDestination = televisionDestinations.firstOrNull {
                                    it.route == uiState.shellChrome.selectedRoute
                                } ?: NaviampTelevisionDestination.Home
                                navigationFocusRequesters.getValue(underlyingDestination).requestFocus()
                            } else {
                                when {
                                    albumDetailOpen -> actions.albumDetailActions.onBack()
                                    artistDetailOpen -> actions.artistDetailActions.onBack()
                                    playlistDetailOpen -> actions.playlistDetailActions.onBack()
                                    homeCollectionOpen -> actions.homeActions.onCollectionBack()
                                    else -> focusNavigation()
                                }
                            }
                        }
                        val fullScreenNowPlaying = uiState.shellChrome.nowPlayingOpen && nowPlaying != null
                        val enterNavigationDestination: (NaviampTelevisionDestination) -> Unit = { destination ->
                            suppressedFocusActivation = null
                            returnToNowPlayingFromSearch = false
                            if (destination == NaviampTelevisionDestination.NowPlaying) {
                                contentEntryDestination = null
                                nowPlayingPreview = true
                                actions.navigationActions.onOpenNowPlaying()
                            } else {
                                nowPlayingPreview = false
                                actions.navigationActions.onCloseNowPlaying()
                                destination.route?.let(actions.navigationActions.onRouteSelected)
                                contentEntryDestination = destination
                                contentEntryGeneration += 1
                            }
                        }
                        val playerTransition = updateTransition(fullScreenNowPlaying, label = "TV Now Playing fullscreen")
                        // Window-owned rasters cannot inherit the incoming and outgoing Compose transforms.
                        val nativeWaveform = !playerTransition.isRunning &&
                            playerTransition.currentState == playerTransition.targetState
                        playerTransition.AnimatedContent(
                            transitionSpec = {
                                (fadeIn(tween(520, easing = FastOutSlowInEasing)) +
                                    scaleIn(tween(520, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                                    slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 10 })
                                    .togetherWith(
                                        fadeOut(tween(440, easing = FastOutSlowInEasing)) +
                                            scaleOut(tween(440, easing = FastOutSlowInEasing), targetScale = 1.04f) +
                                            slideOutVertically(tween(440, easing = FastOutSlowInEasing)) { -it / 14 },
                                    )
                            },
                        ) { fullscreen ->
                            if (fullscreen && nowPlaying != null) {
                                NaviampFontSizeScope(interfaceSettings.nowPlayingFontSize, interfaceSettings.generalFontSize) {
                                    TelevisionNowPlaying(
                                        nowPlaying = nowPlaying,
                                        playbackProgress = playbackProgress,
                                        colors = colors,
                                        playerColors = backgroundPlayerColors,
                                        actions = actions.nowPlayingActions,
                                        onClose = {
                                            nowPlayingPreview = true
                                            suppressedFocusActivation = NaviampTelevisionDestination.NowPlaying
                                            actions.navigationActions.onCloseNowPlaying()
                                        },
                                        onOpenSettings = { settingsOpen = true },
                                        displaySettings = interfaceSettings.nowPlaying,
                                        contentInsets = contentInsets,
                                        nativeWaveform = nativeWaveform,
                                        showWaveform = fullScreenNowPlaying,
                                    )
                                }
                            } else Column(modifier = Modifier.fillMaxSize().then(contentInsets)) {
                                TelevisionNavigationBar(
                                    destinations = televisionDestinations,
                                    focusDestination = navigationFocusDestination,
                                    navigationFocused = navigationFocused,
                                    colors = colors,
                                    focusRequesters = navigationFocusRequesters,
                                    onFocusChanged = { navigationFocused = it },
                                    onFocused = { destination ->
                                        if (suppressedFocusActivation == destination) return@TelevisionNavigationBar
                                        suppressedFocusActivation = null
                                        if (destination != NaviampTelevisionDestination.Search) {
                                            returnToNowPlayingFromSearch = false
                                        }
                                        if (destination == NaviampTelevisionDestination.NowPlaying) {
                                            nowPlayingPreview = true
                                        } else {
                                            nowPlayingPreview = false
                                            actions.navigationActions.onCloseNowPlaying()
                                            destination.route
                                                ?.takeIf { televisionNavigationFocusChangesRoute(it, uiState.shellChrome.selectedRoute) }
                                                ?.let(actions.navigationActions.onRouteSelected)
                                        }
                                    },
                                    onClicked = enterNavigationDestination,
                                    onEnterContent = enterNavigationDestination,
                                    onSettingsSelected = {
                                        contentEntryDestination = null
                                        returnToNowPlayingFromSearch = false
                                        settingsOpen = true
                                    },
                                    account = connection.savedConnections.firstOrNull { it.current },
                                    accountFocusRequester = accountFocusRequester,
                                    restoreAccountFocus = restoreAccountFocus,
                                    onAccountsSelected = {
                                        settingsOpen = false
                                        actions.connectionActions.onOpenAccounts()
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp, vertical = 18.dp),
                                ) {
                                    if (nowPlayingPreview && nowPlaying != null) {
                                        NaviampFontSizeScope(interfaceSettings.nowPlayingFontSize, interfaceSettings.generalFontSize) {
                                            TelevisionNowPlaying(
                                                nowPlaying = nowPlaying,
                                                playbackProgress = playbackProgress,
                                                colors = colors,
                                                actions = actions.nowPlayingActions,
                                                interactive = false,
                                                onClose = { nowPlayingPreview = false },
                                                onOpenSettings = { settingsOpen = true },
                                                displaySettings = interfaceSettings.nowPlaying,
                                                nativeWaveform = nativeWaveform,
                                                showWaveform = !fullScreenNowPlaying,
                                            )
                                        }
                                    } else {
                                        TelevisionConnectedContent(
                                            colors = colors,
                                            uiState = uiState,
                                            playbackProgress = playbackProgress,
                                            visualizerBandsProvider = visualizerBandsProvider,
                                            settingsSync = settingsSync,
                                            actions = actions,
                                            syncActions = syncActions,
                                            libraryViewport = libraryViewport,
                                            onNavigationActivationSuppressed = { suppressedFocusActivation = it },
                                            topNavigationFocusRequester = navigationFocusRequesters.getValue(
                                                navigationFocusDestination,
                                            ),
                                            contentEntryDestination = contentEntryDestination,
                                            contentEntryGeneration = contentEntryGeneration,
                                            onContentEntryHandled = { generation ->
                                                if (generation == contentEntryGeneration) {
                                                    contentEntryDestination = null
                                                }
                                            },
                                        )
                                    }
                                }
                                if (nowPlaying != null && !nowPlayingPreview) {
                                    NaviampFontSizeScope(interfaceSettings.nowPlayingFontSize, interfaceSettings.generalFontSize) {
                                        TelevisionMiniPlayer(
                                            nowPlaying = nowPlaying,
                                            colors = colors,
                                            modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (settingsOpen) Box(Modifier.fillMaxSize().then(contentInsets)) {
                            TelevisionSettingsSheet(
                                uiState = uiState,
                                colors = colors,
                                actions = actions,
                                onDismiss = {
                                    settingsOpen = false
                                    restoreSettingsFocus = !uiState.shellChrome.nowPlayingOpen
                                },
                            )
                        }
                    }
                }
            }
            NaviampAccountSwitcher(uiState.connectionSettings, actions.connectionActions, colors)
        }
    }
    }
    }
}

internal fun televisionSettingsBackgroundRoute(
    selectedRoute: SharedRoute,
    lastVisibleRoute: SharedRoute,
): SharedRoute = if (selectedRoute == SharedRoute.Settings) lastVisibleRoute else selectedRoute

internal fun televisionNavigationFocusChangesRoute(
    focusedRoute: SharedRoute,
    selectedRoute: SharedRoute,
): Boolean = focusedRoute != selectedRoute

internal fun televisionSettingsShouldDismissForControllerActivity(
    previousControllerDeviceId: String?,
    controllerDeviceId: String?,
): Boolean = controllerDeviceId != null && controllerDeviceId != previousControllerDeviceId

@Composable
private fun TelevisionNavigationBar(
    destinations: List<NaviampTelevisionDestination>,
    focusDestination: NaviampTelevisionDestination,
    navigationFocused: Boolean,
    colors: NaviampColors,
    focusRequesters: Map<NaviampTelevisionDestination, FocusRequester>,
    onFocusChanged: (Boolean) -> Unit,
    onFocused: (NaviampTelevisionDestination) -> Unit,
    onClicked: (NaviampTelevisionDestination) -> Unit,
    onEnterContent: (NaviampTelevisionDestination) -> Unit,
    onSettingsSelected: () -> Unit,
    account: NaviampSavedConnectionUi?,
    accountFocusRequester: FocusRequester,
    restoreAccountFocus: Boolean,
    onAccountsSelected: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.requiredSize(44.dp).background(Color.Black, CircleShape),
        ) {
            Image(
                painter = naviampAppIconPainter(),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
        ) {
            items(
                items = destinations,
                key = { it.name },
            ) { destination ->
                TelevisionNavigationButton(
                    destination = destination,
                    colors = colors,
                    modifier = Modifier
                        .focusRequester(focusRequesters.getValue(destination))
                        .focusProperties {
                            canFocus = navigationFocused ||
                                destination == focusDestination
                        },
                    onClick = { onClicked(destination) },
                    onFocused = { onFocused(destination) },
                    onDown = { onEnterContent(destination) },
                )
            }
        }
        NaviampAccountNavigationButton(account, colors, accountFocusRequester,
            canFocus = navigationFocused || restoreAccountFocus, onClick = onAccountsSelected)
        Spacer(Modifier.width(8.dp))
        TelevisionNavigationIconButton(
            icon = NaviampIcons.Settings,
            description = stringResource(Res.string.nav_settings),
            colors = colors,
            focusRequester = focusRequesters.getValue(NaviampTelevisionDestination.Settings),
            canFocus = navigationFocused || focusDestination == NaviampTelevisionDestination.Settings,
            onClick = onSettingsSelected,
            onFocused = {},
        )
    }
}

@Composable
private fun TelevisionNavigationIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    colors: NaviampColors,
    focusRequester: FocusRequester,
    canFocus: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .requiredSize(48.dp)
            .focusRequester(focusRequester)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                val gainedFocus = it.isFocused && !focused
                focused = it.isFocused
                if (gainedFocus) onFocused()
            }
            .background(if (focused) colors.primaryText else colors.controlSurface, RoundedCornerShape(999.dp)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (focused) colors.background else colors.primaryText,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
private fun TelevisionConnectedContent(
    colors: NaviampColors,
    uiState: NaviampAppShellUiState,
    playbackProgress: StateFlow<PlaybackProgress>?,
    visualizerBandsProvider: () -> List<Float>,
    settingsSync: NaviampSettingsSyncUi,
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
    libraryViewport: NaviampTelevisionLibraryState,
    onNavigationActivationSuppressed: (NaviampTelevisionDestination) -> Unit,
    topNavigationFocusRequester: FocusRequester,
    contentEntryDestination: NaviampTelevisionDestination?,
    contentEntryGeneration: Int,
    onContentEntryHandled: (Int) -> Unit,
) {
    val selectedContentDestination = naviampSelectedTelevisionDestination(
        uiState.shellChrome.selectedRoute,
        nowPlayingOpen = false,
        nowPlayingPreview = false,
    ) ?: NaviampTelevisionDestination.Home
    val televisionMediaActions = actions.mediaActions.copy(
        onMediaItemAction = { request ->
            onNavigationActivationSuppressed(naviampTelevisionVisibleOwner(selectedContentDestination))
            actions.mediaActions.onMediaItemAction(request)
        },
    )
    val artistId = uiState.artistDetail.selectedArtist?.id
    val artistViewport = key(artistId) { rememberSaveableStateHolder() }
    val playlistId = uiState.playlistDetail.selectedPlaylist?.id
    var playlistReturnFocusId by remember(uiState.connectionSettings.currentSourceId) { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedContentDestination, playlistId, uiState.connectionSettings.currentSourceId) {
        if (selectedContentDestination != NaviampTelevisionDestination.Playlists) {
            playlistReturnFocusId = null
        } else if (playlistId != null) {
            playlistReturnFocusId = playlistId
        }
    }
    when {
        uiState.albumDetail.selectedAlbum != null -> TelevisionAlbumDetail(
            screen = uiState.albumDetail,
            colors = colors,
            actions = actions.albumDetailActions,
            topNavigationFocusRequester = topNavigationFocusRequester,
        )
        artistId != null -> artistViewport.SaveableStateProvider(artistId) {
            TelevisionArtistDetail(
                screen = uiState.artistDetail,
                colors = colors,
                actions = actions.artistDetailActions,
                showAlbumYear = uiState.general.interfaceSettings.nowPlaying.showAlbumYear,
                albumSortOrder = uiState.general.interfaceSettings.albumSortOrder,
                groupAlbumsByReleaseType = uiState.general.interfaceSettings.groupAlbumsByReleaseType,
                onAlbumOpening = { onNavigationActivationSuppressed(selectedContentDestination) },
                topNavigationFocusRequester = topNavigationFocusRequester,
            )
        }
        playlistId != null -> TelevisionPlaylistDetail(
            screen = uiState.playlistDetail,
            colors = colors,
            actions = actions.playlistDetailActions,
            topNavigationFocusRequester = topNavigationFocusRequester,
        )
        uiState.home.collectionPage != null -> TelevisionHomeCollection(
            page = uiState.home.collectionPage,
            colors = colors,
            actions = actions.homeActions,
            mediaActions = televisionMediaActions,
            topNavigationFocusRequester = topNavigationFocusRequester,
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Home -> TelevisionHome(
            home = uiState.home,
            discoveryPlayback = uiState.home.samplePlayback,
            colors = colors,
            actions = actions.homeActions,
            mediaActions = televisionMediaActions,
            entryFocusGeneration = contentEntryGeneration.takeIf {
                contentEntryDestination == NaviampTelevisionDestination.Home
            },
            onEntryFocusHandled = onContentEntryHandled,
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Library -> TelevisionLibrary(
            screen = uiState.library,
            colors = colors,
            actions = actions.libraryActions,
            mediaActions = televisionMediaActions,
            viewport = libraryViewport,
            topNavigationFocusRequester = topNavigationFocusRequester,
            entryFocusGeneration = contentEntryGeneration.takeIf {
                contentEntryDestination == NaviampTelevisionDestination.Library
            },
            onEntryFocusHandled = onContentEntryHandled,
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Search -> TelevisionSearch(
            screen = uiState.search,
            colors = colors,
            actions = actions.searchActions,
            mediaActions = televisionMediaActions,
            entryFocusGeneration = contentEntryGeneration.takeIf {
                contentEntryDestination == NaviampTelevisionDestination.Search
            },
            onEntryFocusHandled = onContentEntryHandled,
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Playlists -> TelevisionPlaylists(
            screen = uiState.playlists,
            colors = colors,
            actions = actions.playlistsActions,
            mediaActions = televisionMediaActions,
            topNavigationFocusRequester = topNavigationFocusRequester,
            returnFocusPlaylistId = playlistReturnFocusId,
            onReturnFocusHandled = { playlistReturnFocusId = null },
            entryFocusGeneration = contentEntryGeneration.takeIf {
                contentEntryDestination == NaviampTelevisionDestination.Playlists
            },
            onEntryFocusHandled = onContentEntryHandled,
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Radio -> TelevisionInternetRadio(
            screen = uiState.radio,
            colors = colors,
            actions = actions.radioActions,
            topNavigationFocusRequester = topNavigationFocusRequester,
            entryFocusGeneration = contentEntryGeneration.takeIf {
                contentEntryDestination == NaviampTelevisionDestination.Radio
            },
            onEntryFocusHandled = onContentEntryHandled,
        )
        else -> ConnectedContent(
            colors = colors,
            uiState = uiState,
            showFullNowPlaying = false,
            playbackProgress = playbackProgress,
            visualizerBandsProvider = visualizerBandsProvider,
            settingsSync = settingsSync,
            actions = actions,
            syncActions = syncActions,
        )
    }
}

@Composable
internal fun TelevisionNavigationButton(
    destination: NaviampTelevisionDestination,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onDown: (() -> Unit)? = null,
) {
    val inputModeManager = LocalInputModeManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) colors.primaryText else Color.Transparent,
            contentColor = if (focused) colors.background else colors.secondaryText,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        shape = shape,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (onDown != null && naviampTelevisionNavigationEntersContent(event.key, event.type)) {
                    // We consume this direction before Compose's default traversal can switch modes.
                    // System-defined content cards otherwise reject focus after native text/pointer input.
                    inputModeManager.requestInputMode(InputMode.Keyboard)
                    onDown()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged {
                val gainedFocus = it.isFocused && !focused
                focused = it.isFocused
                if (gainedFocus) onFocused()
            },
    ) {
        Text(
            text = televisionDestinationLabel(destination),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TelevisionConnectionScreen(
    uiState: NaviampAppShellUiState,
    settingsSync: NaviampSettingsSyncUi,
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
    colors: NaviampColors,
) {
    val connectionSettings = uiState.connectionSettings
    val connection = connectionSettings.connection
    val addingAccount = connectionSettings.accountSwitcher.addingAccount
    val canCancel = connection.connected || addingAccount
    NaviampSystemBackHandler(enabled = canCancel && connection.editingConnection) {
        actions.connectionActions.onCancelConnectionForm()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 72.dp, vertical = 40.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .widthIn(max = 820.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            TelevisionSetupIntro(
                addingAccount = addingAccount,
                connect = uiState.connect,
                actions = actions.connectActions,
                colors = colors,
            )
            if (!addingAccount && connection.savedConnections.isNotEmpty()) Button(
                onClick = actions.connectionActions.onOpenAccounts,
            ) { Text(stringResource(Res.string.tv_accounts)) }
            if (connectionSettings.accountSwitcher.error != null) Text(
                stringResource(Res.string.tv_account_switch_failed), color = colors.primaryText,
            )
            NaviampConnectionForm(
                form = connection.form,
                colors = colors,
                isReconnect = connection.editingSavedConnection,
                saveWithoutReconnect = connection.saveWithoutReconnect,
                isConnecting = connection.isConnecting,
                connectionStatus = connection.status,
                connectionStatusIsError = connection.statusIsError,
                connectionErrorOccurrence = connection.errorOccurrence,
                availableMusicFolders = connection.availableMusicFolders,
                musicFoldersStatus = connection.musicFoldersStatus,
                capabilities = connectionSettings.capabilities,
                allowLocalFileInputs = false,
                allowFallbackUrls = false,
                settingsSyncStatus = settingsSync.status,
                onFormChanged = actions.connectionActions.onFormChanged,
                onConnect = actions.connectionActions.onConnect,
                onImportSettingsSyncFile = null,
                onCancel = actions.connectionActions.onCancelConnectionForm.takeIf { canCancel },
            )
        }
    }
}

/** Title, the phone/computer code box, then the direct-setup alternative and keyboard hint. */
@Composable
internal fun TelevisionSetupIntro(
    addingAccount: Boolean,
    connect: NaviampConnectSettingsUi,
    actions: NaviampConnectSettingsActions?,
    colors: NaviampColors,
) {
    Text(stringResource(if (addingAccount) Res.string.tv_account_add else Res.string.tv_set_up_naviamp_tv),
        color = colors.primaryText, fontSize = 36.sp, fontWeight = FontWeight.Black)
    if (!addingAccount) {
        TelevisionFirstRunConnectSetup(connect = connect, actions = actions, colors = colors)
        // "Or …" names the alternative, so it follows the code box it is an alternative to.
        Text(stringResource(Res.string.tv_direct_setup_or), color = colors.secondaryText, fontSize = 18.sp)
    }
    Text(stringResource(Res.string.tv_keyboard_navigation_hint), color = colors.mutedText, fontSize = 14.sp)
}

@Composable
internal fun TelevisionFirstRunConnectSetup(
    connect: NaviampConnectSettingsUi,
    actions: NaviampConnectSettingsActions?,
    colors: NaviampColors,
) {
    if (!connect.available || !connect.canAdvertise || actions == null) return
    // A fresh TV shows its code as soon as setup is on screen; leaving setup ends the offer.
    val start by rememberUpdatedState(actions.onStartPairingMode)
    val stop by rememberUpdatedState(actions.onStopPairingMode)
    NaviampWhileVisibleEffect(onVisible = { start() }, onHidden = { stop() })
    // The box takes the remote's first focus, so setup opens scrolled to the code and Up returns to it.
    val codeBoxFocus = remember { FocusRequester() }
    var codeBoxFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { codeBoxFocus.requestFocus() } }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tv-connect-code-box")
            .focusRequester(codeBoxFocus)
            .onFocusChanged { codeBoxFocused = it.isFocused }
            .focusable()
            .background(colors.controlSurface.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .then(if (codeBoxFocused) Modifier.border(2.dp, colors.primaryText, RoundedCornerShape(14.dp)) else Modifier)
            .padding(16.dp),
    ) {
        Text(stringResource(Res.string.tv_set_up_from_a_phone_or_computer), color = colors.primaryText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(Res.string.connect_show_code_instructions),
            color = colors.secondaryText,
            fontSize = 15.sp,
        )
        connect.pairingCode?.let { code ->
            Text(
                formatNaviampConnectPairingCode(code),
                color = colors.primaryText,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        Text(connect.localDeviceName, color = colors.secondaryText, fontSize = 15.sp)
        connect.recovery?.let { recovery ->
            NaviampConnectRecoveryPanel(recovery, colors, actions.onRetryConnection,
                actions.onOpenPermissionSettings, television = true)
        } ?: run {
            connect.displayStatus()?.let { Text(it, color = colors.secondaryText, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun TelevisionStatusScreen(
    title: String,
    message: String,
    colors: NaviampColors,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(48.dp),
    ) {
        Text(title, color = colors.primaryText, fontSize = 36.sp, fontWeight = FontWeight.Black)
        if (message.isNotBlank()) {
            Text(message, color = colors.secondaryText, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
