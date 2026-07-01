package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavidromeRateLimitBackoffTest {
    @Test
    fun retryAfterSecondsBacksOffUntilRetryTime() {
        var now = 1_000L
        val backoff = NavidromeRateLimitBackoff(nowMillis = { now })

        val error = backoff.record(429, "12")

        assertNotNull(error)
        assertEquals(13_000L, error.retryAtEpochMillis)
        assertTrue(error.message.orEmpty().contains("12 seconds"))

        now = 12_999L
        assertNotNull(backoff.activeExceptionOrNull())

        now = 13_000L
        assertNull(backoff.activeExceptionOrNull())
    }

    @Test
    fun retryAfterHttpDateBacksOffUntilRetryTime() {
        var now = 1_445_412_475_000L
        val backoff = NavidromeRateLimitBackoff(nowMillis = { now })

        val error = backoff.record(429, "Wed, 21 Oct 2015 07:28:00 GMT")

        assertNotNull(error)
        assertEquals(1_445_412_480_000L, error.retryAtEpochMillis)
        assertNotNull(backoff.activeExceptionOrNull())

        now = 1_445_412_480_000L
        assertNull(backoff.activeExceptionOrNull())
    }

    @Test
    fun missingRetryAfterUsesFallbackBackoff() {
        val backoff = NavidromeRateLimitBackoff(nowMillis = { 5_000L })

        val error = backoff.record(429, null)

        assertNotNull(error)
        assertEquals(65_000L, error.retryAtEpochMillis)
        assertTrue(error.message.orEmpty().contains("60 seconds"))
    }

    @Test
    fun nonRateLimitStatusDoesNotBackOff() {
        val backoff = NavidromeRateLimitBackoff(nowMillis = { 5_000L })

        assertNull(backoff.record(500, "30"))
        assertNull(backoff.activeExceptionOrNull())
    }
}
