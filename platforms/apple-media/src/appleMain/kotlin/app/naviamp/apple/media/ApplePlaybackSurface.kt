@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.apple.media

import app.naviamp.presentation.*
import app.naviamp.ui.NaviampExternalPlaybackAction
import app.naviamp.ui.NaviampExternalPlaybackLabels
import app.naviamp.ui.NaviampRepeatMode
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.*
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.UIKit.UIImage

/** MediaPlayer/AVAudioSession/UIKit ABI only. Requests, state, scheduling and retries stay in Core. */
class ApplePlaybackSurface(
    private val lifecyclePayloads: AppleAudioSessionLifecyclePayloads = AppleNativeAudioSessionLifecyclePayloads,
    private val remotePayloads: AppleRemoteCommandPayloads = AppleNativeRemoteCommandPayloads,
    private val registerRemoteCommand: (MPRemoteCommand, (MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus) -> Any =
        { native, handler -> native.addTargetWithHandler(handler) },
) : NaviampExternalPlaybackSurface<UIImage> {
    private val notifications = NSNotificationCenter.defaultCenter
    private val info = MPNowPlayingInfoCenter.defaultCenter()
    private val commands = MPRemoteCommandCenter.sharedCommandCenter()
    private val observers = mutableListOf<Any>()
    private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()
    private var attached = false
    private var closed = false
    private var nativeImage: UIImage? = null
    private var nativeArtwork: MPMediaItemArtwork? = null

    override fun attach(command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean,
                        audioEvent: (NaviampExternalPlaybackAudioEvent) -> Unit) {
        check(!attached && !closed)
        attached = true
        if (appleAudioSessionUsesLifecycleNotifications()) {
            observe(AppleAudioSessionLifecycleNotificationNames.active) { audioEvent(NaviampExternalPlaybackAudioEvent.SessionBecameActive) }
            observe(AppleAudioSessionLifecycleNotificationNames.inactive) { notification ->
                lifecyclePayloads.deactivation(notification)?.let(audioEvent)
            }
            observe(AppleAudioSessionLifecycleNotificationNames.resumption) { notification ->
                lifecyclePayloads.resumption(notification)?.let { audioEvent(NaviampExternalPlaybackAudioEvent.ResumptionRecommended(it)) }
            }
        } else observe(AVAudioSessionInterruptionNotification) { notification ->
            when (notification.number(AVAudioSessionInterruptionTypeKey)?.unsignedIntegerValue) {
                AVAudioSessionInterruptionTypeBegan -> audioEvent(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
                AVAudioSessionInterruptionTypeEnded -> audioEvent(NaviampExternalPlaybackAudioEvent.InterruptionEnded(
                    (notification.number(AVAudioSessionInterruptionOptionKey)?.unsignedIntegerValue ?: 0uL) and
                        AVAudioSessionInterruptionOptionShouldResume != 0uL))
            }
        }
        observe(AVAudioSessionRouteChangeNotification) { notification ->
            if (notification.number(AVAudioSessionRouteChangeReasonKey)?.unsignedIntegerValue == AVAudioSessionRouteChangeReasonOldDeviceUnavailable)
                audioEvent(NaviampExternalPlaybackAudioEvent.OutputDisconnected)
        }
        add(commands.playCommand, command) { NaviampExternalPlaybackSurfaceCommand.Play }
        add(commands.pauseCommand, command) { NaviampExternalPlaybackSurfaceCommand.Pause }
        add(commands.togglePlayPauseCommand, command) { NaviampExternalPlaybackSurfaceCommand.Toggle }
        add(commands.stopCommand, command) { NaviampExternalPlaybackSurfaceCommand.Stop }
        add(commands.previousTrackCommand, command) { NaviampExternalPlaybackSurfaceCommand.Previous }
        add(commands.nextTrackCommand, command) { NaviampExternalPlaybackSurfaceCommand.Next }
        add(commands.changePlaybackPositionCommand, command) { event ->
            remotePayloads.positionSeconds(event)?.let { NaviampExternalPlaybackSurfaceCommand.Seek(it) }
        }
        add(commands.changeShuffleModeCommand, command) { event ->
            remotePayloads.shuffleType(event)?.let {
                NaviampExternalPlaybackSurfaceCommand.Shuffle(it != MPShuffleType.MPShuffleTypeOff)
            }
        }
        add(commands.changeRepeatModeCommand, command) { event ->
            remotePayloads.repeatType(event)?.let {
                NaviampExternalPlaybackSurfaceCommand.Repeat(when (it) {
                    MPRepeatType.MPRepeatTypeOne -> NaviampRepeatMode.Track
                    MPRepeatType.MPRepeatTypeAll -> NaviampRepeatMode.Queue
                    else -> NaviampRepeatMode.Off
                })
            }
        }
        add(commands.likeCommand, command) { NaviampExternalPlaybackSurfaceCommand.Favorite }
    }

    override fun decodeArtwork(bytes: ByteArray): UIImage? =
        if (bytes.isEmpty()) null else bytes.usePinned { UIImage.imageWithData(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())) }

    override fun publish(snapshot: NaviampExternalPlaybackSnapshot, actions: Set<NaviampExternalPlaybackSurfaceAction>,
                         artwork: UIImage?, labels: NaviampExternalPlaybackLabels) {
        check(attached && !closed)
        commands.playCommand.enabled = NaviampExternalPlaybackSurfaceAction.Play in actions
        commands.pauseCommand.enabled = NaviampExternalPlaybackSurfaceAction.Pause in actions
        commands.togglePlayPauseCommand.enabled = NaviampExternalPlaybackSurfaceAction.Play in actions
        commands.stopCommand.enabled = NaviampExternalPlaybackSurfaceAction.Stop in actions
        commands.previousTrackCommand.enabled = NaviampExternalPlaybackSurfaceAction.Previous in actions
        commands.nextTrackCommand.enabled = NaviampExternalPlaybackSurfaceAction.Next in actions
        commands.changePlaybackPositionCommand.enabled = NaviampExternalPlaybackSurfaceAction.Seek in actions
        commands.changeShuffleModeCommand.enabled = NaviampExternalPlaybackSurfaceAction.Shuffle in actions
        commands.changeRepeatModeCommand.enabled = NaviampExternalPlaybackSurfaceAction.Repeat in actions
        commands.likeCommand.enabled = NaviampExternalPlaybackSurfaceAction.Favorite in actions
        commands.likeCommand.active = snapshot.favorite
        commands.likeCommand.localizedTitle = labels.actions.getValue(NaviampExternalPlaybackAction.Favorite)
        commands.likeCommand.localizedShortTitle = labels.actions.getValue(NaviampExternalPlaybackAction.Favorite)
        commands.changeShuffleModeCommand.currentShuffleType = if (snapshot.shuffleActive) MPShuffleType.MPShuffleTypeItems else MPShuffleType.MPShuffleTypeOff
        commands.changeRepeatModeCommand.currentRepeatType = when (snapshot.repeatMode) {
            NaviampRepeatMode.Off -> MPRepeatType.MPRepeatTypeOff
            NaviampRepeatMode.Queue -> MPRepeatType.MPRepeatTypeAll
            NaviampRepeatMode.Track -> MPRepeatType.MPRepeatTypeOne
        }
        if (artwork !== nativeImage) {
            nativeImage = artwork
            nativeArtwork = artwork?.let { image -> MPMediaItemArtwork(image.size) { image } }
        }
        val current = snapshot.current
        info.nowPlayingInfo = current?.let { item -> mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to item.title,
            MPMediaItemPropertyArtist to item.subtitle,
            MPNowPlayingInfoPropertyExternalContentIdentifier to item.mediaId,
            MPNowPlayingInfoPropertyMediaType to MPNowPlayingInfoMediaTypeAudio,
            MPNowPlayingInfoPropertyPlaybackRate to if (snapshot.state == NaviampExternalPlaybackState.Playing) 1.0 else 0.0,
        ).apply {
            item.albumTitle.takeIf(String::isNotBlank)?.let { this[MPMediaItemPropertyAlbumTitle] = it }
            snapshot.durationMillis?.let { this[MPMediaItemPropertyPlaybackDuration] = it / 1_000.0 }
            snapshot.positionMillis?.let { this[MPNowPlayingInfoPropertyElapsedPlaybackTime] = it / 1_000.0 }
            nativeArtwork?.let { this[MPMediaItemPropertyArtwork] = it }
        } }
        info.playbackState = when (snapshot.state) {
            NaviampExternalPlaybackState.Playing -> MPNowPlayingPlaybackStatePlaying
            NaviampExternalPlaybackState.Paused, NaviampExternalPlaybackState.Loading -> MPNowPlayingPlaybackStatePaused
            NaviampExternalPlaybackState.Idle -> MPNowPlayingPlaybackStateStopped
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        observers.forEach(notifications::removeObserver); observers.clear()
        targets.forEach { (command, target) -> command.removeTarget(target); command.enabled = false }; targets.clear()
        nativeImage = null; nativeArtwork = null
        if (attached) {
            info.nowPlayingInfo = null
            info.playbackState = MPNowPlayingPlaybackStateStopped
        }
    }

    private fun observe(name: String?, handler: (NSNotification) -> Unit) {
        observers += notifications.addObserverForName(name, null, NSOperationQueue.mainQueue) {
            if (!closed) it?.let(handler)
        }
    }

    private fun add(native: MPRemoteCommand, command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean,
                    translate: (MPRemoteCommandEvent?) -> NaviampExternalPlaybackSurfaceCommand?) {
        val target = registerRemoteCommand(native) { event ->
            val request = translate(event)
            if (!closed && request != null && command(request)) MPRemoteCommandHandlerStatusSuccess else MPRemoteCommandHandlerStatusCommandFailed
        }
        targets += native to target
    }

    private fun NSNotification.number(key: String?): NSNumber? = userInfo?.get(key) as? NSNumber
}
