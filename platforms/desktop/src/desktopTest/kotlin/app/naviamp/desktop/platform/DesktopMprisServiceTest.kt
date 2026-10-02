package app.naviamp.desktop.platform

import app.naviamp.presentation.NaviampExternalMediaItem
import app.naviamp.presentation.NaviampExternalPlaybackControl
import app.naviamp.presentation.NaviampExternalPlaybackSnapshot
import app.naviamp.presentation.NaviampExternalPlaybackState
import app.naviamp.ui.NaviampRepeatMode
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import app.naviamp.presentation.NaviampExternalPlaybackRegistrationController
import app.naviamp.presentation.maintainExternalPlaybackRegistration
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopMprisServiceTest {
    @Test
    fun malformedPropertiesAreRejectedAndFixedRateCanBeWritten() {
        val control = RecordingExternalPlaybackControl()
        val exported = DesktopMprisObject(control)
        assertFailsWith<IllegalArgumentException> { exported.Set(MprisPlayerInterface, "Volume", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { exported.Set(MprisPlayerInterface, "Shuffle", "true") }
        exported.Set(MprisPlayerInterface, "Rate", 1.0)
        assertTrue(control.commands.isEmpty())
        exported.Set(MprisPlayerInterface, "Rate", 0.0)
        assertEquals(listOf("pause"), control.commands)
    }

    @Test
    fun unavailableBusFailsRegistrationWithoutAffectingPlayback() {
        val control = RecordingExternalPlaybackControl()
        assertTrue(runCatching {
            registerMpris(control) {
                DBusConnectionBuilder.forAddress("unix:path=/tmp/naviamp-nonexistent-session-bus").withShared(false).build()
            }
        }.isFailure)
        assertTrue(control.commands.isEmpty())
    }

    @Test
    fun registrationRecoversAfterNameLossAndConnectionLossAndReleasesOnShutdown() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("NAVIAMP_MPRIS_INTEGRATION") == "true")
        val control = RecordingExternalPlaybackControl()
        val snapshots = MutableStateFlow(control.snapshot())
        val connections = CopyOnWriteArrayList<org.freedesktop.dbus.connections.impl.DBusConnection>()
        val client = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        val job = kotlinx.coroutines.CoroutineScope(coroutineContext + Dispatchers.IO)
            .maintainExternalPlaybackRegistration(snapshots, NaviampExternalPlaybackRegistrationController(50L, 100L)) {
                registerMpris(control) {
                    DBusConnectionBuilder.forSessionBus().withShared(false).build().also { connections += it }
                }
            }
        try {
            withContext(Dispatchers.IO) { await { connections.size == 1 && MprisBusName in connections[0].names } }
            val firstOwner = client.getDBusOwnerName(MprisBusName)
            connections[0].releaseBusName(MprisBusName)
            withContext(Dispatchers.IO) { await { connections.size >= 2 && MprisBusName in connections.last().names } }
            assertNotEquals(firstOwner, client.getDBusOwnerName(MprisBusName))
            connections.last().close()
            withContext(Dispatchers.IO) { await { connections.size >= 3 && MprisBusName in connections.last().names } }
            assertTrue(control.commands.isEmpty())
        } finally {
            job.cancelAndJoin()
            assertTrue(connections.all { !it.isConnected })
            await { runCatching { client.getDBusOwnerName(MprisBusName) }.isFailure }
            client.close()
        }
    }

    @Test
    fun mapsCoreSnapshotToMprisPropertiesAndStableTrackPath() {
        val current = NaviampExternalMediaItem(
            mediaId = "naviamp:queue-track:track-1",
            title = "Title",
            subtitle = "Artist",
            description = "Album",
            albumTitle = "Album",
            artworkUrl = "https://example.test/art",
        )
        val snapshot = NaviampExternalPlaybackSnapshot(
            state = NaviampExternalPlaybackState.Playing,
            current = current,
            queue = listOf(current),
            currentQueueIndex = 0,
            positionMillis = 12_000L,
            durationMillis = 180_000L,
            canPlayPause = true,
            canSeek = true,
            canChangeVolume = true,
            volumePercent = 72,
            shuffleActive = true,
            repeatMode = NaviampRepeatMode.Queue,
        )

        val properties = playerProperties(snapshot)
        val metadata = properties.getValue("Metadata").value as Map<*, *>

        assertEquals("Playing", properties.getValue("PlaybackStatus").value)
        assertEquals("Playlist", properties.getValue("LoopStatus").value)
        assertEquals(0.72, properties.getValue("Volume").value)
        assertEquals(12_000_000L, properties.getValue("Position").value)
        assertEquals("Title", (metadata["xesam:title"] as Variant<*>).value)
        assertEquals(listOf("Artist"), (metadata["xesam:artist"] as Variant<*>).value)
        assertEquals("Album", (metadata["xesam:album"] as Variant<*>).value)
        assertEquals("https://example.test/art", (metadata["mpris:artUrl"] as Variant<*>).value)
        assertEquals(180_000_000L, (metadata["mpris:length"] as Variant<*>).value)
        val synthetic = playerProperties(snapshot.copy(current = current.copy(artworkUrl = "naviamp-radio-tile://tile")))
        assertFalse("mpris:artUrl" in (synthetic.getValue("Metadata").value as Map<*, *>))
        assertEquals(trackObjectPath(current.mediaId), trackObjectPath(current.mediaId))
        assertNotEquals(trackObjectPath(current.mediaId), trackObjectPath("another-track"))
    }

    @Test
    fun translatesMprisMethodsAndWritablePropertiesToCoreControl() {
        val control = RecordingExternalPlaybackControl()
        val objectUnderTest = DesktopMprisObject(control)
        val trackPath = trackObjectPath(control.snapshot().current?.mediaId)

        objectUnderTest.PlayPause()
        objectUnderTest.Next()
        objectUnderTest.Seek(5_500_000L)
        objectUnderTest.SetPosition(DBusPath(trackPath), 9_250_000L)
        objectUnderTest.Set(MprisPlayerInterface, "Shuffle", Variant(true))
        objectUnderTest.Set(MprisPlayerInterface, "LoopStatus", Variant("Track"))
        objectUnderTest.Set(MprisPlayerInterface, "Volume", Variant(0.42))
        objectUnderTest.Set(MprisPlayerInterface, "Volume", Variant(0.29))

        assertEquals(
            listOf("playPause", "next", "seekBy:5500:true", "seekTo:9250", "shuffle:true", "repeat:Track", "volume:42", "volume:29"),
            control.commands,
        )
    }

    @Test
    fun ignoresSetPositionForAStaleTrackAndAdvertisesNoQuitSupport() {
        val control = RecordingExternalPlaybackControl()
        val objectUnderTest = DesktopMprisObject(control)

        objectUnderTest.SetPosition(DBusPath(trackObjectPath("stale")), 5_000_000L)

        assertTrue(control.commands.isEmpty())
        assertFalse(rootProperties().getValue("CanQuit").value as Boolean)
    }

    @Test
    fun registersAndPublishesThroughARealSessionBus() {
        org.junit.Assume.assumeTrue(System.getenv("NAVIAMP_MPRIS_INTEGRATION") == "true")
        val control = RecordingExternalPlaybackControl()
        val server = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        val client = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        val contender = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        try {
            val exported = DesktopMprisObject(control, server)
            server.exportObject(MprisObjectPath, exported)
            server.requestBusName(MprisBusName)

            val properties = client.getRemoteObject(MprisBusName, MprisObjectPath, Properties::class.java)
            val player = client.getRemoteObject(MprisBusName, MprisObjectPath, DesktopMprisPlayer::class.java)
            assertEquals("Stopped", properties.GetAll(MprisPlayerInterface).getValue("PlaybackStatus").value)
            assertEquals("Stopped", properties.Get<String>(MprisPlayerInterface, "PlaybackStatus"))
            System.getenv("NAVIAMP_PLAYERCTL")?.let { executable ->
                val process = ProcessBuilder(executable, "-l").redirectErrorStream(true).start()
                assertTrue(process.inputStream.bufferedReader().readText().lineSequence().any { it == "naviamp" })
                assertEquals(0, process.waitFor())
                val command = ProcessBuilder(executable, "-p", "naviamp", "next").redirectErrorStream(true).start()
                val commandOutput = command.inputStream.bufferedReader().readText()
                assertEquals(0, command.waitFor(), commandOutput)
                await { "next" in control.commands }
            }
            player.PlayPause()
            await { "playPause" in control.commands }
            assertTrue(runCatching { contender.requestBusName(MprisBusName) }.isFailure)
            assertTrue(MprisBusName in server.names)

            val propertiesChanged = CountDownLatch(1)
            val seeked = CountDownLatch(1)
            val propertySignals = CopyOnWriteArrayList<Properties.PropertiesChanged>()
            client.addSigHandler(Properties.PropertiesChanged::class.java) { signal ->
                propertySignals += signal
                propertiesChanged.countDown()
            }
            client.addSigHandler(DesktopMprisPlayer.Seeked::class.java) { seeked.countDown() }
            val initial = control.snapshot().copy(state = NaviampExternalPlaybackState.Playing, positionMillis = 1_000L)
            exported.publish(initial)
            assertTrue(propertiesChanged.await(5L, TimeUnit.SECONDS))
            val initialSignalCount = propertySignals.size
            exported.publish(initial.copy(positionMillis = 1_500L))
            exported.publish(initial.copy(positionMillis = 2_000L))
            assertFalse(seeked.await(250L, TimeUnit.MILLISECONDS))
            assertEquals(initialSignalCount, propertySignals.size)
            exported.publish(initial.copy(positionMillis = 2_500L, seekGeneration = 1L))

            assertTrue(seeked.await(5L, TimeUnit.SECONDS))
            assertTrue(propertySignals.all { "Position" !in it.propertiesChanged })
        } finally {
            contender.close()
            client.close()
            server.close()
        }
    }
}

private fun await(condition: () -> Boolean) {
    repeat(500) {
        if (condition()) return
        Thread.sleep(10L)
    }
    assertTrue(condition())
}

private class RecordingExternalPlaybackControl : NaviampExternalPlaybackControl {
    val commands = CopyOnWriteArrayList<String>()
    private val current = NaviampExternalMediaItem("track", "Title", "Artist")
    private val snapshot = NaviampExternalPlaybackSnapshot(
        current = current,
        queue = listOf(current),
        canPlayPause = true,
        canSeek = true,
        canChangeVolume = true,
        hasNext = true,
        hasPrevious = true,
    )

    override fun snapshot() = snapshot
    override fun play() { commands += "play" }
    override fun pause() { commands += "pause" }
    override fun playPause() { commands += "playPause" }
    override fun stop() { commands += "stop" }
    override fun previous() { commands += "previous" }
    override fun next() { commands += "next" }
    override fun seekTo(positionMillis: Long) { commands += "seekTo:$positionMillis" }
    override fun seekBy(deltaMillis: Long, advancePastEnd: Boolean) { commands += "seekBy:$deltaMillis:$advancePastEnd" }
    override fun setShuffleActive(active: Boolean) { commands += "shuffle:$active" }
    override fun setRepeatMode(mode: NaviampRepeatMode) { commands += "repeat:$mode" }
    override fun setVolumePercent(percent: Int) { commands += "volume:$percent" }
}
