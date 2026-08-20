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
fun NaviampAlbumDetailContent(
    colors: NaviampColors,
    screen: NaviampAlbumDetailScreenUi,
    actions: NaviampAlbumDetailActions,
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    playlistActionStatus: String? = null,
) {
    val detail = screen.detail
    if (detail == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            Text(
                screen.selectedAlbum?.title ?: "Album",
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            screen.status?.let { Text(it, color = colors.secondaryText) }
        }
        return
    }
    AlbumDetailContent(
        colors = colors,
        detail = detail,
        onBack = actions.onBack,
        onPlayAlbum = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.Play(false)),
            )
        },
        onShuffleAlbum = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.Play(true)),
            )
        },
        onAlbumRadio = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.StartRadio),
            )
        },
        onAlbumDownload = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.Download),
            )
        },
        onAlbumAddToQueue = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.AddToQueue),
            )
        },
        onAlbumAddToPlaylist = { playlist ->
            playlist?.let {
                actions.onAlbumAction(
                    NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.AddToPlaylist(it)),
                )
            }
        },
        onAlbumCreatePlaylistAndAdd = { name ->
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(
                    detail.album,
                    NaviampAlbumDetailCommand.CreatePlaylistAndAdd(name),
                ),
            )
        },
        onAlbumFavoriteToggled = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.ToggleFavorite),
            )
        },
        playbackProfile = screen.playbackProfile,
        playbackProfileStatus = screen.playbackProfileStatus,
        onPlaybackProfileSaved = { profile ->
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(
                    detail.album,
                    NaviampAlbumDetailCommand.SavePlaybackProfile(profile),
                ),
            )
        },
        onArtistSelected = actions.onArtistSelected,
        onTrackAction = actions.onTrackAction,
        playlistChoices = playlistChoices,
        playlistActionStatus = playlistActionStatus,
    )
}

