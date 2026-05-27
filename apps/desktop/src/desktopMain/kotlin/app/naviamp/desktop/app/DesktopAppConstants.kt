package app.naviamp.desktop

const val PreviousRestartThresholdSeconds = 10.0

const val LibraryPageSize = 50
const val PlaybackPositionSaveThresholdSeconds = 5.0
const val PlaybackProgressUiUpdateIntervalMillis = 500L
const val PlaybackProgressUiUpdateThresholdSeconds = 0.45
const val VisualizerFrameIntervalMillis = 125L
const val NowPlayingHeartbeatIntervalMillis = 30_000L
const val PlaylistDetailRefreshIntervalMillis = 60_000L
const val PendingSeekToleranceSeconds = 2.0
const val PendingSeekStaleProgressWindowMillis = 1_500L
const val RadioRefillThreshold = 10
const val RadioRefillCount = 50
const val RadioQueueHistoryLimit = 25
const val PopularRadioSeedLimit = 5
const val InitialSimilarRadioCount = 10
val SimilarRadioExpansionCounts = listOf(25, 50)

const val PlayReportDurationFraction = 0.5
const val PlayReportMaxThresholdSeconds = 240.0
const val CoverArtPreloadHistoryLimit = 1
const val CoverArtPreloadUpcomingLimit = 5
const val PopularTracksFetchLimit = 25
const val PopularTracksDisplayLimit = 10
const val SimilarArtistsFetchLimit = 20
const val SimilarArtistsDisplayLimit = 10
