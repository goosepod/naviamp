package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The same destination is rendered by standard and TV shells; hosts add no discovery behavior. */
@Composable
internal fun NaviampDiscoveryArtistPage(
    discovery: SharedProviderDiscoveryUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    playback: DiscoverySamplePlaybackUi,
    television: Boolean = false,
    entryFocusGeneration: Int? = null,
    onEntryFocusHandled: (Int) -> Unit = {},
) {
    val artist = discovery.artistDetail ?: return
    NaviampSystemBackHandler(enabled = true, onBack = actions.onDiscoveryArtistDismiss)
    val release = discovery.releasePage
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(television, discovery.sourceId, discovery.sourceGeneration, artist.artist.id,
        release?.release?.id, entryFocusGeneration) {
        if (television) {
            withFrameNanos { }
            backFocus.requestFocus()
            entryFocusGeneration?.let(onEntryFocusHandled)
        }
    }
    if (release != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = actions.onDiscoveryArtistDismiss, modifier = Modifier.focusRequester(backFocus)) { Text(stringResource(Res.string.common_back)) }
            Text(release.result?.release?.title ?: release.release.title, color = colors.primaryText)
            val metadata = release.result?.release ?: release.release
            val date = metadata.releaseDate ?: release.release.releaseDate
            val kind = metadata.takeIf { it.type != null || it.secondaryTypes.isNotEmpty() }
                ?: release.release.takeIf { it.type != null || it.secondaryTypes.isNotEmpty() }
            val context = listOfNotNull(date, kind?.let { albumReleaseSectionLabel(it.section) }).joinToString(" · ")
            if (context.isNotEmpty()) Text(context, color = colors.secondaryText)
            val cover = release.result?.release?.imageUrl ?: release.release.imageUrl
            NaviampCoverArt(cover, colors, 240.dp, resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Album, 240.dp, 7.dp), mediaKind = SharedMediaItemKind.Album)
            when {
                release.loading -> Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                release.result?.status != ProviderDiscoveryStatus.Ready -> {
                    Text(stringResource(Res.string.discovery_unavailable), color = colors.secondaryText)
                    TextButton(onClick = { actions.onDiscoveryRelease(release.release.id) }) { Text(stringResource(Res.string.library_refresh)) }
                }
                else -> {
                    Text(release.result.credits.joinToString(", ") { it.name }, color = colors.secondaryText)
                    NaviampProviderDescription(release.result.overview, release.release.id, colors)
                }
            }
            if (artist.details?.canRequestAlbums == true) TextButton(
                enabled = !artist.actionBusy && !artist.savingFeedback && ProviderDiscoveryAction.RequestAlbum(release.release.id) !in artist.blockedActions,
                onClick = { actions.onDiscoveryAction(artist.artist.id, ProviderDiscoveryAction.RequestAlbum(release.release.id)) }) {
                Text(stringResource(Res.string.discovery_request_album))
            }
            if (artist.actionBusy) Text(stringResource(Res.string.discovery_action_busy), color = colors.secondaryText)
            artist.operationStatus?.let { Text(stringResource(discoveryOperationMessage(it)), color = colors.secondaryText) }
        }
        return
    }
    NaviampDiscoveryArtistPanel(artist, localizedProviderName(discovery.providerId), colors,
        onDismiss = actions.onDiscoveryArtistDismiss,
        onRefresh = { actions.onDiscoveryArtistDetails(artist.artist.id) },
        onExternal = { actions.onDiscoveryArtistSelected(artist.artist.id) },
        onFeedback = { actions.onDiscoveryFeedback(artist.artist.id, it) },
        onLibrary = { actions.onDiscoveryLibraryArtist(artist.artist.id) },
        onAction = { actions.onDiscoveryAction(artist.artist.id, it) }, playback = playback,
        fullPage = true, onArtistDetails = actions.onDiscoveryArtistDetails,
        onReleaseSelected = actions.onDiscoveryRelease, onViewport = actions.onDiscoveryViewport,
        stateKey = discovery.sourceId to discovery.sourceGeneration,
        dismissModifier = Modifier.focusRequester(backFocus))
}
