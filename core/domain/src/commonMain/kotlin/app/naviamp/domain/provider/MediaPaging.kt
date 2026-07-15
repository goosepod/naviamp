package app.naviamp.domain.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DefaultMediaPageSize: Int = 50
const val MaximumMediaPageSize: Int = 200

data class MediaPageRequest(
    val offset: Int = 0,
    val limit: Int = DefaultMediaPageSize,
) {
    init {
        require(offset >= 0) { "Media page offset must not be negative." }
        require(limit in 1..MaximumMediaPageSize) {
            "Media page limit must be between 1 and $MaximumMediaPageSize."
        }
    }
}

data class MediaPage<T>(
    val items: List<T>,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
) {
    val nextRequest: MediaPageRequest?
        get() = if (hasMore) {
            MediaPageRequest(offset = offset + items.size, limit = limit)
        } else {
            null
        }
}

fun <T> MediaPageRequest.toMediaPage(items: List<T>): MediaPage<T> {
    require(items.size <= limit) { "A media page cannot contain more items than requested." }
    return MediaPage(
        items = items,
        offset = offset,
        limit = limit,
        hasMore = items.size == limit,
    )
}

data class MediaPagerState<T>(
    val items: List<T> = emptyList(),
    val nextRequest: MediaPageRequest? = MediaPageRequest(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class MediaPager<T>(
    private val scope: CoroutineScope,
    private val itemKey: (T) -> String,
    private val loadPage: suspend (MediaPageRequest) -> MediaPage<T>,
    private val initialRequest: MediaPageRequest = MediaPageRequest(),
) {
    private val mutableState = MutableStateFlow(MediaPagerState<T>(nextRequest = initialRequest))
    val state: StateFlow<MediaPagerState<T>> = mutableState.asStateFlow()

    private var activeLoad: Job? = null
    private var generation: Long = 0

    fun refresh() {
        generation += 1
        activeLoad?.cancel()
        mutableState.value = MediaPagerState(nextRequest = initialRequest)
        startLoad(request = initialRequest, replaceItems = true, loadGeneration = generation)
    }

    fun loadNext() {
        val current = mutableState.value
        if (current.isLoading) return
        val request = current.nextRequest ?: return
        startLoad(request = request, replaceItems = false, loadGeneration = generation)
    }

    fun cancel() {
        generation += 1
        activeLoad?.cancel()
        activeLoad = null
        mutableState.value = mutableState.value.copy(isLoading = false)
    }

    private fun startLoad(
        request: MediaPageRequest,
        replaceItems: Boolean,
        loadGeneration: Long,
    ) {
        if (mutableState.value.isLoading) return
        mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
        activeLoad = scope.launch {
            try {
                val page = loadPage(request)
                require(page.offset == request.offset && page.limit == request.limit) {
                    "Provider page metadata must match the requested offset and limit."
                }
                if (loadGeneration != generation) return@launch
                val existingItems = if (replaceItems) emptyList() else mutableState.value.items
                mutableState.value = MediaPagerState(
                    items = (existingItems + page.items).distinctBy(itemKey),
                    nextRequest = page.nextRequest,
                    isLoading = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (loadGeneration != generation) return@launch
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load media.",
                )
            } finally {
                if (loadGeneration == generation) activeLoad = null
            }
        }
    }
}
