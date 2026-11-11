package app.naviamp.domain.queue

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UpcomingShufflePolicyTest {
    // Fisher-Yates draws that always select the current slot, leaving the input unchanged.
    private fun unchangedRandom() = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(until: Int): Int = until - 1
    }

    @Test
    fun repeatedUnchangedRandomDrawsStillProduceDifferentOrders() {
        val tracks = (1..5).map { track(it.toString()) }
        val policy = UpcomingShufflePolicy(unchangedRandom())
        var previous = tracks
        repeat(12) {
            val shuffled = policy.selectOrder(tracks)
            assertNotEquals(tracks, shuffled)
            assertNotEquals(previous, shuffled)
            assertEquals(tracks.toSet(), shuffled.toSet())
            previous = shuffled
        }
    }

    @Test
    fun repeatedRandomPermutationIsRejected() {
        val policy = UpcomingShufflePolicy(object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        })
        val tracks = (1..5).map { track(it.toString()) }
        val first = policy.selectOrder(tracks)
        val second = policy.selectOrder(tracks)
        assertNotEquals(tracks, first)
        assertNotEquals(tracks, second)
        assertNotEquals(first, second)
    }

    @Test
    fun duplicatesRetainEveryOccurrenceAndUseAvailableVariation() {
        val a = track("a")
        val b = track("b")
        for (tracks in listOf(listOf(a, b, a), listOf(a, a, b), listOf(b, a, a))) {
            val policy = UpcomingShufflePolicy(unchangedRandom())
            var previous = tracks
            repeat(6) {
                val shuffled = policy.selectOrder(tracks)
                assertNotEquals(tracks, shuffled)
                assertNotEquals(previous, shuffled)
                assertEquals(tracks.groupingBy { it }.eachCount(), shuffled.groupingBy { it }.eachCount())
                previous = shuffled
            }
        }
    }

    @Test
    fun smallOrIndistinguishableQueuesTerminateWithoutDroppingTracks() {
        val a = track("a")
        val b = track("b")
        val policy = UpcomingShufflePolicy(unchangedRandom())
        for (tracks in listOf(emptyList(), listOf(a), listOf(a, a, a))) {
            assertEquals(tracks, policy.selectOrder(tracks))
        }
        repeat(3) { assertEquals(listOf(b, a), policy.selectOrder(listOf(a, b))) }
    }

    @Test
    fun largeQueuesUseFullRandomPermutationsWithBroadVariation() {
        val tracks = (1..100).map { track(it.toString()) }
        val policy = UpcomingShufflePolicy(Random(580))
        val orders = List(30) { policy.selectOrder(tracks) }
        assertEquals(30, orders.toSet().size)
        for (order in orders) {
            assertEquals(tracks.toSet(), order.toSet())
            assertTrue(order.zip(tracks).count { (a, b) -> a != b } > 80)
        }
        assertTrue(orders.map { it.first() }.toSet().size > 15)
    }

    private fun track(id: String) = Track(
        id = TrackId(id), title = id, artistName = "Artist", albumTitle = "Album",
        durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null,
    )
}
