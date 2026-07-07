package app.naviamp.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
internal val DesktopWaveformWorkDispatcher = Dispatchers.IO.limitedParallelism(2)
