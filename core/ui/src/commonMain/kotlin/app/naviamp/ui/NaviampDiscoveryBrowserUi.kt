package app.naviamp.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NaviampDiscoveryBrowserUi(discovery: SharedProviderDiscoveryUi, colors: NaviampColors, actions: NaviampHomeActions, playback: DiscoverySamplePlaybackUi = DiscoverySamplePlaybackUi(), television: Boolean = false,
    entryFocusGeneration: Int? = null, onEntryFocusHandled: (Int) -> Unit = {}) {
    val screen = discovery.browser ?: return
    val send: (DiscoveryBrowseAction) -> Unit = { actions.onDiscoveryBrowse(DiscoveryBrowseAction.Scoped(discovery.sourceId, discovery.sourceGeneration, it)) }
    NaviampSystemBackHandler(enabled = true) { send(DiscoveryBrowseAction.Close) }
    val focusManager = LocalFocusManager.current
    val navigation = Modifier.onPreviewKeyEvent { event ->
        if (!television || event.type != KeyEventType.KeyDown) false else {
            val direction = when (event.key) {
                Key.DirectionDown -> FocusDirection.Down
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            direction?.let(focusManager::moveFocus) ?: false
        }
    }
    val backFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val list = remember(screen.page, screen.view, screen.sort, screen.genre, screen.seed, screen.filter) {
        LazyListState(screen.firstVisibleItem, screen.firstVisibleOffset)
    }
    val latestSend by rememberUpdatedState(send)
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.isScrollInProgress, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .collect { (moving, index, offset) -> if (!moving) latestSend(DiscoveryBrowseAction.Viewport(index, offset)) }
    }
    if (screen.page == DiscoveryBrowsePage.Hub) {
        NaviampAurralHub(discovery, colors, actions, list, backFocus, send, navigation, entryFocusGeneration, onEntryFocusHandled)
        return
    }
    Column(Modifier.fillMaxSize().then(navigation)) {
        DiscoveryPageHeader(stringResource(when (screen.page) {
            DiscoveryBrowsePage.Search -> Res.string.discovery_catalog_search
            DiscoveryBrowsePage.Trending -> Res.string.discovery_charts_title
            DiscoveryBrowsePage.BlockedGenres -> Res.string.discovery_blocked_genres
            else -> Res.string.discovery_recommendations
        }), colors, { send(DiscoveryBrowseAction.Close) }, backFocus, discovery.providerId,
            onRefresh = { send(DiscoveryBrowseAction.Refresh) }, refreshing = screen.loading,
            onRequests = actions.onDiscoveryRequests, entryFocusGeneration = entryFocusGeneration, onEntryFocusHandled = onEntryFocusHandled)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val columns = if (maxWidth >= 1000.dp) 3 else if (maxWidth >= 660.dp) 2 else 1
        LazyColumn(Modifier.fillMaxSize().testTag("discovery-browser-list"), state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (screen.page == DiscoveryBrowsePage.BlockedGenres) {
                item(key = "genre-controls") {
                    Text(stringResource(Res.string.discovery_genre_block_description), color = colors.secondaryText)
                    if (screen.loading) Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                    if (screen.blockedGenres.status != ProviderDiscoveryStatus.Ready) Text(stringResource(discoveryStatusMessage(screen.blockedGenres.status)), color = colors.secondaryText)
                    screen.genreMutation?.let { Text(stringResource(discoveryOperationMessage(it)), color = colors.secondaryText) }
                    if (screen.blockedGenres.status == ProviderDiscoveryStatus.Ready && screen.blockedGenres.tags.isEmpty()) Text(stringResource(Res.string.discovery_no_blocked_genres), color = colors.secondaryText)
                }
                if (screen.blockedGenres.status == ProviderDiscoveryStatus.Ready) {
                    items(screen.blockedGenres.tags.distinctBy(::discoveryGenreKey), key = { "blocked:$it" }) { tag ->
                        GenreBlockRow(tag, true, screen, colors, send)
                    }
                    items(screen.genres.distinctBy(::discoveryGenreKey).filter { genre -> screen.blockedGenres.tags.none { discoveryGenreKey(it) == discoveryGenreKey(genre) } }, key = { "genre:$it" }) { tag ->
                        GenreBlockRow(tag, false, screen, colors, send)
                    }
                }
            }
            else if (screen.page == DiscoveryBrowsePage.Trending) {
                if (!discovery.feed.chartsAvailable && discovery.feed.trending.isNotEmpty()) {
                    items(discovery.feed.trending, key = { "trending:${it.id}" }) { artist ->
                        DiscoveryBrowserArtist(artist, colors) { actions.onDiscoveryArtistDetails(artist.id) }
                    }
                } else discoveryCharts(screen, colors, actions.copy(onDiscoveryBrowse = send), playback)
            }
            else if (screen.page == DiscoveryBrowsePage.Search) {
                item(key = "search") {
                    OutlinedTextField(value = screen.query, onValueChange = { send(DiscoveryBrowseAction.Query(it)) },
                        leadingIcon = { Icon(NaviampIcons.Search, null) },
                        label = { Text(stringResource(Res.string.discovery_catalog_query)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { send(DiscoveryBrowseAction.Search) }),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                        DiscoveryActionButton(colors = colors, primary = true, onClick = { send(DiscoveryBrowseAction.Search) }, enabled = !screen.loading && screen.query.isNotBlank()) {
                            Icon(NaviampIcons.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.discovery_catalog_search_action))
                        }
                        DiscoveryIconControl(NaviampIcons.ExternalLink, stringResource(Res.string.discovery_open_provider, localizedProviderName(discovery.providerId)), colors) {
                            send(DiscoveryBrowseAction.External)
                        }
                    }
                    TabRow(selectedTabIndex = screen.scope.ordinal, containerColor = colors.background, contentColor = colors.primaryText) {
                        ProviderCatalogScope.entries.forEach { scope -> Tab(selected = screen.scope == scope,
                            onClick = { send(DiscoveryBrowseAction.Scope(scope)) }, text = {
                                Text(stringResource(if (scope == ProviderCatalogScope.Artist) Res.string.discovery_catalog_artists else Res.string.discovery_catalog_albums))
                            }) }
                    }
                    if (screen.loading) Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                    if (screen.status != ProviderDiscoveryStatus.Ready) Text(stringResource(discoveryStatusMessage(screen.status)), color = colors.secondaryText)
                    if (screen.submittedQuery.isNotEmpty()) Text(stringResource(Res.string.discovery_catalog_count,
                        (screen.catalog.artists.size + screen.catalog.albums.size).toString(), screen.catalog.totalCount?.toString() ?: "?"), color = colors.secondaryText)
                }
                items(screen.catalog.artists, key = { "catalog-artist:${it.id}" }) { artist -> DiscoveryBrowserArtist(artist, colors) { actions.onDiscoveryArtistDetails(artist.id) } }
                items(screen.catalog.albums, key = { "catalog-album:${it.release.id}" }) { album ->
                    DiscoveryNavigationCard(colors, { send(DiscoveryBrowseAction.Album(album)) }, Modifier.fillMaxWidth(),
                        enabled = album.release.releaseGroupMbid != null && !screen.loading) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NaviampCoverArt(album.release.imageUrl, colors, 64.dp,
                            resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Album, 64.dp, 6.dp), mediaKind = SharedMediaItemKind.Album)
                        Column(Modifier.weight(1f)) {
                            Text(album.release.title, color = colors.primaryText)
                            Text(album.artistName, color = colors.secondaryText)
                            listOfNotNull(album.release.releaseDate, album.release.type).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), color = colors.secondaryText) }
                        }
                    }
                    }
                }
                if (screen.submittedQuery.isNotEmpty() && screen.catalog.artists.isEmpty() && screen.catalog.albums.isEmpty() && !screen.loading)
                    item { Text(stringResource(Res.string.discovery_catalog_empty), color = colors.secondaryText) }
                if (screen.catalog.nextOffset != null) item { DiscoveryActionButton(colors = colors, onClick = { send(DiscoveryBrowseAction.More) }, enabled = !screen.loading) { Text(stringResource(Res.string.discovery_load_more)) } }
            } else {
            item(key = "controls") {
                TabRow(selectedTabIndex = screen.view.ordinal, containerColor = colors.background, contentColor = colors.primaryText) {
                    DiscoveryRecommendationView.entries.forEach { view ->
                        Tab(selected = screen.view == view, onClick = { send(DiscoveryBrowseAction.View(view)) }, text = {
                            Text(stringResource(if (view == DiscoveryRecommendationView.ForYou) Res.string.discovery_for_you else Res.string.discovery_all_recommendations))
                        })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.discovery_collection_count, screen.recommendations.size.toString(), screen.total?.toString() ?: "?"), color = colors.secondaryText)
                if (screen.nextOffset != null) Text(stringResource(Res.string.discovery_collection_partial), color = colors.secondaryText)
                if (screen.loading) Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                if (screen.building) Text(stringResource(Res.string.discovery_building), color = colors.secondaryText)
                if (screen.status !in setOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Building)) Text(stringResource(discoveryStatusMessage(screen.status)), color = colors.secondaryText)
                screen.lastUpdated?.let { Text(stringResource(Res.string.discovery_last_updated, it), color = colors.secondaryText) }
                DiscoveryRecommendationFilters(screen, colors, send)
                if (screen.blockedGenres.status == ProviderDiscoveryStatus.Ready) DiscoveryActionButton(colors = colors, onClick = { send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.BlockedGenres)) }) {
                    Text(stringResource(Res.string.discovery_blocked_genres))
                }

            }
            val groups = if (screen.view == DiscoveryRecommendationView.ForYou) screen.groups()
                else listOf(DiscoveryRecommendationGroup(DiscoveryRecommendationGroupKind.Other) to screen.filtered())
            groups.forEachIndexed { groupIndex, (group, artists) ->
                item(key = "group:$groupIndex") {
                    Text(stringResource(when (group.kind) {
                        DiscoveryRecommendationGroupKind.Top -> Res.string.discovery_top_pick
                        DiscoveryRecommendationGroupKind.New -> Res.string.discovery_new_this_week
                        DiscoveryRecommendationGroupKind.Seed -> Res.string.discovery_because_you_like
                        DiscoveryRecommendationGroupKind.Direct -> Res.string.discovery_close_to_home
                        DiscoveryRecommendationGroupKind.Further -> Res.string.discovery_further_out
                        DiscoveryRecommendationGroupKind.Other -> Res.string.discovery_recommendations
                    }, *if (group.kind == DiscoveryRecommendationGroupKind.Seed) arrayOf(group.seed.orEmpty()) else emptyArray()),
                        Modifier.semantics { heading() }, color = colors.primaryText)
                }
                items(artists.chunked(columns), key = { "artists:${it.first().id}" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { artist -> Box(Modifier.weight(1f)) {
                            DiscoveryBrowserArtist(artist, colors) { actions.onDiscoveryArtistDetails(artist.id) }
                        } }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (screen.filtered().isEmpty() && !screen.loading) item { Text(stringResource(Res.string.discovery_empty), color = colors.secondaryText) }
            if (screen.nextOffset != null) item { DiscoveryActionButton(colors = colors, onClick = { send(DiscoveryBrowseAction.More) }, enabled = !screen.loading) { Text(stringResource(Res.string.discovery_load_more)) } }
            }
        }
        }
    }
}

