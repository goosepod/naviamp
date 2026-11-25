package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Kept outside the scrolling catalog so pending navigation always remains visible. */
@Composable
internal fun NaviampLibraryLoadingStatus(
    colors: NaviampColors,
    view: NaviampLibraryView,
    catalog: NaviampLibraryCatalogUi,
) {
    val letter = catalog.pendingJump
    val loading = letter != null || catalog.syncStatus.isSyncing
    val message = when {
        letter != null -> stringResource(Res.string.library_jump_loading, letter.toString())
        catalog.syncStatus.albumIndexCount != null -> stringResource(Res.string.library_indexing_albums, catalog.syncStatus.albumIndexCount)
        catalog.syncStatus.albumIndexFailed -> stringResource(Res.string.library_album_index_failed)
        loading -> when (view) {
            NaviampLibraryView.Artists -> stringResource(Res.string.library_loading_artists)
            NaviampLibraryView.Albums -> stringResource(Res.string.library_loading_albums)
            NaviampLibraryView.Songs -> stringResource(Res.string.library_loading_songs)
        }
        catalog.jumpFailed -> stringResource(Res.string.library_jump_failed)
        else -> catalog.syncStatus.message
    } ?: return
    val foreground = if (loading) colors.onAccent else colors.primaryText
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (loading) colors.accent else colors.controlSurface)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (letter != null) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(colors.onAccent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter.toString(), color = foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            } else if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = foreground)
            }
            Text(message, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = foreground,
                trackColor = foreground.copy(alpha = 0.2f))
        }
    }
}

internal const val LibraryNewItemsIndicatorTestTag = "library-new-items"

/** Items a sync added that are currently listed, in display order. */
internal data class NaviampLibraryAddedItems(val count: Int, val firstIndex: Int, val firstId: String)

internal fun libraryAddedItems(
    view: NaviampLibraryView,
    catalog: NaviampLibraryCatalogUi,
    displayedIds: List<String>,
): NaviampLibraryAddedItems? {
    // Songs are paged, so Core never reports added songs.
    if (view == NaviampLibraryView.Songs || catalog.query.isNotBlank() || catalog.addedItemIds.isEmpty()) return null
    val added = catalog.addedItemIds.toSet()
    val firstIndex = displayedIds.indexOfFirst(added::contains).takeIf { it >= 0 } ?: return null
    return NaviampLibraryAddedItems(displayedIds.count(added::contains), firstIndex, displayedIds[firstIndex])
}

@Composable
internal fun libraryAddedItemsLabel(view: NaviampLibraryView, count: Int): String =
    if (view == NaviampLibraryView.Artists) pluralStringResource(Res.plurals.library_new_artists, count, count)
    else pluralStringResource(Res.plurals.library_new_albums, count, count)

/** Reports added items as they come on screen, so the notice never counts what was already seen. */
@Composable
internal fun ReportVisibleAddedItems(
    view: NaviampLibraryView,
    addedIds: List<String>,
    visibleKeys: () -> List<Any>,
    onSeen: (NaviampLibraryView, Set<String>) -> Unit,
) {
    if (addedIds.isEmpty()) return
    val currentVisibleKeys by rememberUpdatedState(visibleKeys)
    val currentOnSeen by rememberUpdatedState(onSeen)
    LaunchedEffect(view, addedIds) {
        val added = addedIds.toSet()
        snapshotFlow { currentVisibleKeys().filterIsInstance<String>().filterTo(mutableSetOf(), added::contains) }
            .filter { it.isNotEmpty() }
            .collect { currentOnSeen(view, it) }
    }
}

/** Rows at least half visible below [top]; a row under the floating notice has not been seen. */
internal fun LazyListLayoutInfo.keysVisibleBelow(top: Int): List<Any> = visibleItemsInfo.filter {
    (minOf(it.offset + it.size, viewportEndOffset) - maxOf(it.offset, top, viewportStartOffset)) * 2 >= it.size
}.map { it.key }

/** Grid cells at least half visible. */
internal fun LazyGridLayoutInfo.mostlyVisibleKeys(): List<Any> = visibleItemsInfo.filter {
    val top = it.offset.y
    val height = it.size.height
    (minOf(top + height, viewportEndOffset) - maxOf(top, viewportStartOffset)) * 2 >= height
}.map { it.key }

/**
 * New items can sort anywhere in a title-ordered catalog, so returning to the top is not enough.
 * Callers float it over the list: a report from an automatic sync must not move the rows.
 */
@Composable
internal fun NaviampLibraryNewItemsIndicator(
    colors: NaviampColors,
    view: NaviampLibraryView,
    added: NaviampLibraryAddedItems,
    onShow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showLabel = stringResource(Res.string.library_new_items_show)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier.padding(top = 8.dp).shadow(6.dp, shape).clip(shape).background(colors.controlSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = showLabel, role = Role.Button, onClick = onShow)
                .testTag(LibraryNewItemsIndicatorTestTag)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(libraryAddedItemsLabel(view, added.count), color = colors.primaryText, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Text(showLabel, color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onDismiss) {
            Icon(NaviampIcons.Close, contentDescription = stringResource(Res.string.library_new_items_dismiss),
                tint = colors.secondaryText, modifier = Modifier.size(20.dp))
        }
    }
}
