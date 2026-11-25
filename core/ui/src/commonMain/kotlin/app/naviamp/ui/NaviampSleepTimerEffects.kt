package app.naviamp.ui

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun currentTimeMillis(): Long =
    kotlin.time.Clock.System.now().toEpochMilliseconds()