@Composable
private fun DiscoveryRecommendationFilters(screen: DiscoveryBrowserState, colors: NaviampColors, send: (DiscoveryBrowseAction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        DiscoveryFilterMenu(stringResource(when (screen.sort) {
            DiscoveryRecommendationSort.Rank -> Res.string.discovery_sort_rank
            DiscoveryRecommendationSort.Name -> Res.string.discovery_sort_name
            DiscoveryRecommendationSort.FirstSeen -> Res.string.discovery_sort_first_seen
        }), colors) { close ->
            DiscoveryRecommendationSort.entries.filter { it != DiscoveryRecommendationSort.FirstSeen || screen.hasDates }.forEach { sort ->
                DropdownMenuItem(text = { Text(stringResource(when (sort) {
                    DiscoveryRecommendationSort.Rank -> Res.string.discovery_sort_rank
                    DiscoveryRecommendationSort.Name -> Res.string.discovery_sort_name
                    DiscoveryRecommendationSort.FirstSeen -> Res.string.discovery_sort_first_seen
                })) }, onClick = { send(DiscoveryBrowseAction.Sort(sort)); close() })
            }
        }
        DiscoveryFilterMenu(screen.genre ?: stringResource(Res.string.discovery_chart_genres), colors) { close ->
            DropdownMenuItem(text = { Text(stringResource(Res.string.discovery_all_recommendations)) }, onClick = { send(DiscoveryBrowseAction.Genre(null)); close() })
            screen.genres.distinctBy(::discoveryGenreKey).forEach { genre ->
                DropdownMenuItem(text = { Text(genre) }, onClick = { send(DiscoveryBrowseAction.Genre(genre)); close() })
            }
        }
        if (screen.seeds.isNotEmpty()) DiscoveryFilterMenu(screen.seed ?: stringResource(Res.string.discovery_because_you_like, "…"), colors) { close ->
            DropdownMenuItem(text = { Text(stringResource(Res.string.discovery_all_recommendations)) }, onClick = { send(DiscoveryBrowseAction.Seed(null)); close() })
            screen.seeds.distinctBy { it.name }.forEach { seed -> DropdownMenuItem(text = { Text(seed.name) }, onClick = { send(DiscoveryBrowseAction.Seed(seed.name)); close() }) }
        }
        DiscoveryFilterMenu(stringResource(when (screen.filter) {
            DiscoveryRecommendationFilter.All -> Res.string.discovery_all_recommendations
            DiscoveryRecommendationFilter.New -> Res.string.discovery_new_this_week
            DiscoveryRecommendationFilter.Direct -> Res.string.discovery_close_to_home
            DiscoveryRecommendationFilter.Further -> Res.string.discovery_further_out
        }), colors) { close ->
            DiscoveryRecommendationFilter.entries.filter { when (it) {
                DiscoveryRecommendationFilter.New -> screen.hasDates
                DiscoveryRecommendationFilter.Direct, DiscoveryRecommendationFilter.Further -> screen.hasDepth
                else -> true
            } }.forEach { filter -> DropdownMenuItem(text = { Text(stringResource(when (filter) {
                DiscoveryRecommendationFilter.All -> Res.string.discovery_all_recommendations
                DiscoveryRecommendationFilter.New -> Res.string.discovery_new_this_week
                DiscoveryRecommendationFilter.Direct -> Res.string.discovery_close_to_home
                DiscoveryRecommendationFilter.Further -> Res.string.discovery_further_out
            })) }, onClick = { send(DiscoveryBrowseAction.Filter(filter)); close() }) }
        }
    }
}

