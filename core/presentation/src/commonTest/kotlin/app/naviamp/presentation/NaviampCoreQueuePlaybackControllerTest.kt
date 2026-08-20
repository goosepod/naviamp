package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileAssignment
import app.naviamp.domain.playback.PlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampCoreQueuePlaybackControllerTest {
    @Test
    fun configuredPlaylistLaunchCreatesProfiledQueueGroup() {
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, "playlist-1")
        val profile = PlaybackProfile(
            transitionMode = PlaybackTransitionMode.Gapless,
            replayGainMode = PlaybackReplayGainMode.Album,
        )
        val fixture = fixture(mapOf(target to profile))

        assertTrue(fixture.controller.play(testTracks(), groupTarget = target, groupLabel = "Live set"))

        val queue = fixture.live.state.value.queue
        assertEquals(1, queue.groups.size)
        assertEquals(target, queue.groups.single().target)
        assertEquals(profile, queue.groups.single().profile)
        assertEquals(0, queue.groups.single().startIndex)
        assertEquals(2, queue.groups.single().endIndexExclusive)
        assertEquals(queue, fixture.effects.appliedQueue)
    }

    @Test
    fun unconfiguredPlaylistAndShuffledLaunchDoNotCreateGroups() {
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, "playlist-1")
        val fixture = fixture()

        fixture.controller.play(testTracks(), groupTarget = target, groupLabel = "Playlist")
        assertTrue(fixture.live.state.value.queue.groups.isEmpty())

        fixture.controller.play(
            testTracks(),
            shuffle = true,
            groupTarget = target,
            groupLabel = "Playlist",
            groupWithoutProfile = true,
        )
        assertTrue(fixture.live.state.value.queue.groups.isEmpty())
    }

    @Test
    fun addingConfiguredAlbumToQueueCarriesItsProfile() {
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-1")
        val profile = PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless)
        val fixture = fixture(mapOf(target to profile))
        fixture.live.replace(
            fixture.live.state.value.copy(
                currentTrack = testTrack("current"),
                queue = PlaybackQueue(listOf(testTrack("current")), currentIndex = 0),
            ),
        )

        val update = fixture.controller.addToQueue(testTracks(), target, "Profiled album")

        val group = update.queue.groups.single()
        assertEquals(target, group.target)
        assertEquals(profile, group.profile)
        assertEquals(1, group.startIndex)
        assertEquals(3, group.endIndexExclusive)
    }

    @Test
    fun playingConfiguredPlaylistNextCarriesItsProfile() {
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, "playlist-1")
        val profile = PlaybackProfile(replayGainMode = PlaybackReplayGainMode.Album)
        val fixture = fixture(mapOf(target to profile))
        fixture.live.replace(
            fixture.live.state.value.copy(
                currentTrack = testTrack("current"),
                queue = PlaybackQueue(listOf(testTrack("current"), testTrack("later")), currentIndex = 0),
            ),
        )

        val update = fixture.controller.playNext(testTracks(), target, "Profiled playlist")

        val group = update.queue.groups.single()
        assertEquals(target, group.target)
        assertEquals(profile, group.profile)
        assertEquals(1, group.startIndex)
        assertEquals(3, group.endIndexExclusive)
    }

    @Test
    fun addingUnconfiguredCollectionLeavesTracksUngrouped() {
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-1")
        val fixture = fixture()

        val update = fixture.controller.addToQueue(testTracks(), target, "Album")

        assertTrue(update.queue.groups.isEmpty())
    }
}

private data class QueuePlaybackFixture(
    val live: NaviampLivePlaybackController,
    val effects: QueuePlaybackEffects,
    val controller: NaviampCoreQueuePlaybackController,
)

private fun fixture(profiles: Map<PlaybackProfileTarget, PlaybackProfile> = emptyMap()): QueuePlaybackFixture {
    val live = NaviampLivePlaybackController()
    val effects = QueuePlaybackEffects()
    val controller = NaviampCoreQueuePlaybackController(
        playback = live,
        queue = NaviampPlaybackQueueCoordinator(live),
        effects = effects,
        publishNowPlaying = {},
        openNowPlaying = {},
        activeSourceId = { "source-1" },
        profiles = MapPlaybackProfileRepository(profiles),
    )
    return QueuePlaybackFixture(live, effects, controller)
}

private class MapPlaybackProfileRepository(
    private val profiles: Map<PlaybackProfileTarget, PlaybackProfile>,
) : PlaybackProfileRepository {
    override fun playbackProfile(sourceId: String, target: PlaybackProfileTarget) = profiles[target]

    override fun playbackProfiles(sourceId: String) = profiles.map { PlaybackProfileAssignment(it.key, it.value) }

    override fun savePlaybackProfile(sourceId: String, target: PlaybackProfileTarget, profile: PlaybackProfile?) = Unit
}

private class QueuePlaybackEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities()
    override val playbackSource = PlaybackSource.ProviderStream
    var appliedQueue: PlaybackQueue? = null

    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore() = true
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) { appliedQueue = queue }
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) = Unit
}

private fun testTracks() = listOf("one", "two").map { id ->
    testTrack(id)
}

private fun testTrack(id: String) = Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
