package app.naviamp.domain.connect

private const val InstanceIdKey = "id"
private const val DisplayNameKey = "name"
private const val MinimumProtocolKey = "pmin"
private const val MaximumProtocolKey = "pmax"
private const val CapabilitiesKey = "caps"
private const val FingerprintKey = "fp"

/** Stable, minimal DNS-SD TXT representation. Network endpoints and local expiry are not TXT data. */
object NaviampConnectDiscoveryMetadata {
    fun encode(advertisement: NaviampConnectAdvertisement): Map<String, String> = mapOf(
        InstanceIdKey to advertisement.instanceId,
        DisplayNameKey to advertisement.displayName,
        MinimumProtocolKey to advertisement.protocolRange.minimum.toString(),
        MaximumProtocolKey to advertisement.protocolRange.maximum.toString(),
        CapabilitiesKey to advertisement.capabilities
            .map(NaviampConnectCapability::discoveryToken)
            .sorted()
            .joinToString(","),
        FingerprintKey to advertisement.identityFingerprint,
    )

    fun decode(
        attributes: Map<String, String>,
        port: Int,
        expiresAtEpochMillis: Long,
    ): NaviampConnectAdvertisement? = runCatching {
        NaviampConnectAdvertisement(
            instanceId = attributes.getValue(InstanceIdKey),
            displayName = attributes.getValue(DisplayNameKey),
            protocolRange = NaviampConnectProtocolRange(
                minimum = attributes.getValue(MinimumProtocolKey).toInt(),
                maximum = attributes.getValue(MaximumProtocolKey).toInt(),
            ),
            capabilities = attributes[CapabilitiesKey]
                .orEmpty()
                .split(',')
                .filter(String::isNotBlank)
                .mapNotNull(::naviampConnectCapabilityForDiscoveryToken)
                .toSet(),
            port = port,
            identityFingerprint = attributes.getValue(FingerprintKey),
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }.getOrNull()
}

private fun NaviampConnectCapability.discoveryToken(): String = when (this) {
    NaviampConnectCapability.TransportControls -> "transport"
    NaviampConnectCapability.Seeking -> "seek"
    NaviampConnectCapability.Favorites -> "favorite"
    NaviampConnectCapability.Repeat -> "repeat"
    NaviampConnectCapability.Shuffle -> "shuffle"
    NaviampConnectCapability.QueueRead -> "queue_read"
    NaviampConnectCapability.QueueSelect -> "queue_select"
    NaviampConnectCapability.QueueEdit -> "queue_edit"
    NaviampConnectCapability.QueueReorder -> "queue_reorder"
    NaviampConnectCapability.CatalogPlayback -> "catalog"
    NaviampConnectCapability.InternetRadio -> "radio"
    NaviampConnectCapability.DisplayControl -> "display"
    NaviampConnectCapability.QueueHandoff -> "handoff"
    NaviampConnectCapability.ConnectionProvisioning -> "provision"
}

private fun naviampConnectCapabilityForDiscoveryToken(token: String): NaviampConnectCapability? = when (token) {
    "transport" -> NaviampConnectCapability.TransportControls
    "seek" -> NaviampConnectCapability.Seeking
    "favorite" -> NaviampConnectCapability.Favorites
    "repeat" -> NaviampConnectCapability.Repeat
    "shuffle" -> NaviampConnectCapability.Shuffle
    "queue_read" -> NaviampConnectCapability.QueueRead
    "queue_select" -> NaviampConnectCapability.QueueSelect
    "queue_edit" -> NaviampConnectCapability.QueueEdit
    "queue_reorder" -> NaviampConnectCapability.QueueReorder
    "catalog" -> NaviampConnectCapability.CatalogPlayback
    "radio" -> NaviampConnectCapability.InternetRadio
    "display" -> NaviampConnectCapability.DisplayControl
    "handoff" -> NaviampConnectCapability.QueueHandoff
    "provision" -> NaviampConnectCapability.ConnectionProvisioning
    else -> null
}
