package app.naviamp.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampTelevisionLibraryStateTest {
    @Test fun restorationFollowsIdentityAcrossReorderingAndHandlesRemovedOrEmptyItems() {
        assertEquals(2, televisionLibraryRestoreIndex(listOf("new", "a", "b"), "b"))
        assertEquals(0, televisionLibraryRestoreIndex(listOf("new", "a"), "removed"))
        assertNull(televisionLibraryRestoreIndex(emptyList(), "b"))
    }

    @Test fun catalogsRetainIndependentIdentitiesAndANewSourceStartsEmpty() {
        val state = state()
        state.record(NaviampLibraryView.Artists, "artist")
        state.record(NaviampLibraryView.Albums, "album")
        state.record(NaviampLibraryView.Songs, "song")
        assertEquals("artist", state.focusedId(NaviampLibraryView.Artists))
        assertEquals("album", state.focusedId(NaviampLibraryView.Albums))
        assertEquals("song", state.focusedId(NaviampLibraryView.Songs))
        assertTrue(state.restoreContent)
        assertNull(state().focusedId(NaviampLibraryView.Songs))
        assertFalse(state().restoreContent)
    }

    @Test fun aLateJumpCannotMoveAnotherCatalogAndCompletedJumpsAreNotReplayed() {
        val state = state()
        val jump = NaviampLibraryJumpUi(NaviampLibraryView.Songs, 'Z', 1)
        assertFalse(state.consumeJump(jump, NaviampLibraryView.Albums))
        assertTrue(state.consumeJump(jump, NaviampLibraryView.Songs))
        assertFalse(state.consumeJump(jump, NaviampLibraryView.Songs))
        assertTrue(state.consumeJump(jump.copy(generation = 2), NaviampLibraryView.Songs))
    }

    private fun state() = NaviampTelevisionLibraryState(
        NaviampLibraryViewportState(NaviampLibraryView.entries.associateWith { LazyListState() }),
        mapOf(NaviampLibraryView.Artists to LazyGridState(), NaviampLibraryView.Albums to LazyGridState()),
    )
}
