@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.apple.media

import app.naviamp.presentation.*
import app.naviamp.ui.NaviampExternalPlaybackAction
import app.naviamp.ui.NaviampExternalPlaybackLabels
import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImagePNGRepresentation
import kotlin.test.*

class ApplePlaybackSurfaceTest {
    private val labels = NaviampExternalPlaybackLabels("Queue", mapOf(NaviampExternalPlaybackAction.Favorite to "Translated favorite"))

    @Test fun realMediaPlayerMetadataCommandFlagsAndImageDecodeAreClearedOnClose() {
        assertTrue(NSThread.isMainThread)
        val surface = ApplePlaybackSurface()
        try {
            surface.attach({ false }, {})
            val image = UIGraphicsImageRenderer(size = CGSizeMake(2.0, 2.0)).imageWithActions {}
            val data = assertNotNull(UIImagePNGRepresentation(image))
            val bytes = assertNotNull(data.bytes).reinterpret<ByteVar>().readBytes(data.length.toInt())
            val decoded = assertNotNull(surface.decodeArtwork(bytes))
            val snapshot = NaviampExternalPlaybackSnapshot(
                current = NaviampExternalMediaItem("media", "Native title", "Native artist",
                    description = "Track details", albumTitle = "Native album"),
                state = NaviampExternalPlaybackState.Playing, durationMillis = 180_000, positionMillis = 12_500,
                canPlayPause = true, canSeek = false, canShuffle = false, canRepeat = false, canFavorite = true,
            )
            surface.publish(snapshot, snapshot.surfaceActions(), decoded, labels)
            val info = assertNotNull(MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo)
            assertEquals("Native title", info[MPMediaItemPropertyTitle])
            assertEquals("Native artist", info[MPMediaItemPropertyArtist])
            assertEquals("Native album", info[MPMediaItemPropertyAlbumTitle])
            assertNotNull(info[MPMediaItemPropertyArtwork])
            val commands = MPRemoteCommandCenter.sharedCommandCenter()
            assertTrue(commands.playCommand.enabled)
            assertTrue(commands.pauseCommand.enabled)
            assertTrue(commands.togglePlayPauseCommand.enabled)
            assertFalse(commands.changePlaybackPositionCommand.enabled)
            assertFalse(commands.changeShuffleModeCommand.enabled)
            assertFalse(commands.changeRepeatModeCommand.enabled)
            assertEquals("Translated favorite", commands.likeCommand.localizedTitle)
            surface.publish(snapshot.copy(current = snapshot.current!!.copy(albumTitle = "")), snapshot.surfaceActions(), decoded, labels)
            assertNull(MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo?.get(MPMediaItemPropertyAlbumTitle))
            assertNull(surface.decodeArtwork(byteArrayOf()))
            assertNull(surface.decodeArtwork(byteArrayOf(1, 2, 3)))
        } finally { surface.close() }
        surface.close()
        assertNull(MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo)
        assertFalse(MPRemoteCommandCenter.sharedCommandCenter().playCommand.enabled)
        assertFalse(MPRemoteCommandCenter.sharedCommandCenter().togglePlayPauseCommand.enabled)
        assertFalse(MPRemoteCommandCenter.sharedCommandCenter().likeCommand.enabled)
    }

