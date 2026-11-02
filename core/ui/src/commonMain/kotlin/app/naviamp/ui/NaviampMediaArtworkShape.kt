package app.naviamp.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Shared artwork preference, provided by each shared shell including its dialogs. */
internal val LocalSquareAlbumArtwork = staticCompositionLocalOf { false }

/** Artist portraits are circular; album and collection artwork retain a square silhouette. */
internal fun mediaArtworkCornerRadius(
    kind: SharedMediaItemKind,
    size: Dp,
    squareCornerRadius: Dp,
    squareAlbumArtwork: Boolean = false,
): Dp = when {
    kind == SharedMediaItemKind.Artist -> size / 2
    squareAlbumArtwork && (kind == SharedMediaItemKind.Album || kind == SharedMediaItemKind.Track) -> 0.dp
    else -> squareCornerRadius
}

@Composable
internal fun resolvedMediaArtworkCornerRadius(kind: SharedMediaItemKind, size: Dp, cornerRadius: Dp): Dp =
    mediaArtworkCornerRadius(kind, size, cornerRadius, LocalSquareAlbumArtwork.current)
