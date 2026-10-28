package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.Dp
import kotlin.math.floor
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.naviamp.domain.media.AlbumReleaseSection
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
import app.naviamp.domain.provider.*
import app.naviamp.domain.provider.ProviderDiscoveryArtist
import app.naviamp.domain.provider.ProviderDiscoveryStatus
import app.naviamp.domain.provider.ProviderDiscoveryFeedbackAction
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Discovery artists do not offer playback actions: they may have no files in this Library. */
@Composable
internal fun NaviampProviderDiscoveryContent(
    discovery: SharedProviderDiscoveryUi,
    colors: NaviampColors,
    onArtistSelected: (String) -> Unit,
    onArtistDetails: (String) -> Unit = {},
    onArtistDismiss: () -> Unit = {},
    onFeedback: (String, ProviderDiscoveryFeedbackAction) -> Unit = { _, _ -> },
    onLibraryArtist: (String) -> Unit = {},
    onAction: (String, ProviderDiscoveryAction) -> Unit = { _, _ -> },
    playback: DiscoverySamplePlaybackUi = DiscoverySamplePlaybackUi(),
) {
    val providerName = localizedProviderName(discovery.providerId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.discovery_provider_title, providerName),
            color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        val message = when {
            discovery.loading -> Res.string.discovery_loading
            else -> when (discovery.feed.status) {
                ProviderDiscoveryStatus.Building -> Res.string.discovery_building
                ProviderDiscoveryStatus.Unsupported -> Res.string.discovery_unsupported
                ProviderDiscoveryStatus.PasswordRequired -> Res.string.discovery_password_required
                ProviderDiscoveryStatus.SignInRequired -> Res.string.discovery_sign_in_required
                ProviderDiscoveryStatus.Forbidden -> Res.string.discovery_forbidden
                ProviderDiscoveryStatus.Unavailable -> Res.string.discovery_unavailable
                ProviderDiscoveryStatus.Ready -> if (discovery.feed.recommendations.isEmpty()) Res.string.discovery_empty else null
            }
        }
        message?.let { Text(stringResource(it), color = colors.secondaryText, fontSize = 13.sp) }
        if (discovery.feed.recommendations.isNotEmpty()) {
            DiscoveryArtistRail(stringResource(Res.string.discovery_recommendations), discovery.feed.recommendations, providerName, colors, onArtistSelected, onArtistDetails)
        }
        if (discovery.feed.trending.isNotEmpty()) {
            DiscoveryArtistRail(stringResource(Res.string.discovery_trending), discovery.feed.trending, providerName, colors, onArtistSelected, onArtistDetails)
        }
    }
    discovery.artistDetail?.let { detail ->
        DiscoveryArtistDialog(detail, providerName, colors, onArtistDismiss,
            onRefresh = { onArtistDetails(detail.artist.id) },
            onExternal = { onArtistSelected(detail.artist.id) },
            onFeedback = { onFeedback(detail.artist.id, it) },
            onLibrary = { onLibraryArtist(detail.artist.id) },
            onAction = { onAction(detail.artist.id, it) }, playback = playback)
    }
}

