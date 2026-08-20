package app.naviamp.presentation

import app.naviamp.domain.playback.EmptyPlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType

/** Owns source-scoped playback-profile lookup, persistence, and shared detail-screen state. */
class NaviampCorePlaybackProfileController(
    private val stateStore: NaviampCoreStateStore,
    private val repository: PlaybackProfileRepository = EmptyPlaybackProfileRepository,
) {
    fun albumProfile(albumId: String): PlaybackProfile =
        profile(PlaybackProfileTarget(PlaybackProfileTargetType.Album, albumId))

    fun playlistProfile(playlistId: String): PlaybackProfile =
        profile(PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, playlistId))

    fun saveAlbumProfile(albumId: String, profile: PlaybackProfile) {
        save(PlaybackProfileTarget(PlaybackProfileTargetType.Album, albumId), profile)
    }

    fun savePlaylistProfile(playlistId: String, profile: PlaybackProfile) {
        save(PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, playlistId), profile)
    }

    private fun profile(target: PlaybackProfileTarget): PlaybackProfile {
        val sourceId = currentSourceId() ?: return PlaybackProfile()
        return repository.playbackProfile(sourceId, target)?.normalized() ?: PlaybackProfile()
    }

    private fun save(target: PlaybackProfileTarget, requested: PlaybackProfile) {
        val normalized = requested.normalized()
        val sourceId = currentSourceId()
        if (sourceId == null) {
            publish(target, null, "Connect to a library to save a playback profile.")
            return
        }
        repository.savePlaybackProfile(
            sourceId = sourceId,
            target = target,
            profile = normalized.takeUnless(PlaybackProfile::isInherited),
        )
        publish(
            target = target,
            profile = normalized,
            status = if (normalized.isInherited) "Playback profile cleared." else "Playback profile saved.",
        )
    }

    private fun publish(target: PlaybackProfileTarget, profile: PlaybackProfile?, status: String) {
        stateStore.updateShell { shell ->
            when (target.type) {
                PlaybackProfileTargetType.Album -> shell.copy(
                    albumDetail = shell.albumDetail.takeIf { it.selectedAlbum?.id == target.id }
                        ?.let { detail ->
                            detail.copy(
                                playbackProfile = profile ?: detail.playbackProfile,
                                playbackProfileStatus = status,
                            )
                        }
                        ?: shell.albumDetail,
                )
                PlaybackProfileTargetType.Playlist -> shell.copy(
                    playlistDetail = shell.playlistDetail.takeIf { it.selectedPlaylist?.id == target.id }
                        ?.let { detail ->
                            detail.copy(
                                playbackProfile = profile ?: detail.playbackProfile,
                                playbackProfileStatus = status,
                            )
                        }
                        ?: shell.playlistDetail,
                )
                PlaybackProfileTargetType.Work -> shell
            }
        }
    }

    private fun currentSourceId(): String? =
        stateStore.state.value.shell.connectionSettings.currentSourceId
}
