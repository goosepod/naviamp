package app.naviamp.domain.queue

import app.naviamp.domain.Track
import kotlin.random.Random

/** Remembers only the last shuffle, across on/off toggles, for this queue owner. */
class UpcomingShufflePolicy(private val random: Random = Random.Default) {
    private var previousOrder: List<Track>? = null

    fun selectOrder(tracks: List<Track>): List<Track> {
        fun acceptable(candidate: List<Track>) = candidate != tracks && candidate != previousOrder

        repeat(8) {
            val candidate = tracks.shuffled(random)
            if (acceptable(candidate)) return candidate.also { previousOrder = it }
        }

        // Bound the work even for an unlucky (or deterministic) random source. Swapping the
        // first distinct pair supplies another order; either pivot can supply a third order.
        val different = tracks.indexOfFirst { it != tracks.firstOrNull() }
        if (different < 0) return tracks.also { previousOrder = it }
        var alternative: List<Track> = tracks
        for (pivot in listOf(0, different)) {
            for (index in tracks.indices) {
                if (tracks[index] == tracks[pivot]) continue
                val candidate = tracks.toMutableList().apply {
                    this[pivot] = tracks[index]
                    this[index] = tracks[pivot]
                }
                alternative = candidate
                if (acceptable(candidate)) return candidate.also { previousOrder = it }
            }
        }
        // Two distinguishable items have only one order other than the original.
        return alternative.also { previousOrder = it }
    }
}
