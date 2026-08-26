package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NaviampTelevisionDetailPolicyTest {
    @Test
    fun trackSecondaryActionsStaySmallAndExcludePlaylistManagement() {
        val actions = televisionTrackSecondaryActions()

        assertEquals(
            listOf(
                SharedTrackRowAction.PlayNext,
                SharedTrackRowAction.AddToQueue,
                SharedTrackRowAction.StartRadio,
            ),
            actions,
        )
        assertFalse(SharedTrackRowAction.AddToPlaylist in actions)
        assertFalse(SharedTrackRowAction.CreatePlaylistAndAdd in actions)
    }

    @Test
    fun trackSecondaryActionLabelsAreRemoteFriendly() {
        assertEquals("Play Next", televisionTrackSecondaryActionLabel(SharedTrackRowAction.PlayNext))
        assertEquals("Add to Queue", televisionTrackSecondaryActionLabel(SharedTrackRowAction.AddToQueue))
        assertEquals("Start Radio", televisionTrackSecondaryActionLabel(SharedTrackRowAction.StartRadio))
    }

    @Test
    fun detailCountsUseNaturalSingularAndPluralLabels() {
        assertEquals("1 track", televisionTrackCountLabel(1))
        assertEquals("2 tracks", televisionTrackCountLabel(2))
        assertEquals("1 release", televisionReleaseCountLabel(1))
        assertEquals("6 releases", televisionReleaseCountLabel(6))
    }
}
