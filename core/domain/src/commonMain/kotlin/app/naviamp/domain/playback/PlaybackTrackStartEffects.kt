package app.naviamp.domain.playback

import app.naviamp.domain.Track

data class PlaybackTrackStartEffectApplier(
    val clearShuffleSnapshot: () -> Unit = {},
    val clearRadioContinuation: () -> Unit = {},
    val clearInternetRadioNowPlaying: () -> Unit = {},
    val resetStreamMetadata: () -> Unit = {},
    val setNowPlayingTrack: (Track) -> Unit = {},
    val setNowPlayingCoverArtUrl: (String?) -> Unit = {},
    val applyFavoriteState: (Boolean, Boolean) -> Unit = { _, _ -> },
    val incrementPlayReportSession: () -> Unit = {},
    val clearSubmittedPlayReportSession: () -> Unit = {},
    val savePlaybackSession: () -> Unit = {},
    val openNowPlaying: () -> Unit = {},
    val reportNowPlaying: (Track) -> Unit = {},
    val resetSidecars: () -> Unit = {},
    val resetProgress: () -> Unit = {},
    val refillRadioQueue: () -> Unit = {},
    val loadRelatedTracks: (Track) -> Unit = {},
    val loadAudioTags: (Track) -> Unit = {},
    val loadLyrics: (Track) -> Unit = {},
    val startAudioPrefetch: () -> Unit = {},
    val startSidecarPrep: () -> Unit = {},
    val updateNotificationMetadata: (String?, String?, String?) -> Unit = { _, _, _ -> },
)

fun applyPlaybackTrackStartEffects(
    track: Track,
    coverArtUrl: String?,
    effects: PlaybackTrackStartEffectsPlan,
    applier: PlaybackTrackStartEffectApplier,
) {
    val presentation = effects.presentation
    if (presentation.clearShuffleSnapshot) applier.clearShuffleSnapshot()
    if (effects.clearRadioContinuation) applier.clearRadioContinuation()
    if (presentation.clearInternetRadioNowPlaying) applier.clearInternetRadioNowPlaying()
    if (presentation.resetStreamMetadata) applier.resetStreamMetadata()
    applier.setNowPlayingTrack(track)
    applier.setNowPlayingCoverArtUrl(coverArtUrl)
    applier.applyFavoriteState(presentation.canFavoriteTrack, presentation.isFavoriteTrack)
    applier.incrementPlayReportSession()
    applier.clearSubmittedPlayReportSession()
    if (effects.savePlaybackSession) applier.savePlaybackSession()
    if (presentation.shouldOpenNowPlaying) applier.openNowPlaying()
    if (presentation.shouldReportNowPlaying) applier.reportNowPlaying(track)
    if (presentation.resetSidecars) applier.resetSidecars()
    if (presentation.resetProgress) applier.resetProgress()
    if (effects.refillRadioQueue) applier.refillRadioQueue()
    if (effects.loadRelatedTracks) applier.loadRelatedTracks(track)
    applier.loadAudioTags(track)
    if (presentation.shouldLoadLyrics) applier.loadLyrics(track)
    if (effects.startAudioPrefetch) applier.startAudioPrefetch()
    if (effects.startSidecarPrep) applier.startSidecarPrep()
    if (effects.updateNotificationMetadata) {
        applier.updateNotificationMetadata(
            effects.notificationTitle,
            effects.notificationSubtitle,
            coverArtUrl,
        )
    }
}
