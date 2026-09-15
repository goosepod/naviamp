package app.naviamp.ui

internal actual val platformSupportsContinuousWaveformProgress: Boolean
    get() = !System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)
