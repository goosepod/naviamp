package app.naviamp.domain.playback

import app.naviamp.domain.settings.PlaybackSettings
import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackProfileTargetType {
    Album,
    Playlist,
    Work,
}

@Serializable
data class PlaybackProfileTarget(
    val type: PlaybackProfileTargetType,
    val id: String,
) {
    fun normalized(): PlaybackProfileTarget? =
        id.trim().takeIf(String::isNotEmpty)?.let { copy(id = it) }
}

@Serializable
enum class PlaybackTransitionMode {
    Inherit,
    Gapless,
    Crossfade,
    Pause,
}

@Serializable
enum class PlaybackReplayGainMode {
    Inherit,
    Off,
    Track,
    Album,
}

@Serializable
data class PlaybackProfile(
    val transitionMode: PlaybackTransitionMode = PlaybackTransitionMode.Inherit,
    val crossfadeDurationSeconds: Int? = null,
    val replayGainMode: PlaybackReplayGainMode = PlaybackReplayGainMode.Inherit,
) {
    val isInherited: Boolean
        get() = transitionMode == PlaybackTransitionMode.Inherit &&
            replayGainMode == PlaybackReplayGainMode.Inherit

    fun normalized(): PlaybackProfile = copy(
        crossfadeDurationSeconds = crossfadeDurationSeconds
            ?.coerceIn(MinPlaybackProfileCrossfadeSeconds, MaxPlaybackProfileCrossfadeSeconds)
            ?.takeIf { transitionMode == PlaybackTransitionMode.Crossfade },
    )
}

data class PlaybackProfileAssignment(
    val target: PlaybackProfileTarget,
    val profile: PlaybackProfile,
)

interface PlaybackProfileRepository {
    fun playbackProfile(sourceId: String, target: PlaybackProfileTarget): PlaybackProfile?

    fun playbackProfiles(sourceId: String): List<PlaybackProfileAssignment>

    fun savePlaybackProfile(sourceId: String, target: PlaybackProfileTarget, profile: PlaybackProfile?)
}

object EmptyPlaybackProfileRepository : PlaybackProfileRepository {
    override fun playbackProfile(sourceId: String, target: PlaybackProfileTarget): PlaybackProfile? = null

    override fun playbackProfiles(sourceId: String): List<PlaybackProfileAssignment> = emptyList()

    override fun savePlaybackProfile(sourceId: String, target: PlaybackProfileTarget, profile: PlaybackProfile?) = Unit
}

fun PlaybackProfile.resolveAgainst(global: PlaybackSettings): PlaybackSettings {
    val profile = normalized()
    val withTransition = when (profile.transitionMode) {
        PlaybackTransitionMode.Inherit -> global
        PlaybackTransitionMode.Gapless -> global.copy(
            gaplessEnabled = true,
            crossfadeDurationSeconds = 0,
        )
        PlaybackTransitionMode.Crossfade -> global.copy(
            gaplessEnabled = false,
            crossfadeDurationSeconds = profile.crossfadeDurationSeconds
                ?: global.crossfadeDurationSeconds.takeIf { it > 0 }
                ?: DefaultPlaybackProfileCrossfadeSeconds,
        )
        PlaybackTransitionMode.Pause -> global.copy(
            gaplessEnabled = false,
            crossfadeDurationSeconds = 0,
        )
    }
    return withTransition.copy(
        replayGainMode = when (profile.replayGainMode) {
            PlaybackReplayGainMode.Inherit -> global.replayGainMode
            PlaybackReplayGainMode.Off -> ReplayGainMode.Off
            PlaybackReplayGainMode.Track -> ReplayGainMode.Track
            PlaybackReplayGainMode.Album -> ReplayGainMode.Album
        },
    )
}

const val DefaultPlaybackProfileCrossfadeSeconds = 5
const val MinPlaybackProfileCrossfadeSeconds = 1
const val MaxPlaybackProfileCrossfadeSeconds = 12
