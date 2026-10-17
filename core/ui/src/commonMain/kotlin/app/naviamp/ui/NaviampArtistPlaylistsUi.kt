package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ArtistPlaylistsSection(
    state: SharedArtistPlaylistsUi,
    colors: NaviampColors,
    onSelected: (SharedMediaItemUi) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("artist_playlists"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.artist_playlists).uppercase(), color = colors.primaryText,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !state.loading) {
                Text(stringResource(if (state.failed) Res.string.artist_playlists_retry else Res.string.artist_playlists_refresh))
            }
        }
        if (state.loading) Text(stringResource(Res.string.artist_playlists_loading), color = colors.secondaryText)
        if (state.failed) Text(stringResource(Res.string.artist_playlists_failed), color = colors.secondaryText)
        if (state.truncated) Text(stringResource(Res.string.artist_playlists_truncated), color = colors.secondaryText)
        if (!state.loading && !state.failed && !state.truncated && state.items.isEmpty()) {
            Text(stringResource(Res.string.artist_playlists_empty), color = colors.secondaryText)
        }
        state.items.forEach { playlist ->
            SharedMediaRow(item = playlist, colors = colors, mediaKind = SharedMediaItemKind.Playlist,
                onClick = { onSelected(playlist) })
        }
    }
}
