package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileAssignment
import app.naviamp.domain.playback.PlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampConnectionSettingsUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.SharedMediaItemUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCorePlaybackProfileControllerTest {
    @Test
    fun savesAndPublishesAlbumProfileForCurrentSource() {
        val repository = RecordingPlaybackProfileRepository()
        val store = profileStateStore()
        val controller = NaviampCorePlaybackProfileController(store, repository)
        val profile = PlaybackProfile(
            transitionMode = PlaybackTransitionMode.Gapless,
            replayGainMode = PlaybackReplayGainMode.Album,
        )

        controller.saveAlbumProfile("album-1", profile)

        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-1")
        assertEquals(profile, repository.values["source-1" to target])
        assertEquals(profile, store.state.value.shell.albumDetail.playbackProfile)
        assertEquals("Playback profile saved.", store.state.value.shell.albumDetail.playbackProfileStatus)
        assertEquals(profile, controller.albumProfile("album-1"))
    }

    @Test
    fun inheritedProfileDeletesPlaylistAssignment() {
        val repository = RecordingPlaybackProfileRepository()
        val store = profileStateStore()
        val controller = NaviampCorePlaybackProfileController(store, repository)
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, "playlist-1")
        repository.values["source-1" to target] = PlaybackProfile(transitionMode = PlaybackTransitionMode.Crossfade)

        controller.savePlaylistProfile("playlist-1", PlaybackProfile())

        assertNull(repository.values["source-1" to target])
        assertEquals(PlaybackProfile(), store.state.value.shell.playlistDetail.playbackProfile)
        assertEquals("Playback profile cleared.", store.state.value.shell.playlistDetail.playbackProfileStatus)
    }
}

private fun profileStateStore(): NaviampCoreStateStore {
    val store = NaviampCoreStateStore()
    store.updateShell { shell ->
        shell.copy(
            connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source-1"),
            albumDetail = NaviampAlbumDetailScreenUi(selectedAlbum = profileItem("album-1")),
            playlistDetail = NaviampPlaylistDetailScreenUi(selectedPlaylist = profileItem("playlist-1")),
        )
    }
    return store
}

private fun profileItem(id: String) = SharedMediaItemUi(id = id, title = id, subtitle = "")

private class RecordingPlaybackProfileRepository : PlaybackProfileRepository {
    val values = mutableMapOf<Pair<String, PlaybackProfileTarget>, PlaybackProfile>()

    override fun playbackProfile(sourceId: String, target: PlaybackProfileTarget) = values[sourceId to target]

    override fun playbackProfiles(sourceId: String): List<PlaybackProfileAssignment> = values
        .filterKeys { key -> key.first == sourceId }
        .map { (key, profile) -> PlaybackProfileAssignment(key.second, profile) }

    override fun savePlaybackProfile(sourceId: String, target: PlaybackProfileTarget, profile: PlaybackProfile?) {
        if (profile == null) values.remove(sourceId to target) else values[sourceId to target] = profile
    }
}
