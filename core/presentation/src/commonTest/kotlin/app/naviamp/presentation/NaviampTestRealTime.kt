package app.naviamp.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lets work on real dispatchers, such as resource loading for translated messages, make progress. */
internal suspend fun awaitRealTime(millis: Long = 10) = withContext(Dispatchers.Default) { delay(millis) }
