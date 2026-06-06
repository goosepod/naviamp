package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.InternetRadioStation

@Composable
fun TrackRow(
    track: SharedTrackRowUi,
    colors: NaviampColors,
    onTrackSelected: ((SharedTrackRowUi) -> Unit)?,
    onStartRadio: ((SharedTrackRowUi) -> Unit)? = null,
    onAddToQueue: ((SharedTrackRowUi) -> Unit)? = null,
    onDownload: ((SharedTrackRowUi) -> Unit)? = null,
    onAddToPlaylist: ((SharedTrackRowUi) -> Unit)? = null,
    reservePopularIndicatorSpace: Boolean = false,
    modifier: Modifier = Modifier,
    background: Boolean = false,
    horizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 6.dp,
    showCoverArt: Boolean = true,
    coverArtSize: Dp = 44.dp,
    coverArtCornerRadius: Dp = 5.dp,
    titleStyle: TextStyle = TextStyle(fontSize = 16.sp),
    subtitleStyle: TextStyle = TextStyle(fontSize = 12.sp),
    metaStyle: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    titleSubtitleSpacing: Dp = 3.dp,
    showMenu: Boolean = false,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (background) {
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.12f))
                } else {
                    Modifier
                },
            )
            .let { rowModifier ->
                if (onTrackSelected != null) rowModifier.clickable { onTrackSelected(track) } else rowModifier
            }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingContent?.invoke(this)
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
                Text(track.meta, color = colors.mutedText, style = metaStyle)
            }
        }
        if (showCoverArt) {
            PlatformCoverArt(track.coverArtUrl, colors, coverArtSize, coverArtCornerRadius)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(titleSubtitleSpacing)) {
            Text(track.title, color = colors.primaryText, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.subtitle, color = colors.secondaryText, style = subtitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailingContent?.invoke(this)
        val rowActions = trackRowActions(
            canStartRadio = onStartRadio != null,
            canDownload = onDownload != null,
            canAddToQueue = onAddToQueue != null,
            canAddToPlaylist = onAddToPlaylist != null,
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.StartTrackRadio -> onStartRadio?.let { startRadio ->
                    NaviampRowMenuItem(action.label, action.icon, { startRadio(track) }, action.enabled)
                }
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
        if (showMenu || rowActions.isNotEmpty()) {
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
    emptyText: String? = null,
) {
    if (items.isEmpty() && emptyText == null) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title.uppercase(), colors)
        if (items.isEmpty()) {
            Text(emptyText.orEmpty(), color = colors.secondaryText, fontSize = 13.sp)
        } else {
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
fun SharedMediaRow(
    item: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: (() -> Unit)? = null,
    menuItems: List<NaviampRowMenuItem> = emptyList(),
    coverArtSize: Dp = 44.dp,
    coverArtCornerRadius: Dp = 5.dp,
    verticalPadding: Dp = 7.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .let { rowModifier ->
                if (onClick != null) rowModifier.clickable(onClick = onClick) else rowModifier
            }
            .padding(horizontal = 8.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val coverUrls = listOfNotNull(item.coverArtUrl).ifEmpty { item.coverArtUrls }
        MultiCoverArt(colors = colors, covers = coverUrls, size = coverArtSize, cornerRadius = coverArtCornerRadius)
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

@Composable
fun ArtistMixBuilderContent(
    colors: NaviampColors,
    builder: SharedArtistMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistRemoved: (SharedMediaItemUi) -> Unit,
    onPlayMix: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Artist Mix Builder", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = "Search artists",
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(NaviampIcons.Search, contentDescription = "Search artists", modifier = Modifier.size(18.dp))
            }
        }
        if (builder.selectedArtists.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                builder.selectedArtists.take(4).forEach { artist ->
                    ArtistMixSelectedArtist(
                        artist = artist,
                        colors = colors,
                        onRemove = { onArtistRemoved(artist) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        (builder.status ?: if (builder.loading) "Loading artists..." else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        builder.suggestedArtists.chunked(3).forEach { rowArtists ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowArtists.forEach { artist ->
                    ArtistMixArtistTile(
                        artist = artist,
                        colors = colors,
                        onClick = { onArtistSelected(artist) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowArtists.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
        if (builder.selectedArtists.isNotEmpty()) {
            PrimaryButton("Play Mix", colors, onClick = onPlayMix)
        }
    }
}

@Composable
private fun ArtistMixSelectedArtist(
    artist: SharedMediaItemUi,
    colors: NaviampColors,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onRemove)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlatformCoverArt(artist.coverArtUrl, colors, 30.dp, 15.dp)
        Text(artist.title, color = colors.primaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArtistMixArtistTile(
    artist: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(116.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PlatformCoverArt(artist.coverArtUrl, colors, 64.dp, 32.dp)
        Text(
            artist.title,
            color = colors.primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun InternetRadioContent(
    colors: NaviampColors,
    stations: List<InternetRadioStation>,
    status: String?,
    onStationSelected: (InternetRadioStation) -> Unit,
    onSaveStation: ((InternetRadioStation) -> Unit)? = null,
    onDeleteStation: ((InternetRadioStation) -> Unit)? = null,
) {
    var stationBeingEdited by remember { mutableStateOf<InternetRadioStation?>(null) }
    var stationBeingDeleted by remember { mutableStateOf<InternetRadioStation?>(null) }
    var creatingStation by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Internet Radio", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (onSaveStation != null) {
                Button(onClick = { creatingStation = true }, modifier = Modifier.height(34.dp)) {
                    Text("New station", fontSize = 12.sp)
                }
            }
        }
        status?.let { Text(it, color = colors.secondaryText, fontSize = 12.sp) }
        if (stations.isEmpty()) {
            Text("Saved internet radio stations will appear here.", color = colors.secondaryText, fontSize = 12.sp)
        }
        stations.sortedBy { it.name.lowercase() }.forEach { station ->
            SharedMediaRow(
                item = station.toSharedMediaItemUi(),
                colors = colors,
                onClick = { onStationSelected(station) },
                menuItems = stationRowActions(
                    canEdit = onSaveStation != null,
                    canDelete = onDeleteStation != null,
                ).mapNotNull { action ->
                    when (action.action) {
                        NaviampAction.EditStation -> NaviampRowMenuItem(
                            label = action.label,
                            icon = action.icon,
                            onClick = { stationBeingEdited = station },
                            enabled = action.enabled,
                        )
                        NaviampAction.DeleteStation -> NaviampRowMenuItem(
                            label = action.label,
                            icon = action.icon,
                            onClick = { stationBeingDeleted = station },
                            enabled = action.enabled,
                        )
                        else -> null
                    }
                },
            )
        }
    }

    if (creatingStation) {
        InternetRadioStationDialog(
            initialStation = null,
            onDismiss = { creatingStation = false },
            onConfirm = { station ->
                creatingStation = false
                onSaveStation?.invoke(station)
            },
        )
    }

    stationBeingEdited?.let { station ->
        InternetRadioStationDialog(
            initialStation = station,
            onDismiss = { stationBeingEdited = null },
            onConfirm = { updated ->
                stationBeingEdited = null
                onSaveStation?.invoke(updated)
            },
        )
    }

    stationBeingDeleted?.let { station ->
        AlertDialog(
            onDismissRequest = { stationBeingDeleted = null },
            title = { Text("Delete station") },
            text = { Text("Delete ${station.name}? This removes the server internet radio station.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        stationBeingDeleted = null
                        onDeleteStation?.invoke(station)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { stationBeingDeleted = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun InternetRadioStationDialog(
    initialStation: InternetRadioStation?,
    onDismiss: () -> Unit,
    onConfirm: (InternetRadioStation) -> Unit,
) {
    var name by remember(initialStation?.id) { mutableStateOf(initialStation?.name.orEmpty()) }
    var streamUrl by remember(initialStation?.id) { mutableStateOf(initialStation?.streamUrl.orEmpty()) }
    var homePageUrl by remember(initialStation?.id) { mutableStateOf(initialStation?.homePageUrl.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialStation == null) "New station" else "Edit station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, label = { Text("Stream URL") }, singleLine = true)
                OutlinedTextField(value = homePageUrl, onValueChange = { homePageUrl = it }, label = { Text("Home page URL") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && streamUrl.isNotBlank(),
                onClick = {
                    onConfirm(
                        InternetRadioStation(
                            id = initialStation?.id ?: streamUrl.trim(),
                            name = name.trim(),
                            streamUrl = streamUrl.trim(),
                            homePageUrl = homePageUrl.trim().takeIf { it.isNotBlank() },
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
