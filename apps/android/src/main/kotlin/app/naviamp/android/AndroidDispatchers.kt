package app.naviamp.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
internal val AndroidWaveformWorkDispatcher = Dispatchers.Default.limitedParallelism(2)

@OptIn(ExperimentalCoroutinesApi::class)
internal val AndroidWaveformStorageDispatcher = Dispatchers.IO.limitedParallelism(1)
