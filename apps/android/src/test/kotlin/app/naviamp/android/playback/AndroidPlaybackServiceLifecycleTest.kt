package app.naviamp.android.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPlaybackServiceLifecycleTest {
    @Test
    fun serviceOwnershipKeepsPlaybackAliveAcrossUiDetachAndTaskRemoval() {
        assertEquals(
            AndroidPlaybackServiceRetention.KeepAlive,
            androidPlaybackServiceRetention(ownsPlayback = true),
        )
        assertEquals(
            AndroidPlaybackServiceRetention.Stop,
            androidPlaybackServiceRetention(ownsPlayback = false),
        )
    }

    @Test
    fun nullIntentStickyRestartRepublishesNotificationAndMediaSession() {
        val restart = planAndroidPlaybackServiceStart(intentPresent = false)
        val command = planAndroidPlaybackServiceStart(intentPresent = true)

        assertTrue(restart.stickyRestart)
        assertTrue(restart.publishNotification)
        assertTrue(restart.republishMediaSession)
        assertFalse(command.stickyRestart)
        assertTrue(command.publishNotification)
        assertFalse(command.republishMediaSession)
    }
}
