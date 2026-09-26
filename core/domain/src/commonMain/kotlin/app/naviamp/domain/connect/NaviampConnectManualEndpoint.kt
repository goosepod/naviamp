package app.naviamp.domain.connect

/** An address supplied by the user. It carries no device identity or pairing authority. */
data class NaviampConnectManualEndpoint(val host: String, val port: Int) {
    val displayAddress: String
        get() = if (':' in host) "[$host]:$port" else "$host:$port"
}

fun parseNaviampConnectManualEndpoint(value: String): NaviampConnectManualEndpoint? {
    val text = value.trim()
    val host: String
    val portText: String
    if (text.startsWith('[')) {
        val closing = text.indexOf(']')
        if (closing <= 1 || text.getOrNull(closing + 1) != ':') return null
        host = text.substring(1, closing)
        portText = text.substring(closing + 2)
        if (!validIpv6(host)) return null
    } else {
        val separator = text.lastIndexOf(':')
        if (separator <= 0 || ':' in text.substring(0, separator)) return null
        host = text.substring(0, separator)
        portText = text.substring(separator + 1)
        if (!validIpv4(host) && !validHostname(host)) return null
    }
    if (portText.isEmpty() || portText.length > 5 || portText.any { !it.isDigit() }) return null
    val port = portText.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
    return NaviampConnectManualEndpoint(host.lowercase(), port)
}

private fun validIpv4(host: String): Boolean = host.split('.').let { parts ->
    parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
            part.toIntOrNull()?.let { it in 0..255 } == true && (part.length == 1 || part.first() != '0')
    }
}

private fun validHostname(host: String): Boolean =
    host.length in 1..253 && host.any(Char::isLetter) && host.split('.').all { label ->
        label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
            label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
    }

private fun validIpv6(host: String): Boolean {
    if (host.isEmpty() || host.any { it !in "0123456789abcdefABCDEF:" }) return false
    if (host.windowed(3).any { it == ":::" }) return false
    val compressed = "::" in host
    if (compressed && host.indexOf("::") != host.lastIndexOf("::")) return false
    val groups = host.split(':').filter(String::isNotEmpty)
    if (groups.any { it.length !in 1..4 }) return false
    return if (compressed) groups.size < 8 else groups.size == 8 && !host.startsWith(':') && !host.endsWith(':')
}