@Composable
private fun DiscoveryArtistRail(
    title: String,
    artists: List<ProviderDiscoveryArtist>,
    providerName: String,
    colors: NaviampColors,
    onArtistSelected: (String) -> Unit,
    onArtistDetails: (String) -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = colors.primaryText, fontWeight = FontWeight.SemiBold)
        listOf(false, true).forEach { forward ->
            HomeRailArrow(forward, if (forward) state.canScrollForward else state.canScrollBackward, colors) {
                scope.launch {
                    val viewport = state.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
                    state.animateScrollBy(viewport.toFloat() * if (forward) 1f else -1f)
                }
            }
        }
    }
    LazyRow(modifier = Modifier.fillMaxWidth(), state = state,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(artists, key = { it.id }) { artist ->
            val actionLabel = if (artist.detailsAvailable) stringResource(Res.string.discovery_view_artist)
                else stringResource(Res.string.discovery_open_provider, providerName)
            val openArtist = {
                if (artist.detailsAvailable) onArtistDetails(artist.id)
                else onArtistSelected(artist.id)
            }
            val interaction = if (artist.detailsAvailable || artist.externalUrl != null)
                Modifier.clickable(role = Role.Button, onClickLabel = actionLabel, onClick = openArtist)
                else Modifier
            Column(modifier = Modifier.width(142.dp).then(interaction), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NaviampCoverArt(artist.imageUrl, colors, 128.dp,
                    mediaArtworkCornerRadius(SharedMediaItemKind.Artist, 128.dp, 7.dp))
                Text(artist.name, color = colors.primaryText, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (artist.genres.isNotEmpty()) {
                    Text(artist.genres.joinToString(", "), color = colors.secondaryText, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!artist.detailsAvailable && artist.externalUrl != null) {
                    Text(actionLabel, color = colors.secondaryText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryArtistDialog(
    screen: SharedProviderDiscoveryArtistUi,
    providerName: String,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onExternal: () -> Unit,
    onFeedback: (ProviderDiscoveryFeedbackAction) -> Unit,
    onLibrary: () -> Unit,
    onAction: (ProviderDiscoveryAction) -> Unit,
    playback: DiscoverySamplePlaybackUi,
) {
    NaviampDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NaviampDiscoveryArtistPanel(screen, providerName, colors, onDismiss, onRefresh, onExternal, onFeedback, onLibrary, onAction, playback)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NaviampDiscoveryArtistPanel(
    screen: SharedProviderDiscoveryArtistUi,
    providerName: String,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onExternal: () -> Unit,
    onFeedback: (ProviderDiscoveryFeedbackAction) -> Unit,
    onLibrary: () -> Unit,
    onAction: (ProviderDiscoveryAction) -> Unit,
    playback: DiscoverySamplePlaybackUi,
) {
    val releases = screen.details?.releases.orEmpty()
    val sections = AlbumReleaseSection.entries.filter { section -> releases.any { it.section == section } }
    var selectedSection by remember(screen.artist.id) { mutableStateOf<AlbumReleaseSection?>(null) }
    val effectiveSection = selectedSection?.takeIf { it in sections }
    val visibleReleases = releases.filter { effectiveSection == null || it.section == effectiveSection }
    Surface(Modifier.widthIn(max = 1080.dp).fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(12.dp), color = colors.controlSurface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(screen.artist.name, modifier = Modifier.weight(1f), color = colors.primaryText,
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val releaseColumns = discoveryReleaseColumns(maxWidth.value)
                val coverSize = (maxWidth - 12.dp * (releaseColumns - 1)) / releaseColumns
                val sampleColumns = if (maxWidth >= 680.dp) 2 else 1
                val sampleRows = discoverySampleRows(screen.samples, sampleColumns)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        NaviampCoverArt(screen.artist.imageUrl, colors, 96.dp,
                            mediaArtworkCornerRadius(SharedMediaItemKind.Artist, 96.dp, 6.dp))
                        if (screen.loading) Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                        if (!screen.loading && screen.status != ProviderDiscoveryStatus.Ready) {
                            Text(stringResource(when (screen.status) {
                                ProviderDiscoveryStatus.Unsupported -> Res.string.discovery_unsupported
                                ProviderDiscoveryStatus.PasswordRequired -> Res.string.discovery_password_required
                                ProviderDiscoveryStatus.SignInRequired -> Res.string.discovery_sign_in_required
                                ProviderDiscoveryStatus.Forbidden -> Res.string.discovery_forbidden
                                else -> Res.string.discovery_unavailable
                            }), color = colors.secondaryText)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (screen.details?.canChangeMonitoring == true && screen.details.monitored != null) {
                                TextButton(onClick = { onAction(ProviderDiscoveryAction.SetMonitoring(screen.details.monitored != true)) },
                                    enabled = !screen.actionBusy && !screen.savingFeedback && ProviderDiscoveryAction.SetMonitoring(screen.details.monitored != true) !in screen.blockedActions) {
                                    Text(stringResource(if (screen.details.monitored == true) Res.string.discovery_stop_monitoring else Res.string.discovery_monitor_artist))
                                }
                            }
                            if (screen.details?.libraryArtist != null) TextButton(onClick = onLibrary) {
                                Text(stringResource(Res.string.discovery_open_library))
                            }
                            if (screen.artist.externalUrl != null) TextButton(onClick = onExternal) {
                                Text(stringResource(Res.string.discovery_open_provider, providerName))
                            }
                            TextButton(onClick = onRefresh, enabled = !screen.loading && !screen.savingFeedback && !screen.actionBusy) {
                                Text(stringResource(Res.string.library_refresh))
                            }
                        }
                        screen.details?.let { details ->
                            NaviampProviderDescription(details.biography, details.artist.id, colors)
                            if (details.feedbackAvailable) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProviderDiscoveryFeedbackAction.entries.forEach { action ->
                                    FilterChip(selected = action in details.feedback, enabled = !screen.savingFeedback && !screen.actionBusy,
                                        onClick = { onFeedback(action) }, label = {
                                            Text(stringResource(when (action) {
                                                ProviderDiscoveryFeedbackAction.MoreLikeThis -> Res.string.discovery_more_like_this
                                                ProviderDiscoveryFeedbackAction.LessLikeThis -> Res.string.discovery_less_like_this
                                                ProviderDiscoveryFeedbackAction.BlockArtist -> Res.string.discovery_block_artist
                                            }))
                                        })
                                }
                            }
                        }
                        if (screen.actionBusy) Text(stringResource(Res.string.discovery_action_busy), color = colors.secondaryText)
                        screen.operationStatus?.let { status -> Text(stringResource(discoveryOperationMessage(status)), color = colors.secondaryText) }
                        if (screen.savingFeedback) Text(stringResource(Res.string.discovery_saving_feedback), color = colors.secondaryText)
                        if (screen.feedbackFailed) Text(stringResource(Res.string.discovery_feedback_failed), color = colors.secondaryText)
                    }
                    if (!screen.loading) item(key = "samples-header") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(Res.string.discovery_samples), color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                            if (playback.failed) Text(stringResource(Res.string.discovery_samples_failed), color = colors.secondaryText)
                            if (screen.loading || screen.samplesLoading || screen.samplesStatus == null) Text(stringResource(Res.string.discovery_samples_loading), color = colors.secondaryText)
                            else if (screen.samples.isEmpty()) Text(stringResource(
                                if (screen.samplesStatus == ProviderDiscoveryStatus.Ready) Res.string.discovery_no_samples else Res.string.discovery_samples_failed), color = colors.secondaryText)
                        }
                    }
                    items(sampleRows, key = { "samples:${it.first().value.id}" }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            row.forEach { sample ->
                                DiscoverySampleRow(sample.index + 1, sample.value, screen, playback, colors,
                                    Modifier.weight(1f), onAction)
                            }
                            if (row.size < sampleColumns) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (releases.isNotEmpty()) item(key = "release-filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(Res.string.discovery_releases), color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = effectiveSection == null, onClick = { selectedSection = null },
                                    label = { Text(stringResource(Res.string.discovery_all_releases)) })
                                sections.forEach { section ->
                                    FilterChip(selected = effectiveSection == section, onClick = { selectedSection = section },
                                        label = { Text(albumReleaseSectionLabel(section)) })
                                }
                            }
                        }
                    }
                    items(visibleReleases.chunked(releaseColumns), key = { "releases:${it.first().id}" }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { release -> DiscoveryReleaseCard(release, coverSize, screen, colors, onAction) }
                        }
                    }
                }
            }
        }
    }
}

