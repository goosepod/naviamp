package app.naviamp.domain.cache

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioCacheEvictionTest {
    @Test
    fun skipsProtectedTracksWhenTrimmingOldestFirst() {
        val plan = planAudioCacheEviction(
            currentSizeBytes = 400,
            maxSizeBytes = 150,
            oldestFirstCandidates = listOf(
                candidate("current", 100),
                candidate("old-1", 100),
                candidate("next", 100),
                candidate("old-2", 100),
            ),
            protectedTrackIds = setOf("current", "next"),
        )

        assertEquals(listOf("old-1", "old-2"), plan.candidatesToEvict.map { it.trackId })
        assertEquals(200, plan.projectedSizeBytes)
    }

    @Test
    fun evictsUntilUnderLimitWhenNoTracksAreProtected() {
        val plan = planAudioCacheEviction(
            currentSizeBytes = 500,
            maxSizeBytes = 250,
            oldestFirstCandidates = listOf(
                candidate("old-1", 100),
                candidate("old-2", 150),
                candidate("old-3", 100),
            ),
        )

        assertEquals(listOf("old-1", "old-2"), plan.candidatesToEvict.map { it.trackId })
        assertEquals(250, plan.projectedSizeBytes)
    }

    private fun candidate(trackId: String, sizeBytes: Long): CachedAudioEvictionCandidate =
        CachedAudioEvictionCandidate(
            sourceId = "source",
            trackId = trackId,
            qualityKey = "original",
            sizeBytes = sizeBytes,
        )
}

