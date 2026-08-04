package app.naviamp.ui

class NaviampSmartPlaylistSourceUnavailableException(
    item: SharedMediaItemUi,
) : IllegalStateException("Playlist ${item.title} is no longer available.")

/**
 * Resolves the host-owned playlist object for a shared smart-playlist command.
 * Missing sources are a visible common failure rather than a host-specific no-op.
 */
fun <T> requireSmartPlaylistActionSource(
    item: SharedMediaItemUi,
    resolve: (String) -> T?,
): T = resolve(item.id) ?: throw NaviampSmartPlaylistSourceUnavailableException(item)
