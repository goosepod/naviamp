package app.naviamp.domain

private const val InternetRadioTrackIdPrefix = "internet-radio:"

fun internetRadioTrackId(stationId: String): TrackId =
    TrackId("$InternetRadioTrackIdPrefix$stationId")

fun Track.isInternetRadioTrack(): Boolean =
    id.value.startsWith(InternetRadioTrackIdPrefix)

fun Track.internetRadioStationId(): String? =
    id.value.takeIf { it.startsWith(InternetRadioTrackIdPrefix) }
        ?.removePrefix(InternetRadioTrackIdPrefix)
