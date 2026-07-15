package app.naviamp.domain.playback

import app.naviamp.domain.ReplayGain
import app.naviamp.domain.settings.SampleRateMatching
import kotlinx.coroutines.CoroutineScope

interface PlaybackEngine {
    val name: String
    val supportsPause: Boolean
    val supportsSeek: Boolean
    val supportsGapless: Boolean
    val supportsCrossfade: Boolean
    val supportsReplayGain: Boolean
    val supportsSoftwareVolume: Boolean
    val prefersOriginalStream: Boolean

    fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit = {},
    )

    fun pause()

    fun resume()

    fun seek(positionSeconds: Double)

    fun setVolume(percent: Int)

    fun stop()
}

interface QueueAwarePlaybackEngine : PlaybackEngine {
    fun setCrossfadeDuration(seconds: Int)

    fun prepareNext(request: PlaybackRequest)

    fun clearPreparedNext()
}

interface VisualizerPlaybackEngine : PlaybackEngine {
    val supportsVisualizer: Boolean

    fun visualizerFrame(): PlaybackVisualizerFrame?
}

interface EqualizerPlaybackEngine : PlaybackEngine {
    val supportsEqualizer: Boolean

    fun setEqualizer(settings: EqualizerSettings)
}

interface ReplayGainPlaybackEngine : PlaybackEngine {
    fun setReplayGain(mode: ReplayGainMode, preampDb: Float)
}

interface SampleRateConverterPlaybackEngine : PlaybackEngine {
    fun setSampleRateConverter(converter: app.naviamp.domain.settings.SampleRateConverter)
}

interface SampleRateMatchingPlaybackEngine : PlaybackEngine {
    fun setSampleRateMatching(mode: app.naviamp.domain.settings.SampleRateMatching)
}

fun targetOutputSampleRate(
    mode: SampleRateMatching,
    requestedSampleRateHz: Int?,
    startingFromIdle: Boolean,
): Int? {
    val requested = requestedSampleRateHz
        ?.takeIf { it in 8_000..768_000 }
        ?: return null
    return when (mode) {
        SampleRateMatching.Disabled -> null
        SampleRateMatching.Smart -> requested.takeIf { startingFromIdle }
        SampleRateMatching.Strict -> requested
    }
}

interface AudioOutputDevicePlaybackEngine : PlaybackEngine {
    val supportsAudioOutputDeviceSelection: Boolean

    fun outputDevices(): List<AudioOutputDevice>

    fun setAudioOutputDevice(deviceId: String?): Result<Unit>
}

@kotlinx.serialization.Serializable
data class AudioOutputDevice(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val isInitialized: Boolean = false,
) {
    fun normalized(): AudioOutputDevice? {
        val normalizedId = id.trim()
        val normalizedName = name.trim()
        if (normalizedId.isBlank() || normalizedName.isBlank()) return null
        return copy(
            id = normalizedId,
            name = normalizedName,
        )
    }
}

@kotlinx.serialization.Serializable
data class EqualizerSettings(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.Flat,
    val bandsDb: List<Float> = FlatEqualizerBandsDb,
    val profileId: String? = null,
    val savedProfiles: List<EqualizerProfile> = emptyList(),
) {
    fun normalized(): EqualizerSettings {
        val normalizedBands = EqualizerBandFrequencies.mapIndexed { index, _ ->
            (bandsDb.getOrNull(index) ?: 0f).coerceIn(MinEqualizerGainDb, MaxEqualizerGainDb)
        }
        val normalizedProfiles = savedProfiles
            .mapNotNull { it.normalized().takeIf { profile -> profile.name.isNotBlank() } }
            .distinctBy { it.id }
            .take(MaxEqualizerSavedProfiles)
        val activeProfileId = profileId?.takeIf { id -> normalizedProfiles.any { it.id == id } }
        return copy(
            enabled = enabled && normalizedBands.any { it != 0f },
            bandsDb = normalizedBands,
            profileId = activeProfileId,
            savedProfiles = normalizedProfiles,
        )
    }

    fun withPreset(preset: EqualizerPreset): EqualizerSettings =
        copy(
            enabled = preset != EqualizerPreset.Flat,
            preset = preset,
            bandsDb = equalizerPresetBandsDb(preset),
            profileId = null,
        ).normalized()

    fun withBandGain(index: Int, gainDb: Float): EqualizerSettings =
        copy(
            enabled = true,
            preset = EqualizerPreset.Custom,
            profileId = null,
            bandsDb = EqualizerBandFrequencies.mapIndexed { bandIndex, _ ->
                if (bandIndex == index) gainDb else bandsDb.getOrNull(bandIndex) ?: 0f
            },
        ).normalized()

    fun withProfile(profile: EqualizerProfile): EqualizerSettings =
        copy(
            enabled = true,
            preset = EqualizerPreset.Custom,
            bandsDb = profile.normalized().bandsDb,
            profileId = profile.id,
        ).normalized()

    fun savedAsProfile(name: String): EqualizerSettings {
        val profile = EqualizerProfile(
            id = equalizerProfileId(name),
            name = name.trim(),
            bandsDb = bandsDb,
        ).normalized()
        if (profile.name.isBlank()) return normalized()
        return copy(
            enabled = true,
            preset = EqualizerPreset.Custom,
            profileId = profile.id,
            savedProfiles = (savedProfiles.filterNot { it.id == profile.id || it.id == profileId } + profile),
        ).normalized()
    }
}

