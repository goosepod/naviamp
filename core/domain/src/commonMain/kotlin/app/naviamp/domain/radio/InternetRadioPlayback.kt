package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.SavedInternetRadioStation

const val MaxRecentInternetRadioStations = 12

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
    applier.saveRecentStations(plan.recentSavedStations)
    applier.setRecentStations(plan.recentStations)
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

data class InternetRadioMetadataUpdatePlan(
    val metadata: PlaybackStreamMetadata,
    val nowPlayingTrack: Track?,
    val updateNotificationMetadata: Boolean,
    val notificationTitle: String?,
    val notificationSubtitle: String,
    val notificationCoverArtUrl: String?,
)

data class InternetRadioMetadataUpdateApplier(
    val setStreamMetadata: (PlaybackStreamMetadata) -> Unit = {},
    val setNowPlayingTrack: (Track?) -> Unit = {},
    val updateNotificationMetadata: (String?, String, String?) -> Unit = { _, _, _ -> },
)

fun planInternetRadioMetadataUpdate(
    station: InternetRadioStation,
    metadata: PlaybackStreamMetadata,
    fallbackTrack: Track? = null,
    updateNotificationMetadata: Boolean = true,
): InternetRadioMetadataUpdatePlan {
    val title = metadata.title?.takeIf { it.isNotBlank() }
    return InternetRadioMetadataUpdatePlan(
        metadata = metadata,
        nowPlayingTrack = fallbackTrack?.let { internetRadioTrackWithMetadata(it, station, metadata) },
        updateNotificationMetadata = updateNotificationMetadata && title != null,
        notificationTitle = title,
        notificationSubtitle = station.name,
        notificationCoverArtUrl = null,
    )
}

fun applyInternetRadioMetadataUpdate(
    plan: InternetRadioMetadataUpdatePlan,
    applier: InternetRadioMetadataUpdateApplier,
) {
    applier.setStreamMetadata(plan.metadata)
    plan.nowPlayingTrack?.let(applier.setNowPlayingTrack)
    if (plan.updateNotificationMetadata) {
        applier.updateNotificationMetadata(
            plan.notificationTitle,
            plan.notificationSubtitle,
            plan.notificationCoverArtUrl,
        )
    }
}

fun planInternetRadioStart(
    station: InternetRadioStation,
    recentStations: List<InternetRadioStation>,
    recentSavedStations: List<SavedInternetRadioStation>,
): InternetRadioStartPlan =
    InternetRadioStartPlan(
        recentStations = recentInternetRadioStationsWith(recentStations, station),
        recentSavedStations = recentSavedInternetRadioStationsWith(recentSavedStations, station),
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

fun internetRadioTrack(station: InternetRadioStation): Track =
    Track(
        id = internetRadioTrackId(station.id),
        title = station.name,
        artistName = "Internet Radio",
        albumTitle = station.homePageUrl ?: station.streamUrl,
        durationSeconds = null,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )

fun internetRadioTrackWithMetadata(
    fallbackTrack: Track,
    station: InternetRadioStation,
    metadata: PlaybackStreamMetadata,
): Track =
    metadata.title
        ?.takeIf { it.isNotBlank() }
        ?.let { streamTitle ->
            fallbackTrack.copy(
                title = streamTitle,
                artistName = station.name,
                albumTitle = "Internet Radio",
            )
        }
        ?: fallbackTrack

fun recentInternetRadioStationsWith(
    recentStations: List<InternetRadioStation>,
    station: InternetRadioStation,
    limit: Int = MaxRecentInternetRadioStations,
): List<InternetRadioStation> =
    (listOf(station) + recentStations.filterNot { it.id == station.id }).take(limit)

fun recentSavedInternetRadioStationsWith(
    recentStations: List<SavedInternetRadioStation>,
    station: InternetRadioStation,
    limit: Int = MaxRecentInternetRadioStations,
): List<SavedInternetRadioStation> {
    val saved = SavedInternetRadioStation.fromStation(station)
    return (listOf(saved) + recentStations.filterNot { it.id == saved.id }).take(limit)
}
