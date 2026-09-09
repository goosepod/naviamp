package app.naviamp.provider.navidrome

/** Inspect a bounded prefix before any bytes are committed to the audio store. */
internal fun validateSubsonicMediaResponse(contentType: String?, prefix: ByteArray) {
    val text = prefix.decodeToString().trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    val type = contentType.orEmpty().substringBefore(';').lowercase()
    val document = type.contains("xml") || type.contains("json") || type == "text/html" ||
        text.startsWith("<?xml") || text.startsWith("<subsonic-response") ||
        (text.startsWith("{") && text.contains("\"subsonic-response\""))
    if (!document) return
    val code = Regex("(?:code=[\"']|\"code\"\\s*:\\s*)([0-9]+)")
        .find(text)?.groupValues?.get(1)?.toIntOrNull()
    val message = Regex("message=[\"']([^\"']+)").find(text)?.groupValues?.get(1)
        ?: Regex("\"message\"\\s*:\\s*\"([^\"]+)").find(text)?.groupValues?.get(1)
        ?: "Navidrome request failed."
    throw NavidromeException(message, code)
}
