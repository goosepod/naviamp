package app.naviamp.presentation

import app.naviamp.domain.provider.MediaProvider

/** Provider decorators may be recreated for each lookup; compare the server/library identity. */
internal fun NaviampCoreMediaProviderSource.isCurrent(provider: MediaProvider): Boolean =
    current()?.let { active ->
        active.id == provider.id && active.cacheNamespace == provider.cacheNamespace &&
            active.selectedMusicFolderIds.toSet() == provider.selectedMusicFolderIds.toSet()
    } == true

/** Discovery can change accounts without changing the primary playback/library source. */
internal fun NaviampCoreMediaProviderSource.isCurrentDiscovery(provider: MediaProvider): Boolean =
    isCurrent(provider) && current()?.let { active ->
        active.discoveryAccountScope == provider.discoveryAccountScope &&
            (active.discovery != null) == (provider.discovery != null)
    } == true