@kotlinx.serialization.Serializable
data class EqualizerProfile(
    val id: String,
    val name: String,
    val bandsDb: List<Float>,
) {
    fun normalized(): EqualizerProfile =
        copy(
            id = id.ifBlank { equalizerProfileId(name) },
            name = name.trim(),
            bandsDb = EqualizerBandFrequencies.mapIndexed { index, _ ->
                (bandsDb.getOrNull(index) ?: 0f).coerceIn(MinEqualizerGainDb, MaxEqualizerGainDb)
            },
        )
}

@kotlinx.serialization.Serializable
enum class EqualizerPreset(
    val displayName: String,
) {
    Flat("Flat"),
    BassBoost("Bass Boost"),
    TrebleBoost("Treble Boost"),
    Rock("Rock"),
    Pop("Pop"),
    Jazz("Jazz"),
    Classical("Classical"),
    DanceElectronic("Dance/Electronic"),
    HipHop("Hip Hop"),
    Vocal("Vocal"),
    Acoustic("Acoustic"),
    Custom("Custom"),
}

val EqualizerBandFrequencies: List<Int> =
    listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

val FlatEqualizerBandsDb: List<Float> =
    List(EqualizerBandFrequencies.size) { 0f }

const val MinEqualizerGainDb: Float = -12f
const val MaxEqualizerGainDb: Float = 12f
const val MaxEqualizerSavedProfiles: Int = 24

fun equalizerProfileId(name: String): String =
    name
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "custom" }

fun equalizerPresetBandsDb(preset: EqualizerPreset): List<Float> =
    when (preset) {
        EqualizerPreset.Flat,
        EqualizerPreset.Custom,
        -> FlatEqualizerBandsDb
        EqualizerPreset.BassBoost -> listOf(7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)
        EqualizerPreset.TrebleBoost -> listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f)
        EqualizerPreset.Rock -> listOf(5f, 4f, 3f, 1f, -2f, -1f, 2f, 4f, 5f, 5f)
        EqualizerPreset.Pop -> listOf(-1f, 2f, 4f, 5f, 3f, 0f, -1f, -1f, 1f, 2f)
        EqualizerPreset.Jazz -> listOf(3f, 2f, 1f, 2f, -1f, -1f, 1f, 2f, 3f, 4f)
        EqualizerPreset.Classical -> listOf(4f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 4f)
        EqualizerPreset.DanceElectronic -> listOf(6f, 5f, 3f, 0f, -2f, -1f, 1f, 3f, 5f, 6f)
        EqualizerPreset.HipHop -> listOf(7f, 6f, 4f, 2f, -1f, -1f, 1f, 2f, 3f, 4f)
        EqualizerPreset.Vocal -> listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)
        EqualizerPreset.Acoustic -> listOf(3f, 2f, 1f, 2f, 3f, 2f, 1f, 2f, 3f, 2f)
    }

data class PlaybackVisualizerFrame(
    val bands: List<Float>,
    val timestampMillis: Long,
)

