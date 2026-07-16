package app.naviamp.domain.popular

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal actual fun currentTimeMillis(): Long =
    Clock.System.now().toEpochMilliseconds()
