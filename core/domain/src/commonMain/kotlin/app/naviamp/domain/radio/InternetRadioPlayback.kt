package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.settings.SavedInternetRadioStation

const val MaxRecentInternetRadioStations = 12

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
