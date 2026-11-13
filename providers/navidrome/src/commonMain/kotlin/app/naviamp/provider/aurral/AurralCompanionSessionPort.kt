package app.naviamp.provider.aurral

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.provider.*
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.provider.navidrome.*
import app.naviamp.presentation.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Composes discovery only. Every ordinary media operation remains owned by the primary provider. */
@OptIn(ExperimentalAtomicApi::class)
internal class AurralCompanionSessionPort(
    private val primary: NaviampCoreProviderSessionPort,
    private val sources: MediaSourceRepository,
) : NaviampCoreProviderSessionPort by primary {
    private class Binding(val sourceId: String?, val primary: MediaProvider,
        val configuration: AurralCompanion?, val composed: MediaProvider)
    // A fresh state object makes invalidation observable even during the first repository read.
    private class CacheState(val binding: Binding? = null)
    private val cache = AtomicReference(CacheState())
    override val providerSource = NaviampCoreMediaProviderSource(::currentProvider)

    private fun invalidate() { cache.store(CacheState()) }

    override fun currentProvider(): MediaProvider? {
        while (true) {
            val provider = primary.currentProvider() ?: run { invalidate(); return null }
            val sourceId = primary.currentSourceId()
            val before = cache.load()
            val bound = before.binding
            if (bound != null && bound.sourceId == sourceId && bound.primary === provider) return bound.composed
            val configuration = if (bound != null && bound.sourceId == sourceId) bound.configuration
                else sourceId?.let(sources::mediaSource)?.aurralCompanion?.normalized()
            val composed = configuration?.takeIf { it.enabled && provider.id.value != ProviderIdAurral }
                ?.let { companionProvider(provider, it) } ?: provider
            val active = primary.currentProvider()
            if (primary.currentSourceId() != sourceId || active == null || active.id != provider.id || active.cacheNamespace != provider.cacheNamespace) continue
            if (cache.compareAndSet(before, CacheState(Binding(sourceId, provider, configuration, composed)))) return composed
        }
    }

    override fun replaceSavedConnections(connections: List<NaviampCoreSavedConnectionRecord>) {
        primary.replaceSavedConnections(connections)
        invalidate()
    }

    override suspend fun clearActiveSession() {
        primary.clearActiveSession()
        invalidate()
    }

    override suspend fun editableConnection(id: String): NaviampCoreEditableConnection =
        primary.editableConnection(id).let { it.copy(form = it.form.copy(aurralCompanion = sources.mediaSource(id)?.takeUnless { saved -> saved.providerId == ProviderIdAurral }?.aurralCompanion)) }

    override suspend fun connect(request: NaviampCoreConnectionRequest, plan: NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
        val configured = (request as? NaviampCoreConnectionRequest.Form)?.form?.aurralCompanion
        val result = primary.connect(request, plan)
        if (request is NaviampCoreConnectionRequest.Form && configured != null && primary.currentProvider()?.id?.value != ProviderIdAurral)
            sources.updateAurralCompanion(result.sourceId, configured)
        invalidate()
        return result
    }

    override suspend fun saveAurralCompanion(sourceId: String, companion: AurralCompanion) {
        sources.updateAurralCompanion(sourceId, companion)
        invalidate()
    }
}

internal fun companionProvider(primary: MediaProvider, settings: AurralCompanion,
    companionHttp: NavidromeHttpClient? = null,
    createHttp: (app.naviamp.domain.source.ConnectionTlsSettings) -> NavidromeHttpClient = ::createDefaultNavidromeHttpClient,
): MediaProvider = try {
    val endpoint = settings.baseUrl.trim()
    val authority = endpoint.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
    require((endpoint.startsWith("https://", true) || endpoint.startsWith("http://", true)) &&
        authority.isNotBlank() && '@' !in authority && settings.username.isNotBlank())
    createCompanionProvider(primary, settings, companionHttp ?: createHttp(settings.tlsSettings))
} catch (_: Exception) {
    object : MediaProvider by primary {
        override val discoveryProviderId = app.naviamp.domain.ProviderId(ProviderIdAurral)
        override val discoveryAccountScope = "aurral:${settings.baseUrl}:${settings.username}"
        override val discovery = ProviderDiscovery { ProviderDiscoveryFeed(ProviderDiscoveryStatus.Unavailable) }
    }
}

private fun createCompanionProvider(primary: MediaProvider, settings: AurralCompanion,
    rawHttp: NavidromeHttpClient,
): MediaProvider {
    val connection = NavidromeConnection(
        providerId = ProviderIdAurral, baseUrl = settings.baseUrl, username = settings.username,
        password = settings.password, token = "", salt = "", tlsSettings = settings.tlsSettings,
        customHeaders = settings.customHeaders,
    )
    val http = (rawHttp as? KtorNavidromeHttpClient)?.withConnectionHeaderNames(
        connection.customHeaders.mapNotNull { it.normalized()?.takeIf { header -> !header.value.isNullOrBlank() }?.name }.toSet(),
    ) ?: rawHttp
    val previews = NavidromeProvider(connection, http)
    // Always use the companion HTTP client for samples, including its own TLS policy. The primary
    // connection's native URL-consumer certificate defaults must never govern another account.
    val sampleTransport = object : MediaProvider by previews {
        override val requiresAuthenticatedPlaybackTransport = true
    }
    // Aurral Library IDs are never primary Library IDs. Do not expose Aurral's Library links here.
    val discovery = AurralDiscoveryClient(connection, http)
    return object : MediaProvider by primary {
        override val discoveryProviderId = app.naviamp.domain.ProviderId(ProviderIdAurral)
        override val discovery = discovery
        override val discoveryAccountScope = previews.discoveryAccountScope
        override val discoveryPreviewProvider: MediaProvider = sampleTransport
        override suspend fun bytesForOwnedUrl(url: String): ByteArray? = when {
            discovery.ownsDiscoveryCoverUrl(url) -> discovery.discoveryCoverBytes(url)
            url.startsWith("${connection.normalizedBaseUrl}/api/") -> previews.bytesForOwnedUrl(url)
            else -> primary.bytesForOwnedUrl(url)
        }
    }
}
