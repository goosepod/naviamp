package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track

@Composable
fun MediaRow(
    appColors: AppColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Boolean = true,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 3.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (background) {
                Modifier.background(MediaRowOverlay, RoundedCornerShape(5.dp))
            } else {
                Modifier
            },
        )
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }

    Row(
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        modifier = rowModifier,
        content = content,
    )
}

@Composable
fun ArtistRow(
    appColors: AppColors,
    artist: Artist,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
) {
    MediaRow(appColors = appColors, modifier = modifier, onClick = onClick) {
        MediaTextBlock(
            appColors = appColors,
            title = artist.name,
            subtitle = "Artist",
            titleStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        RowOverflowMenu(
            appColors = appColors,
            items = listOfNotNull(
                onStartRadio?.let { RowMenuItem("Start artist radio", it) },
            ),
        )
    }
}

@Composable
fun AlbumRow(
    appColors: AppColors,
    album: Album,
    coverArtUrl: String?,
    modifier: Modifier = Modifier,
    coverArtSize: Dp = 34.dp,
    verticalPadding: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
) {
    MediaRow(
        appColors = appColors,
        modifier = modifier,
        onClick = onClick,
        verticalPadding = verticalPadding,
    ) {
        CoverArtThumb(
            appColors = appColors,
            coverArtUrl = coverArtUrl,
            size = coverArtSize,
            cornerRadius = 4.dp,
        )
        MediaTextBlock(
            appColors = appColors,
            title = album.title,
            subtitle = album.artistName,
            titleStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        Text("Album", color = appColors.mutedText, fontSize = 11.sp)
        album.releaseYear?.let {
            Text(it.toString(), color = appColors.mutedText, fontSize = 11.sp)
        }
        RowOverflowMenu(
            appColors = appColors,
            items = listOfNotNull(
                onStartRadio?.let { RowMenuItem("Start album radio", it) },
                onDownload?.let { RowMenuItem("Download album", it) },
            ),
        )
    }
}

@Composable
fun TrackRow(
    appColors: AppColors,
    track: Track,
    modifier: Modifier = Modifier,
    coverArtUrl: String? = null,
    showCoverArt: Boolean = false,
    coverArtSize: Dp = 34.dp,
    index: Int? = null,
    titleStyle: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    subtitleStyle: TextStyle = TextStyle(fontSize = 11.sp),
    subtitle: String = listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
    background: Boolean = true,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 3.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    showDuration: Boolean = true,
    showMenu: Boolean = false,
    onStartRadio: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    MediaRow(
        appColors = appColors,
        modifier = modifier,
        onClick = onClick,
        background = background,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        verticalAlignment = verticalAlignment,
    ) {
        leadingContent?.invoke(this)
        if (index != null) {
            Text(
                index.toString(),
                color = appColors.mutedText,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (showCoverArt) {
            CoverArtThumb(
                appColors = appColors,
                coverArtUrl = coverArtUrl,
                size = coverArtSize,
                cornerRadius = 4.dp,
            )
        }
        MediaTextBlock(
            appColors = appColors,
            title = track.title,
            subtitle = subtitle,
            titleStyle = titleStyle,
            subtitleStyle = subtitleStyle,
            modifier = Modifier.weight(1f),
        )
        TrackMetadataTrailing(
            appColors = appColors,
            track = track,
            showDuration = showDuration,
        )
        if (showMenu || onStartRadio != null || onDownload != null) {
            RowOverflowMenu(
                appColors = appColors,
                items = listOfNotNull(
                    onStartRadio?.let { RowMenuItem("Start track radio", it) },
                    onDownload?.let { RowMenuItem("Download track", it) },
                ),
            )
        }
    }
}

@Composable
fun RowOverflowMenu(
    appColors: AppColors,
    items: List<RowMenuItem>,
) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp),
        ) {
            Text("⋮", color = appColors.mutedText, fontSize = 15.sp)
        }
        NaviampDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                NaviampDropdownMenuItem(
                    label = item.label,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

data class RowMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun TrackMetadataTrailing(
    appColors: AppColors,
    track: Track,
    showDuration: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        track.compactFavoriteRatingLabel()?.let {
            Text(it, color = appColors.primaryText, fontSize = 11.sp)
        }
        if (showDuration) {
            Text(track.durationLabel(), color = appColors.mutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MediaTextBlock(
    appColors: AppColors,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = TextStyle(fontWeight = FontWeight.SemiBold),
    subtitleStyle: TextStyle = TextStyle(fontSize = 11.sp),
) {
    Column(modifier = modifier) {
        Text(
            title,
            color = appColors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = titleStyle,
        )
        Text(
            subtitle,
            color = appColors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = subtitleStyle,
        )
    }
}

private val MediaRowOverlay = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
