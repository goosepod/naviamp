package app.naviamp.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NaviampCoverArtByteSourceTest {
    @Test fun staleApplicationCleanupCannotRemoveTheNewApplicationsLoader() = runTest {
        resetNaviampCoverArtByteLoader()
        val old = bindNaviampCoverArtByteLoader { byteArrayOf(1) }
        val current = bindNaviampCoverArtByteLoader { byteArrayOf(2) }
        try {
            old()
            assertContentEquals(byteArrayOf(2), loadNaviampCoverArtBytes("art"))
            current()
            assertNull(loadNaviampCoverArtBytes("art"))
        } finally { resetNaviampCoverArtByteLoader() }
    }

    @Test fun fallbackRunsOnlyWhenTheProviderReturnsNoBytesAndEmptyBytesRemainExplicit() = runTest {
        var calls = 0
        val fallback: suspend (String) -> ByteArray? = { calls++; byteArrayOf(9) }
        val release = bindNaviampCoverArtByteLoader { if (it == "missing") null else byteArrayOf() }
        try {
            assertContentEquals(byteArrayOf(), loadNaviampCoverArtBytes("empty", fallback))
            assertEquals(0, calls)
            assertContentEquals(byteArrayOf(9), loadNaviampCoverArtBytes("missing", fallback))
            assertEquals(1, calls)
        } finally { release() }
    }
}
