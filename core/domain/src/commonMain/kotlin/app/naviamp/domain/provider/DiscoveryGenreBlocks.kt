package app.naviamp.domain.provider

data class ProviderDiscoveryBlockedGenres(val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val tags: List<String> = emptyList())
data class ProviderDiscoveryGenreMutation(val status: ProviderDiscoveryOperationStatus = ProviderDiscoveryOperationStatus.Unsupported,
    val tags: List<String>? = null)
/** Used only for comparison with server-supplied names; the server owns genre normalization. */
fun discoveryGenreKey(value: String): String = value.lowercase().replace(Regex("[\\s_-]+"), "")
