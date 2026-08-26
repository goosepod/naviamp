package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.waveform.playbackFraction
import app.naviamp.domain.waveform.seekSecondsForFraction
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
internal fun TelevisionHome(
    home: NaviampHomeScreenUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
) {
    val sections = televisionHomeSections(home.content.collectionSections)
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text("Home", color = colors.primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black)
        if (sections.isEmpty()) {
            Text(
                if (home.refreshing) "Loading your music…" else "Your Home sections are empty.",
                color = colors.secondaryText,
                fontSize = 20.sp,
            )
        }
        sections.forEach { section ->
            TelevisionHomeCarousel(
                section = section,
                colors = colors,
                onSelected = { item -> dispatchHomeCollectionItem(item, actions, mediaActions) },
            )
        }
    }
}

@Composable
private fun TelevisionHomeCarousel(
    section: SharedHomeCollectionSectionUi,
    colors: NaviampColors,
    onSelected: (SharedHomeCollectionItemUi) -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val itemStride = with(density) { TelevisionHomeCardStride.roundToPx() }
    val itemWidth = with(density) { TelevisionHomeCardWidth.roundToPx() }
    var focusedIndex by remember(section.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusedIndex) {
        val index = focusedIndex ?: return@LaunchedEffect
        scrollState.animateScrollTo(
            homeCarouselFocusedItemScrollTarget(
                current = scrollState.value,
                viewport = scrollState.viewportSize,
                itemIndex = index,
                itemWidth = itemWidth,
                itemStride = itemStride,
                maximum = scrollState.maxValue,
            ),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            section.title,
            color = colors.primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val trailingSpace = (maxWidth - TelevisionHomeCardWidth).coerceAtLeast(0.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(TelevisionHomeCardSpacing),
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(end = trailingSpace),
            ) {
                section.items.forEachIndexed { index, item ->
                    TelevisionHomeCard(
                        item = item,
                        colors = colors,
                        onFocused = { focusedIndex = index },
                        onClick = { onSelected(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TelevisionHomeCard(
    item: SharedHomeCollectionItemUi,
    colors: NaviampColors,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    TelevisionFocusableCard(
        colors = colors,
        width = TelevisionHomeCardWidth,
        onFocused = onFocused,
        onClick = onClick,
    ) {
        HomeCollectionArtwork(item, colors, TelevisionHomeCardWidth)
        TelevisionCardLabels(item.title, item.subtitle, colors)
    }
}

@Composable
internal fun TelevisionLibrary(
    screen: NaviampLibraryScreenUi,
    colors: NaviampColors,
    actions: NaviampLibraryActions,
    mediaActions: NaviampMediaActions,
    onOpenPlaylists: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Library", color = colors.primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            TelevisionTextButton("Playlists", colors, onClick = onOpenPlaylists)
            Box(modifier = Modifier.padding(start = 10.dp)) {
                TelevisionTextButton("Refresh", colors, onClick = actions.onRefresh)
            }
        }
        screen.syncStatus.message?.let {
            Text(it, color = colors.secondaryText, fontSize = 15.sp)
        }
        if (screen.artists.isEmpty()) {
            Text("No artists are available.", color = colors.secondaryText, fontSize = 20.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TelevisionGridMinimumWidth),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(screen.artists, key = { it.id }) { artist ->
                    TelevisionMediaGridCard(
                        item = artist,
                        colors = colors,
                        onClick = {
                            mediaActions.onMediaItemAction(artist.artistActionRequest(NaviampArtistMediaCommand.Select))
                        },
                    )
                }
            }
        }
    }
}

private data class TelevisionSearchGridItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String?,
    val action: () -> Unit,
)

@Composable
internal fun TelevisionSearch(
    screen: NaviampSearchScreenUi,
    colors: NaviampColors,
    actions: NaviampSearchActions,
    mediaActions: NaviampMediaActions,
) {
    val focusManager = LocalFocusManager.current
    var editingQuery by remember { mutableStateOf(true) }
    val submitSearch = {
        focusManager.clearFocus(force = true)
        editingQuery = false
        actions.onSearch()
    }
    val results = screen.results
    val items = buildList {
        results.artists.forEach { artist ->
            add(
                TelevisionSearchGridItem("artist:${artist.id}", artist.title, artist.subtitle, artist.coverArtUrl) {
                    mediaActions.onMediaItemAction(artist.artistActionRequest(NaviampArtistMediaCommand.Select))
                },
            )
        }
        results.albums.forEach { album ->
            add(
                TelevisionSearchGridItem("album:${album.id}", album.title, album.subtitle, album.coverArtUrl) {
                    mediaActions.onMediaItemAction(album.albumActionRequest(NaviampArtistAlbumCommand.Select))
                },
            )
        }
        results.tracks.forEach { track ->
            add(
                TelevisionSearchGridItem("track:${track.id}", track.title, track.subtitle, track.coverArtUrl) {
                    mediaActions.onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Select))
                },
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        Text("Search", color = colors.primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black)
        if (editingQuery) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = screen.query,
                    onValueChange = actions.onQueryChanged,
                    label = { Text("Artists, albums, or tracks") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    modifier = Modifier.weight(1f),
                )
                TelevisionTextButton(
                    label = if (screen.searching) "Searching…" else "Search",
                    colors = colors,
                    onClick = submitSearch,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (screen.query.isBlank()) "Search results" else "Results for “${screen.query}”",
                    color = colors.secondaryText,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                TelevisionTextButton("New search", colors, onClick = { editingQuery = true })
            }
        }
        screen.status?.let { Text(it, color = colors.secondaryText, fontSize = 15.sp) }
        if (items.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TelevisionGridMinimumWidth),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(items, key = { it.key }) { item ->
                    TelevisionMediaGridCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        coverArtUrl = item.coverArtUrl,
                        colors = colors,
                        onClick = item.action,
                    )
                }
            }
        }
    }
}

