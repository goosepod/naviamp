package app.naviamp.ios

import app.naviamp.storage.PassthroughStorageCredentialProtector
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalForeignApi::class)
class NaviampIosApplicationOwnerTest {
    @Test
    fun nativeApplicationIsReusedAcrossSceneAcquisitionsAndClosedOnlyAtShutdown() {
        val directory = NSTemporaryDirectory() + "naviamp-scene-owner-" + NSUUID().UUIDString
        assertTrue(NSFileManager.defaultManager.createDirectoryAtPath(
            directory, withIntermediateDirectories = true, attributes = null, error = null,
        ))
        val owner = NaviampIosApplicationOwner(directory, PassthroughStorageCredentialProtector)
        try {
            val car = owner.application()
            val phone = owner.application()
            assertSame(car, phone)
            assertSame(car, owner.application(), "Reconnecting a scene must retain the native application")
            var tabs: List<NaviampIosVehicleBrowseTab>? = null
            var revision = -1L
            val browse = car.vehicleBrowseSession(100, 3) { value, projection ->
                revision = value
                tabs = projection
            }
            try {
                // Exercise real Foundation main-queue dispatch and packaged shared translations.
                repeat(500) {
                    if (tabs == null) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
                }
                val projected = assertNotNull(tabs)
                assertEquals(listOf("Home", "Playlists", "Radio"), projected.map { it.id })
                assertTrue(projected.all { it.title.isNotBlank() && it.status.isNotBlank() && it.rows.isEmpty() })
                val completions = mutableListOf<Boolean>()
                browse.select("Home", "missing", revision, completions::add)
                assertEquals(listOf(false), completions)
            } finally { browse.close() }
            browse.close()
            assertSame(car, owner.application(), "Car browse detach must retain the runtime")
            val firstView = phone.viewController()
            val secondView = phone.viewController()
            assertNotSame(firstView, secondView, "Each phone scene needs its own native controller")
            phone.releaseViewController(firstView)
            assertSame(car, owner.application(), "Releasing a phone controller must not close playback")
            phone.releaseViewController(secondView)
            owner.close()
            owner.close()
            assertFailsWith<IllegalStateException> { owner.application() }
        } finally {
            owner.close()
            assertTrue(directory.startsWith(NSTemporaryDirectory() + "naviamp-scene-owner-"))
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }
}
