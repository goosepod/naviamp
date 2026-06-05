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
