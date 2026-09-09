package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.*
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
