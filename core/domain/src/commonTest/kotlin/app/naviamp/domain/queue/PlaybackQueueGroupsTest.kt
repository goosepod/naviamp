package app.naviamp.domain.queue

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackTransitionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackQueueGroupsTest {
    @Test
    fun resolvesTheGroupContainingTheCurrentOccurrence() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three")),
            currentIndex = 1,
            groups = listOf(albumGroup(start = 0, end = 3)),
        )

        assertEquals("album", queue.groupAt()?.id)
        assertEquals(PlaybackTransitionMode.Gapless, queue.groupAt()?.profile?.transitionMode)
        assertEquals("album", queue.groupForTransition(toIndex = 2)?.id)
        assertNull(queue.groupForTransition(toIndex = 3))
    }

    @Test
    fun normalizationClampsRangesAndRejectsOverlaps() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three")),
            currentIndex = 0,
            groups = listOf(
                albumGroup(id = "first", start = -4, end = 2),
                albumGroup(id = "overlap", start = 1, end = 3),
                albumGroup(id = "last", start = 2, end = 20),
            ),
        )

        assertEquals(listOf("first", "last"), queue.normalizedGroups().map { it.id })
        assertEquals(0, queue.normalizedGroups().first().startIndex)
        assertEquals(3, queue.normalizedGroups().last().endIndexExclusive)
        assertNull(queue.groupAt(index = 8))
    }

    @Test
    fun appendingTracksPreservesExistingGroupBoundaries() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two")),
            currentIndex = 0,
            groups = listOf(albumGroup(start = 0, end = 2)),
        )

        val appended = queue.appendTracks(listOf(track("three")))

        assertEquals(0, appended.groups.single().startIndex)
        assertEquals(2, appended.groups.single().endIndexExclusive)
    }

    @Test
    fun appendingAProfiledCollectionAddsItsOwnGroup() {
        val queue = PlaybackQueue(
            tracks = listOf(track("current")),
            currentIndex = 0,
        )

        val appended = queue.appendGroupedTracks(
            tracks = listOf(track("album-one"), track("album-two")),
            group = albumGroup(start = 0, end = 0),
        )

        assertEquals(listOf("current", "album-one", "album-two"), appended.tracks.map { it.id.value })
        assertEquals(1, appended.groups.single().startIndex)
        assertEquals(3, appended.groups.single().endIndexExclusive)
    }

    @Test
    fun playingAProfiledCollectionNextSplitsAndPreservesTheCurrentGroup() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three")),
            currentIndex = 0,
            groups = listOf(albumGroup(id = "current-album", start = 0, end = 3)),
        )

        val inserted = queue.playNextGroupedTracks(
            tracks = listOf(track("inserted-one"), track("inserted-two")),
            group = albumGroup(id = "inserted-album", start = 0, end = 0),
        )

        assertEquals(
            listOf("one", "inserted-one", "inserted-two", "two", "three"),
            inserted.tracks.map { it.id.value },
        )
        assertEquals(
            listOf(0 to 1, 1 to 3, 3 to 5),
            inserted.groups.map { it.startIndex to it.endIndexExclusive },
        )
        assertEquals("inserted-album", inserted.groupAt(1)?.id)
        assertEquals("current-album:after:1", inserted.groupAt(3)?.id)
    }

    @Test
    fun pruningPlayedHistoryShiftsAndClampsGroupBoundaries() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three")),
            currentIndex = 1,
            groups = listOf(albumGroup(start = 0, end = 3)),
        )

        val pruned = queue.removePlayedHistory()

        assertEquals(0, pruned.groups.single().startIndex)
        assertEquals(2, pruned.groups.single().endIndexExclusive)
    }

    @Test
    fun selectingALaterTrackKeepsTheProfileAttachedToItsAlbumTracks() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three"), track("four")),
            currentIndex = 0,
            groups = listOf(albumGroup(start = 0, end = 4)),
        )

        val selected = queue.jumpTo(2)

        assertEquals(listOf("one", "three", "two", "four"), selected.tracks.map { it.id.value })
        assertEquals("three", selected.current?.id?.value)
        assertEquals("album", selected.groupAt()?.id)
        assertEquals(0, selected.groups.single().startIndex)
        assertEquals(4, selected.groups.single().endIndexExclusive)
    }

    @Test
    fun selectingIntoAProfiledGroupSplitsItAroundSkippedNonGroupTracks() {
        val queue = PlaybackQueue(
            tracks = listOf(track("current"), track("unprofiled"), track("album-one"), track("album-two")),
            currentIndex = 0,
            groups = listOf(albumGroup(start = 2, end = 4)),
        )

        val selected = queue.jumpTo(3)

        assertEquals(
            listOf("current", "album-two", "unprofiled", "album-one"),
            selected.tracks.map { it.id.value },
        )
        assertEquals("album", selected.groupAt()?.id)
        assertNull(selected.groupAt(2))
        assertEquals("album:reordered:3", selected.groupAt(3)?.id)
    }

    @Test
    fun structuralQueueEditsDissolveGroups() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three")),
            currentIndex = 0,
            groups = listOf(albumGroup(start = 0, end = 3)),
        )

        assertTrue(queue.playNextTracks(listOf(track("inserted"))).groups.isEmpty())
        assertTrue(queue.removeAt(2).groups.isEmpty())
        assertTrue(queue.moveToNext(2).groups.isEmpty())
        assertTrue(queue.shuffleUpcoming()!!.first.groups.isEmpty())
    }
}

private fun albumGroup(
    id: String = "album",
    start: Int,
    end: Int,
) = PlaybackQueueGroup(
    id = id,
    target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-id"),
    label = "Album",
    startIndex = start,
    endIndexExclusive = end,
    profile = PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless),
)

private fun track(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = null,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)
