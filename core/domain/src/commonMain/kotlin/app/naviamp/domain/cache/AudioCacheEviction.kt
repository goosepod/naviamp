package app.naviamp.domain.cache

data class CachedAudioEvictionCandidate(
    val sourceId: String,
    val trackId: String,
    val qualityKey: String,
    val sizeBytes: Long,
)

data class AudioCacheEvictionPlan(
    val candidatesToEvict: List<CachedAudioEvictionCandidate>,
    val projectedSizeBytes: Long,
)

fun planAudioCacheEviction(
    currentSizeBytes: Long,
    maxSizeBytes: Long,
    oldestFirstCandidates: List<CachedAudioEvictionCandidate>,
    protectedTrackIds: Set<String> = emptySet(),
): AudioCacheEvictionPlan {
    var projectedSize = currentSizeBytes
    if (projectedSize <= maxSizeBytes.coerceAtLeast(0)) {
        return AudioCacheEvictionPlan(emptyList(), projectedSize)
    }

    val evictions = buildList {
        oldestFirstCandidates.forEach { candidate ->
            if (projectedSize <= maxSizeBytes.coerceAtLeast(0)) return@forEach
            if (candidate.trackId in protectedTrackIds) return@forEach
            add(candidate)
            projectedSize -= candidate.sizeBytes
        }
    }
    return AudioCacheEvictionPlan(evictions, projectedSize)
}

