package app.naviamp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.provider.ProviderDiscoveryArtist
import app.naviamp.domain.provider.ProviderDiscoveryStatus
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Discovery artists do not offer playback actions: they may have no files in this Library. */
@Composable
internal fun NaviampProviderDiscoveryContent(
    discovery: SharedProviderDiscoveryUi,
    colors: NaviampColors,
    onArtistSelected: (String) -> Unit,
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
            DiscoveryArtistRail(discovery.feed.recommendations, providerName, colors, onArtistSelected)
        }
        if (discovery.feed.trending.isNotEmpty()) {
            Text(stringResource(Res.string.discovery_trending), color = colors.primaryText, fontWeight = FontWeight.SemiBold)
            DiscoveryArtistRail(discovery.feed.trending, providerName, colors, onArtistSelected)
        }
    }
}

@Composable
private fun DiscoveryArtistRail(
    artists: List<ProviderDiscoveryArtist>,
    providerName: String,
    colors: NaviampColors,
    onArtistSelected: (String) -> Unit,
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
                if (artist.externalUrl != null) {
                    TextButton(onClick = { onArtistSelected(artist.id) }) {
                        Text(stringResource(Res.string.discovery_open_provider, providerName), color = colors.secondaryText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
