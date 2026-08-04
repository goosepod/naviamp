package app.naviamp.app

import app.naviamp.domain.Playlist
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.provider.PlaylistPlaybackPreparedApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class NaviampPlaylistPlaybackControllerTest {
    @Test
    fun successfulExecutionOwnsAndClearsPendingAction() = runTest {
        val controller = NaviampPlaylistPlaybackController()
        var pending: PendingPlaybackAction? = null
        var status: String? = null
        var applied = 0
        val effects = effects(
            pending = { pending },
            setPending = { pending = it },
            setStatus = { status = it },
            applyPrepared = { applied += 1 },
        )

        val plan = assertNotNull(controller.begin(playlist(), shuffle = false, effects))
        assertNotNull(pending)
        controller.execute(
            playlist = playlist(),
            plan = plan,
            loadPrepared = { prepared() },
            effects = effects,
        )

        assertEquals(1, applied)
        assertNull(pending)
        assertEquals("Loading Playlist...", status)
    }

    @Test
    fun duplicateStartIsRejectedWithoutReplacingPendingAction() {
        val controller = NaviampPlaylistPlaybackController()
        val existing = PendingPlaybackAction(key = "other", status = "Already loading")
        var pending: PendingPlaybackAction? = existing
        val effects = effects(
            pending = { pending },
            setPending = { pending = it },
        )

        assertNull(controller.begin(playlist(), shuffle = false, effects))
        assertEquals(existing, pending)
    }

    @Test
    fun failurePublishesErrorAndClearsPendingAction() = runTest {
        val controller = NaviampPlaylistPlaybackController()
        var pending: PendingPlaybackAction? = null
        var status: String? = null
        val effects = effects(
            pending = { pending },
            setPending = { pending = it },
            setStatus = { status = it },
        )
        val plan = assertNotNull(controller.begin(playlist(), shuffle = true, effects))

        controller.execute(
            playlist = playlist(),
            plan = plan,
            loadPrepared = { error("offline") },
            effects = effects,
        )

        assertEquals("offline", status)
        assertNull(pending)
    }

    private fun effects(
        pending: () -> PendingPlaybackAction?,
        setPending: (PendingPlaybackAction?) -> Unit,
        setStatus: (String?) -> Unit = {},
        applyPrepared: (PlaylistPlaybackPreparedApplication) -> Unit = {},
    ) = NaviampPlaylistPlaybackEffects(pending, setPending, setStatus, applyPrepared)

    private fun playlist() = Playlist(id = "playlist", name = "Playlist", trackCount = 1)

    private fun prepared() = PlaylistPlaybackPreparedApplication(
        playlistTracksById = emptyMap(),
        loadedTracksToStore = null,
        playbackWork = null,
        status = null,
    )
}
