package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.SavedInternetRadioStation

data class InternetRadioStartPlan(
    val recentStations: List<InternetRadioStation>,
    val recentSavedStations: List<SavedInternetRadioStation>,
    val nowPlayingTrack: Track?,
    val station: InternetRadioStation,
    val streamMetadata: PlaybackStreamMetadata,
    val playbackProgress: PlaybackProgress,
    val playbackQueue: PlaybackQueue,
    val openNowPlaying: Boolean,
    val canFavorite: Boolean,
    val isFavorite: Boolean,
    val clearShuffleSnapshot: Boolean,
    val clearRadioContinuation: Boolean,
    val savePlaybackSession: Boolean,
    val status: String,
    val notificationTitle: String,
    val notificationSubtitle: String,
    val notificationCoverArtUrl: String?,
    val engineMediaId: String,
    val replayGainOff: Boolean,
)

data class InternetRadioStartApplier(
    val saveRecentStations: (List<SavedInternetRadioStation>) -> Unit = {},
    val setRecentStations: (List<InternetRadioStation>) -> Unit = {},
    val clearRadioContinuation: () -> Unit = {},
    val clearShuffleSnapshot: () -> Unit = {},
    val clearPlaybackQueue: () -> Unit = {},
    val setNowPlayingTrack: (Track?) -> Unit = {},
    val setNowPlayingCoverArtUrl: (String?) -> Unit = {},
    val resetNowPlayingSidecars: () -> Unit = {},
    val applyFavoriteState: (Boolean, Boolean) -> Unit = { _, _ -> },
    val setNowPlayingStation: (InternetRadioStation) -> Unit = {},
    val setStreamMetadata: (PlaybackStreamMetadata) -> Unit = {},
    val setPlaybackProgress: (PlaybackProgress) -> Unit = {},
    val setPlaybackQueue: (PlaybackQueue) -> Unit = {},
    val setStatus: (String) -> Unit = {},
    val savePlaybackSession: () -> Unit = {},
    val openNowPlaying: () -> Unit = {},
    val updateNotificationMetadata: (String, String, String?) -> Unit = { _, _, _ -> },
)

fun applyInternetRadioStart(
    plan: InternetRadioStartPlan,
    applier: InternetRadioStartApplier,
    nowPlayingTrack: Track? = plan.nowPlayingTrack,
) {
    applyRememberInternetRadioStation(
        plan = InternetRadioRecentStationPlan(
            recentStations = plan.recentStations,
            recentSavedStations = plan.recentSavedStations,
        ),
        applier = InternetRadioRecentStationApplier(
            saveRecentStations = applier.saveRecentStations,
            setRecentStations = applier.setRecentStations,
        ),
    )
    if (plan.clearRadioContinuation) applier.clearRadioContinuation()
    if (plan.clearShuffleSnapshot) applier.clearShuffleSnapshot()
    applier.clearPlaybackQueue()
    applier.setNowPlayingTrack(nowPlayingTrack)
    applier.setNowPlayingCoverArtUrl(plan.notificationCoverArtUrl)
    applier.resetNowPlayingSidecars()
    applier.applyFavoriteState(plan.canFavorite, plan.isFavorite)
    applier.setNowPlayingStation(plan.station)
    applier.setStreamMetadata(plan.streamMetadata)
    applier.setPlaybackProgress(plan.playbackProgress)
    applier.setPlaybackQueue(plan.playbackQueue)
    applier.setStatus(plan.status)
    if (plan.savePlaybackSession) applier.savePlaybackSession()
    if (plan.openNowPlaying) applier.openNowPlaying()
    applier.updateNotificationMetadata(
        plan.notificationTitle,
        plan.notificationSubtitle,
        plan.notificationCoverArtUrl,
    )
}

fun planInternetRadioStart(
    station: InternetRadioStation,
    recentStations: List<InternetRadioStation>,
    recentSavedStations: List<SavedInternetRadioStation>,
): InternetRadioStartPlan {
    val recentPlan = planRememberInternetRadioStation(
        station = station,
        recentStations = recentStations,
        recentSavedStations = recentSavedStations,
    )
    return InternetRadioStartPlan(
        recentStations = recentPlan.recentStations,
        recentSavedStations = recentPlan.recentSavedStations,
        nowPlayingTrack = null,
        station = station,
        streamMetadata = PlaybackStreamMetadata(),
        playbackProgress = PlaybackProgress.Unknown,
        playbackQueue = PlaybackQueue(),
        openNowPlaying = true,
        canFavorite = false,
        isFavorite = false,
        clearShuffleSnapshot = true,
        clearRadioContinuation = true,
        savePlaybackSession = true,
        status = "Loading ${station.name}...",
        notificationTitle = station.name,
        notificationSubtitle = "Internet radio",
        notificationCoverArtUrl = null,
        engineMediaId = station.id,
        replayGainOff = true,
    )
}