internal fun discoveryReleaseColumns(width: Float): Int = floor((width + 12f) / 148f).toInt().coerceIn(1, 6)

internal fun discoverySampleRows(samples: List<ProviderDiscoverySample>, columns: Int): List<List<IndexedValue<ProviderDiscoverySample>>> {
    val count = columns.coerceIn(1, 2)
    val rows = (samples.size + count - 1) / count
    return (0 until rows).map { row -> (0 until count).mapNotNull { column ->
        val index = row + column * rows
        samples.getOrNull(index)?.let { IndexedValue(index, it) }
    } }
}

@Composable
private fun DiscoverySampleRow(
    number: Int, sample: ProviderDiscoverySample, screen: SharedProviderDiscoveryArtistUi,
    playback: DiscoverySamplePlaybackUi, colors: NaviampColors, modifier: Modifier,
    onAction: (ProviderDiscoveryAction) -> Unit,
) {
    val active = playback.trackId == DiscoveryPreviewTrackPrefix + sample.id
    val playing = active && playback.playing
    val enabled = !active || playback.canToggle
    val playLabel = stringResource(if (playing) Res.string.transport_pause else Res.string.discovery_play_sample)
    val toggle = { onAction(ProviderDiscoveryAction.PlaySample(sample.id)) }
    Row(modifier.background(if (active) colors.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent,
        RoundedCornerShape(7.dp)).clickable(enabled = enabled, role = Role.Button, onClickLabel = playLabel, onClick = toggle)
        .padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(number.toString(), Modifier.width(20.dp), color = colors.mutedText, fontSize = 11.sp)
        IconButton(onClick = toggle, enabled = enabled, modifier = Modifier.size(30.dp)) {
            Icon(if (playing) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                playLabel, Modifier.size(16.dp), tint = colors.primaryText)
        }
        Column(Modifier.weight(1f)) {
            Text(sample.title, color = colors.primaryText, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            sample.albumTitle?.let { Text(it, color = colors.secondaryText, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (screen.details?.canRequestTracks == true) IconButton(
            onClick = { onAction(ProviderDiscoveryAction.RequestTrack(sample.id)) },
            enabled = !screen.actionBusy && !screen.savingFeedback && ProviderDiscoveryAction.RequestTrack(sample.id) !in screen.blockedActions,
            modifier = Modifier.size(32.dp)) {
            Icon(NaviampIcons.Downloads, stringResource(Res.string.discovery_request_track), Modifier.size(16.dp), tint = colors.secondaryText)
        }
    }
}

@Composable
private fun DiscoveryReleaseCard(
    release: ProviderDiscoveryRelease, size: Dp, screen: SharedProviderDiscoveryArtistUi,
    colors: NaviampColors, onAction: (ProviderDiscoveryAction) -> Unit,
) {
    Column(Modifier.width(size), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            NaviampCoverArt(release.imageUrl, colors, size, mediaArtworkCornerRadius(SharedMediaItemKind.Album, size, 7.dp))
            if (screen.details?.canRequestAlbums == true) Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp),
                shape = RoundedCornerShape(6.dp), color = colors.controlSurface.copy(alpha = 0.92f)) {
                IconButton(onClick = { onAction(ProviderDiscoveryAction.RequestAlbum(release.id)) },
                    enabled = !screen.actionBusy && !screen.savingFeedback && ProviderDiscoveryAction.RequestAlbum(release.id) !in screen.blockedActions,
                    modifier = Modifier.size(30.dp)) {
                    Icon(NaviampIcons.Downloads, stringResource(Res.string.discovery_request_album), Modifier.size(17.dp), tint = colors.primaryText)
                }
            }
        }
        Text(release.title, color = colors.primaryText, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(release.releaseDate?.take(4), albumReleaseSectionLabel(release.section)).joinToString(" · "),
            color = colors.secondaryText, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun discoveryOperationMessage(status: ProviderDiscoveryOperationStatus) = when (status) {
    ProviderDiscoveryOperationStatus.Queued -> Res.string.discovery_request_queued
    ProviderDiscoveryOperationStatus.AlreadyQueued -> Res.string.discovery_request_already_queued
    ProviderDiscoveryOperationStatus.AlreadyOwned -> Res.string.discovery_request_owned
    ProviderDiscoveryOperationStatus.Saved -> Res.string.discovery_request_saved
    ProviderDiscoveryOperationStatus.MonitoringEnabled -> Res.string.discovery_monitoring_enabled
    ProviderDiscoveryOperationStatus.MonitoringDisabled -> Res.string.discovery_monitoring_disabled
    ProviderDiscoveryOperationStatus.MissingSource -> Res.string.discovery_request_missing_source
    ProviderDiscoveryOperationStatus.Forbidden -> Res.string.discovery_action_forbidden
    ProviderDiscoveryOperationStatus.Unsupported -> Res.string.discovery_action_unsupported
    ProviderDiscoveryOperationStatus.Unavailable, ProviderDiscoveryOperationStatus.Uncertain -> Res.string.discovery_action_uncertain
}
