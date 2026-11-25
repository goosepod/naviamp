package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.domain.*
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.*
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Sven #520: exercise the album Play entry point through Core and its real engine adapter. */
@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreAlbumPlaybackProfileTest {
    @Test fun albumPlayResolvesSavedTransitionOverridesBeforePlaybackAndPreparation() = runTest {
        verifyEntryPoint(selectTrack = false)
    }

    @Test fun albumTrackSelectionRetainsTheAlbumProfileBeforePlaybackAndPreparation() = runTest {
        verifyEntryPoint(selectTrack = true)
    }

    @Test fun nowPlayingVolumeChangeDuringStreamResolutionKeepsTheAlbumGaplessProfile() = runTest {
        verifyGlobalApplyDuringResolution { core, _ ->
            core.execute(NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.ChangeVolume, volumePercent = 40)))
        }
    }

    @Test fun playbackSettingsChangeDuringStreamResolutionKeepsTheAlbumGaplessProfile() = runTest {
        verifyGlobalApplyDuringResolution { core, _ ->
            core.execute(NaviampCoreCommand.Settings.ChangePlayback(
                core.state.value.shell.playback.settings.copy(volumePercent = 55), redownload = false))
        }
    }

    // Settings sync and import apply the shared playback settings port directly.
    @Test fun syncedPlaybackSettingsApplyDuringStreamResolutionKeepsTheAlbumGaplessProfile() = runTest {
        verifyGlobalApplyDuringResolution { _, settings ->
            settings.apply(PlaybackSettings(gaplessEnabled = false, crossfadeDurationSeconds = 8, volumePercent = 70),
                redownload = false)
        }
    }

    /**
     * A global playback-settings apply can land while Core is still resolving a stream URL, after
     * the album profile was pushed to the engine but before the engine creates or prepares audio.
     */
    private suspend fun TestScope.verifyGlobalApplyDuringResolution(
        applyGlobalSettings: suspend (NaviampCore, NaviampCorePlaybackSettingsPort) -> Unit,
    ) {
        val original = FakeCoreMediaProvider()
        val album = original.album
        val tracks = (1..3).map { original.track.copy(id = TrackId("track-$it")) }
        var gate: CompletableDeferred<Unit>? = null
        val provider = object : MediaProvider by original {
            override suspend fun album(albumId: AlbumId) = AlbumDetails(album, tracks)
            override suspend fun streamUrl(request: StreamRequest): String {
                gate?.await()
                return original.streamUrl(request)
            }
        }
        val saved = mutableMapOf<PlaybackProfileTarget, PlaybackProfile>()
        val profiles = object : PlaybackProfileRepository {
            override fun playbackProfile(sourceId: String, target: PlaybackProfileTarget) = saved[target]
            override fun playbackProfiles(sourceId: String) = saved.map { PlaybackProfileAssignment(it.key, it.value) }
            override fun savePlaybackProfile(sourceId: String, target: PlaybackProfileTarget, profile: PlaybackProfile?) {
                if (profile == null) saved.remove(target) else saved[target] = profile
            }
        }
        val engine = AlbumProfileProbeEngine()
        val defaults = fakeCoreServices(provider, profiles = profiles)
        val global = PlaybackSettings(gaplessEnabled = false, crossfadeDurationSeconds = 8)
        val streaming = naviampCoreStreamingPlaybackServices(this, engine, defaults.content.providerSource,
            global, {},
            object : PlaybackSessionRepository {
                override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
                override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
            }, {}).copy(profiles = profiles)
        val initial = NaviampCoreInitialState()
        val shell = initial.product.shell
        val core = NaviampCore.create(this, defaults.copy(playback = streaming), initial.copy(
            connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "source"),
            product = initial.product.copy(shell = shell.copy(
                connectionSettings = shell.connectionSettings.copy(currentSourceId = "source"),
                playback = shell.playback.copy(settings = global))),
        ))
        try {
            val item = album.toSharedMediaItemUi({ null })
            core.execute(NaviampCoreCommand.Media.ItemAction(item.albumActionRequest(NaviampArtistAlbumCommand.Select)))
            advanceUntilIdle()
            core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(item,
                NaviampAlbumDetailCommand.SavePlaybackProfile(PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless)))))

            gate = CompletableDeferred()
            core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(item,
                NaviampAlbumDetailCommand.Play(shuffle = false))))
            advanceUntilIdle()
            assertEquals(emptyList(), engine.crossfadeAtPlay, "the first stream URL is still resolving")
            applyGlobalSettings(core, streaming.settings)
            advanceUntilIdle()
            gate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(0), engine.crossfadeAtPlay, "crossfade when the engine starts the album")

            gate = CompletableDeferred()
            engine.progress(PlaybackProgress(179.0, 180.0))
            advanceUntilIdle()
            assertEquals(emptyList(), engine.preparedIds, "the next stream URL is still resolving")
            applyGlobalSettings(core, streaming.settings)
            advanceUntilIdle()
            gate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf<String?>("track-2"), engine.preparedIds)
            assertEquals(listOf(0), engine.crossfadeAtPrepare, "crossfade when the engine prepares the next album track")
        } finally { core.close() }
    }

    private suspend fun TestScope.verifyEntryPoint(selectTrack: Boolean) {
        val cases = listOf(
            PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless) to 0,
            PlaybackProfile(transitionMode = PlaybackTransitionMode.Crossfade, crossfadeDurationSeconds = 3) to 3,
            PlaybackProfile(transitionMode = PlaybackTransitionMode.Pause) to 0,
            PlaybackProfile() to 8,
        )
        for ((profile, expectedCrossfade) in cases) {
            val original = FakeCoreMediaProvider()
            val album = original.album
            val tracks = (1..4).map { original.track.copy(id = TrackId("track-$it")) }
            val provider = object : MediaProvider by original {
                override suspend fun album(albumId: AlbumId) = AlbumDetails(album, tracks)
            }
            val target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, album.id.value)
            val saved = mutableMapOf<PlaybackProfileTarget, PlaybackProfile>()
            val reads = mutableListOf<Pair<String, PlaybackProfileTarget>>()
            val profiles = object : PlaybackProfileRepository {
                override fun playbackProfile(sourceId: String, target: PlaybackProfileTarget): PlaybackProfile? {
                    reads += sourceId to target
                    return saved[target]
                }
                override fun playbackProfiles(sourceId: String) = saved.map { PlaybackProfileAssignment(it.key, it.value) }
                override fun savePlaybackProfile(sourceId: String, target: PlaybackProfileTarget, profile: PlaybackProfile?) {
                    assertEquals("source", sourceId)
                    if (profile == null) saved.remove(target) else saved[target] = profile
                }
            }
            val engine = AlbumProfileProbeEngine()
            val defaults = fakeCoreServices(provider, profiles = profiles)
            val streaming = naviampCoreStreamingPlaybackServices(this, engine, defaults.content.providerSource,
                PlaybackSettings(gaplessEnabled = false, crossfadeDurationSeconds = 8), {},
                object : PlaybackSessionRepository {
                    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
                    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
                }, {}).copy(profiles = profiles)
            val initial = NaviampCoreInitialState()
            val core = NaviampCore.create(this, defaults.copy(playback = streaming), initial.copy(
                connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "source"),
                product = initial.product.copy(shell = initial.product.shell.copy(
                    connectionSettings = initial.product.shell.connectionSettings.copy(currentSourceId = "source"))),
            ))
            try {
                val item = album.toSharedMediaItemUi({ null })
                core.execute(NaviampCoreCommand.Media.ItemAction(item.albumActionRequest(NaviampArtistAlbumCommand.Select)))
                advanceUntilIdle()
                core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(item,
                    NaviampAlbumDetailCommand.SavePlaybackProfile(profile))))
                if (selectTrack) {
                    val selected = assertNotNull(core.state.value.shell.albumDetail.detail).tracks[1]
                    core.execute(NaviampCoreCommand.Detail.AlbumTrack(
                        SharedTrackRowActionRequest(selected, SharedTrackRowAction.Select)))
                } else {
                    core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(item,
                        NaviampAlbumDetailCommand.Play(shuffle = false))))
                }
                advanceUntilIdle()
                assertEquals(expectedCrossfade, engine.crossfadeAtPlay.single(), profile.toString())
                val queue = assertNotNull(assertNotNull(core.state.value.shell.nowPlaying).queueSnapshot)
                assertEquals(target, queue.groups.single().target)
                assertEquals(profile, queue.groups.single().profile)
                assertEquals(true, reads.any { it == "source" to target })
                engine.progress(PlaybackProgress(179.0, 180.0))
                advanceUntilIdle()
                if (profile.transitionMode == PlaybackTransitionMode.Pause) {
                    assertEquals(emptyList(), engine.preparedIds)
                } else {
                    assertEquals(listOf<String?>(if (selectTrack) "track-3" else "track-2"), engine.preparedIds)
                    assertEquals(listOf(expectedCrossfade), engine.crossfadeAtPrepare)
                }
                engine.state(PlaybackState.Finished)
                advanceUntilIdle()
                assertEquals(listOf(expectedCrossfade, expectedCrossfade), engine.crossfadeAtPlay)
            } finally { core.close() }
        }
    }
}

private class AlbumProfileProbeEngine : QueueAwarePlaybackEngine {
    override val name = "Album profile probe"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = true
    override val supportsCrossfade = true
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    var crossfade = -1
    val crossfadeAtPlay = mutableListOf<Int>()
    val crossfadeAtPrepare = mutableListOf<Int>()
    val preparedIds = mutableListOf<String?>()
    lateinit var state: (PlaybackState) -> Unit
    lateinit var progress: (PlaybackProgress) -> Unit
    override fun play(scope: CoroutineScope, request: PlaybackRequest, onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit, onMetadataChanged: (PlaybackStreamMetadata) -> Unit) {
        crossfadeAtPlay += crossfade
        state = onStateChanged
        progress = onProgressChanged
        state(PlaybackState.Playing)
        progress(PlaybackProgress(0.0, 180.0))
    }
    override fun setCrossfadeDuration(seconds: Int) { crossfade = seconds }
    override fun prepareNext(request: PlaybackRequest) {
        preparedIds += request.mediaId
        crossfadeAtPrepare += crossfade
    }
    override fun clearPreparedNext() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
}
