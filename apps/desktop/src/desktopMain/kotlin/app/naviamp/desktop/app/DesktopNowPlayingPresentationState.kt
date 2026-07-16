package app.naviamp.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.provider.CoverArtSize
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.DefaultSingleColorHex
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.NaviampPlayerColors
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NaviampRadioArtworkLookupEffect
import app.naviamp.ui.effectiveNowPlayingCoverArtUrl
import app.naviamp.ui.isNaviampVisualizerVisible
import app.naviamp.ui.naviampColorFromHex
import app.naviamp.ui.naviampVisualizerFromName
import app.naviamp.ui.rememberPlatformCoverArtPlayerColors

internal class DesktopNowPlayingPresentationState(
    initialVisualizerSettings: VisualizerSettings,
) {
    var visualizerFrame by mutableStateOf<PlaybackVisualizerFrame?>(null)
        private set
    var visualizerRequestedVisible by mutableStateOf(false)
        private set
    var selectedVisualizer by mutableStateOf(naviampVisualizerFromName(initialVisualizerSettings.selectedVisualizer))
        private set
    var radioTrackArtworkByKey by mutableStateOf<Map<String, String?>>(emptyMap())
        private set

    var effectiveCoverArtUrl by mutableStateOf<String?>(null)
        private set
    var targetBackgroundColors by mutableStateOf(NaviampPlayerColors.solid(Color.Transparent))
        private set
    var backgroundStart by mutableStateOf(Color.Transparent)
        private set
    var backgroundMid by mutableStateOf(Color.Transparent)
        private set
    var backgroundEnd by mutableStateOf(Color.Transparent)
        private set

    fun updateVisualizerFrame(frame: PlaybackVisualizerFrame?) {
        visualizerFrame = frame
    }

    fun toggleVisualizer() {
        visualizerRequestedVisible = !visualizerRequestedVisible
    }

    fun selectVisualizer(visualizer: NaviampVisualizer) {
        selectedVisualizer = visualizer
        visualizerRequestedVisible = true
    }

    fun isVisualizerVisible(playbackState: PlaybackState): Boolean =
        isNaviampVisualizerVisible(visualizerRequestedVisible, playbackState)

    fun applyRadioArtwork(key: String, artworkUrl: String?) {
        radioTrackArtworkByKey = radioTrackArtworkByKey + (key to artworkUrl)
    }

    internal fun updateDerived(
        effectiveCoverArtUrl: String?,
        targetBackgroundColors: NaviampPlayerColors,
        backgroundStart: Color,
        backgroundMid: Color,
        backgroundEnd: Color,
    ) {
        this.effectiveCoverArtUrl = effectiveCoverArtUrl
        this.targetBackgroundColors = targetBackgroundColors
        this.backgroundStart = backgroundStart
        this.backgroundMid = backgroundMid
        this.backgroundEnd = backgroundEnd
    }
}

@Composable
internal fun rememberDesktopNowPlayingPresentationState(
    initialVisualizerSettings: VisualizerSettings,
    appColors: DesktopAppColors,
    interfaceSettings: InterfaceSettings,
    currentCoverArtUrl: String?,
    nowPlayingTrack: Track?,
    nowPlayingStation: InternetRadioStation?,
    streamMetadata: PlaybackStreamMetadata,
    provider: NavidromeProvider?,
): DesktopNowPlayingPresentationState {
    val state = remember { DesktopNowPlayingPresentationState(initialVisualizerSettings) }
    val heroCoverArtUrl = nowPlayingTrack?.coverArtId
        ?.let { provider?.coverArtUrl(it, CoverArtSize.Hero) }
        ?: currentCoverArtUrl
    val effectiveCoverArtUrl = effectiveNowPlayingCoverArtUrl(
        currentCoverArtUrl = heroCoverArtUrl,
        nowPlayingTrack = nowPlayingTrack,
        nowPlayingStation = nowPlayingStation,
        streamMetadata = streamMetadata,
        radioTrackArtworkByKey = state.radioTrackArtworkByKey,
    )
    val coverArtPlayerColors = rememberPlatformCoverArtPlayerColors(effectiveCoverArtUrl, appColors)
    val targetBackgroundColors = when (interfaceSettings.appBackgroundStyle) {
        AppBackgroundStyle.SingleColor -> NaviampPlayerColors.fromSingleColor(
            naviampColorFromHex(interfaceSettings.singleColorHex)
                ?: naviampColorFromHex(DefaultSingleColorHex)!!,
            appColors,
        )
        AppBackgroundStyle.Aurora -> if (effectiveCoverArtUrl != null) {
            coverArtPlayerColors.withAuroraTone(interfaceSettings.auroraTone)
        } else {
            NaviampPlayerColors.solid(appColors.background)
        }
        AppBackgroundStyle.AlbumBlur -> if (effectiveCoverArtUrl != null) {
            coverArtPlayerColors
        } else {
            NaviampPlayerColors.solid(appColors.background)
        }
    }

    NaviampRadioArtworkLookupEffect(
        station = nowPlayingStation,
        streamMetadata = streamMetadata,
        provider = provider,
        artworkByKey = state.radioTrackArtworkByKey,
        onArtworkResolved = state::applyRadioArtwork,
    )

    val backgroundStart by animateColorAsState(
        targetValue = targetBackgroundColors.backgroundStart,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "backgroundStart",
    )
    val backgroundMid by animateColorAsState(
        targetValue = targetBackgroundColors.backgroundMid,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "backgroundMid",
    )
    val backgroundEnd by animateColorAsState(
        targetValue = targetBackgroundColors.backgroundEnd,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "backgroundEnd",
    )
    state.updateDerived(
        effectiveCoverArtUrl = effectiveCoverArtUrl,
        targetBackgroundColors = targetBackgroundColors,
        backgroundStart = backgroundStart,
        backgroundMid = backgroundMid,
        backgroundEnd = backgroundEnd,
    )
    return state
}
