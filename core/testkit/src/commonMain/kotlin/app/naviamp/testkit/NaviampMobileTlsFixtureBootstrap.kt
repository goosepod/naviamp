package app.naviamp.testkit

/** A runner file supplies identity; only the native transport can prove its fixture is reachable. */
internal suspend fun loadVerifiedNaviampMobileTlsFixture(
    bootstrap: String?,
    fallbackControlPort: Int?,
    fetch: suspend (Int) -> Map<String, String>,
): Map<String, String> {
    val expected = bootstrap?.let(::decodeNaviampMobileTlsControlResponse)
    val port = expected?.let(NaviampMobileTlsEndpoints::fromManifest)?.controlPort
        ?: requireNotNull(fallbackControlPort) { "Runner must supply fixture bootstrap or control port" }
    require(port in 1..65535)
    val actual = fetch(port)
    check(NaviampMobileTlsEndpoints.fromManifest(actual).controlPort == port) {
        "Fixture control response describes a different listener"
    }
    check(expected == null || actual == expected) {
        "Fixture control response does not match the runner bootstrap"
    }
    return actual
}
