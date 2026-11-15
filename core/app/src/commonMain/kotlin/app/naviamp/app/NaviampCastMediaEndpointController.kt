package app.naviamp.app

import app.naviamp.domain.provider.ProviderMediaByteResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NaviampCastHttpRequest(
    val method: String,
    val path: String,
    val rangeHeader: String?,
)

interface NaviampCastHttpResponse {
    suspend fun begin(statusCode: Int, headers: Map<String, String>)
    suspend fun write(bytes: ByteArray, count: Int)
}

/** Native socket binding only; request decisions and provider bytes stay in shared code. */
interface NaviampCastHttpServerEffect {
    suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String
    suspend fun start(localAddress: String?, handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String =
        start(handler)
    suspend fun stop()
}

class NaviampCastMediaEndpointController(
    private val server: NaviampCastHttpServerEffect,
    private val leases: NaviampCastMediaLeaseController,
    private val source: NaviampCastMediaByteSource,
    private val localAddress: () -> String? = { null },
) {
    private val requests = NaviampCastMediaRequestController(leases)
    private var baseUrl: String? = null
    private val lifetime = Mutex()
    private val issued = mutableMapOf<String, NaviampCastMediaResource>()

    suspend fun start() = lifetime.withLock {
        if (baseUrl != null) return@withLock
        try {
            val bound = server.start(localAddress(), ::handle).trimEnd('/')
            require(bound.startsWith("http://") || bound.startsWith("https://")) {
                "Cast media server must return an HTTP URL."
            }
            baseUrl = bound
        } catch (failure: Exception) {
            withContext(NonCancellable) { server.stop() }
            throw failure
        }
    }

    suspend fun issue(resource: NaviampCastMediaResource, retainUntilRevoked: Boolean = false): String = lifetime.withLock {
        val base = checkNotNull(baseUrl) { "Cast media endpoint has not started." }
        source.prepare(resource)
        try {
            (base + leases.issue(resource, retainUntilRevoked).receiverPath).also { issued[it] = resource }
        } catch (failure: Exception) {
            if (resource !in issued.values) withContext(NonCancellable) { source.release(resource) }
            throw failure
        }
    }

    suspend fun revoke(url: String) = lifetime.withLock {
        val base = baseUrl ?: return@withLock
        val prefix = "$base/cast/media/"
        if (url.startsWith(prefix)) leases.revoke(url.removePrefix(prefix))
        issued.remove(url)?.let { resource ->
            if (resource !in issued.values) source.release(resource)
        }
    }

    suspend fun stop() = lifetime.withLock {
        leases.revokeAll()
        baseUrl = null
        issued.clear()
        try { server.stop() } finally { source.clear() }
    }

    private suspend fun handle(request: NaviampCastHttpRequest, response: NaviampCastHttpResponse) {
        when (val decision = requests.authorize(request.method, request.path, request.rangeHeader)) {
            NaviampCastMediaRequestDecision.NotFound -> response.begin(404, emptyMap())
            NaviampCastMediaRequestDecision.MethodNotAllowed -> response.begin(405, mapOf("Allow" to "GET, HEAD"))
            NaviampCastMediaRequestDecision.InvalidRange -> response.begin(416, emptyMap())
            is NaviampCastMediaRequestDecision.Allowed -> {
                var responseStarted = false
                var failed = false
                val completed = try {
                    source.stream(
                        resource = decision.resource,
                        range = decision.range,
                        headOnly = decision.headOnly,
                        onResponse = { upstream ->
                            response.begin(upstream.statusCode, responseHeaders(upstream, decision.resource.contentTypeOverride))
                            responseStarted = true
                        },
                        writeChunk = { bytes, count ->
                            check(responseStarted) { "Cast media bytes arrived before response metadata." }
                            if (!decision.headOnly) response.write(bytes, count)
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                    false
                }
                if (!completed && !responseStarted) response.begin(if (failed) 502 else 404, emptyMap())
                if (!completed && responseStarted) error("Cast media stream ended before completion.")
            }
        }
    }

    private fun responseHeaders(upstream: ProviderMediaByteResponse, override: String?): Map<String, String> = buildMap {
        put("Accept-Ranges", "bytes")
        put("Access-Control-Allow-Origin", "*")
        val upstreamType = upstream.contentType?.substringBefore(';')?.trim()?.lowercase()
        val effectiveOverride = override.takeIf { upstream.statusCode == 200 || upstream.statusCode == 206 }
        if (effectiveOverride != null && upstreamType != null && upstreamType != "application/octet-stream") {
            fun canonical(value: String) = when (value) { "audio/x-flac" -> "audio/flac"; "audio/mp3" -> "audio/mpeg"; else -> value }
            check(canonical(upstreamType) == canonical(effectiveOverride))
        }
        (effectiveOverride ?: upstream.contentType)?.takeIf { '\r' !in it && '\n' !in it }?.let { put("Content-Type", it) }
        upstream.contentLength?.takeIf { it >= 0 }?.let { put("Content-Length", it.toString()) }
        upstream.contentRange?.takeIf { '\r' !in it && '\n' !in it }?.let { put("Content-Range", it) }
    }
}
