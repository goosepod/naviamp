package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.playback.PlaybackStreamMetadata

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
