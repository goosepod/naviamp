package app.naviamp.ui

import kotlin.time.Clock

internal actual fun currentTimeMillis(): Long =
    Clock.System.now().toEpochMilliseconds()
