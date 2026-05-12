package app.naviamp.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Genre
import app.naviamp.domain.Playlist

data class HomeContent(
    val recentlyAddedAlbums: List<Album> = emptyList(),
    val mixAlbums: List<Album> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val frequentAlbums: List<Album> = emptyList(),
    val randomAlbums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val genreSpotlight: Genre? = null,
    val genreSpotlightAlbums: List<Album> = emptyList(),
    val decadeLabel: String = "The 2000s",
    val decadeFromYear: Int = 2000,
    val decadeToYear: Int = 2009,
    val decadeAlbums: List<Album> = emptyList(),
) {
    val isEmpty: Boolean
        get() = recentlyAddedAlbums.isEmpty() &&
            mixAlbums.isEmpty() &&
            recentAlbums.isEmpty() &&
            frequentAlbums.isEmpty() &&
            randomAlbums.isEmpty() &&
            playlists.isEmpty() &&
            genreSpotlightAlbums.isEmpty() &&
            decadeAlbums.isEmpty()
}

@Composable
fun HomePanel(
    appColors: AppColors,
    connectionStatus: String?,
    homeContent: HomeContent,
    coverArtUrl: (String?) -> String?,
    onAlbumSelected: (Album) -> Unit,
    onAlbumRadioSelected: (Album) -> Unit,
    onAlbumDownloadSelected: (Album) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onPlaylistDownloadSelected: (Playlist) -> Unit,
    onLibraryRadioSelected: () -> Unit,
    onRandomAlbumRadioSelected: () -> Unit,
    onGenreRadioSelected: (Genre) -> Unit,
    onDecadeRadioSelected: (Int, Int) -> Unit,
    onOpenArtistMixBuilder: () -> Unit,
    onOpenAlbumMixBuilder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Music", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)
            connectionStatus?.let {
                Text(it, color = appColors.secondaryText, fontSize = 11.sp)
            }
        }

        if (homeContent.isEmpty) {
            Text("Home sections will appear after connection.", color = appColors.secondaryText)
        }

        if (homeContent.mixAlbums.isNotEmpty()) {
            HomeSection(title = "Mixes For You", appColors = appColors) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    homeContent.mixAlbums.take(6).forEach { album ->
                        MixCard(
                            appColors = appColors,
                            album = album,
                            coverArtUrl = coverArtUrl(album.coverArtId),
                            onClick = { onAlbumRadioSelected(album) },
                        )
                    }
                }
            }
        }

        HomeAlbumSection(
            title = "Recently Added In Music",
            appColors = appColors,
            albums = homeContent.recentlyAddedAlbums,
            coverArtUrl = coverArtUrl,
            onAlbumSelected = onAlbumSelected,
            onAlbumRadioSelected = onAlbumRadioSelected,
            onAlbumDownloadSelected = onAlbumDownloadSelected,
        )

        if (homeContent.playlists.isNotEmpty()) {
            HomeSection(title = "Recent Playlists", appColors = appColors) {
                homeContent.playlists.take(5).forEach { playlist ->
                    PlaylistRow(
                        appColors = appColors,
                        playlist = playlist,
                        coverArtUrl = coverArtUrl(playlist.coverArtId),
                        onClick = { onPlaylistSelected(playlist) },
                        onDownload = { onPlaylistDownloadSelected(playlist) },
                    )
                }
            }
        }

        HomeSection(title = "Stations", appColors = appColors) {
            StationRow(appColors, "Library Radio", "Random tracks from your full library", onLibraryRadioSelected)
            StationRow(appColors, "Random Album Radio", "Start from a random album", onRandomAlbumRadioSelected)
            homeContent.genreSpotlight?.let { genre ->
                StationRow(appColors, "${genre.name} Radio", "A random ${genre.name} station") {
                    onGenreRadioSelected(genre)
                }
            }
            StationRow(appColors, "${homeContent.decadeLabel} Radio", "Random songs from ${homeContent.decadeLabel}") {
                onDecadeRadioSelected(homeContent.decadeFromYear, homeContent.decadeToYear)
            }
            StationRow(appColors, "Artist Mix Builder", "Pick an artist from your library", onOpenArtistMixBuilder)
            StationRow(appColors, "Album Mix Builder", "Pick an album from your library", onOpenAlbumMixBuilder)
        }

        HomeAlbumSection(
            title = "Recent Albums",
            appColors = appColors,
            albums = homeContent.recentAlbums,
            coverArtUrl = coverArtUrl,
            onAlbumSelected = onAlbumSelected,
            onAlbumRadioSelected = onAlbumRadioSelected,
            onAlbumDownloadSelected = onAlbumDownloadSelected,
        )

        HomeAlbumSection(
            title = "Frequently Played Albums",
            appColors = appColors,
            albums = homeContent.frequentAlbums,
            coverArtUrl = coverArtUrl,
            onAlbumSelected = onAlbumSelected,
            onAlbumRadioSelected = onAlbumRadioSelected,
            onAlbumDownloadSelected = onAlbumDownloadSelected,
        )

        HomeAlbumSection(
            title = "Random Albums",
            appColors = appColors,
            albums = homeContent.randomAlbums,
            coverArtUrl = coverArtUrl,
            onAlbumSelected = onAlbumSelected,
            onAlbumRadioSelected = onAlbumRadioSelected,
            onAlbumDownloadSelected = onAlbumDownloadSelected,
        )

        homeContent.genreSpotlight?.let { genre ->
            HomeAlbumSection(
                title = "More In ${genre.name}",
                appColors = appColors,
                albums = homeContent.genreSpotlightAlbums,
                coverArtUrl = coverArtUrl,
                onAlbumSelected = onAlbumSelected,
                onAlbumRadioSelected = onAlbumRadioSelected,
                onAlbumDownloadSelected = onAlbumDownloadSelected,
            )
        }

        HomeAlbumSection(
            title = "From ${homeContent.decadeLabel}",
            appColors = appColors,
            albums = homeContent.decadeAlbums,
            coverArtUrl = coverArtUrl,
            onAlbumSelected = onAlbumSelected,
            onAlbumRadioSelected = onAlbumRadioSelected,
            onAlbumDownloadSelected = onAlbumDownloadSelected,
        )
    }
}

