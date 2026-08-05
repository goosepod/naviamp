package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class NaviampLegalNoticesTest {
    @Test
    fun noticesExposeBassTermsAndLinkingException() {
        val text = NaviampLegalNotices.joinToString("\n") { "${it.title}\n${it.body}" }

        assertTrue(text.contains("GPLv3 section 7"))
        assertTrue(text.contains("free non-commercial terms"))
        assertTrue(text.contains("Commercial use requires"))
        assertTrue(text.contains("OpenSSL"))
    }
}
