package app.naviamp.desktop

import kotlinx.coroutines.Dispatchers

internal val DesktopStorageWorkDispatcher = Dispatchers.IO.limitedParallelism(2)