@Composable
private fun DiscoveryFilterMenu(label: String, colors: NaviampColors, content: @Composable ((() -> Unit)) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DiscoveryActionButton(colors, { expanded = true }) {
            Text(label); Spacer(Modifier.width(8.dp)); Icon(NaviampIcons.ChevronDown, null, Modifier.size(16.dp))
        }
        DropdownMenu(expanded, { expanded = false }) { content { expanded = false } }
    }
}

@Composable
internal fun DiscoveryBrowserArtist(artist: ProviderDiscoveryArtist, colors: NaviampColors, canOpen: Boolean = artist.artistMbid != null, onOpen: () -> Unit) {
    DiscoveryNavigationCard(colors, onOpen, Modifier.fillMaxWidth(), enabled = canOpen) {
    Row(Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NaviampCoverArt(artist.imageUrl, colors, 64.dp, resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Artist, 64.dp, 6.dp), mediaKind = SharedMediaItemKind.Artist)
        Column(Modifier.weight(1f)) {
            Text(artist.name, color = colors.primaryText)
            artist.disambiguation?.let { Text(it, color = colors.secondaryText) }
            Text(artist.genres.joinToString(", "), color = colors.secondaryText)
            artist.recommendation?.let { context ->
                val names = (context.supportingSeeds + context.sourceArtists).distinct().take(3)
                if (names.isNotEmpty()) Text(stringResource(if ((context.depth ?: 1) >= 2) Res.string.discovery_via_artists else Res.string.discovery_because_you_like, names.joinToString(", ")), color = colors.secondaryText)
                else if (context.matchedTags.isNotEmpty()) Text(context.matchedTags.joinToString(", "), color = colors.secondaryText)
            }
        }
    }
    }
}

@Composable
private fun GenreBlockRow(tag: String, blocked: Boolean, screen: DiscoveryBrowserState, colors: NaviampColors, send: (DiscoveryBrowseAction) -> Unit) {
    val enabled = !screen.loading && discoveryGenreKey(tag) !in screen.uncertainGenres
    val label = stringResource(if (blocked) Res.string.discovery_unblock_genre else Res.string.discovery_block_genre)
    Surface(color = colors.controlSurface, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().toggleable(value = blocked, enabled = enabled, role = Role.Switch,
            onValueChange = { send(DiscoveryBrowseAction.BlockGenre(tag, it)) }).padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tag, color = colors.primaryText, fontWeight = FontWeight.Medium)
                Text(label, color = colors.secondaryText, fontSize = 12.sp)
            }
            Switch(checked = blocked, onCheckedChange = null, enabled = enabled)
        }
    }
}
