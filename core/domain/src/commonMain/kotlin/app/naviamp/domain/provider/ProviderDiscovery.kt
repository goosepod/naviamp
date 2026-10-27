package app.naviamp.domain.provider

/** Discovery identities are deliberately separate from playable Library artist/album/track IDs. */
data class ProviderDiscoveryArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val genres: List<String> = emptyList(),
    val matchPercent: Int? = null,
    val externalUrl: String? = null,
)

enum class ProviderDiscoveryStatus {
    Ready, Building, Unsupported, PasswordRequired, SignInRequired, Forbidden, Unavailable,
}

data class ProviderDiscoveryFeed(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Ready,
    val recommendations: List<ProviderDiscoveryArtist> = emptyList(),
    val trending: List<ProviderDiscoveryArtist> = emptyList(),
    val lastUpdated: String? = null,
)

/** Optional native discovery; failure never disables the provider's standard playback contract. */
fun interface ProviderDiscovery {
    suspend fun load(): ProviderDiscoveryFeed
}
