package app.naviamp.testkit

/** Decode only the bounded manifest/state protocol served by the disposable fixture. */
fun decodeNaviampMobileTlsControlResponse(text: String): Map<String, String> {
    require(text.encodeToByteArray().size in 1..1_048_576)
    return text.lineSequence().filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
}
