package app.naviamp.ios

import app.naviamp.storage.PassthroughStorageCredentialProtector
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

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
