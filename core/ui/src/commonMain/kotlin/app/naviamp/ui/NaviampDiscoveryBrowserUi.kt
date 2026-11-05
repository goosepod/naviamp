package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NaviampDiscoveryBrowserUi(discovery: SharedProviderDiscoveryUi, colors: NaviampColors, actions: NaviampHomeActions, playback: DiscoverySamplePlaybackUi = DiscoverySamplePlaybackUi()) {
    val screen = discovery.browser ?: return
    val send: (DiscoveryBrowseAction) -> Unit = { actions.onDiscoveryBrowse(DiscoveryBrowseAction.Scoped(discovery.sourceId, discovery.sourceGeneration, it)) }
    val backFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(screen.page) { backFocus.requestFocus() }
    val list = remember(screen.page, screen.view, screen.sort, screen.genre, screen.seed, screen.filter) {
        LazyListState(screen.firstVisibleItem, screen.firstVisibleOffset)
    }
    val latestSend by rememberUpdatedState(send)
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.isScrollInProgress, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .collect { (moving, index, offset) -> if (!moving) latestSend(DiscoveryBrowseAction.Viewport(index, offset)) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(when (screen.page) { DiscoveryBrowsePage.Search -> Res.string.discovery_catalog_search; DiscoveryBrowsePage.Trending -> Res.string.discovery_charts_title; DiscoveryBrowsePage.BlockedGenres -> Res.string.discovery_blocked_genres; else -> Res.string.discovery_recommendations }), Modifier.weight(1f).semantics { heading() }, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.Close) }, modifier = Modifier.focusRequester(backFocus)) { Text(stringResource(Res.string.common_back)) }
        }
        Text(stringResource(Res.string.discovery_provider_title, localizedProviderName(discovery.providerId)), color = colors.secondaryText)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("discovery-browser-list"), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (screen.page == DiscoveryBrowsePage.BlockedGenres) {
                item(key = "genre-controls") {
                    Text(stringResource(Res.string.discovery_genre_block_description), color = colors.secondaryText)
                    DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.Refresh) }, enabled = !screen.loading) { Text(stringResource(Res.string.library_refresh)) }
                    if (screen.loading) Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                    if (screen.blockedGenres.status != ProviderDiscoveryStatus.Ready) Text(stringResource(discoveryStatusMessage(screen.blockedGenres.status)), color = colors.secondaryText)
                    screen.genreMutation?.let { Text(stringResource(discoveryOperationMessage(it)), color = colors.secondaryText) }
                    if (screen.blockedGenres.status == ProviderDiscoveryStatus.Ready && screen.blockedGenres.tags.isEmpty()) Text(stringResource(Res.string.discovery_no_blocked_genres), color = colors.secondaryText)
                }
                if (screen.blockedGenres.status == ProviderDiscoveryStatus.Ready) {
                    items(screen.blockedGenres.tags.distinct(), key = { "blocked:$it" }) { tag ->
                        GenreBlockRow(tag, true, screen, colors, send)
                    }
                    items(screen.genres.distinct().filter { genre -> screen.blockedGenres.tags.none { discoveryGenreKey(it) == discoveryGenreKey(genre) } }, key = { "genre:$it" }) { tag ->
                        GenreBlockRow(tag, false, screen, colors, send)
                    }
                }
            }
            else if (screen.page == DiscoveryBrowsePage.Trending) discoveryCharts(screen, colors, actions.copy(onDiscoveryBrowse = send), playback)
            else if (screen.page == DiscoveryBrowsePage.Search) {
                item(key = "search") {
                    OutlinedTextField(value = screen.query, onValueChange = { send(DiscoveryBrowseAction.Query(it)) },
                        label = { Text(stringResource(Res.string.discovery_catalog_query)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { send(DiscoveryBrowseAction.Search) }),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderCatalogScope.entries.forEach { scope -> FilterChip(selected = screen.scope == scope,
                            onClick = { send(DiscoveryBrowseAction.Scope(scope)) }, label = {
                                Text(stringResource(if (scope == ProviderCatalogScope.Artist) Res.string.discovery_catalog_artists else Res.string.discovery_catalog_albums))
                            }) }
                        DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.Search) }, enabled = !screen.loading && screen.query.isNotBlank()) { Text(stringResource(Res.string.discovery_catalog_search_action)) }
                    }
                    if (screen.loading) Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                    if (screen.status != ProviderDiscoveryStatus.Ready) Text(stringResource(discoveryStatusMessage(screen.status)), color = colors.secondaryText)
                    if (screen.submittedQuery.isNotEmpty()) Text(stringResource(Res.string.discovery_catalog_count,
                        (screen.catalog.artists.size + screen.catalog.albums.size).toString(), screen.catalog.totalCount?.toString() ?: "?"), color = colors.secondaryText)
                    DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.External) }) { Text(stringResource(Res.string.discovery_open_provider, localizedProviderName(discovery.providerId))) }
                }
                items(screen.catalog.artists, key = { "catalog-artist:${it.id}" }) { artist -> DiscoveryBrowserArtist(artist, colors) { actions.onDiscoveryArtistDetails(artist.id) } }
                items(screen.catalog.albums, key = { "catalog-album:${it.release.id}" }) { album ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = album.release.releaseGroupMbid != null && !screen.loading,
                        role = Role.Button, onClick = { send(DiscoveryBrowseAction.Album(album)) }).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NaviampCoverArt(album.release.imageUrl, colors, 64.dp,
                            resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Album, 64.dp, 6.dp), mediaKind = SharedMediaItemKind.Album)
                        Column(Modifier.weight(1f)) {
                            Text(album.release.title, color = colors.primaryText)
                            Text(album.artistName, color = colors.secondaryText)
                            listOfNotNull(album.release.releaseDate, album.release.type).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), color = colors.secondaryText) }
                        }
                    }
                }
                if (screen.submittedQuery.isNotEmpty() && screen.catalog.artists.isEmpty() && screen.catalog.albums.isEmpty() && !screen.loading)
                    item { Text(stringResource(Res.string.discovery_catalog_empty), color = colors.secondaryText) }
                if (screen.catalog.nextOffset != null) item { DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.More) }, enabled = !screen.loading) { Text(stringResource(Res.string.discovery_load_more)) } }
            } else {
            item(key = "controls") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscoveryRecommendationView.entries.forEach { view ->
                        FilterChip(selected = screen.view == view, onClick = { send(DiscoveryBrowseAction.View(view)) }, label = {
                            Text(stringResource(if (view == DiscoveryRecommendationView.ForYou) Res.string.discovery_for_you else Res.string.discovery_all_recommendations))
                        })
                    }
                    DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.Refresh) }, enabled = !screen.loading) { Text(stringResource(Res.string.library_refresh)) }
                }
                Text(stringResource(Res.string.discovery_collection_count, screen.recommendations.size.toString(), screen.total?.toString() ?: "?"), color = colors.secondaryText)
                if (screen.nextOffset != null) Text(stringResource(Res.string.discovery_collection_partial), color = colors.secondaryText)
                if (screen.loading) Text(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
                if (screen.building) Text(stringResource(Res.string.discovery_building), color = colors.secondaryText)
                if (screen.status !in setOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Building)) Text(stringResource(discoveryStatusMessage(screen.status)), color = colors.secondaryText)
                screen.lastUpdated?.let { Text(stringResource(Res.string.discovery_last_updated, it), color = colors.secondaryText) }
                DiscoveryRecommendationFilters(screen, send)
                if (screen.blockedGenres.status == ProviderDiscoveryStatus.Ready) DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.BlockedGenres)) }) {
                    Text(stringResource(Res.string.discovery_blocked_genres))
                }
                screen.genre?.takeIf { screen.blockedGenres.status == ProviderDiscoveryStatus.Ready }?.let { genre -> GenreBlockRow(
                    genre, screen.blockedGenres.tags.any { discoveryGenreKey(it) == discoveryGenreKey(genre) }, screen, colors, send) }
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
                items(artists, key = { "artist:${it.id}" }) { artist ->
                    DiscoveryBrowserArtist(artist, colors) { actions.onDiscoveryArtistDetails(artist.id) }
                }
            }
            if (screen.filtered().isEmpty() && !screen.loading) item { Text(stringResource(Res.string.discovery_empty), color = colors.secondaryText) }
            if (screen.nextOffset != null) item { DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.More) }, enabled = !screen.loading) { Text(stringResource(Res.string.discovery_load_more)) } }
            }
        }
    }
}

