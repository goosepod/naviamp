package app.naviamp.app

import app.naviamp.domain.StreamQuality
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NaviampCastMediaKind { Track, Artwork }

/** Provider identity stays on the sender; only the opaque lease token appears in a receiver URL. */
data class NaviampCastMediaResource(
    val kind: NaviampCastMediaKind,
    val sourceId: String,
    val id: String,
    val quality: StreamQuality = StreamQuality.Original,
) {
    init {
        require(sourceId.isNotBlank()) { "A Cast media source ID is required." }
        require(id.isNotBlank()) { "A Cast media resource ID is required." }
    }
}

/** Implementations must use an operating-system cryptographic random source. */
fun interface NaviampCastSecureTokenSource {
    fun newToken(): String
}

data class NaviampCastMediaLease(
    val token: String,
    val resource: NaviampCastMediaResource,
    val expiresAtEpochMillis: Long,
) {
    val receiverPath: String get() = "/cast/media/$token"
}

/** In-memory, sender-scoped access policy for receiver media requests. */
class NaviampCastMediaLeaseController(
    private val tokens: NaviampCastSecureTokenSource,
    private val nowEpochMillis: () -> Long,
    private val lifetimeMillis: Long = 60 * 60 * 1_000L,
    private val maxActiveLeases: Int = 8,
) {
    init {
        require(lifetimeMillis > 0) { "Cast media lease lifetime must be positive." }
        require(maxActiveLeases > 0) { "Cast media lease limit must be positive." }
    }

    private val leases = mutableMapOf<String, NaviampCastMediaLease>()
    private val mutex = Mutex()

    suspend fun issue(resource: NaviampCastMediaResource): NaviampCastMediaLease = mutex.withLock {
        val now = nowEpochMillis()
        removeExpired(now)
        check(leases.size < maxActiveLeases) { "Too many active Cast media leases." }
        check(now <= Long.MAX_VALUE - lifetimeMillis) { "Cast media lease expiry is out of range." }
        val token = tokens.newToken()
        require(token.length >= 32 && token.all { char ->
            (char.isLetterOrDigit() && char.code < 128) || char == '-' || char == '_'
        }) {
            "Cast media token must be an opaque URL-safe value of at least 32 characters."
        }
        check(token !in leases) { "Cast media token was reused." }
        NaviampCastMediaLease(token, resource, now + lifetimeMillis).also {
            leases[token] = it
        }
    }

    suspend fun resolve(token: String): NaviampCastMediaResource? = mutex.withLock {
        val lease = leases[token] ?: return@withLock null
        if (lease.expiresAtEpochMillis <= nowEpochMillis()) {
            leases.remove(token)
            return@withLock null
        }
        lease.resource
    }

    suspend fun revoke(token: String) = mutex.withLock {
        leases.remove(token)
    }

    suspend fun revokeAll() = mutex.withLock {
        leases.clear()
    }

    private fun removeExpired(now: Long) {
        leases.entries.removeAll { (_, lease) -> lease.expiresAtEpochMillis <= now }
    }
}
