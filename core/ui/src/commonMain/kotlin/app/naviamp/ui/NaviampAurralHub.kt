package app.naviamp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NaviampAurralHomeEntry(discovery: SharedProviderDiscoveryUi, colors: NaviampColors, onOpen: () -> Unit) {
    DiscoveryNavigationCard(colors, onOpen, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(56.dp).background(colors.background, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                NaviampServiceIcon(discovery.providerId, colors.primaryText, 38.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedProviderName(discovery.providerId), color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                Text(stringResource(Res.string.discovery_hub_intro), color = colors.secondaryText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(NaviampIcons.ChevronRight, null, tint = colors.secondaryText, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun NaviampAurralHub(discovery: SharedProviderDiscoveryUi, colors: NaviampColors, actions: NaviampHomeActions,
    listState: LazyListState, backFocus: FocusRequester, send: (DiscoveryBrowseAction) -> Unit, navigationModifier: Modifier = Modifier,
    entryFocusGeneration: Int? = null, onEntryFocusHandled: (Int) -> Unit = {}) {
    Column(Modifier.fillMaxSize().then(navigationModifier)) {
        DiscoveryPageHeader(localizedProviderName(discovery.providerId), colors, { send(DiscoveryBrowseAction.Close) }, backFocus,
            providerId = discovery.providerId, hub = true, entryFocusGeneration = entryFocusGeneration, onEntryFocusHandled = onEntryFocusHandled)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columns = if (maxWidth >= 700.dp) 2 else 1
            val avatarCount = if (maxWidth >= 500.dp) 3 else 1
            val sections = buildList {
                add(HubSection(DiscoveryBrowsePage.Search, Res.string.discovery_catalog_search_action, Res.string.discovery_hub_search, NaviampIcons.Search))
                if (discovery.feed.chartsAvailable || discovery.feed.trending.isNotEmpty()) add(HubSection(DiscoveryBrowsePage.Trending,
                    Res.string.discovery_charts_title, Res.string.discovery_hub_charts, NaviampIcons.Fire))
                add(HubSection(null, Res.string.discovery_requests_title, Res.string.discovery_hub_requests, NaviampIcons.Queue))
                if (discovery.feed.genreBlockingAvailable) add(HubSection(DiscoveryBrowsePage.BlockedGenres,
                    Res.string.discovery_chart_genres, Res.string.discovery_hub_genres, NaviampIcons.Settings))
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "intro") {
                    Text(stringResource(Res.string.discovery_hub_intro), color = colors.secondaryText, fontSize = 15.sp)
                    if (discovery.feed.status !in setOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Building))
                        DiscoveryStatusPanel(discovery.feed.status, colors)
                }
                item(key = "recommendations") {
                    DiscoveryNavigationCard(colors, { send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations)) }, Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DiscoveryIconBadge(NaviampIcons.Brain, colors)
                                Text(stringResource(Res.string.discovery_recommendations), color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                                Text(stringResource(Res.string.discovery_hub_recommendations), color = colors.secondaryText, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                                    discovery.feed.recommendations.take(avatarCount).forEach { artist ->
                                        NaviampCoverArt(artist.imageUrl, colors, 64.dp,
                                            resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Artist, 64.dp, 6.dp), mediaKind = SharedMediaItemKind.Artist)
                                    }
                                }
                                Icon(NaviampIcons.ChevronRight, null, tint = colors.secondaryText)
                            }
                        }
                    }
                }
                items(sections.chunked(columns), key = { "sections:${it.first().title}" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { section ->
                            DiscoveryNavigationCard(colors, {
                                if (section.page == null) actions.onDiscoveryRequests()
                                else send(DiscoveryBrowseAction.Open(section.page))
                            }, Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth().heightIn(min = 164.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        DiscoveryIconBadge(section.icon, colors)
                                        Icon(NaviampIcons.ChevronRight, null, tint = colors.secondaryText, modifier = Modifier.size(20.dp))
                                    }
                                    Text(stringResource(section.title), color = colors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(section.description), color = colors.secondaryText, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
private data class HubSection(val page: DiscoveryBrowsePage?, val title: org.jetbrains.compose.resources.StringResource,
    val description: org.jetbrains.compose.resources.StringResource, val icon: ImageVector)

@Composable
internal fun DiscoveryNavigationCard(colors: NaviampColors, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    Surface(onClick = onClick, enabled = enabled, interactionSource = interaction, modifier = modifier,
        shape = RoundedCornerShape(16.dp), color = colors.controlSurface,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.primaryText else colors.border.copy(alpha = if (hovered) .7f else .24f)),
        content = content)
}

@Composable
internal fun DiscoveryIconBadge(icon: ImageVector, colors: NaviampColors) {
    Box(Modifier.size(42.dp).background(colors.accent.copy(alpha = .18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(23.dp), tint = colors.primaryText)
    }
}

@Composable
internal fun DiscoveryPageHeader(title: String, colors: NaviampColors, onBack: () -> Unit,
    backFocus: FocusRequester, providerId: String = ProviderIdAurral, hub: Boolean = false,
    onRefresh: (() -> Unit)? = null, refreshing: Boolean = false, onRequests: (() -> Unit)? = null, entryFocusGeneration: Int? = null, onEntryFocusHandled: (Int) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 520.dp
        LaunchedEffect(backFocus, title, narrow, entryFocusGeneration) {
            backFocus.requestFocus()
            entryFocusGeneration?.let(onEntryFocusHandled)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DiscoveryActionButton(colors, onBack, modifier = Modifier.focusRequester(backFocus)) {
                    Icon(NaviampIcons.Back, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(Res.string.common_back))
                }
                if (!narrow) {
                    if (hub) NaviampServiceIcon(providerId, colors.primaryText, 36.dp)
                    DiscoveryPageTitle(title, colors, providerId, hub, Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                onRefresh?.let { refresh -> DiscoveryIconControl(NaviampIcons.Refresh, stringResource(Res.string.library_refresh), colors, !refreshing, refresh) }
                onRequests?.let { open -> DiscoveryIconControl(NaviampIcons.Queue, stringResource(Res.string.discovery_requests_title), colors, true, open) }
            }
            if (narrow) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hub) NaviampServiceIcon(providerId, colors.primaryText, 36.dp)
                DiscoveryPageTitle(title, colors, providerId, hub, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiscoveryPageTitle(title: String, colors: NaviampColors, providerId: String, hub: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!hub) Text(localizedProviderName(providerId), color = colors.mutedText, fontSize = 12.sp)
        Text(title, color = colors.primaryText, fontSize = if (hub) 26.sp else 23.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
internal fun DiscoveryIconControl(icon: ImageVector, label: String, colors: NaviampColors, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp),
        border = BorderStroke(1.dp, colors.border.copy(alpha = .35f)),
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = colors.primaryText, disabledContentColor = colors.mutedText)) {
        Icon(icon, label, Modifier.size(20.dp))
    }
}

@Composable
internal fun DiscoveryActionButton(colors: NaviampColors, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val color = if (focused) colors.primaryText else if (primary) colors.accent else colors.controlSurface
    val foreground = if (focused) colors.background else if (primary) colors.onAccent else colors.primaryText
    OutlinedButton(onClick, modifier.heightIn(min = 42.dp), enabled, interactionSource = interaction, shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (focused) colors.primaryText else colors.border.copy(alpha = .4f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = color, contentColor = foreground, disabledContentColor = colors.mutedText), content = content)
}

@Composable
internal fun DiscoveryStatusPanel(status: ProviderDiscoveryStatus?, colors: NaviampColors) {
    Surface(color = colors.controlSurface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colors.border.copy(alpha = .25f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NaviampIcons.Info, null, tint = colors.secondaryText, modifier = Modifier.size(20.dp))
            Text(stringResource(discoveryStatusMessage(status)), color = colors.secondaryText, fontSize = 13.sp)
        }
    }
}
