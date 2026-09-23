package app.naviamp.ui

import androidx.compose.ui.unit.Dp

/** Artist portraits are circular; album and collection artwork retain a square silhouette. */
internal fun mediaArtworkCornerRadius(kind: SharedMediaItemKind, size: Dp, squareCornerRadius: Dp): Dp =
    if (kind == SharedMediaItemKind.Artist) size / 2 else squareCornerRadius