data class PlaybackRequest(
    val url: String,
    val fallbackUrl: String? = null,
    val mediaId: String? = null,
    val samplingRateHz: Int? = null,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val replayGainPreampDb: Float = 0f,
    val replayGain: PlaybackReplayGain? = null,
    val startPositionSeconds: Double? = null,
)

fun PlaybackRequest.downloadFallbackRequest(positionSeconds: Double? = null): PlaybackRequest? =
    fallbackUrl?.let { downloadedUrl ->
        copy(
            url = downloadedUrl,
            fallbackUrl = null,
            startPositionSeconds = positionSeconds ?: startPositionSeconds,
        )
    }

data class PlaybackReplayGain(
    val replayGain: ReplayGain,
    val source: ReplayGainSource,
)

enum class ReplayGainSource(
    val displayName: String,
) {
    Provider("Provider metadata"),
    LocalTags("Local file tags"),
}

data class PlaybackStreamMetadata(
    val title: String? = null,
    val properties: Map<String, String> = emptyMap(),
) {
    companion object {
        fun fromProperties(
            properties: Map<String, String>,
            fallbackTitle: String? = null,
        ): PlaybackStreamMetadata =
            PlaybackStreamMetadata(
                title = streamTitle(properties, fallbackTitle),
                properties = properties,
            )
    }
}

fun streamTitle(
    properties: Map<String, String>,
    fallbackTitle: String? = null,
): String? =
    listOfNotNull(
        properties.valueIgnoringCase("icy-title"),
        properties.valueIgnoringCase("StreamTitle"),
        properties.valueIgnoringCase("streamtitle"),
        properties.valueIgnoringCase("title"),
        fallbackTitle,
    ).firstOrNull { !it.isNullOrBlank() }?.trim()

private fun Map<String, String>.valueIgnoringCase(key: String): String? {
    get(key)?.let { return it }
    return entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

enum class ReplayGainMode(
    val displayName: String,
) {
    Off("Off"),
    Track("Track"),
    Album("Album"),
}

data class CrossfadeSettings(
    val enabled: Boolean = false,
    val durationSeconds: Int = 0,
) {
    init {
        require(durationSeconds >= 0) { "Crossfade duration cannot be negative." }
    }

    val isActive: Boolean
        get() = enabled && durationSeconds > 0
}

data class PlaybackProgress(
    val positionSeconds: Double?,
    val durationSeconds: Double?,
    val decodedPositionSeconds: Double? = null,
) {
    val fraction: Double
        get() {
            val position = positionSeconds ?: return 0.0
            val duration = durationSeconds ?: return 0.0
            if (duration <= 0.0) return 0.0
            return (position / duration).coerceIn(0.0, 1.0)
        }

    companion object {
        val Unknown = PlaybackProgress(
            positionSeconds = null,
            durationSeconds = null,
        )
    }
}

val PlaybackProgress.playbackPlanningPositionSeconds: Double?
    get() = decodedPositionSeconds ?: positionSeconds

fun PlaybackProgress.mergeWith(previous: PlaybackProgress): PlaybackProgress {
    val previousPosition = previous.positionSeconds
    val currentPosition = positionSeconds
    val previousDuration = previous.durationSeconds
    val currentDuration = durationSeconds

    return copy(
        positionSeconds = when {
            currentPosition == null -> previousPosition
            previousPosition == null -> currentPosition
            currentPosition >= previousPosition -> currentPosition
            previousPosition - currentPosition <= 1.0 -> currentPosition
            else -> previousPosition
        },
        durationSeconds = when {
            currentDuration == null -> previousDuration
            previousDuration == null -> currentDuration
            currentDuration >= previousDuration -> currentDuration
            else -> previousDuration
        },
    )
}

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
    data object Stopped : PlaybackState
    data object Finished : PlaybackState

    data class Error(
        val message: String,
    ) : PlaybackState
}

fun PlaybackState.label(): String =
    when (this) {
        PlaybackState.Idle -> "Nothing Playing"
        PlaybackState.Loading -> "Loading"
        PlaybackState.Playing -> "Playing"
        PlaybackState.Paused -> "Paused"
        PlaybackState.Stopped -> "Stopped"
        PlaybackState.Finished -> "Finished"
        is PlaybackState.Error -> message
    }
