package app.naviamp.domain.sonicautoplay

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.SimilarityRequest
import app.naviamp.domain.radio.SimilarityService

class SonicAutoplayService(
    private val provider: () -> MediaProvider?,
    private val seedCount: Int = DefaultSonicAutoplaySeedCount,
    private val candidatesPerSeed: Int = DefaultSonicAutoplayCandidatesPerSeed,
) {
    suspend fun continuationTracks(
        queue: PlaybackQueue,
        limit: Int = DefaultSonicAutoplayAppendCount,
    ): List<Track> {
        val activeProvider = provider() ?: return emptyList()
        val seeds = queue.sonicAutoplaySeeds(seedCount)
        if (seeds.isEmpty()) return emptyList()

        val blockedTrackIds = queue.tracks.map { it.id }.toSet()
        val result = SimilarityService(activeProvider).load(SimilarityRequest(
            seeds.map { it.id }, excludedTrackIds = blockedTrackIds, countPerSeed = candidatesPerSeed,
        ))
        return result.seedMatches.flatMapIndexed { seedIndex, seed ->
            seed.matches.mapIndexed { matchIndex, match ->
                SonicAutoplayCandidate(match.track, match.score(seedIndex, matchIndex))
            }
        }.groupBy { it.track.id }
            .mapNotNull { (_, candidates) -> candidates.maxByOrNull { it.score } }
            .sortedByDescending { it.score }
            .map { it.track }
            .take(limit.coerceAtLeast(1))
    }
}

fun PlaybackQueue.sonicAutoplaySeeds(limit: Int = DefaultSonicAutoplaySeedCount): List<Track> {
    val current = current ?: return emptyList()
    return (listOf(current) + backTo())
        .distinctBy { track -> track.id }
        .take(limit.coerceAtLeast(1))
}

private data class SonicAutoplayCandidate(
    val track: Track,
    val score: Double,
)

private fun app.naviamp.domain.provider.SonicSimilarTrack.score(seedIndex: Int, matchIndex: Int): Double =
    (similarity ?: 0.5) - (seedIndex * SeedPenalty) - (matchIndex * MatchPenalty)

private const val SeedPenalty = 0.02
private const val MatchPenalty = 0.001
const val DefaultSonicAutoplaySeedCount = 3
const val DefaultSonicAutoplayCandidatesPerSeed = 12
const val DefaultSonicAutoplayAppendCount = 8
