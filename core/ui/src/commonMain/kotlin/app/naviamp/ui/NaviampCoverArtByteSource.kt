package app.naviamp.ui

import kotlinx.coroutines.flow.MutableStateFlow

private class CoverArtBinding(val load: suspend (String) -> ByteArray?)
private val coverArtBinding = MutableStateFlow<CoverArtBinding?>(null)

/** Shared ownership of the provider byte callback; native adapters supply only decoding/fallback effects. */
fun bindNaviampCoverArtByteLoader(loader: suspend (String) -> ByteArray?): () -> Unit {
    val binding = CoverArtBinding(loader)
    coverArtBinding.value = binding
    return { if (coverArtBinding.compareAndSet(binding, null)) resetNaviampCoverArtCache() }
}

fun setNaviampCoverArtByteLoader(loader: suspend (String) -> ByteArray?) { bindNaviampCoverArtByteLoader(loader) }
fun resetNaviampCoverArtByteLoader() { coverArtBinding.value = null; resetNaviampCoverArtCache() }

internal suspend fun loadNaviampCoverArtBytes(
    url: String, fallback: suspend (String) -> ByteArray? = { null },
): ByteArray? = coverArtBinding.value?.load?.invoke(url) ?: fallback(url)
