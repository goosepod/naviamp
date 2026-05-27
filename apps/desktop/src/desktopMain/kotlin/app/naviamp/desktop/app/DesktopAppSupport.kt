package app.naviamp.desktop

import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.home.HomeAlbumYear
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.provider.ProviderCapabilities

fun PlaybackSettings.forEngine(playbackEngine: PlaybackEngine): PlaybackSettings =
    copy(
        replayGainMode = if (playbackEngine.supportsReplayGain) {
            replayGainMode
        } else {
            ReplayGainMode.Off
        },
        crossfadeDurationSeconds = if (playbackEngine.supportsCrossfade) {
            if (gaplessEnabled) 0 else crossfadeDurationSeconds.coerceIn(0, 12)
        } else {
            0
        },
        gaplessEnabled = playbackEngine.supportsGapless && gaplessEnabled,
        volumePercent = if (playbackEngine.supportsSoftwareVolume) {
            volumePercent.coerceIn(0, 100)
        } else {
            100
        },
        debugLoggingEnabled = debugLoggingEnabled,
        lrclibLyricsEnabled = lrclibLyricsEnabled,
        previousButtonBehavior = previousButtonBehavior,
        upNextSelectionBehavior = upNextSelectionBehavior,
    )

fun DesktopCache.asHomeLibraryRepository(): HomeLibraryRepository =
    object : HomeLibraryRepository {
        override fun albumYears(sourceId: String): List<HomeAlbumYear> =
            libraryAlbumYears(sourceId).map { year ->
                HomeAlbumYear(year = year.year, albumCount = year.albumCount)
            }
    }

fun ProviderCapabilities.asStatsMap(): Map<String, Boolean> =
    mapOf(
        "Streaming transcode" to supportsStreamingTranscode,
        "Download transcode" to supportsDownloadTranscode,
        "Artist radio" to supportsArtistRadio,
        "Album radio" to supportsAlbumRadio,
        "Track radio" to supportsTrackRadio,
        "Track favorites" to supportsTrackFavorites,
        "Track ratings" to supportsTrackRatings,
        "Play reporting" to supportsPlayReporting,
    )

fun restoredRoute(
    savedRouteName: String?,
    hasConnection: Boolean,
    hasRestoredTrack: Boolean,
): AppRoute {
    if (!hasConnection) return AppRoute.Settings
    return when (val route = AppRoute.fromStoredName(savedRouteName)) {
        AppRoute.Player -> if (hasRestoredTrack) AppRoute.Player else AppRoute.Home
        AppRoute.AlbumDetail -> AppRoute.Home
        AppRoute.ArtistDetail -> AppRoute.Search
        AppRoute.PlaylistDetail -> AppRoute.Playlists
        else -> route
    }
}

fun restoredLastContentRoute(savedRouteName: String?): AppRoute =
    when (val route = AppRoute.fromStoredName(savedRouteName)) {
        AppRoute.Player,
        AppRoute.AlbumDetail,
        AppRoute.ArtistDetail,
        AppRoute.PlaylistDetail,
        -> AppRoute.Home
        else -> route
    }
