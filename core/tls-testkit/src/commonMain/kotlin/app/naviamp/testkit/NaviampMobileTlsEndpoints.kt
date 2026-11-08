package app.naviamp.testkit

/** Ports belong to already-bound fixture sockets; no consumer guesses or reserves another port. */
data class NaviampMobileTlsEndpoints(
    val controlPort: Int,
    val mutualPort: Int,
    val privateCaPort: Int,
    val wrongHostPort: Int,
) {
    init {
        val ports = listOf(controlPort, mutualPort, privateCaPort, wrongHostPort)
        require(ports.all { it in 1..65535 } && ports.distinct().size == ports.size)
    }
    val mutualUrl: String get() = "https://127.0.0.1:$mutualPort"
    val privateCaUrl: String get() = "https://127.0.0.1:$privateCaPort"
    val wrongHostUrl: String get() = "https://127.0.0.1:$wrongHostPort"

    companion object {
        fun fromManifest(manifest: Map<String, String>) = NaviampMobileTlsEndpoints(
            manifest.getValue("controlPort").toInt(), manifest.getValue("mutualPort").toInt(),
            manifest.getValue("privateCaPort").toInt(), manifest.getValue("wrongHostPort").toInt(),
        )
    }
}
