package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.label
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.ui.bytesLabel
import app.naviamp.ui.durationLabel
import app.naviamp.ui.label
import app.naviamp.ui.nowPlayingAlbumLine

fun PlaybackEngine.capabilitiesLabel(): String =
    listOf(
        "pause" to supportsPause,
        "seek" to supportsSeek,
        "gapless" to supportsGapless,
        "crossfade" to supportsCrossfade,
        "ReplayGain" to supportsReplayGain,
        "volume" to supportsSoftwareVolume,
    ).joinToString(", ") { (label, supported) ->
        if (supported) label else "no $label"
    }

fun Track.toStreamStats(
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
    waveform: AudioWaveform?,
    waveformStatus: String,
    cachedAudio: CachedAudioMetadata?,
    internetRadioStation: InternetRadioStation?,
    streamMetadata: PlaybackStreamMetadata,
): DesktopStreamStats {
    val effectiveDurationSeconds = durationSeconds?.toDouble() ?: playbackProgress.durationSeconds
    val audio = audioInfo
    return DesktopStreamStats(
        state = playbackState.label(),
        source = if (internetRadioStation != null || isInternetRadioTrack()) "Internet radio" else "Library track",
        trackId = id.value,
        stationId = internetRadioStation?.id ?: "None",
        stationName = internetRadioStation?.name ?: "None",
        stationStreamUrl = internetRadioStation?.streamUrl ?: "None",
        stationHomePageUrl = internetRadioStation?.homePageUrl ?: "None",
        title = title,
        artist = artistName,
        album = nowPlayingAlbumLine().ifBlank { "Unknown album" },
        duration = durationLabel(),
        progress = playbackProgress.label(effectiveDurationSeconds),
        streamQuality = streamQuality.label(),
        isTranscoded = if (streamQuality is StreamQuality.Transcoded) "Yes" else "No",
        replayGainMode = playbackSettings.replayGainMode.displayName,
        codec = audio?.codec ?: "Unknown",
        bitrate = audio?.bitrateKbps?.let { "$it kbps" } ?: "Unknown",
        contentType = audio?.contentType ?: "Unknown",
        coverArtId = coverArtId ?: "None",
        waveformStatus = waveformStatus,
        waveformBuckets = waveform?.amplitudes?.size?.toString() ?: "None",
        audioCacheStatus = cachedAudio?.let { if (it.exists) "Cached" else "Missing file" } ?: "Not cached",
        audioCacheSize = cachedAudio?.sizeBytes?.bytesLabel() ?: "None",
        audioCachePath = cachedAudio?.path?.toAbsolutePath()?.toString() ?: "None",
        streamMetadataTitle = streamMetadata.title ?: "None",
        streamMetadataProperties = streamMetadata.properties.entries
            .sortedBy { it.key.lowercase() }
            .joinToString(", ") { (key, value) -> "$key=$value" }
            .ifBlank { "None" },
    )
}
