package app.naviamp.domain.provider

const val ProviderIdNavidrome = "navidrome"
const val ProviderIdSubsonic = "subsonic"
const val ProviderIdJellyfin = "jellyfin"
const val ProviderIdBandcamp = "bandcamp"

const val BandcampSubsonicServerUrl = "https://bandcamp.com/api/subsonic"

enum class ProviderProtocolFamily {
    Subsonic,
    Jellyfin,
    Unknown,
}

enum class ProviderConnectionIcon {
    Navidrome,
    Subsonic,
    Jellyfin,
    Bandcamp,
}

enum class ProviderAvailability {
    Available,
    ComingSoon,
}

data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val protocolFamily: ProviderProtocolFamily,
    val icon: ProviderConnectionIcon,
    val availability: ProviderAvailability,
    val connectionGuidance: String,
    val fixedServerUrl: String? = null,
) {
    val selectable: Boolean
        get() = availability == ProviderAvailability.Available
}

val NaviampProviderCatalog: List<ProviderDescriptor> = listOf(
    ProviderDescriptor(
        id = ProviderIdNavidrome,
        displayName = "Navidrome",
        protocolFamily = ProviderProtocolFamily.Subsonic,
        icon = ProviderConnectionIcon.Navidrome,
        availability = ProviderAvailability.Available,
        connectionGuidance = "Connect to a Navidrome music server.",
    ),
    ProviderDescriptor(
        id = ProviderIdSubsonic,
        displayName = "Subsonic",
        protocolFamily = ProviderProtocolFamily.Subsonic,
        icon = ProviderConnectionIcon.Subsonic,
        availability = ProviderAvailability.Available,
        connectionGuidance = "Connect to a compatible Subsonic or OpenSubsonic server.",
    ),
    ProviderDescriptor(
        id = ProviderIdJellyfin,
        displayName = "Jellyfin",
        protocolFamily = ProviderProtocolFamily.Jellyfin,
        icon = ProviderConnectionIcon.Jellyfin,
        availability = ProviderAvailability.Available,
        connectionGuidance = "Connect to a Jellyfin music library.",
    ),
    ProviderDescriptor(
        id = ProviderIdBandcamp,
        displayName = "Bandcamp",
        protocolFamily = ProviderProtocolFamily.Subsonic,
        icon = ProviderConnectionIcon.Bandcamp,
        availability = ProviderAvailability.Available,
        connectionGuidance = "Use the Subsonic credentials generated in Bandcamp Fan Settings.",
        fixedServerUrl = BandcampSubsonicServerUrl,
    ),
)

fun providerDescriptor(providerId: String?): ProviderDescriptor {
    val normalizedId = providerId?.trim()?.lowercase().orEmpty()
    if (normalizedId.isEmpty()) return NaviampProviderCatalog.first()
    return NaviampProviderCatalog.firstOrNull { descriptor -> descriptor.id == normalizedId }
        ?: ProviderDescriptor(
            id = normalizedId,
            displayName = normalizedId,
            protocolFamily = ProviderProtocolFamily.Unknown,
            icon = ProviderConnectionIcon.Subsonic,
            availability = ProviderAvailability.ComingSoon,
            connectionGuidance = "This saved provider type is not supported by this version of Naviamp.",
        )
}

fun normalizedProviderId(providerId: String?): String = providerDescriptor(providerId).id
