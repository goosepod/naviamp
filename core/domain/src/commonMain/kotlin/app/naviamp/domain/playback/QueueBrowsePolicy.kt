package app.naviamp.domain.playback

data class QueueBrowsePage(
    val previousPage: Int?,
    val nextPage: Int?,
    val indexedItems: List<Int>,
    val firstOrdinal: Int,
    val lastOrdinal: Int,
    val totalItems: Int,
)

/** Builds a bounded, current-track-first queue page without depending on a platform browse API. */
fun queueBrowsePage(
    queueSize: Int,
    currentIndex: Int,
    requestedPage: Int,
    pageSize: Int,
): QueueBrowsePage {
    require(pageSize > 0) { "pageSize must be positive" }
    if (queueSize <= 0) return QueueBrowsePage(null, null, emptyList(), 0, 0, 0)
    val startIndex = currentIndex.takeIf { it in 0 until queueSize } ?: 0
    val orderedIndices = List(queueSize) { offset -> (startIndex + offset) % queueSize }
    val maximumPage = (queueSize - 1) / pageSize
    val page = requestedPage.coerceIn(0, maximumPage)
    val pageStart = page * pageSize
    val indexedItems = orderedIndices.drop(pageStart).take(pageSize)
    return QueueBrowsePage(
        previousPage = (page - 1).takeIf { page > 0 },
        nextPage = (page + 1).takeIf { page < maximumPage },
        indexedItems = indexedItems,
        firstOrdinal = pageStart + 1,
        lastOrdinal = pageStart + indexedItems.size,
        totalItems = queueSize,
    )
}
