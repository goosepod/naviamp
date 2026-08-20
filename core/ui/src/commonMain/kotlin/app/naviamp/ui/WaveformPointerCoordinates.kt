package app.naviamp.ui

/** Converts the local layout width into the coordinate space used by pointer offsets. */
internal expect fun waveformPointerInteractionWidth(
    layoutWidthPx: Float,
    density: Float,
): Float