@Composable
private fun AlbumDetailContent(
    colors: NaviampColors,
    detail: SharedAlbumDetailUi,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onAlbumRadio: () -> Unit,
    onAlbumDownload: () -> Unit,
    onAlbumAddToQueue: () -> Unit,
    onAlbumAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onAlbumCreatePlaylistAndAdd: (String) -> Unit,
    onAlbumFavoriteToggled: () -> Unit,
    playbackProfile: app.naviamp.domain.playback.PlaybackProfile,
    playbackProfileStatus: String?,
    onPlaybackProfileSaved: (app.naviamp.domain.playback.PlaybackProfile) -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    var addAlbumToPlaylistOpen by remember(detail.album.id) { mutableStateOf(false) }
    var trackForPlaylist by remember(detail.album.id) { mutableStateOf<SharedTrackRowUi?>(null) }
    var albumImageOpen by remember(detail.album.id) { mutableStateOf(false) }
    var informationExpanded by remember(detail.album.id) { mutableStateOf(false) }
    var playbackProfileOpen by remember(detail.album.id) { mutableStateOf(false) }
    val handleTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        if (request.action == SharedTrackRowAction.AddToPlaylist && request.playlistChoice == null) {
            trackForPlaylist = request.track
        } else {
            onTrackAction(request)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
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
                detail.album.title,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.clickable(
                    enabled = detail.album.coverArtUrl != null,
                    onClick = { albumImageOpen = true },
                ),
            ) {
                NaviampCoverArt(detail.album.coverArtUrl, colors, 96.dp, 4.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    detail.album.subtitle,
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        enabled = detail.artist != null,
                        onClick = { detail.artist?.let(onArtistSelected) },
                    ),
                )
                detail.album.releaseYear?.let { year ->
                    Text(year.toString(), color = colors.secondaryText, fontSize = 12.sp)
                }
                NaviampResponsiveActionRow(
                    colors = colors,
                    actions = listOf(
                        NaviampDetailAction("Play album", NaviampTransportIcons.Play, onPlayAlbum, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Shuffle album", NaviampTransportIcons.Shuffle, onShuffleAlbum, detail.tracks.size > 1),
                        NaviampDetailAction("Start album radio", NaviampTransportIcons.Radio, onAlbumRadio, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Download album", NaviampIcons.Downloads, onAlbumDownload, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Add album to queue", NaviampIcons.Queue, onAlbumAddToQueue, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Add album to playlist", NaviampIcons.Playlist, { addAlbumToPlaylistOpen = true }, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Playback profile", NaviampIcons.Settings, { playbackProfileOpen = true }, detail.tracks.isNotEmpty()),
                        NaviampDetailAction(
                            if (detail.album.favoriteActive) "Remove album favorite" else "Favorite album",
                            NaviampTransportIcons.Heart,
                            onAlbumFavoriteToggled,
                            detail.album.canFavorite,
                        ),
                    ),
                )
                playbackProfileStatus?.let { status ->
                    Text(status, color = colors.secondaryText, fontSize = 11.sp)
                }
            }
        }
        Text(
            listOfNotNull(
                "${detail.tracks.size} tracks",
                detail.totalDurationLabel.takeIf { it.isNotBlank() }?.let { "Total $it" },
            ).joinToString(" - "),
            color = colors.secondaryText,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            detail.information
                ?.normalizedAlbumInformation()
                ?.toProviderRichText()
                ?.takeIf { it.text.isNotBlank() }
                ?.let { information ->
                    val showMoreLink = information.length > 260
                    Text(
                        information,
                        color = colors.secondaryText,
                        maxLines = if (informationExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 11.sp, lineHeight = 13.sp),
                    )
                    if (showMoreLink) {
                        Text(
                            if (informationExpanded) "Less" else "More...",
                            color = colors.primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                informationExpanded = !informationExpanded
                            },
                        )
                    }
                }
            val reservePopularIndicatorSpace = detail.tracks.any { it.popular }
            val trackNumberWidth = trackNumberColumnWidth(detail.tracks.size)
            detail.tracks.forEachIndexed { index, track ->
                TrackRow(
                    track,
                    colors,
                    onTrackAction = handleTrackAction,
                    canSelect = true,
                    canStartRadio = false,
                    canAddToQueue = true,
                    canDownload = true,
                    canAddToPlaylist = true,
                    background = false,
                    verticalPadding = 0.dp,
                    showCoverArt = false,
                    showMenu = true,
                    reservePopularIndicatorSpace = reservePopularIndicatorSpace,
                    trackNumber = index + 1,
                    trackNumberWidth = trackNumberWidth,
                )
            }
        }
    }

    if (addAlbumToPlaylistOpen) {
        AddToPlaylistDialog(
            title = detail.album.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { addAlbumToPlaylistOpen = false },
            onAddToExisting = { playlist ->
                addAlbumToPlaylistOpen = false
                onAlbumAddToPlaylist(playlist)
            },
            onCreateAndAdd = { name ->
                addAlbumToPlaylistOpen = false
                onAlbumCreatePlaylistAndAdd(name)
            },
        )
    }

    trackForPlaylist?.let { track ->
        AddToPlaylistDialog(
            title = track.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { trackForPlaylist = null },
            onAddToExisting = { playlist ->
                trackForPlaylist = null
                handleTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.AddToPlaylist,
                        playlistChoice = playlist,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                trackForPlaylist = null
                handleTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    ),
                )
            },
        )
    }

    if (albumImageOpen) {
        ExpandedMediaImageDialog(
            imageUrl = detail.album.coverArtUrl,
            colors = colors,
            onDismissRequest = { albumImageOpen = false },
        )
    }
    if (playbackProfileOpen) {
        PlaybackProfileDialog(
            title = "${detail.album.title} playback profile",
            initialProfile = playbackProfile,
            colors = colors,
            onDismissRequest = { playbackProfileOpen = false },
            onSave = onPlaybackProfileSaved,
        )
    }
}

private fun String.normalizedAlbumInformation(): String =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
