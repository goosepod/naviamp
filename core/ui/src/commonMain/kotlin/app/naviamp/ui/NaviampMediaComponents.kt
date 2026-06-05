package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TrackRow(
    track: AndroidTrackRowUi,
    colors: NaviampColors,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onAddToQueue: ((AndroidTrackRowUi) -> Unit)? = null,
    onDownload: ((AndroidTrackRowUi) -> Unit)? = null,
    onAddToPlaylist: ((AndroidTrackRowUi) -> Unit)? = null,
    reservePopularIndicatorSpace: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackSelected(track) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (reservePopularIndicatorSpace || track.meta.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.width(11.dp),
                ) {
                    if (track.popular) {
                        Icon(
                            imageVector = NaviampIcons.Fire,
                            contentDescription = "Popular on Deezer",
                            tint = colors.primaryText,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Text(track.meta, color = colors.mutedText, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        PlatformCoverArt(track.coverArtUrl, colors, 34.dp, 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, color = colors.primaryText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.subtitle, color = colors.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val rowActions = trackRowActions(
            canDownload = onDownload != null,
            canAddToQueue = onAddToQueue != null,
            canAddToPlaylist = onAddToPlaylist != null,
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.DownloadTrack -> onDownload?.let { download ->
                    NaviampRowMenuItem(action.label, action.icon, { download(track) }, action.enabled)
                }
                NaviampAction.AddToQueue -> onAddToQueue?.let { addToQueue ->
                    NaviampRowMenuItem(action.label, action.icon, { addToQueue(track) }, action.enabled)
                }
                NaviampAction.AddToPlaylist -> onAddToPlaylist?.let { addToPlaylist ->
                    NaviampRowMenuItem(action.label, action.icon, { addToPlaylist(track) }, action.enabled)
                }
                else -> null
            }
        }
        if (rowActions.isNotEmpty()) {
            NaviampRowOverflowMenu(
                colors = colors,
                items = rowActions,
            )
        }
    }
}

@Composable
internal fun MixCard(
    album: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(154.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        PlatformCoverArt(album.coverArtUrl, colors, 154.dp, 6.dp)
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.68f),
                    ),
                )
                .padding(8.dp),
        ) {
            Text(
                "${album.subtitle} Mix",
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.title,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeStationSection(
    stations: List<SharedHomeStationUi>,
    colors: NaviampColors,
    onStationSelected: (SharedHomeStationUi) -> Unit,
) {
    if (stations.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("STATIONS", colors)
        stations.forEach { station ->
            StationRow(
                title = station.title,
                subtitle = station.subtitle,
                colors = colors,
                onClick = { onStationSelected(station) },
            )
        }
    }
}

@Composable
private fun StationRow(
    title: String,
    subtitle: String,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.controlSurface.copy(alpha = 0.5f)),
        ) {
            Icon(NaviampIcons.InternetRadio, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(">", color = colors.mutedText, fontSize = 16.sp)
    }
}

@Composable
internal fun HomeSection(
    title: String,
    items: List<SharedMediaItemUi>,
    colors: NaviampColors,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
    stationStyle: Boolean = false,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title.uppercase(), colors)
        items.take(6).forEach { item ->
            if (stationStyle) {
                StationRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    colors = colors,
                    onClick = { onItemSelected?.invoke(item) },
                )
            } else {
                SharedMediaRow(
                    item = item,
                    colors = colors,
                    onClick = onItemSelected?.let { { it(item) } },
                )
            }
        }
    }
}

@Composable
internal fun MediaSection(
    title: String,
    items: List<SharedMediaItemUi>,
    colors: NaviampColors,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionHeader(title.uppercase(), colors)
        items.forEach { item ->
            SharedMediaRow(item, colors, onClick = onItemSelected?.let { { it(item) } })
        }
    }
}

@Composable
internal fun SharedMediaRow(
    item: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: (() -> Unit)? = null,
    menuItems: List<NaviampRowMenuItem> = emptyList(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .let { modifier ->
                if (onClick != null) modifier.clickable(onClick = onClick) else modifier
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlatformCoverArt(item.coverArtUrl, colors, 34.dp, 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.meta.isNotBlank()) {
            Text(item.meta, color = colors.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (menuItems.isNotEmpty()) {
            NaviampRowOverflowMenu(colors = colors, items = menuItems)
        }
    }
}
