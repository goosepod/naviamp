package app.naviamp.domain.radio

import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.SonicSimilarTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class SimilarityRequest(
    val seeds: List<TrackId>,
    val excludedTrackIds: Set<TrackId> = emptySet(),
    val countPerSeed: Int = 50,
    val preferSonic: Boolean = true,
    val minimumMatches: Int = 1,
)

/** Seed slots retain their order, including empty slots, so callers can preserve ranking policy. */
data class SimilaritySeedMatches(val seedId: TrackId, val matches: List<SonicSimilarTrack>)

class SimilarityResult internal constructor(
    val source: SimilarityEndpoint?,
    val seedMatches: List<SimilaritySeedMatches>,
    val fallback: SonicRadioFallbackReason? = null,
    val failure: SimilarityEndpointResult? = null,
    // Kept out of diagnostic metadata. Radio may propagate a fatal provider error; Autoplay stops.
    private val failureCause: Exception? = null,
    /** Regular matches supplement usable Sonic matches without replacing their ranking or failure policy. */
    val topUp: SimilarityResult? = null,
) {
    val hasMatches: Boolean get() = seedMatches.any { it.matches.isNotEmpty() }

    internal fun withFallback(reason: SonicRadioFallbackReason?) =
        SimilarityResult(source, seedMatches, reason, failure, failureCause, topUp)

    internal fun withTopUp(result: SimilarityResult) =
        SimilarityResult(source, seedMatches, fallback, failure, failureCause, result)

    fun getOrThrow(): List<SimilaritySeedMatches> {
        failureCause?.let { throw it }
        failure?.let {
            throw SimilarityRequestException(it.httpStatus, it.serverCode, it.kind == SimilarityResultKind.TimedOut)
        }
        return seedMatches
    }
}

/** Owns track Radio and Autoplay similarity requests; raw diagnostic probes intentionally bypass it. */
class SimilarityService(private val provider: MediaProvider) {
    suspend fun load(request: SimilarityRequest): SimilarityResult {
        val seeds = request.seeds.distinct()
        if (seeds.isEmpty()) return SimilarityResult(null, emptyList())
        val count = request.countPerSeed.coerceAtLeast(1)
        val excluded = request.excludedTrackIds.toSet()

        suspend fun batch(endpoint: SimilarityEndpoint, additionalExclusions: Set<TrackId> = emptySet()): SimilarityResult {
            val results = seeds.map { SimilaritySeedMatches(it, emptyList()) }.toMutableList()
            var firstFailure: Exception? = null
            val completed = withTimeoutOrNull(SimilarityRequestTimeoutMillis) {
                seeds.forEachIndexed { index, seed ->
                    val matches = try {
                        when (endpoint) {
                            SimilarityEndpoint.Sonic -> provider.sonicSimilarTrackMatches(seed, count)
                            SimilarityEndpoint.Regular -> provider.trackRadio(seed, count).map { SonicSimilarTrack(it) }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (firstFailure == null) firstFailure = failure
                        emptyList()
                    }
                    results[index] = SimilaritySeedMatches(seed, matches.filterNot {
                        it.track.id == seed || it.track.id in excluded || it.track.id in additionalExclusions
                    })
                }
                true
            } == true
            // Keep partial usable matches, rather than replacing them after a later seed fails.
            if (results.any { it.matches.isNotEmpty() }) return SimilarityResult(endpoint, results)
            val cause = firstFailure
            val timedOut = !completed || cause?.isSimilarityTimeout() == true
            val codes = cause as? SimilarityRequestException
            val failure = if (timedOut || cause != null) SimilarityEndpointResult(
                if (timedOut) SimilarityResultKind.TimedOut else SimilarityResultKind.Failed,
                httpStatus = codes?.httpStatus, serverCode = codes?.serverCode,
            ) else null
            return SimilarityResult(endpoint, results, failure = failure,
                failureCause = cause.takeIf { completed })
        }

        var fallback: SonicRadioFallbackReason? = null
        if (request.preferSonic && provider.capabilities.supportsSonicSimilarity) {
            val sonic = batch(SimilarityEndpoint.Sonic)
            if (sonic.hasMatches) {
                val sonicIds = sonic.seedMatches.flatMap { seed -> seed.matches.map { it.track.id } }.toSet()
                if (sonicIds.size >= request.minimumMatches.coerceAtLeast(1)) return sonic
                return sonic.withTopUp(batch(SimilarityEndpoint.Regular, sonicIds))
            }
            fallback = when (sonic.failure?.kind) {
                SimilarityResultKind.TimedOut -> SonicRadioFallbackReason.TimedOut
                SimilarityResultKind.Failed -> SonicRadioFallbackReason.Failed
                else -> SonicRadioFallbackReason.Empty
            }
        }
        val regular = batch(SimilarityEndpoint.Regular)
        return regular.withFallback(fallback)
    }
}
