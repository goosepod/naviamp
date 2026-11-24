package app.naviamp.app

import kotlin.test.*

class NaviampRandomBytesSecureTokenSourceTest {
    @Test fun fullEntropyBytesAreEncodedWithoutUrlDelimitersOrPadding() {
        var calls = 0
        val source = NaviampRandomBytesSecureTokenSource { bytes ->
            assertEquals(32, bytes.size)
            bytes.fill(if (calls++ == 0) 0xfb.toByte() else 0xff.toByte())
        }
        val first = source.newToken()
        val second = source.newToken()
        assertEquals(43, first.length)
        assertTrue(first.all { it.isLetterOrDigit() || it in "-_" })
        assertNotEquals(first, second)
        assertEquals(2, calls)
    }
    @Test fun failedRandomnessNeverIssuesAToken() {
        assertFailsWith<IllegalStateException> { NaviampRandomBytesSecureTokenSource { error("OS random unavailable") }.newToken() }
    }
}
