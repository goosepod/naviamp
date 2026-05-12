package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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

@Composable
fun ArtistDetailPanel(
    appColors: AppColors,
    artist: Artist?,
    artistDetails: ArtistDetails?,
    status: String?,
    coverArtUrl: (String?) -> String?,
    onBack: () -> Unit,
    onArtistRadio: (Artist) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onAlbumRadioSelected: (Album) -> Unit,
    onAlbumDownloadSelected: (Album) -> Unit,
) {
    val effectiveArtist = artistDetails?.artist ?: artist
    val imageUrl = artistDetails?.info?.largeImageUrl
        ?: artistDetails?.info?.mediumImageUrl
        ?: artistDetails?.info?.smallImageUrl
    var biographyExpanded by remember(effectiveArtist?.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Button(
                        enabled = details.albums.isNotEmpty(),
                        onClick = { effectiveArtist?.let(onArtistRadio) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("Radio")
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
                    )
                }
            }
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
