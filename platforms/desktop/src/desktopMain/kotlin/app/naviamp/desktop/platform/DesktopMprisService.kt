package app.naviamp.desktop.platform

import app.naviamp.presentation.NaviampCoreExternalPlaybackBridge
import app.naviamp.presentation.NaviampExternalPlaybackControl
import app.naviamp.presentation.NaviampExternalPlaybackPublicationPlanner
import app.naviamp.presentation.NaviampExternalPlaybackRegistration
import app.naviamp.presentation.NaviampExternalPlaybackSnapshot
import app.naviamp.presentation.NaviampExternalPlaybackState
import app.naviamp.presentation.maintainExternalPlaybackRegistration
import app.naviamp.ui.NaviampRepeatMode
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.IDisconnectCallback
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

/** Linux D-Bus lifetime and ABI adapter for Core-owned external playback. */
class DesktopMprisService private constructor(
    scope: CoroutineScope,
    bridge: NaviampCoreExternalPlaybackBridge,
) : AutoCloseable {
    private val job = CoroutineScope(scope.coroutineContext + Dispatchers.IO)
        .maintainExternalPlaybackRegistration(bridge.snapshots) {
            registerMpris(bridge)
        }

    override fun close() { job.cancel() }

    companion object {
        fun start(
            scope: CoroutineScope,
            bridge: NaviampCoreExternalPlaybackBridge,
            osName: String = System.getProperty("os.name"),
        ): DesktopMprisService? {
            if (!osName.contains("linux", ignoreCase = true)) return null
            return DesktopMprisService(scope, bridge)
        }
    }
}

/** Owns only the D-Bus connection, export, and callback lifetime. */
internal fun registerMpris(
    bridge: NaviampExternalPlaybackControl,
    openConnection: () -> DBusConnection = {
        DBusConnectionBuilder.forSessionBus().withShared(false).build()
    },
): NaviampExternalPlaybackRegistration {
    val connection = openConnection()
    try {
        val lost = CompletableDeferred<Unit>()
        connection.setDisconnectCallback(object : IDisconnectCallback {
            override fun disconnectOnError(exception: IOException) { lost.complete(Unit) }
            override fun clientDisconnect() { lost.complete(Unit) }
            override fun requestedDisconnect(remainingConnections: Int?) { lost.complete(Unit) }
        })
        connection.addSigHandler(DBus.NameLost::class.java) { signal ->
            if (signal.name == MprisBusName) lost.complete(Unit)
        }
        val exported = DesktopMprisObject(bridge, connection)
        connection.exportObject(MprisObjectPath, exported)
        connection.requestBusName(MprisBusName)
        if (!connection.isConnected || MprisBusName !in connection.names) lost.complete(Unit)
        return object : NaviampExternalPlaybackRegistration {
            override suspend fun awaitLoss() { lost.await() }
            override fun publish(snapshot: NaviampExternalPlaybackSnapshot) = exported.publish(snapshot)
            override fun close() = connection.close()
        }
    } catch (failure: Exception) {
        connection.runCatching { close() }
        throw failure
    }
}

@DBusInterfaceName(MprisRootInterface)
interface DesktopMprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName(MprisPlayerInterface)
interface DesktopMprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)

    class Seeked(path: String, val position: Long) : DBusSignal(path, position)
}

internal class DesktopMprisObject(
    private val bridge: NaviampExternalPlaybackControl,
    private val connection: DBusConnection? = null,
) : DesktopMprisRoot, DesktopMprisPlayer, Properties {
    private val publicationPlanner = NaviampExternalPlaybackPublicationPlanner()

    override fun getObjectPath(): String = MprisObjectPath

    override fun Raise() = Unit
    override fun Quit() = Unit
    override fun Next() = bridge.next()
    override fun Previous() = bridge.previous()
    override fun Pause() = bridge.pause()
    override fun PlayPause() = bridge.playPause()
    override fun Stop() = bridge.stop()
    override fun Play() = bridge.play()
    override fun Seek(offset: Long) = bridge.seekBy(offset / MicrosecondsPerMillisecond, advancePastEnd = true)

    override fun SetPosition(trackId: DBusPath, position: Long) {
        val current = bridge.snapshot().current ?: return
        if (position >= 0L && trackId.path == trackObjectPath(current.mediaId)) {
            bridge.seekTo(position / MicrosecondsPerMillisecond)
        }
    }

    override fun OpenUri(uri: String) = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (properties(interfaceName)[propertyName]
            ?: throw IllegalArgumentException("Unknown MPRIS property $interfaceName.$propertyName")) as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        val unwrapped = (value as? Variant<*>)?.value ?: value
        when (interfaceName to propertyName) {
            MprisPlayerInterface to "LoopStatus" -> bridge.setRepeatMode(
                when (unwrapped as? String) {
                    "Track" -> NaviampRepeatMode.Track
                    "Playlist" -> NaviampRepeatMode.Queue
                    "None" -> NaviampRepeatMode.Off
                    else -> throw IllegalArgumentException("Invalid MPRIS loop status: $unwrapped")
                },
            )
            MprisPlayerInterface to "Shuffle" -> bridge.setShuffleActive(
                unwrapped as? Boolean ?: throw IllegalArgumentException("Shuffle requires a boolean"),
            )
            MprisPlayerInterface to "Volume" -> {
                val volume = (unwrapped as? Number)?.toDouble()
                    ?.takeIf(Double::isFinite)
                    ?: throw IllegalArgumentException("Volume requires a finite number")
                bridge.setVolumePercent((volume * 100.0).roundToInt())
            }
            MprisPlayerInterface to "Rate" -> {
                require(unwrapped is Number && unwrapped.toDouble().isFinite())
                if (unwrapped.toDouble() == 0.0) bridge.pause()
                // Only normal speed is supported, as advertised by MinimumRate/MaximumRate.
            }
            MprisRootInterface to "Fullscreen" -> {
                require(unwrapped is Boolean)
                // CanSetFullscreen is false.
            }
            else -> throw IllegalArgumentException("MPRIS property is not writable: $interfaceName.$propertyName")
        }
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties(interfaceName)

    fun publish(next: NaviampExternalPlaybackSnapshot) {
        val publication = publicationPlanner.plan(next)
        val activeConnection = connection ?: return
        if (publication.sessionContent || publication.playbackState) {
            activeConnection.sendMessage(
                Properties.PropertiesChanged(
                    MprisObjectPath,
                    MprisPlayerInterface,
                    playerProperties(next) - "Position",
                    emptyList(),
                ),
            )
        }
        publication.seekedPositionMillis?.let { position ->
            activeConnection.sendMessage(
                DesktopMprisPlayer.Seeked(MprisObjectPath, position * MicrosecondsPerMillisecond),
            )
        }
    }

    internal fun properties(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        MprisRootInterface -> rootProperties()
        MprisPlayerInterface -> playerProperties(bridge.snapshot())
        else -> emptyMap()
    }
}

internal fun rootProperties(): Map<String, Variant<*>> = linkedMapOf(
    "CanQuit" to Variant(false),
    "Fullscreen" to Variant(false),
    "CanSetFullscreen" to Variant(false),
    "CanRaise" to Variant(false),
    "HasTrackList" to Variant(false),
    "Identity" to Variant("Naviamp"),
    "DesktopEntry" to Variant("naviamp"),
    "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
    "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
)

internal fun playerProperties(snapshot: NaviampExternalPlaybackSnapshot): Map<String, Variant<*>> = linkedMapOf(
    "PlaybackStatus" to Variant(
        when (snapshot.state) {
            NaviampExternalPlaybackState.Playing -> "Playing"
            NaviampExternalPlaybackState.Paused -> "Paused"
            NaviampExternalPlaybackState.Idle, NaviampExternalPlaybackState.Loading -> "Stopped"
        },
    ),
    "LoopStatus" to Variant(
        when (snapshot.repeatMode) {
            NaviampRepeatMode.Off -> "None"
            NaviampRepeatMode.Queue -> "Playlist"
            NaviampRepeatMode.Track -> "Track"
        },
    ),
    "Rate" to Variant(1.0),
    "Shuffle" to Variant(snapshot.shuffleActive),
    "Metadata" to Variant(metadata(snapshot), "a{sv}"),
    "Volume" to Variant(snapshot.volumePercent / 100.0),
    "Position" to Variant((snapshot.positionMillis ?: 0L) * MicrosecondsPerMillisecond),
    "MinimumRate" to Variant(1.0),
    "MaximumRate" to Variant(1.0),
    "CanGoNext" to Variant(snapshot.hasNext),
    "CanGoPrevious" to Variant(snapshot.hasPrevious),
    "CanPlay" to Variant(snapshot.canPlayPause),
    "CanPause" to Variant(snapshot.canPlayPause),
    "CanSeek" to Variant(snapshot.canSeek),
    "CanControl" to Variant(snapshot.canControl),
)

private fun metadata(snapshot: NaviampExternalPlaybackSnapshot): Map<String, Variant<*>> {
    val current = snapshot.current ?: return mapOf("mpris:trackid" to Variant(DBusPath(NoTrackObjectPath)))
    return buildMap {
        put("mpris:trackid", Variant(DBusPath(trackObjectPath(current.mediaId))))
        put("xesam:title", Variant(current.title))
        put("xesam:artist", Variant(listOf(current.subtitle), "as"))
        current.albumTitle.takeIf(String::isNotBlank)?.let { put("xesam:album", Variant(it)) }
        current.externalArtworkUrl?.takeIf(String::isNotBlank)?.let { put("mpris:artUrl", Variant(it)) }
        snapshot.durationMillis?.let { put("mpris:length", Variant(it * MicrosecondsPerMillisecond)) }
    }
}

internal fun trackObjectPath(mediaId: String?): String = mediaId?.let { id ->
    val digest = MessageDigest.getInstance("SHA-256").digest(id.encodeToByteArray())
    MprisTrackPathPrefix + digest.joinToString("") { byte -> "%02x".format(byte) }
} ?: NoTrackObjectPath

internal const val MprisBusName = "org.mpris.MediaPlayer2.naviamp"
internal const val MprisObjectPath = "/org/mpris/MediaPlayer2"
internal const val MprisRootInterface = "org.mpris.MediaPlayer2"
internal const val MprisPlayerInterface = "org.mpris.MediaPlayer2.Player"
private const val MprisTrackPathPrefix = "/app/naviamp/track/"
private const val NoTrackObjectPath = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
private const val MicrosecondsPerMillisecond = 1_000L
