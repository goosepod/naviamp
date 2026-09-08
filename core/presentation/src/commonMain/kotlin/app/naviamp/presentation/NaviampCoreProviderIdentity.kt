package app.naviamp.presentation

import app.naviamp.domain.provider.MediaProvider

/** Provider decorators may be recreated for each lookup; compare the server/library identity. */
internal fun NaviampCoreMediaProviderSource.isCurrent(provider: MediaProvider): Boolean =
    current()?.let { active ->
        active.id == provider.id && active.cacheNamespace == provider.cacheNamespace &&
            active.selectedMusicFolderIds.toSet() == provider.selectedMusicFolderIds.toSet()
    } == true
