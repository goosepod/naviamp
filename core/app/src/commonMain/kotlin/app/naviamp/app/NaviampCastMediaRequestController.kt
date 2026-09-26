package app.naviamp.app

sealed interface NaviampCastRequestedRange {
    data class From(val firstByte: Long, val lastByteInclusive: Long? = null) : NaviampCastRequestedRange
    data class Suffix(val byteCount: Long) : NaviampCastRequestedRange
}

data class NaviampCastResolvedRange(val firstByte: Long, val lastByteInclusive: Long, val totalBytes: Long) {
    val length: Long get() = lastByteInclusive - firstByte + 1
    val contentRange: String get() = "bytes $firstByte-$lastByteInclusive/$totalBytes"
}

fun NaviampCastRequestedRange.toHttpHeader(): String = when (this) {
    is NaviampCastRequestedRange.From -> "bytes=$firstByte-${lastByteInclusive?.toString().orEmpty()}"
    is NaviampCastRequestedRange.Suffix -> "bytes=-$byteCount"
}

fun NaviampCastRequestedRange.resolve(totalBytes: Long): NaviampCastResolvedRange? {
    if (totalBytes <= 0) return null
    return when (this) {
        is NaviampCastRequestedRange.From -> {
            if (firstByte >= totalBytes) null
            else NaviampCastResolvedRange(
                firstByte = firstByte,
                lastByteInclusive = (lastByteInclusive ?: totalBytes - 1).coerceAtMost(totalBytes - 1),
                totalBytes = totalBytes,
            )
        }
        is NaviampCastRequestedRange.Suffix -> NaviampCastResolvedRange(
            firstByte = (totalBytes - byteCount).coerceAtLeast(0),
            lastByteInclusive = totalBytes - 1,
            totalBytes = totalBytes,
        )
    }
}

sealed interface NaviampCastMediaRequestDecision {
    data class Allowed(
        val resource: NaviampCastMediaResource,
        val range: NaviampCastRequestedRange?,
        val headOnly: Boolean,
    ) : NaviampCastMediaRequestDecision
    data object NotFound : NaviampCastMediaRequestDecision
    data object MethodNotAllowed : NaviampCastMediaRequestDecision
    data object InvalidRange : NaviampCastMediaRequestDecision
}

/** Shared HTTP authorization policy; the host only accepts sockets and transfers bytes. */
class NaviampCastMediaRequestController(
    private val leases: NaviampCastMediaLeaseController,
) {
    suspend fun authorize(method: String, path: String, rangeHeader: String?): NaviampCastMediaRequestDecision {
        if (method != "GET" && method != "HEAD") return NaviampCastMediaRequestDecision.MethodNotAllowed
        val token = path.removePrefix(MEDIA_PREFIX).takeIf { path.startsWith(MEDIA_PREFIX) && '/' !in it }
            ?: return NaviampCastMediaRequestDecision.NotFound
        val resource = leases.resolve(token) ?: return NaviampCastMediaRequestDecision.NotFound
        val range = rangeHeader?.let(::parseRange)
        if (rangeHeader != null && range == null) return NaviampCastMediaRequestDecision.InvalidRange
        return NaviampCastMediaRequestDecision.Allowed(resource, range, method == "HEAD")
    }

    private fun parseRange(header: String): NaviampCastRequestedRange? {
        if (!header.startsWith("bytes=", ignoreCase = true)) return null
        val value = header.substringAfter('=').trim()
        if (',' in value) return null
        val split = value.split('-', limit = 2)
        if (split.size != 2) return null
        val first = split[0].trim()
        val last = split[1].trim()
        if (first.isEmpty()) {
            val count = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return NaviampCastRequestedRange.Suffix(count)
        }
        val start = first.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val end = last.takeIf(String::isNotEmpty)?.toLongOrNull()
        if (last.isNotEmpty() && (end == null || end < start)) return null
        return NaviampCastRequestedRange.From(start, end)
    }

    private companion object {
        const val MEDIA_PREFIX = "/cast/media/"
    }
}
