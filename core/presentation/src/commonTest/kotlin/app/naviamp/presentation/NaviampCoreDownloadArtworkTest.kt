package app.naviamp.presentation

import app.naviamp.domain.cache.ImageCacheRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NaviampCoreDownloadArtworkTest {
    @Test
    fun downloadedTrackArtworkIsWrittenToThePersistentCache() = runTest {
        val expected = byteArrayOf(1, 2, 3, 4)
        val provider = FakeCoreMediaProvider(ownedArtworkBytes = expected)
        val cache = RecordingImageCache()
        val track = provider.track.copy(coverArtId = "cover-1")

        cacheDownloadedTrackArtwork(provider, listOf(track, track), cache)

        assertEquals(listOf("https://example.test/art/cover-1"), cache.urls)
        assertContentEquals(expected, cache.bytes)
    }
}

private class RecordingImageCache : ImageCacheRepository {
    val urls = mutableListOf<String>()
    var bytes = ByteArray(0)

    override suspend fun cachedImageBytes(url: String): ByteArray? = null

    override suspend fun imageBytes(url: String): ByteArray = error("A fetch callback is required.")

    override suspend fun imageBytes(url: String, fetch: suspend () -> ByteArray): ByteArray {
        urls += url
        return fetch().also { bytes = it }
    }
}