@Composable
private fun TelevisionMediaGridCard(
    item: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) = TelevisionMediaGridCard(item.title, item.subtitle, item.coverArtUrl, colors, onClick)

@Composable
private fun TelevisionMediaGridCard(
    title: String,
    subtitle: String,
    coverArtUrl: String?,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TelevisionFocusableCard(
        colors = colors,
        width = TelevisionGridCardWidth,
        onClick = onClick,
        modifier = modifier,
    ) {
        NaviampCoverArt(coverArtUrl, colors, TelevisionGridArtworkSize, 12.dp)
        TelevisionCardLabels(title, subtitle, colors)
    }
}

@Composable
private fun TelevisionFocusableCard(
    colors: NaviampColors,
    width: Dp,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(width)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(shape)
            .background(if (focused) colors.accent.copy(alpha = 0.2f) else Color.Transparent)
            .border(if (focused) 4.dp else 0.dp, colors.accent, shape)
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun TelevisionCardLabels(title: String, subtitle: String, colors: NaviampColors) {
    Text(
        title,
        color = colors.primaryText,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    if (subtitle.isNotBlank()) {
        Text(
            subtitle,
            color = colors.secondaryText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
internal fun TelevisionNowPlaying(
    nowPlaying: NowPlayingUi,
    playbackProgress: StateFlow<PlaybackProgress>?,
    colors: NaviampColors,
    actions: NaviampNowPlayingActions,
    onClose: () -> Unit,
    onSearch: () -> Unit,
) {
    NaviampSystemBackHandler(enabled = true, onBack = onClose)
    val progress = playbackProgress?.collectAsState()?.value
        ?: PlaybackProgress(nowPlaying.positionSeconds, nowPlaying.durationSeconds)
    val duration = progress.durationSeconds ?: nowPlaying.durationSeconds
    var scrubberOverride by remember(nowPlaying.id) { mutableFloatStateOf(-1f) }
    val progressFraction = playbackFraction(progress.positionSeconds, duration).toFloat()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            NaviampCoverArt(nowPlaying.coverArtUrl, colors, 270.dp, 18.dp)
            if (nowPlaying.lyricsVisible) {
                TelevisionLyrics(nowPlaying, progress.positionSeconds, colors, Modifier.weight(1f))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        nowPlaying.title,
                        color = colors.primaryText,
                        fontSize = 38.sp,
                        lineHeight = 43.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        nowPlaying.subtitle,
                        color = colors.secondaryText,
                        fontSize = 25.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val album = nowPlaying.albumTitle.ifBlank { nowPlaying.albumLine }
                    if (album.isNotBlank()) {
                        Text(album, color = colors.mutedText, fontSize = 18.sp, maxLines = 1)
                    }
                    if (nowPlaying.audioInfo.isNotBlank()) {
                        Text(nowPlaying.audioInfo, color = colors.mutedText, fontSize = 15.sp)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(televisionSecondsLabel(progress.positionSeconds), color = colors.secondaryText, fontSize = 15.sp)
            WaveformScrubber(
                amplitudes = nowPlaying.waveform?.amplitudes.orEmpty(),
                value = (if (scrubberOverride >= 0f) scrubberOverride else progressFraction).coerceIn(0f, 1f),
                enabled = nowPlaying.canSeek && duration != null,
                colors = colors,
                onValueChange = { scrubberOverride = it },
                onValueChangeFinished = { fraction ->
                    seekSecondsForFraction(fraction, duration)?.let(actions::seek)
                    scrubberOverride = -1f
                },
                modifier = Modifier.weight(1f).height(46.dp),
            )
            Text(televisionSecondsLabel(duration), color = colors.secondaryText, fontSize = 15.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(70.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                TelevisionIconButton(nowPlaying.hasPrevious, NaviampTransportIcons.Previous, "Previous", colors) {
                    actions.playback(NowPlayingPlaybackAction.Previous)
                }
                TelevisionIconButton(
                    enabled = nowPlaying.canPlayPause,
                    icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                    description = if (nowPlaying.isPlaying) "Pause" else "Play",
                    colors = colors,
                    size = 64.dp,
                    prominent = true,
                ) {
                    actions.playback(
                        when {
                            nowPlaying.isPlaying -> NowPlayingPlaybackAction.Pause
                            nowPlaying.isPaused -> NowPlayingPlaybackAction.Resume
                            else -> NowPlayingPlaybackAction.PlayCurrent
                        },
                    )
                }
                TelevisionIconButton(nowPlaying.hasNext, NaviampTransportIcons.Next, "Next", colors) {
                    actions.playback(NowPlayingPlaybackAction.Next)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                TelevisionIconButton(nowPlaying.lyricsAvailable, NaviampTransportIcons.Lyrics, "Lyrics", colors, selected = nowPlaying.lyricsVisible) {
                    actions.display(NowPlayingDisplayAction.ToggleLyrics)
                }
                TelevisionIconButton(nowPlaying.canFavorite, if (nowPlaying.favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart, "Favorite", colors, selected = nowPlaying.favoriteActive) {
                    actions.currentTrack(NowPlayingCurrentTrackAction.ToggleFavorite)
                }
                TelevisionIconButton(nowPlaying.canRepeat, NaviampTransportIcons.Repeat, "Repeat", colors, selected = nowPlaying.repeatMode != NaviampRepeatMode.Off) {
                    actions.playback(NowPlayingPlaybackAction.CycleRepeatMode)
                }
                TelevisionIconButton(nowPlaying.shuffleEnabled, NaviampTransportIcons.Shuffle, "Shuffle", colors, selected = nowPlaying.shuffleActive) {
                    actions.playback(NowPlayingPlaybackAction.ToggleShuffle)
                }
                TelevisionIconButton(true, NaviampIcons.Search, "Search", colors, onClick = onSearch)
            }
        }
    }
}

@Composable
internal fun TelevisionMiniPlayer(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    onOpen: () -> Unit,
    actions: NaviampNowPlayingActions,
    modifier: Modifier = Modifier,
) {
    var identityFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.34f), shape)
            .padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { identityFocused = it.isFocused }
                .clip(shape)
                .background(if (identityFocused) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                .border(if (identityFocused) 4.dp else 0.dp, colors.accent, shape)
                .clickable(onClick = onOpen)
                .padding(6.dp),
        ) {
            NaviampCoverArt(nowPlaying.coverArtUrl, colors, 48.dp, 7.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    nowPlaying.title,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nowPlaying.subtitle,
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TelevisionIconButton(nowPlaying.hasPrevious, NaviampTransportIcons.Previous, "Previous", colors) {
            actions.playback(NowPlayingPlaybackAction.Previous)
        }
        TelevisionIconButton(
            enabled = nowPlaying.canPlayPause,
            icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
            description = if (nowPlaying.isPlaying) "Pause" else "Play",
            colors = colors,
        ) {
            actions.playback(
                when {
                    nowPlaying.isPlaying -> NowPlayingPlaybackAction.Pause
                    nowPlaying.isPaused -> NowPlayingPlaybackAction.Resume
                    else -> NowPlayingPlaybackAction.PlayCurrent
                },
            )
        }
        TelevisionIconButton(nowPlaying.hasNext, NaviampTransportIcons.Next, "Next", colors) {
            actions.playback(NowPlayingPlaybackAction.Next)
        }
    }
}

@Composable
private fun TelevisionLyrics(
    nowPlaying: NowPlayingUi,
    positionSeconds: Double?,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
) {
    val positionMillis = positionSeconds?.times(1000)?.toLong()
    val activeIndex = nowPlaying.lyricsLines.indexOfLast { line ->
        line.startMillis?.plus(nowPlaying.lyricsOffsetMillis)?.let { start ->
            positionMillis != null && start <= positionMillis
        } == true
    }
    val first = if (activeIndex < 0) 0 else (activeIndex - 2).coerceAtLeast(0)
    val visible = nowPlaying.lyricsLines.drop(first).take(6)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        when {
            nowPlaying.lyricsStatus != null -> Text(nowPlaying.lyricsStatus, color = colors.secondaryText, fontSize = 22.sp)
            visible.isEmpty() -> Text("Lyrics are not available.", color = colors.secondaryText, fontSize = 22.sp)
            else -> visible.forEachIndexed { offset, line ->
                val index = first + offset
                val active = index == activeIndex || (activeIndex < 0 && index == 0)
                Text(
                    line.text,
                    color = if (active) colors.primaryText else colors.secondaryText.copy(alpha = 0.58f),
                    fontSize = if (active) 29.sp else 21.sp,
                    lineHeight = if (active) 34.sp else 26.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TelevisionIconButton(
    enabled: Boolean,
    icon: ImageVector,
    description: String,
    colors: NaviampColors,
    selected: Boolean = false,
    size: Dp = 48.dp,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(999.dp)
    IconButton(
        onClick = {
            onClick()
            focusRequester.requestFocus()
        },
        enabled = enabled,
        modifier = Modifier
            .requiredSize(size)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    prominent -> colors.primaryText
                    selected -> colors.accent.copy(alpha = 0.32f)
                    else -> colors.controlSurface.copy(alpha = 0.9f)
                },
            )
            .border(if (focused) 4.dp else 0.dp, colors.accent, shape),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (prominent) colors.background else if (enabled) colors.primaryText else colors.mutedText,
            modifier = Modifier.size(if (prominent) 34.dp else 25.dp),
        )
    }
}

@Composable
private fun TelevisionTextButton(
    label: String,
    colors: NaviampColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 4.dp else 0.dp, colors.accent, shape),
        shape = shape,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun televisionSecondsLabel(seconds: Double?): String {
    val total = seconds?.takeIf { it.isFinite() && it >= 0.0 }?.roundToInt() ?: return "--:--"
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

private val TelevisionHomeCardWidth = 168.dp
private val TelevisionHomeCardSpacing = 16.dp
private val TelevisionHomeCardStride = TelevisionHomeCardWidth + TelevisionHomeCardSpacing
private val TelevisionGridMinimumWidth = 158.dp
private val TelevisionGridCardWidth = 150.dp
private val TelevisionGridArtworkSize = 150.dp
