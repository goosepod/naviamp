package app.naviamp.domain.bass

/** Shared BASS network policy. Native adapters translate these values into BASS_SetConfig calls. */
data class BassInternetStreamPolicy(
    val playlistsEnabled: Boolean = true,
    val metadataEnabled: Boolean = true,
    val playlistDepth: Int = 5,
    val bufferMillis: Int = 5_000,
    val prebufferPercent: Int = 75,
    val connectionTimeoutMillis: Int = 15_000,
    val readTimeoutMillis: Int = 15_000,
) {
    init {
        require(playlistDepth >= 0)
        require(bufferMillis >= 0)
        require(prebufferPercent in 0..100)
        require(connectionTimeoutMillis >= 0)
        require(readTimeoutMillis >= 0)
    }
}
