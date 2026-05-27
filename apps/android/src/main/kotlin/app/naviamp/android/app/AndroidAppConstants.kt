package app.naviamp.android

const val PendingSeekToleranceSeconds = 2.0
const val PendingSeekStaleProgressWindowMillis = 1_500L
const val AndroidAutoProgressPublishIntervalMillis = 1_000L
const val AndroidNowPlayingHeartbeatIntervalMillis = 30_000L
const val AndroidVisualizerFrameIntervalMillis = 125L
const val AndroidPlaylistDetailRefreshIntervalMillis = 60_000L
const val AndroidAutoIgnoreZeroSeekAfterSeconds = 3.0
const val AndroidGaplessPrepareWindowSeconds = 2.0
const val AndroidAudioPrefetchDepth = 10
const val AndroidSidecarPrepDepth = 5
const val AndroidPlaybackSessionSaveIntervalMillis = 5_000L
const val AndroidLibraryFreshnessCheckIntervalMillis = 60_000L
const val AndroidMaxDownloadBytes = 2L * 1024L * 1024L * 1024L
const val AndroidLibraryAlbumPageSize = 500
const val AndroidLibraryArtistLimit = 100_000
const val AndroidPopularRadioSeedLimit = 5
const val AndroidInitialSimilarRadioCount = 10
const val AndroidPopularTrackFallbackLimit = 120L
const val ProviderArtistPopularTrackAlbumFallbackLimit = 30
const val PopularTracksFetchLimit = 25
const val PopularTracksDisplayLimit = 10
const val SimilarArtistsFetchLimit = 20
const val SimilarArtistsDisplayLimit = 10
val AndroidSimilarRadioExpansionCounts = listOf(25, 50)
