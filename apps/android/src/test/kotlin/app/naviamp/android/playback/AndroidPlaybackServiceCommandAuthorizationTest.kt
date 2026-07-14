package app.naviamp.android.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPlaybackServiceCommandAuthorizationTest {
    @Test
    fun rejectsMissingOrIncorrectCapability() {
        assertFalse(isAuthorizedPlaybackServiceCommand(null, "expected"))
        assertFalse(isAuthorizedPlaybackServiceCommand("incorrect", "expected"))
    }

    @Test
    fun acceptsMatchingCapability() {
        assertTrue(isAuthorizedPlaybackServiceCommand("expected", "expected"))
    }
}