@Composable
private fun DiscoveryRecommendationFilters(screen: DiscoveryBrowserState, send: (DiscoveryBrowseAction) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DiscoveryRecommendationSort.entries.filter { it != DiscoveryRecommendationSort.FirstSeen || screen.hasDates }) { sort ->
            FilterChip(selected = screen.sort == sort, onClick = { send(DiscoveryBrowseAction.Sort(sort)) }, label = {
                Text(stringResource(when (sort) {
                    DiscoveryRecommendationSort.Rank -> Res.string.discovery_sort_rank
                    DiscoveryRecommendationSort.Name -> Res.string.discovery_sort_name
                    DiscoveryRecommendationSort.FirstSeen -> Res.string.discovery_sort_first_seen
                }))
            })
        }
        items(DiscoveryRecommendationFilter.entries.filter { when (it) {
            DiscoveryRecommendationFilter.New -> screen.hasDates
            DiscoveryRecommendationFilter.Direct, DiscoveryRecommendationFilter.Further -> screen.hasDepth
            else -> true
        } }) { filter ->
            FilterChip(selected = screen.filter == filter, onClick = { send(DiscoveryBrowseAction.Filter(filter)) }, label = {
                Text(stringResource(when (filter) {
                    DiscoveryRecommendationFilter.All -> Res.string.discovery_all_recommendations
                    DiscoveryRecommendationFilter.New -> Res.string.discovery_new_this_week
                    DiscoveryRecommendationFilter.Direct -> Res.string.discovery_close_to_home
                    DiscoveryRecommendationFilter.Further -> Res.string.discovery_further_out
                }))
            })
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(screen.genres.distinct()) { genre ->
            FilterChip(selected = screen.genre == genre, onClick = { send(DiscoveryBrowseAction.Genre(genre.takeUnless { screen.genre == genre })) }, label = { Text(genre) })
        }
        items(screen.seeds.distinctBy { it.name }) { seed ->
            FilterChip(selected = screen.seed == seed.name, onClick = { send(DiscoveryBrowseAction.Seed(seed.name.takeUnless { screen.seed == seed.name })) }, label = { Text(stringResource(Res.string.discovery_because_you_like, seed.name)) })
        }
    }
}

