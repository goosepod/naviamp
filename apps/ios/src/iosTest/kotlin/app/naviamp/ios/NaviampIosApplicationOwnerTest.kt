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
    fun phoneFirstLaunchAndRepeatedCarReattachmentsRetainTheNativeRuntime() {
        val directory = NSTemporaryDirectory() + "naviamp-scene-owner-" + NSUUID().UUIDString
        assertTrue(NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null))
        val owner = NaviampIosApplicationOwner(directory, PassthroughStorageCredentialProtector)
        try {
            val phone = owner.application()
            val view = phone.viewController()
            var discardedCallbacks = 0
            val discarded = phone.vehicleBrowseSession(100, 5) { discardedCallbacks++ }
            discarded.close()
            repeat(3) { NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01)) }
            assertEquals(0, discardedCallbacks, "A scene closed before asynchronous creation must not publish")
            repeat(12) { index ->
                assertSame(phone, owner.application())
                var projection: NaviampIosVehicleBrowseState? = null
                var callbacks = 0
                val car = owner.application().vehicleBrowseSession(100, 5) { projection = it; callbacks++ }
                repeat(500) {
                    if (projection == null) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
                }
                val value = assertNotNull(projection)
                assertTrue(!value.nowPlaying && !value.queueAvailable)
                assertEquals(listOf("Home", "Playlists", "Radio", "Library", "Downloads"), value.tabs.map { it.id })
                car.close(); car.close()
                val closedCount = callbacks
                car.openQueue(); car.openCurrentAlbum(value.revision)
                val completions = mutableListOf<Boolean>()
                car.select("Home", "stale", value.revision, completions::add)
                assertEquals(listOf(false), completions)
                repeat(3) { NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01)) }
                assertEquals(closedCount, callbacks)
                if (index == 0) phone.releaseViewController(view)
            }
            assertSame(phone, owner.application())
            owner.close(); owner.close()
            assertFailsWith<IllegalStateException> { owner.application() }
        } finally {
            owner.close()
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }

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
            var projection: NaviampIosVehicleBrowseState? = null
            var revision = -1L
            val browse = car.vehicleBrowseSession(100, 3) { value ->
                revision = value.revision
                projection = value
            }
            try {
                // Exercise real Foundation main-queue dispatch and packaged shared translations.
                repeat(500) {
                    if (projection == null) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
                }
                val snapshot = assertNotNull(projection)
                val projected = snapshot.tabs
                assertTrue(snapshot.pages.isEmpty())
                assertTrue(!snapshot.queueAvailable, "A fresh application must not expose a placeholder as queued media")
                assertTrue(!snapshot.nowPlaying, "Acquiring a scene must not request playback presentation")
                assertTrue(snapshot.presentationAvailable)
                assertTrue(!snapshot.currentAlbumAvailable, "Idle playback must not offer album navigation")
                browse.openCurrentAlbum(snapshot.revision)
                assertTrue(snapshot.playback.buttons.isEmpty(), "Idle playback must not invent native controls")
                assertEquals(null, snapshot.playback.alert)
                browse.selectPlaybackAction("StartRadio", snapshot.playback.revision)
                browse.selectPlaybackAction("unknown", snapshot.playback.revision)
                browse.dismissPlaybackAlert(-1)
                assertEquals(listOf("Home", "Playlists", "Radio"), projected.map { it.id })
                assertTrue(projected.all { it.title.isNotBlank() && it.status.isNotBlank() })
                assertTrue(projected.drop(1).all { it.rows.isEmpty() })
                val connections = projected.first().rows.single()
                assertEquals("naviamp:vehicle:connections", connections.id)
                assertTrue(connections.enabled)
                assertTrue(connections.title.isNotBlank())
                val connectionCompletions = mutableListOf<Boolean>()
                browse.select("Home", connections.id, snapshot.revision, connectionCompletions::add)
                assertEquals(listOf(false), connectionCompletions)
                repeat(500) {
                    if (projection?.pages.isNullOrEmpty()) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
                }
                val picker = assertNotNull(projection).pages.single()
                assertEquals(connections.title, picker.title)
                assertTrue(picker.status.isNotBlank(), "A source-free application explains phone setup")
                assertTrue(picker.rows.isNotEmpty())
                assertTrue(picker.rows.all { !it.enabled && it.artworkId == null })
                browse.selectPage(picker.id, picker.rows.first().id, assertNotNull(projection).revision, connectionCompletions::add)
                assertEquals(listOf(false, false), connectionCompletions)
                browse.dismissPages(listOf(picker.id))
                val completions = mutableListOf<Boolean>()
                browse.select("Home", "missing", revision, completions::add)
                assertEquals(listOf(false), completions)
                browse.selectPage("missing-page", "missing", revision, completions::add)
                assertEquals(listOf(false, false), completions)
                var artworkCompletions = 0
                browse.requestArtwork("missing-artwork") { assertEquals(null, it); artworkCompletions++ }
                assertEquals(1, artworkCompletions)
                browse.openQueue()
                browse.dismissPages(listOf("missing-page"))
            } finally { browse.close() }
            browse.close()
            var closedArtworkCompletions = 0
            browse.requestArtwork("missing-artwork") { assertEquals(null, it); closedArtworkCompletions++ }
            assertEquals(1, closedArtworkCompletions)
            var libraryProjection: NaviampIosVehicleBrowseState? = null
            val libraryBrowse = car.vehicleBrowseSession(100, 4, supportsSearch = true) { libraryProjection = it }
            try {
                repeat(500) {
                    if (libraryProjection == null) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
                }
                val snapshot = assertNotNull(libraryProjection)
                assertEquals(listOf("Home", "Playlists", "Radio", "Library"), snapshot.tabs.map { it.id })
                assertTrue(snapshot.tabs.last().title.isNotBlank())
                assertTrue(snapshot.tabs.last().rows.isEmpty(), "A disconnected source must not invent library media")
                val completions = mutableListOf<Boolean>()
                libraryBrowse.select("Library", "missing", snapshot.revision, completions::add)
                assertEquals(listOf(false), completions)
                assertTrue(snapshot.pages.none { it.search })
                var searchCompletions = 0
                libraryBrowse.updateSearch("missing", "query") { results ->
                    searchCompletions++
                    assertTrue(results.rows.isEmpty())
                }
                assertEquals(1, searchCompletions)
                libraryBrowse.selectSearch("missing", "missing", -1, completions::add)
                assertEquals(listOf(false, false), completions)
                libraryBrowse.submitSearch("missing")
            } finally { libraryBrowse.close() }
            var downloadsProjection: NaviampIosVehicleBrowseState? = null
            val downloadsBrowse = car.vehicleBrowseSession(100, 5) { downloadsProjection = it }
            try {
                repeat(500) {
                    if (downloadsProjection == null) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
                }
                val snapshot = assertNotNull(downloadsProjection)
                assertEquals(listOf("Home", "Playlists", "Radio", "Library", "Downloads"), snapshot.tabs.map { it.id })
                assertTrue(snapshot.tabs.last().title.isNotBlank())
                assertTrue(snapshot.tabs.last().rows.isEmpty(), "No source must not expose another source's files")
                val completions = mutableListOf<Boolean>()
                downloadsBrowse.select("Downloads", "missing", snapshot.revision, completions::add)
                assertEquals(listOf(false), completions)
            } finally { downloadsBrowse.close() }
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
