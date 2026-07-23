package app.naviamp.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Bounded JVM filesystem dispatcher for serialized Android sidecar writes. */
@OptIn(ExperimentalCoroutinesApi::class)
internal val AndroidWaveformStorageDispatcher = Dispatchers.IO.limitedParallelism(1)
