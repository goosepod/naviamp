package app.naviamp.domain.playback

/**
 * Produces a useful URL for diagnostics without retaining provider credentials or other request
 * values. Playback URLs are provider-controlled, so all query values are treated as sensitive.
 */
internal fun String.sanitizedPlaybackDiagnosticUrl(): String =
    runCatching {
        val withoutFragment = substringBefore('#')
        val rawBase = withoutFragment.substringBefore('?')
        val base = rawBase.redactedUrlUserInfo()
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .filter { it.isNotBlank() }
            .joinToString("&") { parameter ->
                val key = parameter.substringBefore('=')
                if ('=' in parameter) "$key=<redacted>" else key
            }

        buildString {
            append(base)
            if (query.isNotBlank()) {
                append('?')
                append(query)
            }
            if ('#' in this@sanitizedPlaybackDiagnosticUrl) {
                append("#<redacted>")
            }
        }
    }.getOrDefault("<unparseable url>")

private fun String.redactedUrlUserInfo(): String {
    val authorityStart = indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return this
    val authorityEnd = indexOf('/', startIndex = authorityStart).takeIf { it >= 0 } ?: length
    val userInfoEnd = lastIndexOf('@', startIndex = authorityEnd - 1)
    if (userInfoEnd < authorityStart) return this
    return replaceRange(authorityStart, userInfoEnd, "<redacted>")
}
