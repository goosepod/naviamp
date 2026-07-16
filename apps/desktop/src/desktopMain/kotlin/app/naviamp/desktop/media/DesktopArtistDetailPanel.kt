package app.naviamp.desktop

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import app.naviamp.domain.media.groupedByReleaseSection
import app.naviamp.domain.media.sortedForAlbumDisplay
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.ExpandedMediaImageDialog
import app.naviamp.ui.NaviampDetailAction
import app.naviamp.ui.NaviampResponsiveActionRow
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedAlbumGridTile
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackGroupAction
import app.naviamp.ui.SharedTrackGroupActionRequest
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.actionRequest
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedTrackRowUi
import app.naviamp.ui.albumRowActions
import app.naviamp.ui.NaviampAction
import app.naviamp.ui.NaviampRowMenuItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopArtistDetailPanel(
    appColors: DesktopAppColors,
    artist: Artist?,
    artistDetails: ArtistDetails?,
    popularTracks: List<Track>,
    similarArtists: List<SimilarArtistMatch>,
    status: String?,
    popularTracksStatus: String?,
    similarArtistsStatus: String?,
    coverArtUrl: (String?) -> String?,
    albumCollectionLayout: AlbumCollectionLayout,
    albumSortOrder: AlbumSortOrder,
    groupAlbumsByReleaseType: Boolean,
    onBack: () -> Unit,
    onSimilarArtistSelected: (Artist) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onArtistAction: (SharedMediaItemActionRequest) -> Unit,
    onArtistCatalogPlay: (List<Album>, Boolean) -> Unit,
    onPopularTracksAction: (SharedTrackGroupActionRequest) -> Unit,
    onPopularTrackAction: (SharedTrackRowActionRequest) -> Unit,
    onAlbumAction: (SharedMediaItemActionRequest) -> Unit,
) {
    val effectiveArtist = artistDetails?.artist ?: artist
    val imageUrl = artistDetails?.info?.largeImageUrl
        ?: artistDetails?.info?.mediumImageUrl
        ?: artistDetails?.info?.smallImageUrl
    var biographyExpanded by remember(effectiveArtist?.id) { mutableStateOf(false) }
    var artistImageOpen by remember(effectiveArtist?.id) { mutableStateOf(false) }
    val similarArtistsVisible = similarArtists.isNotEmpty() || similarArtistsStatus != null
    val visibleAlbumSections = artistDetails?.let { details ->
        if (groupAlbumsByReleaseType) {
            details.albums.groupedByReleaseSection()
        } else {
            listOf(
                app.naviamp.domain.media.AlbumReleaseSectionGroup(
                    section = app.naviamp.domain.media.AlbumReleaseSection.Albums,
                    albums = details.albums,
                ),
            )
        }.map { section ->
            section.copy(albums = section.albums.sortedForAlbumDisplay(albumSortOrder))
        }
    }.orEmpty()
    val displayedAlbums = visibleAlbumSections.flatMap { section -> section.albums }
    val artistItem = effectiveArtist?.toSharedMediaItemUi(
        coverArtUrl = { imageUrl },
        canFavorite = true,
    )
    fun requestArtistAction(action: SharedMediaItemAction) {
        artistItem?.let { item ->
            onArtistAction(item.actionRequest(action, kind = SharedMediaItemKind.Artist))
        }
    }
    fun requestPopularTracksAction(action: SharedTrackGroupAction) {
        onPopularTracksAction(
            SharedTrackGroupActionRequest(
                tracks = popularTracks.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
                action = action,
            ),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                    imageVector = DesktopNavigationIcons.Back,
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
            Box(
                modifier = Modifier.clickable(
                    enabled = imageUrl != null,
                    onClick = { artistImageOpen = true },
                ),
            ) {
                DesktopCoverArtThumb(
                    appColors = appColors,
                    coverArtUrl = imageUrl,
                    size = 96.dp,
                    cornerRadius = 48.dp,
                )
            }
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
                    NaviampResponsiveActionRow(
                        colors = appColors,
                        actions = listOf(
                            NaviampDetailAction("Play artist catalog", TransportIcons.Play, { onArtistCatalogPlay(displayedAlbums, false) }, displayedAlbums.isNotEmpty()),
                            NaviampDetailAction("Start artist radio", TransportIcons.Radio, { requestArtistAction(SharedMediaItemAction.StartRadio) }, details.albums.isNotEmpty()),
                            NaviampDetailAction(
                                if (effectiveArtist?.favoritedAtIso8601 != null) "Remove artist favorite" else "Favorite artist",
                                TransportIcons.Heart,
                                { requestArtistAction(SharedMediaItemAction.ToggleFavorite) },
                                effectiveArtist != null,
                            ),
                            NaviampDetailAction(
                                if (similarArtistsVisible) "Hide similar artists" else "Find similar artists",
                                DesktopNavigationIcons.Artist,
                                { requestArtistAction(SharedMediaItemAction.FindSimilar) },
                                effectiveArtist != null,
                                selected = similarArtistsVisible,
                            ),
                            NaviampDetailAction("Add artist to queue", DesktopNavigationIcons.Queue, { requestArtistAction(SharedMediaItemAction.AddToQueue) }, details.albums.isNotEmpty()),
                            NaviampDetailAction("Add artist to playlist", DesktopNavigationIcons.Playlist, { requestArtistAction(SharedMediaItemAction.AddToPlaylist) }, details.albums.isNotEmpty()),
                            NaviampDetailAction("Shuffle artist catalog", TransportIcons.Shuffle, { onArtistCatalogPlay(displayedAlbums, true) }, displayedAlbums.isNotEmpty()),
                        ),
                    )
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
            ) {
                if (similarArtistsVisible) {
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
                            icon = DesktopNavigationIcons.Artist,
                            contentDescription = "Hide similar artists",
                            enabled = effectiveArtist != null,
                            onClick = { requestArtistAction(SharedMediaItemAction.FindSimilar) },
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
                                onClick = { requestPopularTracksAction(SharedTrackGroupAction.Play) },
                            )
                            DetailActionIconButton(
                                appColors = appColors,
                                icon = TransportIcons.Radio,
                                contentDescription = "Start popular tracks radio",
                                enabled = true,
                                onClick = { requestPopularTracksAction(SharedTrackGroupAction.StartRadio) },
                            )
                            DetailActionIconButton(
                                appColors = appColors,
                                icon = DesktopNavigationIcons.Queue,
                                contentDescription = "Add popular tracks to queue",
                                enabled = true,
                                onClick = { requestPopularTracksAction(SharedTrackGroupAction.AddToQueue) },
                            )
                        }
                    }
                    popularTracksStatus?.let {
                        Text(it, color = appColors.secondaryText, fontSize = 11.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        popularTracks.forEach { track ->
                            DesktopTrackRow(
                                appColors = appColors,
                                track = track,
                                coverArtUrl = coverArtUrl(track.coverArtId),
                                showCoverArt = true,
                                canStartRadio = true,
                                canDownload = false,
                                canAddToQueue = true,
                                canGoToArtist = false,
                                onTrackAction = onPopularTrackAction,
                            )
                        }
                    }
                }
                Text(
                    "Discography".uppercase(),
                    color = appColors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                visibleAlbumSections.forEach { section ->
                    Text(
                        section.section.label.uppercase(),
                        color = appColors.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    if (albumCollectionLayout == AlbumCollectionLayout.Grid) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            section.albums.forEach { album ->
                                val item = album.toSharedMediaItemUi(coverArtUrl, canFavorite = true)
                                val menuItems = albumRowActions(
                                    canStartRadio = true,
                                    canDownload = true,
                                    canAddToQueue = true,
                                    canAddToPlaylist = true,
                                    canFavorite = false,
                                    favoriteActive = item.favoriteActive,
                                ).mapNotNull { action ->
                                    val sharedAction = when (action.action) {
                                        NaviampAction.StartAlbumRadio -> SharedMediaItemAction.StartRadio
                                        NaviampAction.DownloadAlbum -> SharedMediaItemAction.Download
                                        NaviampAction.AddToQueue -> SharedMediaItemAction.AddToQueue
                                        NaviampAction.AddToPlaylist -> SharedMediaItemAction.AddToPlaylist
                                        else -> null
                                    }
                                    sharedAction?.let { mappedAction ->
                                        NaviampRowMenuItem(
                                            action.label,
                                            action.icon,
                                            { onAlbumAction(item.actionRequest(mappedAction, kind = SharedMediaItemKind.Album)) },
                                            action.enabled,
                                        )
                                    }
                                }
                                SharedAlbumGridTile(
                                    item = item,
                                    colors = appColors,
                                    onClick = { onAlbumAction(item.actionRequest(SharedMediaItemAction.Select, kind = SharedMediaItemKind.Album)) },
                                    menuItems = menuItems,
                                    onFavoriteToggled = { selected ->
                                        onAlbumAction(selected.actionRequest(SharedMediaItemAction.ToggleFavorite, kind = SharedMediaItemKind.Album))
                                    },
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            section.albums.forEach { album ->
                                DesktopAlbumRow(
                                    appColors = appColors,
                                    album = album,
                                    coverArtUrl = coverArtUrl(album.coverArtId),
                                    canStartRadio = true,
                                    canDownload = true,
                                    canAddToQueue = true,
                                    canAddToPlaylist = true,
                                    canFavorite = true,
                                    onItemAction = onAlbumAction,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (artistImageOpen) {
        ExpandedMediaImageDialog(
            imageUrl = imageUrl,
            colors = appColors,
            onDismissRequest = { artistImageOpen = false },
        )
    }
}

@Composable
private fun SimilarArtistRow(
    appColors: DesktopAppColors,
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
        DesktopCoverArtThumb(
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
                if (localArtist != null) "In library" else "View in browser",
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
                    imageVector = DesktopNavigationIcons.ExternalLink,
                    contentDescription = "View in browser",
                    tint = appColors.secondaryText,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Icon(
                imageVector = DesktopNavigationIcons.ChevronRight,
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

private val ArtistActionsExpandedMinWidth = 240.dp
