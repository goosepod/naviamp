package app.naviamp.ui

import app.naviamp.ui.generated.resources.connect_setup_code_consent
import org.jetbrains.compose.resources.stringResource
import app.naviamp.ui.generated.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
    val colors = NaviampTelevisionColors
    val connection = uiState.connectionSettings.connection
    val interfaceSettings = uiState.general.interfaceSettings
    val nowPlaying = uiState.nowPlaying?.withDisplaySettings(interfaceSettings.nowPlaying)
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
    var observedProvisioningController by remember {
        mutableStateOf(uiState.connect.pendingProvisioningControllerName)
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
    LaunchedEffect(
        uiState.connect.connectedControllerDeviceId,
        uiState.connect.pendingProvisioningControllerName,
    ) {
        val connectedControllerDeviceId = uiState.connect.connectedControllerDeviceId
        val provisioningController = uiState.connect.pendingProvisioningControllerName
        val dismiss = televisionSettingsShouldDismissForControllerActivity(
            previousControllerDeviceId = observedControllerDeviceId,
            controllerDeviceId = connectedControllerDeviceId,
            previousProvisioningController = observedProvisioningController,
            provisioningController = provisioningController,
        )
        observedControllerDeviceId = connectedControllerDeviceId
        observedProvisioningController = provisioningController
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

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = colors.background,
            surface = colors.controlSurface,
            primary = colors.accent,
            onPrimary = colors.onAccent,
            onBackground = colors.primaryText,
            onSurface = colors.primaryText,
        ),
        typography = rememberNaviampTypography(),
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
            when {
                connection.restoringConnection && !connection.editingConnection -> TelevisionStatusScreen(
                    title = stringResource(Res.string.tv_restoring_naviamp_tv),
                    message = connection.status.orEmpty(),
                    colors = colors,
                )
                !connection.connected || connection.editingConnection -> TelevisionConnectionScreen(
                    uiState = uiState,
                    settingsSync = settingsSync,
                    actions = actions,
                    syncActions = syncActions,
                    colors = colors,
                )
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
                    val internetRadioOpen = uiState.shellChrome.selectedRoute == SharedRoute.Radio
                    NaviampSystemBackHandler(
                        enabled = returnToNowPlayingFromSearch || nowPlayingPreview ||
                            albumDetailOpen || artistDetailOpen || playlistDetailOpen || homeCollectionOpen ||
                            internetRadioOpen ||
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
                                internetRadioOpen -> actions.navigationActions.onRouteSelected(SharedRoute.Library)
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
                    AnimatedContent(
                        targetState = fullScreenNowPlaying,
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
                        label = "TV Now Playing fullscreen transition",
                    ) { fullscreen ->
                        if (fullscreen && nowPlaying != null) TelevisionNowPlaying(
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
                        ) else Column(modifier = Modifier.fillMaxSize()) {
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
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 36.dp, vertical = 18.dp),
                            ) {
                                if (nowPlayingPreview && nowPlaying != null) {
                                    TelevisionNowPlaying(
                                        nowPlaying = nowPlaying,
                                        playbackProgress = playbackProgress,
                                        colors = colors,
                                        actions = actions.nowPlayingActions,
                                        interactive = false,
                                        onClose = { nowPlayingPreview = false },
                                        onOpenSettings = { settingsOpen = true },
                                    )
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
                                TelevisionMiniPlayer(
                                    nowPlaying = nowPlaying,
                                    colors = colors,
                                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    if (settingsOpen) {
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
    previousProvisioningController: String?,
    provisioningController: String?,
): Boolean =
    (controllerDeviceId != null && controllerDeviceId != previousControllerDeviceId) ||
        (previousProvisioningController != null && provisioningController == null)

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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Naviamp",
            color = colors.primaryText,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.padding(end = 14.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEach { destination ->
                TelevisionNavigationButton(
                    destination = destination,
                    colors = colors,
                    modifier = Modifier
                        .widthIn(min = 128.dp)
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
        Spacer(Modifier.weight(1f))
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
    when {
        uiState.albumDetail.selectedAlbum != null -> TelevisionAlbumDetail(
            screen = uiState.albumDetail,
            colors = colors,
            actions = actions.albumDetailActions,
            topNavigationFocusRequester = topNavigationFocusRequester,
        )
        uiState.artistDetail.selectedArtist != null -> TelevisionArtistDetail(
            screen = uiState.artistDetail,
            colors = colors,
            actions = actions.artistDetailActions,
            showAlbumYear = uiState.general.interfaceSettings.nowPlaying.showAlbumYear,
            albumSortOrder = uiState.general.interfaceSettings.albumSortOrder,
            groupAlbumsByReleaseType = uiState.general.interfaceSettings.groupAlbumsByReleaseType,
            onAlbumOpening = {
                onNavigationActivationSuppressed(
                    naviampSelectedTelevisionDestination(uiState.shellChrome.selectedRoute, false, false)
                        ?: NaviampTelevisionDestination.Home,
                )
            },
            topNavigationFocusRequester = topNavigationFocusRequester,
        )
        uiState.playlistDetail.selectedPlaylist != null -> TelevisionPlaylistDetail(
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
            onOpenPlaylists = {
                onNavigationActivationSuppressed(
                    naviampTelevisionVisibleOwner(NaviampTelevisionDestination.Playlists),
                )
                actions.navigationActions.onRouteSelected(SharedRoute.Playlists)
            },
            onOpenInternetRadio = {
                onNavigationActivationSuppressed(NaviampTelevisionDestination.Library)
                actions.navigationActions.onRouteSelected(SharedRoute.Radio)
            },
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
        )
        else -> ConnectedContent(
            colors = colors,
            uiState = uiState,
            playbackProgress = playbackProgress,
            visualizerBandsProvider = visualizerBandsProvider,
            settingsSync = settingsSync,
            actions = actions,
            syncActions = syncActions,
        )
    }
}

@Composable
private fun TelevisionNavigationButton(
    destination: NaviampTelevisionDestination,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) colors.primaryText else Color.Transparent,
            contentColor = if (focused) colors.background else colors.secondaryText,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        shape = shape,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (onDown != null && naviampTelevisionNavigationEntersContent(event.key, event.type)) {
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
    NaviampSystemBackHandler(enabled = connection.connected && connection.editingConnection) {
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
            Text(stringResource(Res.string.tv_set_up_naviamp_tv), color = colors.primaryText, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Text(
                stringResource(Res.string.tv_direct_setup_description),
                color = colors.secondaryText,
                fontSize = 18.sp,
            )
            Text(
                stringResource(Res.string.tv_keyboard_navigation_hint),
                color = colors.mutedText,
                fontSize = 14.sp,
            )
            TelevisionFirstRunConnectSetup(
                connect = uiState.connect,
                actions = actions.connectActions,
                colors = colors,
            )
            NaviampConnectionForm(
                form = connection.form,
                colors = colors,
                isReconnect = connection.editingSavedConnection,
                isConnecting = connection.isConnecting,
                connectionStatus = connection.status,
                connectionStatusIsError = connection.statusIsError,
                availableMusicFolders = connection.availableMusicFolders,
                musicFoldersStatus = connection.musicFoldersStatus,
                capabilities = connectionSettings.capabilities,
                allowLocalFileInputs = false,
                allowFallbackUrls = false,
                settingsSyncStatus = settingsSync.status,
                onFormChanged = actions.connectionActions.onFormChanged,
                onConnect = actions.connectionActions.onConnect,
                onImportSettingsSyncFile = null,
                onCancel = actions.connectionActions.onCancelConnectionForm.takeIf { connection.connected },
            )
        }
    }
}

@Composable
private fun TelevisionFirstRunConnectSetup(
    connect: NaviampConnectSettingsUi,
    actions: NaviampConnectSettingsActions?,
    colors: NaviampColors,
) {
    if (!connect.available || !connect.canAdvertise || actions == null) return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlSurface.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(stringResource(Res.string.tv_set_up_from_a_phone_or_computer), color = colors.primaryText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(Res.string.connect_setup_code_consent),
            color = colors.secondaryText,
            fontSize = 15.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TelevisionTextButton(
                label = if (connect.pairingActive) stringResource(Res.string.tv_stop_pairing) else stringResource(Res.string.tv_show_pairing_code),
                colors = colors,
                calmFocus = true,
                onClick = if (connect.pairingActive) actions.onStopPairingMode else actions.onStartPairingMode,
            )
            connect.pairingCode?.let { code ->
                Text(
                    formatNaviampConnectPairingCode(code),
                    color = colors.primaryText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        connect.recovery?.let { recovery ->
            NaviampConnectRecoveryPanel(recovery, colors, actions.onRetryConnection,
                actions.onOpenPermissionSettings, television = true)
        } ?: run {
            connect.displayStatus()?.let { Text(it, color = colors.secondaryText, fontSize = 14.sp) }
        }
        if (connect.pairingPhase == NaviampConnectPairingUiPhase.AwaitingApproval) {
            Text(
                stringResource(Res.string.tv_controller_asking_to_pair, connect.pendingControllerName ?: stringResource(Res.string.tv_controller_fallback)),
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelevisionTextButton(stringResource(Res.string.tv_approve_controller), colors, calmFocus = true, onClick = actions.onApproveController)
                TelevisionTextButton(stringResource(Res.string.tv_reject), colors, calmFocus = true, onClick = actions.onRejectController)
            }
        }
        connect.pendingProvisioningConnectionName?.let { connectionName ->
            Text(
                stringResource(Res.string.tv_controller_can_set_up, connect.pendingProvisioningControllerName ?: stringResource(Res.string.tv_paired_controller_fallback), connectionName),
                color = colors.primaryText,
                fontSize = 16.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelevisionTextButton(stringResource(Res.string.tv_set_up_server), colors, calmFocus = true, onClick = actions.onApproveProvisioning)
                TelevisionTextButton(stringResource(Res.string.tv_reject), colors, calmFocus = true, onClick = actions.onRejectProvisioning)
            }
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