@Composable
internal fun DiscoveryBrowserArtist(artist: ProviderDiscoveryArtist, colors: NaviampColors, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = artist.artistMbid != null, role = Role.Button, onClick = onOpen).padding(8.dp),
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

@Composable
private fun GenreBlockRow(tag: String, blocked: Boolean, screen: DiscoveryBrowserState, colors: NaviampColors, send: (DiscoveryBrowseAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(tag, Modifier.weight(1f), color = colors.primaryText)
        DiscoveryTextButton(colors = colors, onClick = { send(DiscoveryBrowseAction.BlockGenre(tag, !blocked)) }, enabled = !screen.loading && discoveryGenreKey(tag) !in screen.uncertainGenres) {
            Text(stringResource(if (blocked) Res.string.discovery_unblock_genre else Res.string.discovery_block_genre))
        }
    }
}

/** Shared high-contrast pointer/keyboard/remote controls; no host-specific focus policy. */
@Composable
internal fun DiscoveryTextButton(colors: NaviampColors, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = focused || pressed
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, interactionSource = interaction,
        colors = ButtonDefaults.textButtonColors(containerColor = if (active) colors.primaryText else Color.Transparent,
            contentColor = if (active) colors.background else colors.primaryText, disabledContentColor = colors.mutedText), content = content)
}
