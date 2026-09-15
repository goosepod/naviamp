package app.naviamp.ui

/** Whether this renderer can animate waveform progress without repainting the entire app surface. */
internal expect val platformSupportsContinuousWaveformProgress: Boolean
