package app.naviamp.presentation

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Portable wall-clock values used by Core service composition. */
@OptIn(ExperimentalTime::class)
fun naviampNowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalTime::class)
fun naviampNowIso8601(): String = Clock.System.now().toString()
