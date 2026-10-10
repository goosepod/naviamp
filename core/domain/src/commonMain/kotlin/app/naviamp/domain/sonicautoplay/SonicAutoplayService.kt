package app.naviamp.domain.sonicautoplay

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.SonicSimilarTrack
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.SimilarityRequestTimeoutMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

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
        suspend fun collectCandidates(load: suspend (Track) -> List<SonicSimilarTrack>): List<Track> {
            val candidates = mutableListOf<SonicAutoplayCandidate>()
            // Bound the whole seed batch, keeping any usable results collected before the deadline.
            withTimeoutOrNull(SimilarityRequestTimeoutMillis) {
                seeds.forEachIndexed { seedIndex, seed ->
                    val matches = try {
                        load(seed)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                    candidates += matches
                        .filterNot { match -> match.track.id in blockedTrackIds || match.track.id == seed.id }
                        .mapIndexed { matchIndex, match ->
                            SonicAutoplayCandidate(
                                track = match.track,
                                score = match.score(seedIndex, matchIndex),
                            )
                        }
                }
            }
            return candidates.groupBy { it.track.id }
                .mapNotNull { (_, matches) -> matches.maxByOrNull { it.score } }
                .sortedByDescending { it.score }
                .map { it.track }
                .take(limit.coerceAtLeast(1))
        }
        if (activeProvider.capabilities.supportsSonicSimilarity) {
            val sonic = collectCandidates { activeProvider.sonicSimilarTrackMatches(it.id, candidatesPerSeed) }
            if (sonic.isNotEmpty()) return sonic
        }
        return collectCandidates { seed ->
            activeProvider.trackRadio(seed.id, candidatesPerSeed).map { SonicSimilarTrack(it) }
        }
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
