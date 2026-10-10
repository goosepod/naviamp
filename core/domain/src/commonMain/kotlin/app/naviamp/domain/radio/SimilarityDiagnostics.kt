package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException

enum class SimilarityEndpoint { Sonic, Regular }
enum class SimilaritySupport { Advertised, Missing }
enum class SimilarityResultKind { Matches, Empty, Unsupported, TimedOut, Failed }

/** Only safe protocol codes cross into the diagnostics UI, never request URLs or error text. */
class SimilarityRequestException(val httpStatus: Int? = null, val serverCode: Int? = null, val timedOut: Boolean = false) : Exception()

fun Throwable.isSimilarityTimeout(): Boolean = this is HttpRequestTimeoutException ||
    this is ConnectTimeoutException || this is SocketTimeoutException ||
    (this is SimilarityRequestException && timedOut)

data class SimilarityEndpointResult(
    val kind: SimilarityResultKind,
    val count: Int = 0,
    val httpStatus: Int? = null,
    val serverCode: Int? = null,
)

data class SimilarityDiagnostics(
    val seed: Track,
    val support: SimilaritySupport?,
    val supportFailure: SimilarityEndpointResult?,
    val sonic: SimilarityEndpointResult,
    val regular: SimilarityEndpointResult,
)

const val SimilarityRequestTimeoutMillis = 10_000L

/** Explicit diagnostic only: both raw endpoints use the same seed/account and never alter playback. */
suspend fun testSimilarity(provider: MediaProvider, seed: Track, count: Int = 20): SimilarityDiagnostics {
    var support: SimilaritySupport? = null
    val supportResult = similarityAttempt {
        support = provider.similaritySupport()
        emptyList()
    }
    return coroutineScope {
        val sonic = async { probeEndpoint(provider, seed.id, SimilarityEndpoint.Sonic, count) }
        val regular = async { probeEndpoint(provider, seed.id, SimilarityEndpoint.Regular, count) }
        SimilarityDiagnostics(seed, support,
            supportResult.takeUnless { it.kind == SimilarityResultKind.Empty }, sonic.await(), regular.await())
    }
}

private suspend fun probeEndpoint(provider: MediaProvider, seed: TrackId, endpoint: SimilarityEndpoint, count: Int) =
    similarityAttempt {
        provider.similarityEndpointTracks(seed, endpoint, count.coerceIn(1, 50))
            .filterNot { it.id == seed }.distinctBy { it.id }
    }

private suspend fun similarityAttempt(load: suspend () -> List<Track>): SimilarityEndpointResult = try {
    val tracks = withTimeoutOrNull(SimilarityRequestTimeoutMillis) { load() }
    when {
        tracks == null -> SimilarityEndpointResult(SimilarityResultKind.TimedOut)
        tracks.isEmpty() -> SimilarityEndpointResult(SimilarityResultKind.Empty)
        else -> SimilarityEndpointResult(SimilarityResultKind.Matches, tracks.size)
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: UnsupportedOperationException) {
    SimilarityEndpointResult(SimilarityResultKind.Unsupported)
} catch (failure: Exception) {
    val codes = failure as? SimilarityRequestException
    SimilarityEndpointResult(if (failure.isSimilarityTimeout()) SimilarityResultKind.TimedOut else SimilarityResultKind.Failed,
        httpStatus = codes?.httpStatus, serverCode = codes?.serverCode)
}

enum class SonicRadioFallbackReason { Empty, TimedOut, Failed }
enum class RadioBuildOutcome { Fallback, Empty, Failed }
data class RadioBuildDiagnostics(val outcome: RadioBuildOutcome, val fallback: SonicRadioFallbackReason? = null)
