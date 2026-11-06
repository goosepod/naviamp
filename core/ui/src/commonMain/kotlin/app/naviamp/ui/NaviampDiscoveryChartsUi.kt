package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.discoveryCharts(screen: DiscoveryBrowserState, colors: NaviampColors,
    actions: NaviampHomeActions, playback: DiscoverySamplePlaybackUi) {
    val send = actions.onDiscoveryBrowse
    item(key = "chart-controls") {
        ScrollableTabRow(selectedTabIndex = screen.chartSection.ordinal, containerColor = colors.background, contentColor = colors.primaryText, edgePadding = 0.dp) {
            DiscoveryChartSection.entries.forEach { section -> Tab(selected = screen.chartSection == section,
                onClick = { send(DiscoveryBrowseAction.ChartSection(section)) }, text = {
                    DiscoveryText(stringResource(when (section) {
                        DiscoveryChartSection.Songs -> Res.string.discovery_chart_songs
                        DiscoveryChartSection.Albums -> Res.string.discovery_catalog_albums
                        DiscoveryChartSection.Artists -> Res.string.discovery_catalog_artists
                        DiscoveryChartSection.Genres -> Res.string.discovery_chart_genres
                    }))
                }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (screen.chartGenreId != null) DiscoveryActionButton(colors = colors, onClick = { send(DiscoveryBrowseAction.ChartGenre(null)) }) { DiscoveryText(stringResource(Res.string.discovery_charts_title)) }
        }
        if (screen.loading) DiscoveryText(stringResource(Res.string.discovery_loading), color = colors.secondaryText)
        if (screen.status != ProviderDiscoveryStatus.Ready) DiscoveryText(stringResource(discoveryStatusMessage(screen.status)), color = colors.secondaryText)
        val region = if (screen.chartGenreId != null) screen.genreChart?.region else screen.charts.region
        val updated = if (screen.chartGenreId != null) screen.genreChart?.updatedAt else screen.charts.updatedAt
        region?.let { DiscoveryText(stringResource(Res.string.discovery_chart_region, it), color = colors.secondaryText) }
        discoveryTimestamp(updated)?.let { DiscoveryText(stringResource(Res.string.discovery_last_updated, it), color = colors.secondaryText) }
        screen.genreChart?.takeIf { it.id == screen.chartGenreId }?.name?.let { DiscoveryText(it, color = colors.primaryText) }
    }
    if (screen.chartGenreId != null || screen.chartSection == DiscoveryChartSection.Songs) {
        if (screen.chartGenreId == null) item(key = "chart-playlist") {
            screen.charts.spotlight?.let { spotlight ->
                spotlight.daysAtTop?.let { DiscoveryText(stringResource(Res.string.discovery_chart_days_at_top, it.toString()), color = colors.secondaryText) }

            }
            if (screen.charts.canSave && screen.charts.playlistId != null) DiscoveryActionButton(colors = colors, primary = true,
                onClick = { send(DiscoveryBrowseAction.SaveChart) }, enabled = !screen.loading && screen.charts.libraryPlaylistId == null &&
                    screen.chartSave?.status !in setOf(ProviderDiscoveryOperationStatus.Uncertain, ProviderDiscoveryOperationStatus.Saved)) {
                DiscoveryText(stringResource(Res.string.discovery_chart_save_playlist))
            }
            if (screen.charts.libraryPlaylistId != null) DiscoveryText(stringResource(Res.string.discovery_chart_saved), color = colors.secondaryText)
            else screen.chartSave?.let { DiscoveryText(stringResource(discoveryOperationMessage(it.status)), color = colors.secondaryText) }
            if (screen.charts.canSave) DiscoveryText(stringResource(Res.string.discovery_chart_playlist_description), color = colors.secondaryText)
        }
        val songs = if (screen.chartGenreId != null) screen.genreChart?.takeIf { it.id == screen.chartGenreId }?.songs else screen.charts.songs
        if (songs == null) item { DiscoveryText(stringResource(Res.string.discovery_chart_unavailable), color = colors.secondaryText) }
        else items(songs, key = { "chart-song:${it.key}" }) { song ->
            Row(Modifier.fillMaxWidth().background(colors.controlSurface, RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NaviampCoverArt(song.imageUrl, colors, 56.dp, resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Album, 56.dp, 6.dp), mediaKind = SharedMediaItemKind.Album)
                Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = { send(DiscoveryBrowseAction.ChartSearch(song.artistName, ProviderCatalogScope.Artist)) })) {
                    DiscoveryText(listOfNotNull(song.position?.toString(), song.title).joinToString(" · "), color = colors.primaryText)
                    DiscoveryText(song.artistName, color = colors.secondaryText)
                    song.albumTitle?.let { DiscoveryText(it, color = colors.secondaryText) }
                    if (song.newEntry) DiscoveryText(stringResource(Res.string.discovery_chart_new), color = colors.secondaryText)
                    else song.movement?.takeIf { it != 0 }?.let { DiscoveryText(stringResource(Res.string.discovery_chart_movement, if (it > 0) "+$it" else it.toString()), color = colors.secondaryText) }
                }
                song.previewId?.let { preview ->
                    val active = playback.trackId == DiscoveryPreviewTrackPrefix + preview.value
                    val playing = active && playback.playing
                    OutlinedIconButton(onClick = { send(DiscoveryBrowseAction.ChartPreview(song.key)) }, enabled = !active || playback.canToggle) {
                        Icon(if (playing) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                            stringResource(if (playing) Res.string.transport_pause else Res.string.discovery_play_sample), tint = colors.primaryText)
                    }
                }
            }
        }
    } else when (screen.chartSection) {
        DiscoveryChartSection.Albums -> {
            if (screen.charts.albums == null) item { DiscoveryText(stringResource(Res.string.discovery_chart_unavailable), color = colors.secondaryText) }
            items(screen.charts.albums.orEmpty(), key = { "chart-album:${it.key}" }) { album ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { send(DiscoveryBrowseAction.ChartSearch(album.title, ProviderCatalogScope.Album)) }).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NaviampCoverArt(album.imageUrl, colors, 64.dp, resolvedMediaArtworkCornerRadius(SharedMediaItemKind.Album, 64.dp, 6.dp), mediaKind = SharedMediaItemKind.Album)
                    Column(Modifier.weight(1f)) {
                        DiscoveryText(listOfNotNull(album.position?.toString(), album.title).joinToString(" · "), color = colors.primaryText)
                        DiscoveryText(album.artistName, color = colors.secondaryText)
                        discoveryCalendarDate(album.releaseDate)?.let { DiscoveryText(it, color = colors.secondaryText) }
                    }
                }
            }
        }
        DiscoveryChartSection.Artists -> {
            if (screen.charts.artists == null) item { DiscoveryText(stringResource(Res.string.discovery_chart_unavailable), color = colors.secondaryText) }
            items(screen.charts.artists.orEmpty(), key = { "chart-artist:${it.key}" }) { entry ->
                // An unmatched chart artist goes through explicit catalog selection, never a name-to-ID guess.
                val open = { if (entry.artist.artistMbid != null) actions.onDiscoveryArtistDetails(entry.artist.id)
                    else send(DiscoveryBrowseAction.ChartSearch(entry.artist.name, ProviderCatalogScope.Artist)) }
                DiscoveryBrowserArtist(entry.artist, colors, canOpen = true, onOpen = open)
            }
        }
        DiscoveryChartSection.Genres -> items(screen.charts.genres, key = { "genre:${it.id}" }) { genre ->
            DiscoveryNavigationCard(colors, { send(DiscoveryBrowseAction.ChartGenre(genre.id)) }, Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    DiscoveryText(genre.name, color = colors.primaryText)
                    Icon(NaviampIcons.ChevronRight, null, tint = colors.secondaryText)
                }
            }
        }
        else -> Unit
    }
}
