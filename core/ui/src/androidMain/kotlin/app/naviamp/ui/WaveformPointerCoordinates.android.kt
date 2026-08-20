package app.naviamp.ui

/** Android pointer offsets and layout sizes share the same pixel coordinate space. */
internal actual fun waveformPointerInteractionWidth(
    layoutWidthPx: Float,
    density: Float,
): Float = layoutWidthPx
