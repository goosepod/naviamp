package app.naviamp.ui
import org.jetbrains.compose.resources.pluralStringResource
import app.naviamp.ui.generated.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.settings.AlbumSortOrder
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun TelevisionAlbumDetail(
    screen: NaviampAlbumDetailScreenUi,
    colors: NaviampColors,
    actions: NaviampAlbumDetailActions,
    topNavigationFocusRequester: FocusRequester,
) {
    val detail = screen.detail
    if (detail == null) {
        TelevisionDetailLoading(
            title = screen.selectedAlbum?.title ?: stringResource(Res.string.tv_album),
            status = screen.status,
            colors = colors,
        )
        return
    }
    val firstTrackFocusRequester = remember(detail.album.id) { FocusRequester() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "album-hero:${detail.album.id}") {
            TelevisionDetailHero(
                title = detail.album.title,
                subtitle = detail.album.subtitle,
                metadata = listOfNotNull(
                    detail.album.releaseYear?.toString(),
                    televisionTrackCountLabel(detail.tracks.size),
                    detail.totalDurationLabel.takeIf { it.isNotBlank() },
                ).joinToString("  •  "),
                coverArtUrl = detail.album.coverArtUrl,
                colors = colors,
                topNavigationFocusRequester = topNavigationFocusRequester,
                initialActionDownFocusRequester = firstTrackFocusRequester,
                actions = listOf(
                    TelevisionHeroAction(stringResource(Res.string.transport_play), NaviampTransportIcons.Play, detail.tracks.isNotEmpty()) {
                        actions.onAlbumAction(
                            NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.Play(false)),
                        )
                    },
                    TelevisionHeroAction(stringResource(Res.string.tv_start_radio), NaviampTransportIcons.Radio, detail.tracks.isNotEmpty()) {
                        actions.onAlbumAction(
                            NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.StartRadio),
                        )
                    },
                    TelevisionHeroAction(stringResource(Res.string.mix_add_to_queue), NaviampIcons.Queue, detail.tracks.isNotEmpty()) {
                        actions.onAlbumAction(
                            NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.AddToQueue),
                        )
                    },
                ),
            )
        }
        item(key = "album-tracks-heading") {
            TelevisionDetailSectionHeading(stringResource(Res.string.tv_tracks), colors)
        }
        itemsIndexed(detail.tracks, key = { _, track -> track.id }) { index, track ->
            TelevisionTrackRow(
                track = track,
                leadingText = (index + 1).toString(),
                showArtwork = false,
                colors = colors,
                onAction = { action ->
                    actions.onTrackAction(SharedTrackRowActionRequest(track, action))
                },
                modifier = if (index == 0) Modifier.focusRequester(firstTrackFocusRequester) else Modifier,
                artistContext = track.artistCredits.joinToString(", ") { it.name },
                albumContext = detail.album.title,
            )
        }
    }
}

