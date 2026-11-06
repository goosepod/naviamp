package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput
import app.naviamp.domain.provider.DiscoveryBrowseAction

/** One visible-destination policy for native phone Back and shared mouse navigation. */
enum class NaviampBackTarget { NowPlaying, DiscoveryRequests, DiscoveryArtist, DiscoveryBrowser, HomeCollection, Playlist, Album, Artist }

fun naviampBackTarget(shell: NaviampAppShellUiState): NaviampBackTarget? = when {
    shell.shellChrome.nowPlayingOpen && !shell.shellChrome.playerDocked -> NaviampBackTarget.NowPlaying
    shell.shellChrome.selectedRoute == SharedRoute.Home && shell.home.providerDiscovery?.requestsOpen == true -> NaviampBackTarget.DiscoveryRequests
    shell.shellChrome.selectedRoute == SharedRoute.Home && shell.home.providerDiscovery?.let { it.artistPage && it.artistDetail != null } == true -> NaviampBackTarget.DiscoveryArtist
    shell.shellChrome.selectedRoute == SharedRoute.Home && shell.home.providerDiscovery?.browser != null -> NaviampBackTarget.DiscoveryBrowser
    shell.shellChrome.selectedRoute == SharedRoute.Home && shell.home.collectionPage != null -> NaviampBackTarget.HomeCollection
    shell.playlistDetail.selectedPlaylist != null -> NaviampBackTarget.Playlist
    shell.albumDetail.selectedAlbum != null -> NaviampBackTarget.Album
    shell.artistDetail.selectedArtist != null -> NaviampBackTarget.Artist
    shell.shellChrome.nowPlayingOpen -> NaviampBackTarget.NowPlaying
    else -> null
}

internal fun performNaviampBack(target: NaviampBackTarget, actions: NaviampAppShellActions) = when (target) {
    NaviampBackTarget.NowPlaying -> actions.navigationActions.onCloseNowPlaying()
    NaviampBackTarget.DiscoveryRequests -> actions.homeActions.onDiscoveryRequestsDismiss()
    NaviampBackTarget.DiscoveryArtist -> actions.homeActions.onDiscoveryArtistDismiss()
    NaviampBackTarget.DiscoveryBrowser -> actions.homeActions.onDiscoveryBrowse(DiscoveryBrowseAction.Close)
    NaviampBackTarget.HomeCollection -> actions.homeActions.onCollectionBack()
    NaviampBackTarget.Playlist -> actions.playlistDetailActions.onBack()
    NaviampBackTarget.Album -> actions.albumDetailActions.onBack()
    NaviampBackTarget.Artist -> actions.artistDetailActions.onBack()
}

/** React only to the press edge; held buttons and pointer moves never pop further pages. */
@Composable
internal fun Modifier.naviampMouseBack(onBack: () -> Unit): Modifier {
    val latest by rememberUpdatedState(onBack)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            var pressed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.buttons.isBackPressed
                if (down && !pressed && event.changes.none { it.isConsumed }) {
                    latest()
                    event.changes.forEach { it.consume() }
                }
                pressed = down
            }
        }
    }
}

@Composable
internal fun NaviampBackNavigation(shell: NaviampAppShellUiState, actions: NaviampAppShellActions,
    modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val dispatcher = LocalNaviampSystemBackDispatcher.current ?: remember { NaviampSystemBackDispatcher() }
    val target = naviampBackTarget(shell)
    CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
        NaviampSystemBackHandler(target != null) { target?.let { performNaviampBack(it, actions) } }
        Box(modifier.fillMaxSize().naviampMouseBack { dispatcher.currentHandler?.invoke() }) {
            content(Modifier.fillMaxSize())
        }
    }
}
