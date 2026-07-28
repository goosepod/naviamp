package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.DefaultSingleColorHex
import app.naviamp.domain.settings.toggleSelectedMusicFolderId

@Composable
fun NaviampArtistDetailContent(
    colors: NaviampColors,
    screen: NaviampArtistDetailScreenUi,
    albumCollectionLayout: AlbumCollectionLayout,
    albumSortOrder: AlbumSortOrder,
    groupAlbumsByReleaseType: Boolean,
    actions: NaviampArtistDetailActions,
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    playlistActionStatus: String? = null,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val detail = screen.detail
    if (detail == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            Text(
                screen.selectedArtist?.title ?: "Artist",
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            screen.status?.let { Text(it, color = colors.secondaryText) }
        }
        return
    }
    ArtistDetailContent(
        colors = colors,
        detail = detail,
        albumCollectionLayout = albumCollectionLayout,
        albumSortOrder = albumSortOrder,
        groupAlbumsByReleaseType = groupAlbumsByReleaseType,
        onBack = actions.onBack,
        onArtistRadio = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.StartRadio),
            )
        },
        onArtistPlay = { albums ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.PlayCatalog(albums, shuffle = false),
                ),
            )
        },
        onArtistShuffle = { albums ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.PlayCatalog(albums, shuffle = true),
                ),
            )
        },
        onArtistAddToQueue = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.AddToQueue),
            )
        },
        onArtistAddToPlaylist = { playlist ->
            playlist?.let {
                actions.onArtistAction(
                    NaviampArtistDetailActionRequest(
                        detail.artist,
                        NaviampArtistDetailCommand.AddToPlaylist(it),
                    ),
                )
            }
        },
        onArtistCreatePlaylistAndAdd = { name ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.CreatePlaylistAndAdd(name),
                ),
            )
        },
        onArtistFavoriteToggled = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.ToggleFavorite),
            )
        },
        onPopularPlay = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.PlayPopular),
            )
        },
        onPopularRadio = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.StartPopularRadio),
            )
        },
        onPopularAddToQueue = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.AddPopularToQueue),
            )
        },
        onPopularTrackAction = actions.onPopularTrackAction,
        onFindSimilarArtists = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.FindSimilar),
            )
        },
        onSimilarArtistSelected = { similarArtist ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.SelectSimilar(similarArtist),
                ),
            )
        },
        onSimilarArtistExternalSelected = { url ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.OpenSimilarExternal(url),
                ),
            )
        },
        onAlbumSelected = { album ->
            actions.onAlbumAction(
                NaviampArtistAlbumActionRequest(album, NaviampArtistAlbumCommand.Select),
            )
        },
        onAlbumFavoriteToggled = { album ->
            actions.onAlbumAction(
                NaviampArtistAlbumActionRequest(album, NaviampArtistAlbumCommand.ToggleFavorite),
            )
        },
        onAlbumAction = actions.onAlbumAction,
        playlistChoices = playlistChoices,
        playlistActionStatus = playlistActionStatus,
        scrollState = scrollState,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistDetailContent(
    colors: NaviampColors,
    detail: SharedArtistDetailUi,
    albumCollectionLayout: AlbumCollectionLayout,
    albumSortOrder: AlbumSortOrder,
    groupAlbumsByReleaseType: Boolean,
    onBack: () -> Unit,
    onArtistRadio: () -> Unit,
    onArtistPlay: (List<SharedMediaItemUi>) -> Unit,
    onArtistShuffle: (List<SharedMediaItemUi>) -> Unit,
    onArtistAddToQueue: () -> Unit,
    onArtistAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onArtistCreatePlaylistAndAdd: (String) -> Unit,
    onArtistFavoriteToggled: () -> Unit,
    onPopularPlay: () -> Unit,
    onPopularRadio: () -> Unit,
    onPopularAddToQueue: () -> Unit,
    onPopularTrackAction: (SharedTrackRowActionRequest) -> Unit,
    onFindSimilarArtists: () -> Unit,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    onAlbumAction: (NaviampArtistAlbumActionRequest) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    var addArtistToPlaylistOpen by remember(detail.artist.id) { mutableStateOf(false) }
    var popularTrackForPlaylist by remember(detail.artist.id) { mutableStateOf<SharedTrackRowUi?>(null) }
    var albumForPlaylist by remember(detail.artist.id) { mutableStateOf<SharedMediaItemUi?>(null) }
    var biographyExpanded by remember(detail.artist.id) { mutableStateOf(false) }
    var artistImageOpen by remember(detail.artist.id) { mutableStateOf(false) }
    val handlePopularTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        if (request.action == SharedTrackRowAction.AddToPlaylist && request.playlistChoice == null) {
            popularTrackForPlaylist = request.track
        } else {
            onPopularTrackAction(request)
        }
    }
    val similarArtistsVisible = detail.similarArtistsExpanded
    val visibleAlbumSections = if (groupAlbumsByReleaseType) {
        detail.albumSections
    } else {
        listOf(SharedAlbumSectionUi("Albums", detail.albums))
    }.map { section ->
        section.copy(albums = section.albums.sortedForAlbumDisplay(albumSortOrder))
    }
    val displayedAlbums = visibleAlbumSections.flatMap { section -> section.albums }
    val albumMenuItems: (SharedMediaItemUi) -> List<NaviampRowMenuItem> = { album ->
        albumRowActions(
            canStartRadio = true,
            canDownload = true,
            canAddToQueue = true,
            canAddToPlaylist = true,
            canFavorite = false,
            favoriteActive = album.favoriteActive,
        ).mapNotNull { action ->
            val command = action.action.albumMediaCommandOrNull()
            when {
                action.action == NaviampAction.AddToPlaylist -> NaviampRowMenuItem(
                    action.label,
                    action.icon,
                    { albumForPlaylist = album },
                    action.enabled,
                )
                command != null -> {
                NaviampRowMenuItem(
                    action.label,
                    action.icon,
                    { onAlbumAction(NaviampArtistAlbumActionRequest(album, command)) },
                    action.enabled,
                )
                }
                else -> null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            NaviampTooltip("Back", colors) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        NaviampIcons.Back,
                        contentDescription = "Back",
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                detail.artist.title,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.clickable(
                    enabled = detail.artist.coverArtUrl != null,
                    onClick = { artistImageOpen = true },
                ),
            ) {
                NaviampCoverArt(detail.artist.coverArtUrl, colors, 96.dp, 48.dp)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    detail.artist.title,
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail.localLibraryLabel.ifBlank { "${detail.albums.size} albums" },
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                )
                NaviampResponsiveActionRow(
                    colors = colors,
                    actions = listOf(
                        NaviampDetailAction("Play artist catalog", NaviampTransportIcons.Play, { onArtistPlay(displayedAlbums) }, displayedAlbums.isNotEmpty()),
                        NaviampDetailAction("Start artist radio", NaviampTransportIcons.Radio, onArtistRadio, detail.albums.isNotEmpty()),
                        NaviampDetailAction(
                            if (detail.artist.favoriteActive) "Remove artist favorite" else "Favorite artist",
                            NaviampTransportIcons.Heart,
                            onArtistFavoriteToggled,
                            detail.artist.canFavorite,
                        ),
                        NaviampDetailAction(
                            if (similarArtistsVisible) "Hide similar artists" else "Find similar artists",
                            NaviampIcons.Artist,
                            onFindSimilarArtists,
                            selected = similarArtistsVisible,
                        ),
                        NaviampDetailAction("Add artist to queue", NaviampIcons.Queue, onArtistAddToQueue, detail.albums.isNotEmpty()),
                        NaviampDetailAction("Add artist to playlist", NaviampIcons.Playlist, { addArtistToPlaylistOpen = true }, detail.albums.isNotEmpty()),
                        NaviampDetailAction("Shuffle artist catalog", NaviampTransportIcons.Shuffle, { onArtistShuffle(displayedAlbums) }, displayedAlbums.isNotEmpty()),
                    ),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            detail.biography
                ?.normalizedBiography()
                ?.takeIf { it.isNotBlank() }
                ?.let { biography ->
                    val showMoreLink = biography.length > 260
                    Text(
                        biography,
                        color = colors.secondaryText,
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
                            color = colors.primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                biographyExpanded = !biographyExpanded
                            },
                        )
                    }
                }
            if (similarArtistsVisible) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Similar Artists".uppercase(),
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    MiniPlayerIconButton(colors, true, NaviampIcons.Artist, "Hide similar artists", onFindSimilarArtists)
                }
                detail.similarArtistsStatus?.let {
                    Text(it, color = colors.secondaryText, fontSize = 11.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    detail.similarArtists.forEach { artist ->
                        SimilarArtistRow(
                            artist = artist,
                            colors = colors,
                            onSimilarArtistSelected = onSimilarArtistSelected,
                            onSimilarArtistExternalSelected = onSimilarArtistExternalSelected,
                        )
                    }
                }
            }
            if (detail.popularTracks.isNotEmpty() || detail.popularTracksStatus != null) {
                Text(
                    "Popular Tracks".uppercase(),
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (detail.popularTracks.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MiniPlayerIconButton(colors, true, NaviampTransportIcons.Play, "Play popular tracks", onPopularPlay)
                            MiniPlayerIconButton(colors, true, NaviampTransportIcons.Radio, "Start popular tracks radio", onPopularRadio)
                            MiniPlayerIconButton(colors, true, NaviampIcons.Queue, "Add popular tracks to queue", onPopularAddToQueue)
                        }
                    }
                    detail.popularTracksStatus?.let { status ->
                        Text(status, color = colors.secondaryText, fontSize = 11.sp)
                    }
                    detail.popularTracks.forEach { track ->
                        TrackRow(
                            track,
                            colors,
                            onTrackAction = handlePopularTrackAction,
                            canSelect = true,
                            canStartRadio = false,
                            canAddToQueue = true,
                            canDownload = true,
                            canAddToPlaylist = true,
                        )
                    }
                }
            }
            Text("DISCOGRAPHY", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (detail.albums.isEmpty()) {
                Text("No albums found.", color = colors.secondaryText, fontSize = 13.sp)
            } else {
                visibleAlbumSections.forEach { section ->
                    Text(section.title.uppercase(), color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (albumCollectionLayout == AlbumCollectionLayout.Grid) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            section.albums.forEach { album ->
                                SharedAlbumGridTile(
                                    item = album,
                                    colors = colors,
                                    onClick = { onAlbumSelected(album) },
                                    menuItems = albumMenuItems(album),
                                    onFavoriteToggled = { selected ->
                                        onAlbumAction(
                                            NaviampArtistAlbumActionRequest(
                                                selected,
                                                NaviampArtistAlbumCommand.ToggleFavorite,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            section.albums.forEach { album ->
                                SharedMediaRow(
                                    item = album,
                                    colors = colors,
                                    onClick = { onAlbumSelected(album) },
                                    menuItems = albumMenuItems(album),
                                    onFavoriteToggled = onAlbumFavoriteToggled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (addArtistToPlaylistOpen) {
        AddToPlaylistDialog(
            title = detail.artist.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { addArtistToPlaylistOpen = false },
            onAddToExisting = { playlist ->
                addArtistToPlaylistOpen = false
                onArtistAddToPlaylist(playlist)
            },
            onCreateAndAdd = { name ->
                addArtistToPlaylistOpen = false
                onArtistCreatePlaylistAndAdd(name)
            },
        )
    }

    popularTrackForPlaylist?.let { track ->
        AddToPlaylistDialog(
            title = track.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { popularTrackForPlaylist = null },
            onAddToExisting = { playlist ->
                popularTrackForPlaylist = null
                handlePopularTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.AddToPlaylist,
                        playlistChoice = playlist,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                popularTrackForPlaylist = null
                handlePopularTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    ),
                )
            },
        )
    }

    albumForPlaylist?.let { album ->
        AddToPlaylistDialog(
            title = album.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { albumForPlaylist = null },
            onAddToExisting = { playlist ->
                albumForPlaylist = null
                onAlbumAction(
                    NaviampArtistAlbumActionRequest(
                        album,
                        NaviampArtistAlbumCommand.AddToPlaylist(playlist),
                    ),
                )
            },
            onCreateAndAdd = { name ->
                albumForPlaylist = null
                onAlbumAction(
                    NaviampArtistAlbumActionRequest(
                        album,
                        NaviampArtistAlbumCommand.CreatePlaylistAndAdd(name),
                    ),
                )
            },
        )
    }

    if (artistImageOpen) {
        ExpandedMediaImageDialog(
            imageUrl = detail.artist.coverArtUrl,
            colors = colors,
            onDismissRequest = { artistImageOpen = false },
        )
    }
}

@Composable
private fun SimilarArtistRow(
    artist: SharedSimilarArtistUi,
    colors: NaviampColors,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
) {
    val opensLocalArtist = artist.localArtistId != null
    val externalUrl = artist.externalUrl
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(enabled = opensLocalArtist || externalUrl != null) {
                if (opensLocalArtist) {
                    onSimilarArtistSelected(artist)
                } else if (externalUrl != null) {
                    onSimilarArtistExternalSelected(externalUrl)
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        NaviampCoverArt(artist.imageUrl, colors, 42.dp, 21.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                artist.title,
                color = colors.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist.subtitle,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!opensLocalArtist && externalUrl != null) {
            NaviampTooltip("View in browser", colors) {
                IconButton(
                    onClick = { onSimilarArtistExternalSelected(externalUrl) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = NaviampIcons.ExternalLink,
                        contentDescription = "View in browser",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            Icon(
                imageVector = NaviampIcons.ChevronRight,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(18.dp),
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

private val ArtistActionsExpandedMinWidth = 232.dp
