package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.*
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
    val focusManager = LocalFocusManager.current
    val navigation = Modifier.onPreviewKeyEvent { event ->
        if (!television || event.type != KeyEventType.KeyDown) false else {
            val direction = when (event.key) {
                Key.DirectionDown -> FocusDirection.Down
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            direction?.let(focusManager::moveFocus) ?: false
        }
    }
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
        val albumOperation = artist.requestOperations[ProviderDiscoveryAction.RequestAlbum(release.release.id)]
        val available = release.ownership?.status == ProviderDiscoveryStatus.Ready &&
            release.ownership.availability == ProviderDiscoveryReleaseAvailability.Available
        val waitingRequest = albumOperation?.status == ProviderDiscoveryOperationStatus.WaitingForRelease
        val waiting = waitingRequest && !available
        Column(Modifier.fillMaxSize().then(navigation).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DiscoveryActionButton(colors = colors, onClick = actions.onDiscoveryArtistDismiss, modifier = Modifier.focusRequester(backFocus)) { DiscoveryText(stringResource(Res.string.common_back)) }
            DiscoveryActionButton(colors = colors, onClick = actions.onDiscoveryRequests) { DiscoveryText(stringResource(Res.string.discovery_requests_title)) }
            if (discovery.requestsStatus != null && discovery.requestsStatus != ProviderDiscoveryStatus.Ready)
                DiscoveryText(stringResource(Res.string.discovery_requests_stale), color = colors.secondaryText)
            DiscoveryText(release.result?.release?.title ?: release.release.title, color = colors.primaryText,
                modifier = Modifier.semantics { heading() })
            val metadata = release.result?.release ?: release.release
            val date = discoveryCalendarDate(metadata.releaseDate ?: release.release.releaseDate)
            val kind = metadata.takeIf { it.type != null || it.secondaryTypes.isNotEmpty() }
                ?: release.release.takeIf { it.type != null || it.secondaryTypes.isNotEmpty() }
            val context = listOfNotNull(date, kind?.let { albumReleaseSectionLabel(it.section) }).joinToString(" · ")
            if (context.isNotEmpty()) DiscoveryText(context, color = colors.secondaryText)
            val cover = release.result?.release?.imageUrl ?: release.release.imageUrl
            NaviampCoverArt(cover, colors, 240.dp, resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Album, 240.dp, 7.dp), mediaKind = SharedMediaItemKind.Album)
            when {
                release.loading -> DiscoveryText(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                release.result?.status != ProviderDiscoveryStatus.Ready -> {
                    DiscoveryText(stringResource(discoveryStatusMessage(release.result?.status)), color = colors.secondaryText)
                    DiscoveryActionButton(colors = colors, onClick = { actions.onDiscoveryRelease(release.release.id) }) { DiscoveryText(stringResource(Res.string.library_refresh)) }
                }
                else -> {
                    DiscoveryText(release.result.credits.joinToString(", ") { it.name }, color = colors.secondaryText)
                    NaviampProviderDescription(release.result.overview, release.release.id, colors)
                    if (release.result.genres.isNotEmpty()) DiscoveryText(release.result.genres.joinToString(", "), color = colors.secondaryText)
                    if (release.result.editions.isNotEmpty()) {
                        DiscoveryText(stringResource(Res.string.discovery_editions), color = colors.primaryText, modifier = Modifier.semantics { heading() })
                        release.result.editions.forEach { edition ->
                            DiscoveryText(listOfNotNull(edition.title, discoveryCalendarDate(edition.date), edition.status).joinToString(" · "), color = colors.secondaryText)
                        }
                    }
                }
            }
            if (release.ownershipLoading) DiscoveryText(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
            else if (release.ownership?.status !in listOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Unsupported)) {
                DiscoveryText(stringResource(discoveryStatusMessage(release.ownership?.status)), color = colors.secondaryText)
                DiscoveryActionButton(colors = colors, onClick = { actions.onDiscoveryRelease(release.release.id) }) { DiscoveryText(stringResource(Res.string.library_refresh)) }
            }
            release.ownership?.availability?.let { availability ->
                val label = when (availability) {
                    ProviderDiscoveryReleaseAvailability.NotOwned -> null
                    ProviderDiscoveryReleaseAvailability.Catalogued -> if (waiting) null else Res.string.discovery_release_catalogued
                    ProviderDiscoveryReleaseAvailability.Partial -> Res.string.discovery_release_partial
                    ProviderDiscoveryReleaseAvailability.Available -> Res.string.discovery_release_available
                }
                label?.let { DiscoveryText(stringResource(it), color = colors.secondaryText) }
            }
            if (release.ownership?.libraryAlbum != null) DiscoveryActionButton(colors = colors, onClick = {
                actions.onDiscoveryLibraryRelease(discovery.sourceId, discovery.sourceGeneration, release.release.id)
            }) { DiscoveryText(stringResource(Res.string.discovery_open_library)) }
            if (!release.loading && release.result?.status == ProviderDiscoveryStatus.Ready && release.requestContext == null &&
                (artist.details?.canRequestAlbums == true || artist.details?.canRequestTracks == true))
                DiscoveryText(stringResource(Res.string.discovery_request_identity_unavailable), color = colors.secondaryText)
            if (artist.details?.canRequestAlbums == true) DiscoveryActionButton(colors = colors,
                enabled = !waiting && release.requestContext != null && !artist.actionBusy && !artist.savingFeedback && release.ownership?.availability != ProviderDiscoveryReleaseAvailability.Available &&
                    ProviderDiscoveryAction.RequestAlbum(release.release.id) !in artist.blockedActions,
                onClick = { actions.onDiscoveryReleaseAction(discovery.sourceId, discovery.sourceGeneration,
                    release.release.id, ProviderDiscoveryAction.RequestAlbum(release.release.id)) }) {
                DiscoveryText(stringResource(if (waiting) Res.string.discovery_waiting_for_release else Res.string.discovery_request_album))
            }
            if (artist.details?.canRequestAlbums == true || artist.details?.canRequestTracks == true)
                DiscoveryText(stringResource(Res.string.discovery_server_acquisition), color = colors.secondaryText)
            if (artist.actionBusy) DiscoveryText(stringResource(Res.string.discovery_action_busy), color = colors.secondaryText)
            albumOperation?.takeUnless { it.status == ProviderDiscoveryOperationStatus.WaitingForRelease && available }
                ?.let { operation -> artist.acquisitions[ProviderDiscoveryAction.RequestAlbum(release.release.id)]
                    ?.let { DiscoveryAcquisitionResult(it, colors) } ?: DiscoveryOperationResult(operation, colors) }
            DiscoveryText(stringResource(Res.string.search_tracks_section), color = colors.primaryText, modifier = Modifier.semantics { heading() })
            when {
                release.tracksLoading -> DiscoveryText(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                release.tracks?.status != ProviderDiscoveryStatus.Ready -> {
                    DiscoveryText(stringResource(discoveryStatusMessage(release.tracks?.status)), color = colors.secondaryText)
                    DiscoveryActionButton(colors = colors, onClick = { actions.onDiscoveryRelease(release.release.id) }) { DiscoveryText(stringResource(Res.string.library_refresh)) }
                }
                release.tracks.tracks.isEmpty() -> DiscoveryText(stringResource(Res.string.discovery_no_release_tracks), color = colors.secondaryText)
                else -> {
                    if (playback.failed) DiscoveryText(stringResource(Res.string.discovery_samples_failed), color = colors.secondaryText)
                    NaviampDiscoveryReleaseTrackList(discovery, artist, release, colors, actions, playback, waitingRequest)
                }
            }
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
        onRequests = actions.onDiscoveryRequests,
        onReleaseSelected = actions.onDiscoveryRelease, onViewport = actions.onDiscoveryViewport,
        stateKey = discovery.sourceId to discovery.sourceGeneration,
        navigationModifier = navigation,
        dismissModifier = Modifier.focusRequester(backFocus))
}
