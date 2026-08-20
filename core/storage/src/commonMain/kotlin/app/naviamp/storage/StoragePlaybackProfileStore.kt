package app.naviamp.storage

import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileAssignment
import app.naviamp.domain.playback.PlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackTransitionMode

/** Shared SQLDelight persistence for source-scoped album, playlist, and work playback profiles. */
class StoragePlaybackProfileStore(
    private val queries: NaviampStorageQueries,
    private val nowEpochMillis: () -> Long,
) : PlaybackProfileRepository {
    override fun playbackProfile(sourceId: String, target: PlaybackProfileTarget): PlaybackProfile? {
        val source = sourceId.normalizedId() ?: return null
        val normalizedTarget = target.normalized() ?: return null
        return queries.selectPlaybackProfile(
            source_id = source,
            target_type = normalizedTarget.type.name,
            target_id = normalizedTarget.id,
        ).executeAsOneOrNull()?.let { row ->
            storedPlaybackProfile(
                transitionMode = row.transition_mode,
                crossfadeDurationSeconds = row.crossfade_duration_seconds,
                replayGainMode = row.replay_gain_mode,
            )
        }
    }

    override fun playbackProfiles(sourceId: String): List<PlaybackProfileAssignment> {
        val source = sourceId.normalizedId() ?: return emptyList()
        return queries.selectPlaybackProfiles(source).executeAsList().mapNotNull { row ->
            val targetType = enumValueOrNull<PlaybackProfileTargetType>(row.target_type) ?: return@mapNotNull null
            PlaybackProfileAssignment(
                target = PlaybackProfileTarget(targetType, row.target_id),
                profile = storedPlaybackProfile(
                    transitionMode = row.transition_mode,
                    crossfadeDurationSeconds = row.crossfade_duration_seconds,
                    replayGainMode = row.replay_gain_mode,
                ),
            )
        }
    }

    override fun savePlaybackProfile(
        sourceId: String,
        target: PlaybackProfileTarget,
        profile: PlaybackProfile?,
    ) {
        val source = sourceId.normalizedId() ?: return
        val normalizedTarget = target.normalized() ?: return
        val normalizedProfile = profile?.normalized()
        if (normalizedProfile == null || normalizedProfile.isInherited) {
            queries.deletePlaybackProfile(source, normalizedTarget.type.name, normalizedTarget.id)
            return
        }
        queries.upsertPlaybackProfile(
            source_id = source,
            target_type = normalizedTarget.type.name,
            target_id = normalizedTarget.id,
            transition_mode = normalizedProfile.transitionMode.name,
            crossfade_duration_seconds = normalizedProfile.crossfadeDurationSeconds?.toLong(),
            replay_gain_mode = normalizedProfile.replayGainMode.name,
            updated_at_epoch_millis = nowEpochMillis(),
        )
    }
}

private fun storedPlaybackProfile(
    transitionMode: String,
    crossfadeDurationSeconds: Long?,
    replayGainMode: String,
): PlaybackProfile = PlaybackProfile(
    transitionMode = enumValueOrNull<PlaybackTransitionMode>(transitionMode) ?: PlaybackTransitionMode.Inherit,
    crossfadeDurationSeconds = crossfadeDurationSeconds?.toInt(),
    replayGainMode = enumValueOrNull<PlaybackReplayGainMode>(replayGainMode) ?: PlaybackReplayGainMode.Inherit,
).normalized()

private fun String.normalizedId(): String? = trim().takeIf(String::isNotEmpty)

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }
