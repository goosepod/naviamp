package app.naviamp.desktop

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.popular.SimilarArtistMatch

@Composable
fun ArtistDetailPanel(
    appColors: AppColors,
    artist: Artist?,
    artistDetails: ArtistDetails?,
    popularTracks: List<Track>,
    similarArtists: List<SimilarArtistMatch>,
    status: String?,
    popularTracksStatus: String?,
    similarArtistsStatus: String?,
    coverArtUrl: (String?) -> String?,
    onBack: () -> Unit,
    onArtistRadio: (Artist) -> Unit,
    onFindSimilarArtists: (Artist) -> Unit,
    onSimilarArtistSelected: (Artist) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onPopularTracksPlay: (List<Track>) -> Unit,
    onPopularTracksRadio: (List<Track>) -> Unit,
    onPopularTracksAddToQueue: (List<Track>) -> Unit,
    onPopularTrackSelected: (Track) -> Unit,
    onPopularTrackAddToQueue: (Track) -> Unit,
    onAddArtistToPlaylist: (Artist) -> Unit,
    onAddArtistToQueue: (Artist) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onAlbumRadioSelected: (Album) -> Unit,
    onAlbumDownloadSelected: (Album) -> Unit,
    onAlbumAddToQueue: (Album) -> Unit,
    onAlbumAddToPlaylist: (Album) -> Unit,
) {
    val effectiveArtist = artistDetails?.artist ?: artist
    val imageUrl = artistDetails?.info?.largeImageUrl
        ?: artistDetails?.info?.mediumImageUrl
        ?: artistDetails?.info?.smallImageUrl
    var biographyExpanded by remember(effectiveArtist?.id) { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = NavigationIcons.Back,
                    contentDescription = "Back",
                    tint = appColors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                effectiveArtist?.name ?: "Artist",
                color = appColors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CoverArtThumb(
                appColors = appColors,
                coverArtUrl = imageUrl,
                size = 96.dp,
                cornerRadius = 48.dp,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    effectiveArtist?.name ?: "",
                    color = appColors.primaryText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                status?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 11.sp)
                }
                artistDetails?.let { details ->
                    Text(
                        "${details.albums.size} albums, EPs, and singles",
                        color = appColors.secondaryText,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = TransportIcons.Radio,
                            contentDescription = "Start artist radio",
                            enabled = details.albums.isNotEmpty(),
                            onClick = { effectiveArtist?.let(onArtistRadio) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = NavigationIcons.Queue,
                            contentDescription = "Add artist to queue",
                            enabled = details.albums.isNotEmpty(),
                            onClick = { effectiveArtist?.let(onAddArtistToQueue) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = NavigationIcons.Playlist,
                            contentDescription = "Add artist to playlist",
                            enabled = details.albums.isNotEmpty(),
                            onClick = { effectiveArtist?.let(onAddArtistToPlaylist) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = TransportIcons.Play,
                            contentDescription = "Play popular tracks",
                            enabled = popularTracks.isNotEmpty(),
                            onClick = { onPopularTracksPlay(popularTracks) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = NavigationIcons.Artist,
                            contentDescription = "Find similar artists",
                            enabled = effectiveArtist != null,
                            onClick = { effectiveArtist?.let(onFindSimilarArtists) },
                        )
                    }
                    details.info?.biography
                        ?.takeIf { it.isNotBlank() }
                        ?.let { biography ->
                            val normalizedBiography = biography.normalizedBiography()
                            val showMoreLink = normalizedBiography.length > 260
                            Text(
                                normalizedBiography,
                                color = appColors.secondaryText,
                                maxLines = if (biographyExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                ),
                            )
                            if (showMoreLink) {
                                Text(
                                    if (biographyExpanded) "Less" else "More...",
                                    color = appColors.primaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        biographyExpanded = !biographyExpanded
                                    },
                                )
                            }
                        }
                }
            }
        }

        artistDetails?.let { details ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (similarArtists.isNotEmpty() || similarArtistsStatus != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Similar Artists".uppercase(),
                            color = appColors.primaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = NavigationIcons.Artist,
                            contentDescription = "Refresh similar artists",
                            enabled = effectiveArtist != null,
                            onClick = { effectiveArtist?.let(onFindSimilarArtists) },
                        )
                    }
                    similarArtistsStatus?.let {
                        Text(it, color = appColors.secondaryText, fontSize = 11.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        similarArtists.forEach { similarArtist ->
                            SimilarArtistRow(
                                appColors = appColors,
                                similarArtist = similarArtist,
                                onSimilarArtistSelected = onSimilarArtistSelected,
                                onSimilarArtistExternalSelected = onSimilarArtistExternalSelected,
                            )
                        }
                    }
                }
                if (popularTracks.isNotEmpty() || popularTracksStatus != null) {
                    Text(
                        "Popular Tracks".uppercase(),
                        color = appColors.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    if (popularTracks.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            DetailActionIconButton(
                                appColors = appColors,
                                icon = TransportIcons.Play,
                                contentDescription = "Play popular tracks",
                                enabled = true,
                                onClick = { onPopularTracksPlay(popularTracks) },
                            )
                            DetailActionIconButton(
                                appColors = appColors,
                                icon = TransportIcons.Radio,
                                contentDescription = "Start popular tracks radio",
                                enabled = true,
                                onClick = { onPopularTracksRadio(popularTracks) },
                            )
                            DetailActionIconButton(
                                appColors = appColors,
                                icon = NavigationIcons.Queue,
                                contentDescription = "Add popular tracks to queue",
                                enabled = true,
                                onClick = { onPopularTracksAddToQueue(popularTracks) },
                            )
                        }
                    }
                    popularTracksStatus?.let {
                        Text(it, color = appColors.secondaryText, fontSize = 11.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        popularTracks.forEach { track ->
                            TrackRow(
                                appColors = appColors,
                                track = track,
                                coverArtUrl = coverArtUrl(track.coverArtId),
                                showCoverArt = true,
                                onClick = { onPopularTrackSelected(track) },
                                onStartRadio = { onPopularTracksRadio(listOf(track)) },
                                onDownload = {},
                                onAddToQueue = { onPopularTrackAddToQueue(track) },
                            )
                        }
                    }
                }
                Text(
                    "Albums".uppercase(),
                    color = appColors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    details.albums.forEach { album ->
                        AlbumRow(
                            appColors = appColors,
                            album = album,
                            coverArtUrl = coverArtUrl(album.coverArtId),
                            onClick = { onAlbumSelected(album) },
                            onStartRadio = { onAlbumRadioSelected(album) },
                            onDownload = { onAlbumDownloadSelected(album) },
                            onAddToQueue = { onAlbumAddToQueue(album) },
                            onAddToPlaylist = { onAlbumAddToPlaylist(album) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarArtistRow(
    appColors: AppColors,
    similarArtist: SimilarArtistMatch,
    onSimilarArtistSelected: (Artist) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
) {
    val localArtist = similarArtist.matchedArtist
    val externalUrl = similarArtist.candidate.externalUrl
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = localArtist != null || externalUrl != null) {
                when {
                    localArtist != null -> onSimilarArtistSelected(localArtist)
                    externalUrl != null -> onSimilarArtistExternalSelected(externalUrl)
                }
            },
    ) {
        CoverArtThumb(
            appColors = appColors,
            coverArtUrl = similarArtist.candidate.imageUrl,
            size = 36.dp,
            cornerRadius = 18.dp,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                similarArtist.candidate.name,
                color = appColors.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (localArtist != null) "In library" else "Deezer",
                color = appColors.secondaryText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (localArtist == null && externalUrl != null) {
            IconButton(
                onClick = { onSimilarArtistExternalSelected(externalUrl) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = NavigationIcons.ExternalLink,
                    contentDescription = "Open Deezer artist page",
                    tint = appColors.secondaryText,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Icon(
                imageVector = NavigationIcons.ChevronRight,
                contentDescription = null,
                tint = appColors.secondaryText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun String.normalizedBiography(): String =
    trim()
        .replace(Regex("[\\t ]+"), " ")
        .split(Regex("\\R\\s*\\R+"))
        .joinToString("\n\n") { paragraph ->
            paragraph
                .replace(Regex("\\s*\\R\\s*"), " ")
                .trim()
        }
