package app.naviamp.ui

/** Compose Desktop reports pointer offsets in logical points on density-scaled displays. */
internal actual fun waveformPointerInteractionWidth(
    layoutWidthPx: Float,
    density: Float,
): Float = layoutWidthPx / density.coerceAtLeast(1f)
