package app.naviamp.ui

import androidx.compose.runtime.Composable
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.now_playing_from_album
import app.naviamp.ui.generated.resources.now_playing_from_playlist
import app.naviamp.ui.generated.resources.now_playing_from_work
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun nowPlayingQueueContextLabel(context: NowPlayingQueueContextUi): String = when (context.source) {
    PlaybackProfileTargetType.Playlist -> stringResource(Res.string.now_playing_from_playlist, context.name)
    PlaybackProfileTargetType.Album -> stringResource(Res.string.now_playing_from_album, context.name)
    PlaybackProfileTargetType.Work -> stringResource(Res.string.now_playing_from_work, context.name)
}