@Composable
internal fun TelevisionArtistDetail(
    screen: NaviampArtistDetailScreenUi,
    colors: NaviampColors,
    actions: NaviampArtistDetailActions,
    showAlbumYear: Boolean = true,
    albumSortOrder: AlbumSortOrder = AlbumSortOrder.ReleaseYearAscending,
    groupAlbumsByReleaseType: Boolean = true,
    onAlbumOpening: () -> Unit = {},
    topNavigationFocusRequester: FocusRequester,
) {
    val detail = screen.detail
    if (detail == null) {
        TelevisionDetailLoading(
            title = screen.selectedArtist?.title ?: stringResource(Res.string.tv_artist),
            status = screen.status,
            colors = colors,
        )
        return
    }
    val listState = rememberLazyListState()
    val firstPopularTrackFocusRequester = remember(detail.artist.id) { FocusRequester() }
    val firstAlbumFocusRequester = remember(detail.artist.id) { FocusRequester() }
    val popularTracksAvailable = detail.popularTracks.isNotEmpty()
    val albumSections = detail.albumSectionsForDisplay(
        groupByReleaseType = groupAlbumsByReleaseType,
        sortOrder = albumSortOrder,
    )
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "artist-hero:${detail.artist.id}") {
            TelevisionDetailHero(
                title = detail.artist.title,
                subtitle = detail.artist.subtitle,
                metadata = televisionReleaseCountLabel(detail.albums.size),
                coverArtUrl = detail.artist.coverArtUrl,
                circularArtwork = true,
                colors = colors,
                topNavigationFocusRequester = topNavigationFocusRequester,
                initialActionDownFocusRequester = if (popularTracksAvailable) {
                    firstPopularTrackFocusRequester
                } else {
                    firstAlbumFocusRequester
                },
                actions = listOf(
                    TelevisionHeroAction(stringResource(Res.string.transport_play), NaviampTransportIcons.Play, detail.albums.isNotEmpty()) {
                        actions.onArtistAction(
                            NaviampArtistDetailActionRequest(
                                detail.artist,
                                NaviampArtistDetailCommand.PlayCatalog(detail.albums, shuffle = false),
                            ),
                        )
                    },
                    TelevisionHeroAction(stringResource(Res.string.tv_start_radio), NaviampTransportIcons.Radio, detail.albums.isNotEmpty()) {
                        actions.onArtistAction(
                            NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.StartRadio),
                        )
                    },
                    TelevisionHeroAction(stringResource(Res.string.mix_add_to_queue), NaviampIcons.Queue, detail.albums.isNotEmpty()) {
                        actions.onArtistAction(
                            NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.AddToQueue),
                        )
                    },
                ),
            )
        }
        if (detail.popularTracks.isNotEmpty()) {
            item(key = "popular-tracks-heading") {
                TelevisionDetailSectionHeading(stringResource(Res.string.tv_popular_tracks), colors)
            }
            itemsIndexed(detail.popularTracks, key = { _, track -> "popular:${track.id}" }) { index, track ->
                TelevisionTrackRow(
                    track = track,
                    leadingText = (index + 1).toString(),
                    showArtwork = true,
                    colors = colors,
                    onAction = { action ->
                        actions.onPopularTrackAction(SharedTrackRowActionRequest(track, action))
                    },
                    modifier = Modifier
                        .then(
                            if (index == 0) Modifier.focusRequester(firstPopularTrackFocusRequester) else Modifier,
                        )
                        .then(
                            if (index == detail.popularTracks.lastIndex && detail.albums.isNotEmpty()) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                        firstAlbumFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                    artistContext = detail.artist.title,
                    albumContext = track.albumTitle,
                )
            }
        }
        albumSections.forEachIndexed { sectionIndex, section ->
            item(key = "artist-albums-heading:${section.releaseSection}") {
                TelevisionDetailSectionHeading(albumReleaseSectionLabel(section.releaseSection), colors)
            }
            item(key = "artist-albums:${section.releaseSection}") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        start = TelevisionDetailFocusedItemOverflow,
                        top = TelevisionDetailFocusedItemOverflow,
                        end = TelevisionDetailFocusedItemOverflow,
                        bottom = TelevisionDetailFocusedItemOverflow + 8.dp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(section.albums, key = { _, album -> album.id }) { index, album ->
                        TelevisionFocusableCard(
                            colors = colors,
                            width = TelevisionDetailAlbumCardWidth,
                            onClick = {
                                onAlbumOpening()
                                actions.onAlbumAction(
                                    NaviampArtistAlbumActionRequest(album, NaviampArtistAlbumCommand.Select),
                                )
                            },
                            modifier = if (sectionIndex == 0 && index == 0) {
                                Modifier.focusRequester(firstAlbumFocusRequester)
                            } else {
                                Modifier
                            },
                        ) { focused ->
                            NaviampCoverArt(
                                album.coverArtUrl,
                                colors,
                                TelevisionDetailAlbumCardWidth,
                                12.dp,
                                Modifier.televisionMediaArtworkFocusEffect(
                                    focused,
                                    colors,
                                    RoundedCornerShape(12.dp),
                                ),
                            )
                            TelevisionCardLabels(
                                album.title,
                                televisionAlbumSubtitle(album, showAlbumYear),
                                colors,
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(detail.artist.id) {
        repeat(3) { withFrameNanos { } }
        listState.scrollToItem(0)
    }
}

@Composable
internal fun TelevisionPlaylistDetail(
    screen: NaviampPlaylistDetailScreenUi,
    colors: NaviampColors,
    actions: NaviampPlaylistDetailActions,
    topNavigationFocusRequester: FocusRequester,
) {
    val detail = screen.detail
    if (detail == null) {
        TelevisionDetailLoading(
            title = screen.selectedPlaylist?.title ?: stringResource(Res.string.tv_playlist),
            status = screen.status,
            colors = colors,
        )
        return
    }
    val playlist = detail.playlist
    val listState = rememberLazyListState()
    val firstTrackFocusRequester = remember(playlist.id) { FocusRequester() }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "playlist-hero:${playlist.id}") {
            TelevisionDetailHero(
                title = playlist.title,
                subtitle = playlist.subtitle,
                metadata = televisionTrackCountLabel(detail.tracks.size),
                coverArtUrl = playlist.coverArtUrl ?: playlist.coverArtUrls.firstOrNull(),
                colors = colors,
                topNavigationFocusRequester = topNavigationFocusRequester,
                initialActionDownFocusRequester = firstTrackFocusRequester,
                actions = listOf(
                    TelevisionHeroAction(stringResource(Res.string.transport_play), NaviampTransportIcons.Play, detail.tracks.isNotEmpty()) {
                        actions.onPlaylistAction(
                            NaviampPlaylistDetailActionRequest(
                                playlist,
                                NaviampPlaylistDetailCommand.Play(shuffle = false),
                            ),
                        )
                    },
                    TelevisionHeroAction(
                        stringResource(Res.string.transport_shuffle),
                        NaviampTransportIcons.Shuffle,
                        televisionPlaylistSupportsShuffle(detail.tracks.size),
                    ) {
                        actions.onPlaylistAction(
                            NaviampPlaylistDetailActionRequest(
                                playlist,
                                NaviampPlaylistDetailCommand.Play(shuffle = true),
                            ),
                        )
                    },
                    TelevisionHeroAction(stringResource(Res.string.mix_add_to_queue), NaviampIcons.Queue, detail.tracks.isNotEmpty()) {
                        actions.onPlaylistAction(
                            NaviampPlaylistDetailActionRequest(playlist, NaviampPlaylistDetailCommand.AddToQueue),
                        )
                    },
                ),
            )
        }
        item(key = "playlist-tracks-heading") {
            TelevisionDetailSectionHeading(stringResource(Res.string.tv_tracks), colors)
        }
        itemsIndexed(detail.tracks, key = { _, track -> track.id }) { index, track ->
            TelevisionTrackRow(
                track = track,
                leadingText = (index + 1).toString(),
                showArtwork = true,
                colors = colors,
                onAction = { action ->
                    actions.onTrackAction(SharedTrackRowActionRequest(track, action))
                },
                modifier = if (index == 0) Modifier.focusRequester(firstTrackFocusRequester) else Modifier,
                artistContext = track.artistCredits.joinToString(", ") { it.name },
                albumContext = track.albumTitle,
            )
        }
        screen.status?.let { status ->
            item(key = "playlist-status") {
                Text(status, color = colors.secondaryText, fontSize = 16.sp)
            }
        }
    }
    LaunchedEffect(playlist.id) {
        repeat(3) { withFrameNanos { } }
        listState.scrollToItem(0)
    }
}

internal fun televisionPlaylistSupportsShuffle(trackCount: Int): Boolean = trackCount > 1

internal fun televisionAlbumSubtitle(album: SharedMediaItemUi, showAlbumYear: Boolean): String =
    listOfNotNull(
        album.subtitle.takeIf(String::isNotBlank),
        album.releaseYear?.toString().takeIf { showAlbumYear },
    ).joinToString("  •  ")

private data class TelevisionHeroAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val action: () -> Unit,
)

@Composable
private fun TelevisionDetailHero(
    title: String,
    subtitle: String,
    metadata: String,
    coverArtUrl: String?,
    circularArtwork: Boolean = false,
    colors: NaviampColors,
    actions: List<TelevisionHeroAction>,
    initialActionDownFocusRequester: FocusRequester? = null,
    topNavigationFocusRequester: FocusRequester? = null,
) {
    val initialActionFocusRequester = remember(title) { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampCoverArt(
                coverArtUrl,
                colors,
                TelevisionDetailHeroArtworkSize,
                if (circularArtwork) TelevisionDetailHeroArtworkSize / 2 else 16.dp,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    title,
                    color = colors.primaryText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = colors.secondaryText,
                        fontSize = 21.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (metadata.isNotBlank()) {
                    Text(metadata, color = colors.mutedText, fontSize = 16.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    actions.forEachIndexed { index, action ->
                        TelevisionDetailActionButton(
                            action,
                            colors,
                            modifier = if (index == 0) {
                                Modifier
                                    .focusRequester(initialActionFocusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (
                                            event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionUp &&
                                            topNavigationFocusRequester != null
                                        ) {
                                            topNavigationFocusRequester.requestFocus()
                                            true
                                        } else if (
                                            event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionDown &&
                                            initialActionDownFocusRequester != null
                                        ) {
                                            initialActionDownFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(title) {
        withFrameNanos { }
        initialActionFocusRequester.requestFocus()
    }
}

@Composable
private fun TelevisionDetailActionButton(
    action: TelevisionHeroAction,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = action.action,
        enabled = action.enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.controlSurface,
            contentColor = colors.primaryText,
        ),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .televisionFocusEffect(focused, colors, shape),
    ) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(8.dp))
        Text(action.label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun TelevisionTrackRow(
    track: SharedTrackRowUi,
    leadingText: String,
    showArtwork: Boolean,
    colors: NaviampColors,
    onAction: (SharedTrackRowAction) -> Unit,
    modifier: Modifier = Modifier,
    artistContext: String? = null,
    albumContext: String? = null,
) {
    val rowFocusRequester = remember(track.id) { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var actionsOpen by remember(track.id) { mutableStateOf(false) }
    var restoreRowFocus by remember(track.id) { mutableStateOf(false) }
    val closeActions = {
        actionsOpen = false
        restoreRowFocus = true
    }
    LaunchedEffect(restoreRowFocus) {
        if (restoreRowFocus) {
            withFrameNanos { }
            rowFocusRequester.requestFocus()
            restoreRowFocus = false
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = { onAction(SharedTrackRowAction.Select) },
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) colors.accent.copy(alpha = 0.18f) else colors.controlSurface.copy(alpha = 0.58f),
            contentColor = colors.primaryText,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(rowFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    actionsOpen = true
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .televisionFocusEffect(focused, colors, shape),
    ) {
        Text(
            leadingText,
            color = colors.mutedText,
            fontSize = 16.sp,
            modifier = Modifier.width(34.dp),
        )
        if (showArtwork) {
            NaviampCoverArt(track.coverArtUrl, colors, 52.dp, 7.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = colors.primaryText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.subtitle.isNotBlank()) {
                Text(
                    track.subtitle,
                    color = colors.secondaryText,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (track.meta.isNotBlank()) {
            Text(track.meta, color = colors.mutedText, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp))
        }
        if (track.durationLabel.isNotBlank()) {
            Text(
                track.durationLabel,
                color = colors.secondaryText,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
        Text(
            stringResource(Res.string.tv_track_actions_hint),
            color = colors.mutedText,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
    if (actionsOpen) {
        TelevisionTrackActionsDialog(
            track = track,
            artistContext = artistContext,
            albumContext = albumContext,
            colors = colors,
            onDismiss = closeActions,
            onAction = { action ->
                onAction(action)
                closeActions()
            },
        )
    }
}

@Composable
private fun TelevisionTrackActionsDialog(
    track: SharedTrackRowUi,
    artistContext: String?,
    albumContext: String?,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onAction: (SharedTrackRowAction) -> Unit,
) {
    val firstActionFocusRequester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f))) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .background(colors.controlSurface, RoundedCornerShape(18.dp))
                    .padding(28.dp),
            ) {
                Text(
                    stringResource(Res.string.tv_track_actions_title),
                    color = colors.primaryText,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                televisionTrackActionContextLines(track, artistContext, albumContext).forEach { (label, value) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(label), color = colors.mutedText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            value,
                            color = colors.primaryText,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(stringResource(Res.string.tv_choose_action), color = colors.secondaryText, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    televisionTrackSecondaryActions().forEachIndexed { index, action ->
                        TelevisionTextButton(
                            label = stringResource(when (action) {
                                SharedTrackRowAction.PlayNext -> Res.string.action_play_after_current_group
                                SharedTrackRowAction.AddToQueue -> Res.string.mix_add_to_queue
                                else -> Res.string.tv_start_radio
                            }),
                            colors = colors,
                            onClick = { onAction(action) },
                            modifier = Modifier
                                .weight(1f)
                                .then(if (index == 0) Modifier.focusRequester(firstActionFocusRequester) else Modifier),
                        )
                    }
                }
                Text(stringResource(Res.string.tv_back_closes_panel), color = colors.mutedText, fontSize = 14.sp)
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        firstActionFocusRequester.requestFocus()
    }
}

internal fun televisionTrackActionContextLines(
    track: SharedTrackRowUi,
    artistContext: String? = null,
    albumContext: String? = null,
): List<Pair<org.jetbrains.compose.resources.StringResource, String>> = buildList {
    val artists = artistContext?.takeIf(String::isNotBlank)
        ?: track.artistCredits.joinToString(", ") { it.name }.takeIf(String::isNotBlank)
    val album = albumContext?.takeIf(String::isNotBlank) ?: track.albumTitle?.takeIf(String::isNotBlank)
    artists?.let { add(Res.string.tv_track_artist_label to it) }
    album?.let { add(Res.string.tv_track_album_label to it) }
    add(Res.string.tv_track_title_label to track.title)
}

internal fun televisionTrackSecondaryActions(): List<SharedTrackRowAction> = listOf(
    SharedTrackRowAction.PlayNext,
    SharedTrackRowAction.AddToQueue,
    SharedTrackRowAction.StartRadio,
)

@Composable
internal fun televisionTrackCountLabel(count: Int): String = pluralStringResource(Res.plurals.tv_track_count, count, count)

@Composable
internal fun televisionReleaseCountLabel(count: Int): String = pluralStringResource(Res.plurals.tv_release_count, count, count)

@Composable
private fun TelevisionDetailSectionHeading(title: String, colors: NaviampColors) {
    Text(title, color = colors.primaryText, fontSize = 24.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun TelevisionDetailLoading(
    title: String,
    status: String?,
    colors: NaviampColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        Text(title, color = colors.primaryText, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(status ?: stringResource(Res.string.tv_loading), color = colors.secondaryText, fontSize = 19.sp)
    }
}

private val TelevisionDetailHeroArtworkSize = 176.dp
private val TelevisionDetailAlbumCardWidth = 150.dp
private val TelevisionDetailFocusedItemOverflow = 12.dp