@Composable
private fun HomeAlbumSection(
    title: String,
    appColors: AppColors,
    albums: List<Album>,
    coverArtUrl: (String?) -> String?,
    onAlbumSelected: (Album) -> Unit,
    onAlbumRadioSelected: (Album) -> Unit,
    onAlbumDownloadSelected: (Album) -> Unit,
) {
    if (albums.isEmpty()) return
    HomeSection(title = title, appColors = appColors) {
        albums.take(5).forEach { album ->
            AlbumRow(
                appColors = appColors,
                album = album,
                coverArtUrl = coverArtUrl(album.coverArtId),
                coverArtSize = 40.dp,
                verticalPadding = 2.dp,
                onClick = { onAlbumSelected(album) },
                onStartRadio = { onAlbumRadioSelected(album) },
                onDownload = { onAlbumDownloadSelected(album) },
            )
        }
    }
}

@Composable
private fun MixCard(
    appColors: AppColors,
    album: Album,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(154.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        CoverArtThumb(
            appColors = appColors,
            coverArtUrl = coverArtUrl,
            size = 154.dp,
            cornerRadius = 6.dp,
        )
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .matchParentSize()
                .padding(8.dp),
        ) {
            Text(
                "${album.artistName} Mix",
                color = appColors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.title,
                color = appColors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    appColors: AppColors,
    playlist: Playlist,
    coverArtUrl: String?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    MediaRow(appColors = appColors, onClick = onClick) {
        CoverArtThumb(
            appColors = appColors,
            coverArtUrl = coverArtUrl,
            size = 38.dp,
            cornerRadius = 4.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                color = appColors.primaryText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )
            Text(
                playlist.summaryLabel(),
                color = appColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
        Text("Playlist", color = appColors.mutedText, fontSize = 11.sp)
        RowOverflowMenu(
            appColors = appColors,
            items = listOf(RowMenuItem("Download playlist", NavigationIcons.Downloads, onDownload)),
        )
    }
}

@Composable
private fun StationRow(
    appColors: AppColors,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    MediaRow(appColors = appColors, onClick = onClick) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            Text("♪", color = appColors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = appColors.primaryText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )
            Text(
                subtitle,
                color = appColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
        Text("›", color = appColors.mutedText, fontSize = 18.sp)
    }
}

@Composable
private fun HomeSection(
    title: String,
    appColors: AppColors,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title.uppercase(),
            color = appColors.primaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content()
        }
    }
}

private fun Playlist.summaryLabel(): String =
    listOfNotNull(
        "$trackCount tracks",
        durationSeconds?.durationSummary(),
    ).joinToString(" - ")

private fun Int.durationSummary(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
