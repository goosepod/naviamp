package app.naviamp.android

import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.presentation.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Opt-in real Android storage/BASS probe for #520; requires an empty disposable installation.
 * Run scripts/android-tv-fixture.py --tracks 3 --albums 1 --track-seconds 30, with adb reverse.
 * This records native mixer state; audible continuity remains a separate acceptance check.
 */
class AndroidAlbumPlaybackProfileInstrumentedTest {
    @Test
    fun albumPlayHonorsSavedGaplessWithGlobalEightSecondCrossfade() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { runBlocking {
            val core = prepareTvFixturePlayback(startPlayback = false)
            try {
                withContext(Dispatchers.Main) {
                    core.execute(NaviampCoreCommand.Settings.ChangePlayback(
                        core.state.value.shell.playback.settings.copy(
                            gaplessEnabled = false, crossfadeDurationSeconds = 8), false))
                    core.execute(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums))
                    core.execute(NaviampCoreCommand.Library.Refresh)
                }
                await("album catalog") { core.state.value.shell.library.albums.items.isNotEmpty() }
                val album = core.state.value.shell.library.albums.items.single()
                withContext(Dispatchers.Main) {
                    core.execute(NaviampCoreCommand.Media.ItemAction(
                        album.albumActionRequest(NaviampArtistAlbumCommand.Select)))
                }
                await("album detail") { core.state.value.shell.albumDetail.detail != null }
                assertEquals(3, core.state.value.shell.albumDetail.detail!!.tracks.size)
                val firstTrackId = core.state.value.shell.albumDetail.detail!!.tracks.first().id

                // Inherit is the positive control: native crossfade must actually activate.
                for (mode in listOf(PlaybackTransitionMode.Gapless, PlaybackTransitionMode.Inherit)) {
                    val profile = PlaybackProfile(transitionMode = mode)
                    withContext(Dispatchers.Main) {
                        core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(
                            album, NaviampAlbumDetailCommand.SavePlaybackProfile(profile))))
                        core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(
                            album, NaviampAlbumDetailCommand.Play(shuffle = false))))
                    }
                    await("$mode first track playing") {
                        core.externalPlaybackBridge().snapshot().state == NaviampExternalPlaybackState.Playing &&
                            core.state.value.shell.nowPlaying?.id == firstTrackId &&
                            (core.playbackProgress.value.positionSeconds ?: -1.0) in 0.0..10.0
                    }
                    val queue = assertNotNull(core.state.value.shell.nowPlaying?.queueSnapshot)
                    assertEquals(album.id, queue.groups.single().target.id)
                    assertEquals(profile, queue.groups.single().profile)
                    assertTrue(queue.tracks.all { it.durationSeconds == 30 }, "Requires the documented 30-second fixture")
                    assertEquals(8, core.state.value.shell.playback.settings.crossfadeDurationSeconds)
                    val firstId = assertNotNull(core.externalPlaybackBridge().snapshot().current?.mediaId)
                    await("$mode native preparation") {
                        core.externalPlaybackBridge().snapshot().current?.mediaId == firstId &&
                            (core.playbackProgress.value.positionSeconds ?: 0.0) >= 25.0
                    }
                    val expectedSeconds = if (mode == PlaybackTransitionMode.Gapless) "Off" else "8s"
                    val expectedActive = (mode == PlaybackTransitionMode.Inherit).toString()
                    await("$mode mixer state") {
                        val rows = core.statsForNerdsDiagnostics().sections.flatMap { it.rows }.toMap()
                        core.externalPlaybackBridge().snapshot().current?.mediaId == firstId &&
                            rows["Crossfade duration"] == expectedSeconds && rows["Crossfade active"] == expectedActive &&
                            rows["Prepared next state"] == "Ready" && rows["Prepared next"] == queue.tracks[1].id.value
                    }
                    record("$mode before transition: duration=$expectedSeconds active=$expectedActive next=Ready")
                    await("$mode next within-album track") {
                        val snapshot = core.externalPlaybackBridge().snapshot()
                        snapshot.state == NaviampExternalPlaybackState.Playing && snapshot.current?.mediaId != firstId
                    }
                    assertEquals(profile, core.state.value.shell.nowPlaying?.queueSnapshot?.groups?.single()?.profile)
                    assertEquals(queue.tracks[1].id.value, core.state.value.shell.nowPlaying?.id)
                    assertEquals(expectedSeconds, core.statsForNerdsDiagnostics().sections.flatMap { it.rows }
                        .toMap()["Crossfade duration"])
                    record("$mode advanced within the album with duration=$expectedSeconds")
                }
            } finally {
                withContext(Dispatchers.Main) { core.externalPlaybackBridge().pause() }
            }
        } }
    }

    private fun record(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "\nAlbum profile: $message\n")
        })
    }

    private suspend fun await(label: String, predicate: () -> Boolean) {
        val completed = withTimeoutOrNull(45_000) {
            while (!predicate()) delay(100)
            true
        } ?: false
        assertTrue(completed, "Timed out: $label")
    }
}
