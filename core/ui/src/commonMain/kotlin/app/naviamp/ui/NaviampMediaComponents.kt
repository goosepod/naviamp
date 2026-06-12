package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
    onTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        when (request.action) {
            SharedTrackRowAction.Select -> onTrackSelected?.invoke(request.track)
            SharedTrackRowAction.StartRadio -> onStartRadio?.invoke(request.track)
            SharedTrackRowAction.AddToQueue -> onAddToQueue?.invoke(request.track)
            SharedTrackRowAction.Download -> onDownload?.invoke(request.track)
            SharedTrackRowAction.AddToPlaylist -> onAddToPlaylist?.invoke(request.track)
            SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
        }
    },
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
    var detailsOpen by remember(track.id) { mutableStateOf(false) }
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
                if (onTrackSelected != null) {
                    rowModifier.clickable {
                        onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Select))
                    }
                } else {
                    rowModifier
                }
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
                            contentDescription = "Popular track",
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
            canShowDetails = track.detailSections.isNotEmpty(),
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.StartTrackRadio -> onStartRadio?.let {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.StartRadio)) },
                        action.enabled,
                    )
                }
                NaviampAction.DownloadTrack -> onDownload?.let {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Download)) },
                        action.enabled,
                    )
                }
                NaviampAction.AddToQueue -> onAddToQueue?.let {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToQueue)) },
                        action.enabled,
                    )
                }
                NaviampAction.AddToPlaylist -> onAddToPlaylist?.let {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToPlaylist)) },
                        action.enabled,
                    )
                }
                NaviampAction.TrackDetails -> NaviampRowMenuItem(
                    action.label,
                    action.icon,
                    { detailsOpen = true },
                    action.enabled,
                )
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
    if (detailsOpen) {
        TrackDetailsDialog(
            sections = track.detailSections,
            colors = colors,
            onDismissRequest = { detailsOpen = false },
        )
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
    onFavoriteToggled: ((SharedMediaItemUi) -> Unit)? = null,
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
                        onFavoriteToggled = onFavoriteToggled,
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
    onFavoriteToggled: ((SharedMediaItemUi) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionHeader(title.uppercase(), colors)
        items.forEach { item ->
            SharedMediaRow(
                item = item,
                colors = colors,
                onClick = onItemSelected?.let { { it(item) } },
                onFavoriteToggled = onFavoriteToggled,
            )
        }
    }
}

