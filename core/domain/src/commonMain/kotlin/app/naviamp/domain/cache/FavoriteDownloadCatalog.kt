package app.naviamp.domain.cache

import app.naviamp.domain.provider.MediaPage
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MaximumMediaPageSize

sealed interface FavoriteDownloadCatalog<out T> {
    data class Complete<T>(val items: List<T>) : FavoriteDownloadCatalog<T>
    data class TooLarge(val maximumItems: Int) : FavoriteDownloadCatalog<Nothing>
    data object Unsupported : FavoriteDownloadCatalog<Nothing>
}

/** Enumerates an entire favorite collection or refuses it; a truncated list must not be subscribed. */
suspend fun <T> enumerateFavoriteDownloadCatalog(
    maximumItems: Int,
    loadPage: suspend (MediaPageRequest) -> MediaPage<T>?,
): FavoriteDownloadCatalog<T> {
    require(maximumItems > 0) { "Maximum item count must be positive." }
    val items = mutableListOf<T>()
    var request = MediaPageRequest(limit = minOf(MaximumMediaPageSize, maximumItems))
    val visitedOffsets = mutableSetOf<Int>()
    while (visitedOffsets.add(request.offset)) {
        val page = loadPage(request) ?: return FavoriteDownloadCatalog.Unsupported
        require(page.offset == request.offset && page.limit == request.limit) {
            "Favorite page metadata must match the requested offset and limit."
        }
        require(page.items.size <= request.limit) { "Favorite page exceeded the requested limit." }
        require(page.items.isNotEmpty() || !page.hasMore) { "Favorite page cannot advance without items." }
        items += page.items
        if (!page.hasMore) return FavoriteDownloadCatalog.Complete(items)
        if (items.size >= maximumItems) {
            // Some providers only know there is no next page after an empty fetch.
            val probeRequest = requireNotNull(page.nextRequest).copy(limit = 1)
            val probe = loadPage(probeRequest) ?: return FavoriteDownloadCatalog.Unsupported
            require(probe.offset == probeRequest.offset && probe.limit == probeRequest.limit) {
                "Favorite page metadata must match the requested offset and limit."
            }
            return if (probe.items.isEmpty() && !probe.hasMore) {
                FavoriteDownloadCatalog.Complete(items)
            } else {
                FavoriteDownloadCatalog.TooLarge(maximumItems)
            }
        }
        request = requireNotNull(page.nextRequest).copy(limit = minOf(MaximumMediaPageSize, maximumItems - items.size))
    }
    error("Favorite pages repeated an offset.")
}
