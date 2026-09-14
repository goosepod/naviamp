package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectSourceIdentity

/** Builds the non-secret identity used to prove two devices address the same provider account. */
internal fun naviampCoreConnectSourceIdentity(
    stateStore: NaviampCoreStateStore,
    providerSource: NaviampCoreMediaProviderSource,
): NaviampConnectSourceIdentity? {
    val shell = stateStore.state.value.shell
    if (!shell.connectionSettings.connection.connected) return null
    val connection = shell.connectionSettings.connection.savedConnections.firstOrNull { it.current }
        ?: return null
    val provider = providerSource.current() ?: return null
    return NaviampConnectSourceIdentity(
        providerId = connection.providerId,
        canonicalServerOrigin = connection.serverUrl,
        accountIdentity = connection.username,
        libraryIds = provider.selectedMusicFolderIds,
    ).normalized()
}
