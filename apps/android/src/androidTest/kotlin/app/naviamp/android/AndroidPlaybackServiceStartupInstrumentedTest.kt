package app.naviamp.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.presentation.NaviampExternalPlaybackState
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises the real Android start deadline even when there is no playable snapshot or bridge. */
class AndroidPlaybackServiceStartupInstrumentedTest {
    @Test
    fun rejectedNativeStartReturnsADeferredResultInsteadOfCrashing() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startForegroundService(service: Intent): ComponentName? =
                throw ForegroundServiceStartNotAllowedException("Native background restriction")
        }
        assertFalse(context.startNaviampPlaybackService())
    }

    @Test
    fun unrelatedNativeFailuresAreNotSilenced() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startForegroundService(service: Intent): ComponentName? = error("Unexpected native failure")
        }
        assertFailsWith<IllegalStateException> { context.startNaviampPlaybackService() }
    }

    @Test
    fun foregroundStartAcknowledgesAnIdleSnapshot() = assertAcknowledgment(withoutBridge = false)

    @Test
    fun foregroundStartAcknowledgesAnUnavailableBridge() = assertAcknowledgment(withoutBridge = true)

    /** Check the actual service notification and Android's media-session exemption on API 33+. */
    @SuppressLint("NotificationPermission")
    @Test
    fun mediaSessionNotificationCanBeUpdatedWithoutNotificationPermission() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // The app deliberately does not request this permission. Never modify a user's grants.
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        ActivityScenario.launch(MainActivity::class.java).use {
            val originalBridge = AndroidNaviampPlaybackRuntime.bridge()!!
            assumeTrue(originalBridge.snapshot().state == NaviampExternalPlaybackState.Idle)
            SystemClock.sleep(1_500)
            context.stopService(AndroidNaviampPlaybackService.refreshIntent(context))
            instrumentation.waitForIdleSync()
            AndroidNaviampPlaybackRuntime.uninstall(originalBridge)
            val manager = context.getSystemService(NotificationManager::class.java)
            try {
                context.startForegroundService(AndroidNaviampPlaybackService.foregroundStartIntent(context))
                val deadline = SystemClock.elapsedRealtime() + 5_000
                var posted = manager.activeNotifications.firstOrNull { it.notification.channelId == "naviamp-playback-v2" }
                while (posted == null && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(25)
                    posted = manager.activeNotifications.firstOrNull { it.notification.channelId == "naviamp-playback-v2" }
                }
                val initial = checkNotNull(posted) { "Media-session notification was not posted" }
                assertEquals(Notification.MediaStyle::class.java.name, initial.notification.extras.getString(Notification.EXTRA_TEMPLATE))
                assertTrue(initial.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))
                val updated = Notification.Builder.recoverBuilder(context, initial.notification)
                    .setContentText("Notification exemption regression")
                    .build()
                manager.notify(initial.id, updated)
                val updateDeadline = SystemClock.elapsedRealtime() + 5_000
                while (manager.activeNotifications.none {
                    it.id == initial.id && it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == "Notification exemption regression"
                } && SystemClock.elapsedRealtime() < updateDeadline) SystemClock.sleep(25)
                assertTrue(manager.activeNotifications.any {
                    it.id == initial.id && it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == "Notification exemption regression"
                }, "Android rejected the media-session notification update without POST_NOTIFICATIONS")
                assertTrue(isForeground(context))
            } finally {
                AndroidNaviampPlaybackRuntime.install(originalBridge)
                context.stopService(AndroidNaviampPlaybackService.refreshIntent(context))
            }
        }
    }

    private fun assertAcknowledgment(withoutBridge: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val originalBridge = AndroidNaviampPlaybackRuntime.bridge()!!
            // Never interrupt an existing playback session in a non-disposable installation.
            assumeTrue(originalBridge.snapshot().state == NaviampExternalPlaybackState.Idle)
            SystemClock.sleep(1_500) // Let the initial shared idle-retention decision settle.
            context.stopService(AndroidNaviampPlaybackService.refreshIntent(context))
            instrumentation.waitForIdleSync()
            if (withoutBridge) AndroidNaviampPlaybackRuntime.uninstall(originalBridge)
            try {
                context.startForegroundService(
                    AndroidNaviampPlaybackService.foregroundStartIntent(context),
                )
                val deadline = SystemClock.elapsedRealtime() + 2_000
                while (!isForeground(context) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
                assertTrue(isForeground(context), "Accepted start was not acknowledged with a foreground notification")
                // Observe beyond Android's startup timeout; a live PID alone before it is insufficient.
                SystemClock.sleep(25_000)
                assertTrue(isForeground(context), "Foreground service disappeared during the startup deadline")
                if (!withoutBridge) assertEquals(NaviampExternalPlaybackState.Idle, originalBridge.snapshot().state)
            } finally {
                AndroidNaviampPlaybackRuntime.install(originalBridge)
                context.stopService(AndroidNaviampPlaybackService.refreshIntent(context))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isForeground(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == AndroidNaviampPlaybackService::class.java.name && it.foreground }
}
