package app.naviamp.domain.radio

import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class SimilarityAdminStatus { Available, NotAdmin, NativeLoginRequired, Unavailable, Failed, TimedOut }

/** Whitelisted metadata only: no plugin configuration, paths, tokens, or raw error messages. */
data class SimilarityPluginInfo(
    val id: String,
    val name: String,
    val version: String? = null,
    val enabled: Boolean? = null,
    val hasError: Boolean = false,
)

data class SimilarityAdminDiagnostics(
    val status: SimilarityAdminStatus,
    val pluginsEnabled: Boolean? = null,
    val externalServicesEnabled: Boolean? = null,
    val agents: List<String>? = null,
    /** Null means inventory could not be read; an empty list means a successful empty response. */
    val plugins: List<SimilarityPluginInfo>? = null,
    val httpStatus: Int? = null,
)

internal suspend fun testSimilarityAdmin(provider: MediaProvider): SimilarityAdminDiagnostics = try {
    withTimeoutOrNull(SimilarityRequestTimeoutMillis) { provider.similarityAdminDiagnostics() }
        ?: SimilarityAdminDiagnostics(SimilarityAdminStatus.TimedOut)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    SimilarityAdminDiagnostics(
        if (failure.isSimilarityTimeout()) SimilarityAdminStatus.TimedOut else SimilarityAdminStatus.Failed,
        httpStatus = (failure as? SimilarityRequestException)?.httpStatus,
    )
}