    @Test fun registeredRemoteCallbacksTranslateCommandsAndInvalidateOnClose() {
        check(NSThread.isMainThread)
        val callbacks = mutableMapOf<MPRemoteCommand, (MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus>()
        val requests = mutableListOf<NaviampExternalPlaybackSurfaceCommand>()
        var accept = true
        var fixturePosition: Double? = null
        var fixtureShuffle: MPShuffleType? = null
        var fixtureRepeat: MPRepeatType? = null
        val payloads = object : AppleRemoteCommandPayloads {
            override fun positionSeconds(event: MPRemoteCommandEvent?) = fixturePosition ?: AppleNativeRemoteCommandPayloads.positionSeconds(event)
            override fun shuffleType(event: MPRemoteCommandEvent?) = fixtureShuffle ?: AppleNativeRemoteCommandPayloads.shuffleType(event)
            override fun repeatType(event: MPRemoteCommandEvent?) = fixtureRepeat ?: AppleNativeRemoteCommandPayloads.repeatType(event)
        }
        val surface = ApplePlaybackSurface(remotePayloads = payloads, registerRemoteCommand = { native, handler ->
            check(callbacks.put(native, handler) == null)
            native.addTargetWithHandler(handler)
        })
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        fun send(native: MPRemoteCommand, event: MPRemoteCommandEvent?, expected: NaviampExternalPlaybackSurfaceCommand) {
            val before = requests.size
            assertEquals(MPRemoteCommandHandlerStatusSuccess, callbacks.getValue(native)(event))
            assertEquals(before + 1, requests.size)
            assertEquals(expected, requests.last())
        }
        try {
            surface.attach({ requests += it; accept }, {})
            assertEquals(10, callbacks.size)
            val advertised = NaviampExternalPlaybackSnapshot(current = NaviampExternalMediaItem("commands", "Commands", "Fixture"),
                canPlayPause = true, canSeek = true, hasPrevious = true, hasNext = true,
                canShuffle = true, canRepeat = true, canFavorite = true)
            surface.publish(advertised, advertised.surfaceActions(), null, labels)
            callbacks.keys.forEach { assertTrue(it.enabled) }
            surface.publish(NaviampExternalPlaybackSnapshot(), emptySet(), null, labels)
            callbacks.keys.forEach { assertFalse(it.enabled) }
            surface.publish(advertised, advertised.surfaceActions(), null, labels)
            for ((native, expected) in listOf(center.playCommand to NaviampExternalPlaybackSurfaceCommand.Play,
                center.pauseCommand to NaviampExternalPlaybackSurfaceCommand.Pause,
                center.togglePlayPauseCommand to NaviampExternalPlaybackSurfaceCommand.Toggle,
                center.stopCommand to NaviampExternalPlaybackSurfaceCommand.Stop,
                center.previousTrackCommand to NaviampExternalPlaybackSurfaceCommand.Previous,
                center.nextTrackCommand to NaviampExternalPlaybackSurfaceCommand.Next,
                center.likeCommand to NaviampExternalPlaybackSurfaceCommand.Favorite)) send(native, null, expected)
            // Event objects are system-created. Explicit fixture facts exercise mapping without forging one.
            fixturePosition = 12.75
            send(center.changePlaybackPositionCommand, null, NaviampExternalPlaybackSurfaceCommand.Seek(12.75))
            fixturePosition = null
            for (type in listOf(MPShuffleType.MPShuffleTypeOff, MPShuffleType.MPShuffleTypeItems, MPShuffleType.MPShuffleTypeCollections)) {
                fixtureShuffle = type
                send(center.changeShuffleModeCommand, null, NaviampExternalPlaybackSurfaceCommand.Shuffle(type != MPShuffleType.MPShuffleTypeOff))
            }
            fixtureShuffle = null
            for ((type, mode) in listOf(MPRepeatType.MPRepeatTypeOff to app.naviamp.ui.NaviampRepeatMode.Off,
                MPRepeatType.MPRepeatTypeOne to app.naviamp.ui.NaviampRepeatMode.Track,
                MPRepeatType.MPRepeatTypeAll to app.naviamp.ui.NaviampRepeatMode.Queue)) {
                fixtureRepeat = type
                send(center.changeRepeatModeCommand, null, NaviampExternalPlaybackSurfaceCommand.Repeat(mode))
            }
            fixtureRepeat = null
            for (native in listOf(center.changePlaybackPositionCommand, center.changeShuffleModeCommand, center.changeRepeatModeCommand)) {
                val before = requests.size
                assertEquals(MPRemoteCommandHandlerStatusCommandFailed, callbacks.getValue(native)(null))
                assertEquals(before, requests.size)
            }
            accept = false
            assertEquals(MPRemoteCommandHandlerStatusCommandFailed, callbacks.getValue(center.playCommand)(null))
            fixturePosition = Double.NaN
            assertEquals(MPRemoteCommandHandlerStatusCommandFailed, callbacks.getValue(center.changePlaybackPositionCommand)(null))
            fixturePosition = null
        } finally { surface.close() }
        val before = requests.size
        for ((native, callback) in callbacks) {
            assertFalse(native.enabled)
            assertEquals(MPRemoteCommandHandlerStatusCommandFailed, callback(null))
        }
        assertEquals(before, requests.size)
        // A new registration works, while callbacks retained from the old lifetime stay inert.
        var replacementCallback: ((MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus)? = null
        var replacementCalls = 0
        val replacement = ApplePlaybackSurface(registerRemoteCommand = { native, handler ->
            if (native == center.playCommand) replacementCallback = handler
            native.addTargetWithHandler(handler)
        })
        try {
            replacement.attach({ replacementCalls++; true }, {})
            val snapshot = NaviampExternalPlaybackSnapshot(current = NaviampExternalMediaItem("replacement", "Replacement", "Fixture"), canPlayPause = true)
            replacement.publish(snapshot, snapshot.surfaceActions(), null, labels)
            surface.close()
            assertTrue(center.playCommand.enabled)
            assertEquals(MPRemoteCommandHandlerStatusCommandFailed, callbacks.getValue(center.playCommand)(null))
            assertEquals(MPRemoteCommandHandlerStatusSuccess, assertNotNull(replacementCallback)(null))
            assertEquals(1, replacementCalls)
        } finally { replacement.close() }
        // Partial native registration can also be cleaned up before a subsequent attach.
        val partialCallbacks = mutableListOf<(MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus>()
        val partial = ApplePlaybackSurface(registerRemoteCommand = { native, handler ->
            if (partialCallbacks.size == 2) error("Expected fixture registration failure")
            partialCallbacks += handler
            native.addTargetWithHandler(handler)
        })
        try { assertFailsWith<IllegalStateException> { partial.attach({ error("Closed partial callback executed") }, {}) } }
        finally { partial.close() }
        assertEquals(2, partialCallbacks.size)
        partialCallbacks.forEach { assertEquals(MPRemoteCommandHandlerStatusCommandFailed, it(null)) }
        println("remote command native callbacks verified")
    }

    @Test fun realAudioSessionNotificationsTranslateFactsAndObserversDoNotSurviveClose() {
        assertTrue(NSThread.isMainThread)
        println("audio lifecycle native adapter executed")
        val modern = appleAudioSessionUsesLifecycleNotifications()
        val events = mutableListOf<NaviampExternalPlaybackAudioEvent>()
        val surface = ApplePlaybackSurface()
        val center = NSNotificationCenter.defaultCenter
        try {
            surface.attach({ false }, events::add)
            center.postNotificationName(AVAudioSessionInterruptionNotification, null,
                mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionTypeBegan)))
            center.postNotificationName(AVAudioSessionInterruptionNotification, null,
                mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionTypeEnded),
                    AVAudioSessionInterruptionOptionKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionOptionShouldResume)))
            center.postNotificationName(AVAudioSessionRouteChangeNotification, null,
                mapOf(AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLongLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable)))
            center.postNotificationName(AVAudioSessionInterruptionNotification, null,
                mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionTypeEnded)))
            center.postNotificationName(AVAudioSessionInterruptionNotification, null,
                mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionTypeEnded),
                    AVAudioSessionInterruptionOptionKey to NSNumber(unsignedLongLong = 0uL)))
            // Unknown/missing/wrong-type payloads and unrelated route changes are not playback intents.
            center.postNotificationName(AVAudioSessionInterruptionNotification, null, null)
            center.postNotificationName(AVAudioSessionInterruptionNotification, null, mapOf(AVAudioSessionInterruptionTypeKey to "invalid"))
            center.postNotificationName(AVAudioSessionInterruptionNotification, null,
                mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = 999uL)))
            for (reason in listOf(AVAudioSessionRouteChangeReasonNewDeviceAvailable, AVAudioSessionRouteChangeReasonOverride, 999uL)) {
                center.postNotificationName(AVAudioSessionRouteChangeNotification, null,
                    mapOf(AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLongLong = reason)))
            }
            if (modern) {
                center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.active, AVAudioSession.sharedInstance(), null)
                center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.inactive, AVAudioSession.sharedInstance(), null)
                center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.resumption, AVAudioSession.sharedInstance(), mapOf("invalid" to 1))
                assertEquals(listOf(NaviampExternalPlaybackAudioEvent.OutputDisconnected, NaviampExternalPlaybackAudioEvent.SessionBecameActive), events,
                    "OS 27 must not subscribe to legacy interruption notifications")
            } else assertEquals(listOf(NaviampExternalPlaybackAudioEvent.InterruptionBegan,
                NaviampExternalPlaybackAudioEvent.InterruptionEnded(true), NaviampExternalPlaybackAudioEvent.OutputDisconnected,
                NaviampExternalPlaybackAudioEvent.InterruptionEnded(false), NaviampExternalPlaybackAudioEvent.InterruptionEnded(false)), events)
        } finally { surface.close() }
        center.postNotificationName(AVAudioSessionInterruptionNotification, null,
            mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = AVAudioSessionInterruptionTypeBegan)))
        center.postNotificationName(AVAudioSessionRouteChangeNotification, null,
            mapOf(AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLongLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable)))
        if (modern) center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.active, AVAudioSession.sharedInstance(), null)
        assertEquals(if (modern) 2 else 5, events.size)
    }

    @Test fun lifecycleAvailabilityAndObserverDisposalRespectTheRuntimeProtocol() {
        check(NSThread.isMainThread)
        val modern = appleAudioSessionUsesLifecycleNotifications()
        val events = mutableListOf<NaviampExternalPlaybackAudioEvent>()
        val surface = ApplePlaybackSurface()
        val audio = AVAudioSession.sharedInstance()
        fun pump() = NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        try {
            surface.attach({ false }, events::add)
            check(audio.setCategory(AVAudioSessionCategoryPlayback, error = null))
            check(audio.setActive(true, error = null))
            check(audio.setActive(false, error = null))
            if (modern) {
                assertNotNull(AppleAudioSessionLifecycleNotificationNames.active)
                assertNotNull(AppleAudioSessionLifecycleNotificationNames.inactive)
                assertNotNull(AppleAudioSessionLifecycleNotificationNames.resumption)
                if (NSProcessInfo.processInfo.environment["NAVIAMP_OS27_REQUIRE_CONTEXT"] == "1") {
                    val deadline = NSDate.dateWithTimeIntervalSinceNow(3.0)
                    while (events.none { it is NaviampExternalPlaybackAudioEvent.SessionBecameInactive } && deadline.timeIntervalSinceNow > 0) pump()
                    assertTrue(NaviampExternalPlaybackAudioEvent.SessionBecameActive in events)
                    assertTrue(events.any { it is NaviampExternalPlaybackAudioEvent.SessionBecameInactive && it.source == NaviampAudioSessionDeactivationSource.Application })
                    println("OS27 typed application deactivation verified")
                }
                NSNotificationCenter.defaultCenter.postNotificationName(AppleAudioSessionLifecycleNotificationNames.active, audio, null)
                assertTrue(NaviampExternalPlaybackAudioEvent.SessionBecameActive in events)
                println("OS27 notification availability and observer disposal verified")
            } else {
                assertNull(AppleAudioSessionLifecycleNotificationNames.active)
                assertNull(AppleAudioSessionLifecycleNotificationNames.inactive)
                assertNull(AppleAudioSessionLifecycleNotificationNames.resumption)
                assertTrue(events.none { it is NaviampExternalPlaybackAudioEvent.SessionBecameInactive })
                println("pre27 weak-link compatibility verified")
            }
            surface.close()
            val closedCount = events.size
            check(audio.setActive(true, error = null)); check(audio.setActive(false, error = null)); pump()
            if (modern) NSNotificationCenter.defaultCenter.postNotificationName(AppleAudioSessionLifecycleNotificationNames.active, audio, null)
            assertEquals(closedCount, events.size)
        } finally { surface.close(); audio.setActive(false, error = null) }
    }
}
