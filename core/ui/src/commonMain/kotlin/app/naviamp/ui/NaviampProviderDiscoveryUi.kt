package app.naviamp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
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
            Text(stringResource(Res.string.discovery_recommendations), color = colors.primaryText, fontWeight = FontWeight.SemiBold)
            DiscoveryArtistRail(discovery.feed.recommendations, providerName, colors, onArtistSelected, onArtistDetails)
        }
        if (discovery.feed.trending.isNotEmpty()) {
            Text(stringResource(Res.string.discovery_trending), color = colors.primaryText, fontWeight = FontWeight.SemiBold)
            DiscoveryArtistRail(discovery.feed.trending, providerName, colors, onArtistSelected, onArtistDetails)
        }
    }
    discovery.artistDetail?.let { detail ->
        DiscoveryArtistDialog(detail, providerName, colors, onArtistDismiss,
            onRefresh = { onArtistDetails(detail.artist.id) },
            onExternal = { onArtistSelected(detail.artist.id) },
            onFeedback = { onFeedback(detail.artist.id, it) },
            onLibrary = { onLibraryArtist(detail.artist.id) })
    }
}

@Composable
private fun DiscoveryArtistRail(
    artists: List<ProviderDiscoveryArtist>,
    providerName: String,
    colors: NaviampColors,
    onArtistSelected: (String) -> Unit,
    onArtistDetails: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        artists.forEach { artist ->
            Column(modifier = Modifier.width(142.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NaviampCoverArt(artist.imageUrl, colors, 128.dp,
                    mediaArtworkCornerRadius(SharedMediaItemKind.Artist, 128.dp, 7.dp))
                Text(artist.name, color = colors.primaryText, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (artist.genres.isNotEmpty()) {
                    Text(artist.genres.joinToString(", "), color = colors.secondaryText, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (artist.detailsAvailable) {
                    TextButton(onClick = { onArtistDetails(artist.id) }) {
                        Text(stringResource(Res.string.discovery_view_artist), color = colors.secondaryText, fontSize = 11.sp)
                    }
                } else if (artist.externalUrl != null) {
                    TextButton(onClick = { onArtistSelected(artist.id) }) {
                        Text(stringResource(Res.string.discovery_open_provider, providerName), color = colors.secondaryText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
) {
    NaviampDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(12.dp), color = colors.controlSurface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(screen.artist.name, modifier = Modifier.weight(1f), color = colors.primaryText,
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            if (screen.details?.libraryArtist != null) TextButton(onClick = onLibrary) {
                                Text(stringResource(Res.string.discovery_open_library))
                            }
                            if (screen.artist.externalUrl != null) TextButton(onClick = onExternal) {
                                Text(stringResource(Res.string.discovery_open_provider, providerName))
                            }
                            TextButton(onClick = onRefresh, enabled = !screen.loading && !screen.savingFeedback) {
                                Text(stringResource(Res.string.library_refresh))
                            }
                        }
                        screen.details?.let { details ->
                            NaviampProviderDescription(details.biography, details.artist.id, colors)
                            if (details.feedbackAvailable) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProviderDiscoveryFeedbackAction.entries.forEach { action ->
                                    FilterChip(selected = action in details.feedback, enabled = !screen.savingFeedback,
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
                        if (screen.savingFeedback) Text(stringResource(Res.string.discovery_saving_feedback), color = colors.secondaryText)
                        if (screen.feedbackFailed) Text(stringResource(Res.string.discovery_feedback_failed), color = colors.secondaryText)
                    }
                    val releases = screen.details?.releases.orEmpty()
                    if (releases.isNotEmpty()) item {
                        Text(stringResource(Res.string.discovery_releases), color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                    }
                    items(releases, key = { it.id }) { release ->
                        Column {
                            Text(release.title, color = colors.primaryText)
                            Text(listOfNotNull(release.releaseDate, release.type).joinToString(" · "), color = colors.secondaryText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