@Composable
fun SharedMediaRow(
    item: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: (() -> Unit)? = null,
    menuItems: List<NaviampRowMenuItem> = emptyList(),
    onFavoriteToggled: ((SharedMediaItemUi) -> Unit)? = null,
    onItemAction: (SharedMediaItemActionRequest) -> Unit = { request ->
        handleSharedMediaItemAction(
            request,
            SharedMediaItemActionHandlers(
                onSelect = { onClick?.invoke() },
                onToggleFavorite = { selectedItem -> onFavoriteToggled?.invoke(selectedItem) },
            ),
        )
    },
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
                if (onClick != null) {
                    rowModifier.clickable {
                        onItemAction(SharedMediaItemActionRequest(item, SharedMediaItemAction.Select))
                    }
                } else {
                    rowModifier
                }
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
        if (item.favoriteActive) {
            Icon(
                imageVector = NaviampTransportIcons.Heart,
                contentDescription = "Favorite",
                tint = colors.accent,
                modifier = Modifier.size(15.dp),
            )
        }
        val favoriteMenuItem = if (item.canFavorite && onFavoriteToggled != null) {
            NaviampRowMenuItem(
                label = if (item.favoriteActive) "Remove favorite" else "Favorite",
                icon = NaviampTransportIcons.Heart,
                onClick = { onItemAction(SharedMediaItemActionRequest(item, SharedMediaItemAction.ToggleFavorite)) },
            )
        } else {
            null
        }
        val allMenuItems = listOfNotNull(favoriteMenuItem) + menuItems
        if (allMenuItems.isNotEmpty()) {
            NaviampRowOverflowMenu(colors = colors, items = allMenuItems)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ArtistMixBuilderContent(
    colors: NaviampColors,
    builder: SharedArtistMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistRemoved: (SharedMediaItemUi) -> Unit,
    onReset: () -> Unit,
    onPlayMix: () -> Unit,
    showPlayMixButton: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Artist Mix Builder", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (builder.query.isNotBlank() || builder.selectedArtists.isNotEmpty()) {
                TextButton(onClick = onReset) {
                    Text("Reset", fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = "Search artists",
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(NaviampIcons.Search, contentDescription = "Search artists", modifier = Modifier.size(18.dp))
            }
        }
        if (builder.selectedArtists.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                builder.selectedArtists.forEach { artist ->
                    ArtistMixSelectedArtist(
                        artist = artist,
                        colors = colors,
                        onRemove = { onArtistRemoved(artist) },
                        modifier = Modifier.widthIn(min = 128.dp, max = 220.dp),
                    )
                }
            }
        }
        (builder.status ?: if (builder.loading) "Loading artists..." else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            builder.suggestedArtists.forEach { artist ->
                ArtistMixArtistTile(
                    artist = artist,
                    colors = colors,
                    onClick = { onArtistSelected(artist) },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 148.dp, max = 220.dp),
                )
            }
        }
        if (showPlayMixButton && builder.selectedArtists.isNotEmpty()) {
            PrimaryButton("Play Mix", colors, onClick = onPlayMix)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumMixBuilderContent(
    colors: NaviampColors,
    builder: SharedAlbumMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumRemoved: (SharedMediaItemUi) -> Unit,
    onReset: () -> Unit,
    onPlayMix: () -> Unit,
    showPlayMixButton: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Album Mix Builder", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (builder.query.isNotBlank() || builder.selectedAlbums.isNotEmpty()) {
                TextButton(onClick = onReset) {
                    Text("Reset", fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = "Search albums",
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(NaviampIcons.Search, contentDescription = "Search albums", modifier = Modifier.size(18.dp))
            }
        }
        if (builder.selectedAlbums.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                builder.selectedAlbums.forEach { album ->
                    AlbumMixSelectedAlbum(
                        album = album,
                        colors = colors,
                        onRemove = { onAlbumRemoved(album) },
                        modifier = Modifier.widthIn(min = 128.dp, max = 220.dp),
                    )
                }
            }
        }
        (builder.status ?: if (builder.loading) "Loading albums..." else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            builder.suggestedAlbums.forEach { album ->
                AlbumMixAlbumTile(
                    album = album,
                    colors = colors,
                    onClick = { onAlbumSelected(album) },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 148.dp, max = 220.dp),
                )
            }
        }
        if (showPlayMixButton && builder.selectedAlbums.isNotEmpty()) {
            PrimaryButton("Play Mix", colors, onClick = onPlayMix)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreMixBuilderContent(
    colors: NaviampColors,
    builder: SharedGenreMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onGenreSelected: (SharedGenreMixItemUi) -> Unit,
    onGenreRemoved: (SharedGenreMixItemUi) -> Unit,
    onReset: () -> Unit,
    onPlayMix: () -> Unit,
    showPlayMixButton: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Genre Mix Builder", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (builder.query.isNotBlank() || builder.selectedGenres.isNotEmpty()) {
                TextButton(onClick = onReset) {
                    Text("Reset", fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = "Search genres",
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(NaviampIcons.Search, contentDescription = "Search genres", modifier = Modifier.size(18.dp))
            }
        }
        if (builder.selectedGenres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                builder.selectedGenres.forEach { genre ->
                    GenreMixSelectedGenre(
                        genre = genre,
                        colors = colors,
                        onRemove = { onGenreRemoved(genre) },
                    )
                }
            }
        }
        (builder.status ?: if (builder.loading) "Loading genres..." else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            builder.suggestedGenres.forEach { genre ->
                GenreMixGenreRow(
                    genre = genre,
                    colors = colors,
                    onClick = { onGenreSelected(genre) },
                )
            }
        }
        if (showPlayMixButton && builder.selectedGenres.isNotEmpty()) {
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
private fun GenreMixSelectedGenre(
    genre: SharedGenreMixItemUi,
    colors: NaviampColors,
    onRemove: () -> Unit,
) {
    Text(
        genre.title,
        color = colors.primaryText,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onRemove)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun GenreMixGenreRow(
    genre: SharedGenreMixItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            genre.title,
            color = colors.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (genre.subtitle.isNotBlank()) {
            Text(genre.subtitle, color = colors.secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlbumMixSelectedAlbum(
    album: SharedMediaItemUi,
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
        PlatformCoverArt(album.coverArtUrl, colors, 30.dp, 4.dp)
        Text(album.title, color = colors.primaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumMixAlbumTile(
    album: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(142.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PlatformCoverArt(album.coverArtUrl, colors, 72.dp, 6.dp)
        Text(
            album.title,
            color = colors.primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.subtitle,
            color = colors.secondaryText,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
