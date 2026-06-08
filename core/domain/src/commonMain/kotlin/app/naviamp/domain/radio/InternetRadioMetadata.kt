package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackStreamMetadata

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
