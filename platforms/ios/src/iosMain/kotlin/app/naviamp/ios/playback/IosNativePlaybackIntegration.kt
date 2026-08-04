@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.playback

import app.naviamp.presentation.NaviampCoreExternalPlaybackBridge
import app.naviamp.presentation.NaviampExternalPlaybackLifecycleCoordinator
import app.naviamp.presentation.NaviampExternalPlaybackSnapshot
import app.naviamp.presentation.NaviampExternalPlaybackState
import app.naviamp.presentation.NaviampExternalPlaybackPublicationPlanner
import app.naviamp.presentation.lifecycleCoordinator
import app.naviamp.ui.NaviampRepeatMode
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.create
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPChangeRepeatModeCommandEvent
import platform.MediaPlayer.MPChangeShuffleModeCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoMediaTypeAudio
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyExternalContentIdentifier
import platform.MediaPlayer.MPNowPlayingInfoPropertyMediaType
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackState
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.MediaPlayer.MPRepeatType
import platform.MediaPlayer.MPShuffleType
import platform.UIKit.UIImage

/**
 * Apple MediaPlayer and AVAudioSession boundary backed entirely by Core playback state and commands.
 */
class IosNativePlaybackIntegration(
    private val scope: CoroutineScope,
    private val bridge: NaviampCoreExternalPlaybackBridge,
    private val artworkBytes: suspend (String) -> ByteArray?,
) {
    private val lifecycle: NaviampExternalPlaybackLifecycleCoordinator = bridge.lifecycleCoordinator()
    private val publicationPlanner = NaviampExternalPlaybackPublicationPlanner()
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val nowPlayingCenter = MPNowPlayingInfoCenter.defaultCenter()
    private val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()
    private val notificationObservers = mutableListOf<Any>()
    private val commandTargets = mutableListOf<Pair<MPRemoteCommand, Any>>()
    private val publicationJob: Job
    private var artworkJob: Job? = null
    private var artworkMediaId: String? = null
    private var artworkUrl: String? = null
    private var artwork: MPMediaItemArtwork? = null

    init {
        installAudioSessionObservers()
        installRemoteCommands()
        publicationJob = scope.launch {
            bridge.snapshots.collect(::publish)
        }
    }

    fun close() {
        publicationJob.cancel()
        notificationObservers.forEach(notificationCenter::removeObserver)
        notificationObservers.clear()
        commandTargets.forEach { (command, target) -> command.removeTarget(target) }
        commandTargets.clear()
        publicationPlanner.reset()
        clearArtwork()
        nowPlayingCenter.nowPlayingInfo = null
        nowPlayingCenter.playbackState = MPNowPlayingPlaybackStateStopped
    }

    private fun installAudioSessionObservers() {
        observe(AVAudioSessionInterruptionNotification, ::handleInterruption)
        observe(AVAudioSessionRouteChangeNotification, ::handleRouteChange)
    }

    private fun observe(name: String?, handler: (NSNotification) -> Unit) {
        notificationObservers += notificationCenter.addObserverForName(
            name = name,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { notification -> notification?.let(handler) },
        )
    }

    private fun handleInterruption(notification: NSNotification) {
        val type = notification.number(AVAudioSessionInterruptionTypeKey)?.unsignedIntegerValue ?: return
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> lifecycle.interruptionBegan()
            AVAudioSessionInterruptionTypeEnded -> {
                val options = notification.number(AVAudioSessionInterruptionOptionKey)?.unsignedIntegerValue ?: 0uL
                lifecycle.interruptionEnded(
                    shouldResume = options and AVAudioSessionInterruptionOptionShouldResume != 0uL,
                )
            }
        }
    }

    private fun handleRouteChange(notification: NSNotification) {
        val reason = notification.number(AVAudioSessionRouteChangeReasonKey)?.unsignedIntegerValue ?: return
        if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) lifecycle.outputDisconnected()
    }

    private fun installRemoteCommands() {
        addCommand(commandCenter.playCommand) {
            bridge.play()
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.pauseCommand) {
            bridge.pause()
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.stopCommand) {
            bridge.stop()
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.previousTrackCommand) {
            bridge.previous()
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.nextTrackCommand) {
            bridge.next()
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.changePlaybackPositionCommand) { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
                ?: return@addCommand MPRemoteCommandHandlerStatusCommandFailed
            bridge.seekTo((position.coerceAtLeast(0.0) * 1_000.0).toLong())
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.changeShuffleModeCommand) { event ->
            val shuffle = (event as? MPChangeShuffleModeCommandEvent)?.shuffleType
                ?: return@addCommand MPRemoteCommandHandlerStatusCommandFailed
            bridge.setShuffleActive(shuffle != MPShuffleType.MPShuffleTypeOff)
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.changeRepeatModeCommand) { event ->
            val repeat = (event as? MPChangeRepeatModeCommandEvent)?.repeatType
                ?: return@addCommand MPRemoteCommandHandlerStatusCommandFailed
            bridge.setRepeatMode(
                when (repeat) {
                    MPRepeatType.MPRepeatTypeOne -> NaviampRepeatMode.Track
                    MPRepeatType.MPRepeatTypeAll -> NaviampRepeatMode.Queue
                    else -> NaviampRepeatMode.Off
                },
            )
            MPRemoteCommandHandlerStatusSuccess
        }
        addCommand(commandCenter.likeCommand) {
            if (!bridge.snapshot().canFavorite) return@addCommand MPRemoteCommandHandlerStatusCommandFailed
            bridge.toggleFavorite()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.likeCommand.localizedTitle = "Favorite"
        commandCenter.likeCommand.localizedShortTitle = "Favorite"
    }

    private fun addCommand(
        command: MPRemoteCommand,
        handler: (platform.MediaPlayer.MPRemoteCommandEvent?) -> Long,
    ) {
        val target = command.addTargetWithHandler(handler)
        commandTargets += command to target
    }

    private fun publish(snapshot: NaviampExternalPlaybackSnapshot) {
        val publication = publicationPlanner.plan(snapshot)
        updateRemoteCommandAvailability(snapshot)
        val current = snapshot.current
        if (current?.mediaId != artworkMediaId) clearArtwork()
        if (publication.sessionContent || publication.playbackState) publishNowPlaying(snapshot, artwork)
        current ?: return
        val nextArtworkUrl = current.artworkUrl ?: return
        if (artworkMediaId == current.mediaId && artworkUrl == nextArtworkUrl) return
        artworkMediaId = current.mediaId
        artworkUrl = nextArtworkUrl
        artworkJob = scope.launch {
            val image = artworkBytes(nextArtworkUrl)?.toImage() ?: return@launch
            if (bridge.snapshot().current?.mediaId != current.mediaId || artworkUrl != nextArtworkUrl) return@launch
            artwork = MPMediaItemArtwork(image.size) { image }
            publishNowPlaying(bridge.snapshot(), artwork)
        }
    }

    private fun publishNowPlaying(snapshot: NaviampExternalPlaybackSnapshot, artwork: MPMediaItemArtwork?) {
        val current = snapshot.current
        if (current == null) {
            nowPlayingCenter.nowPlayingInfo = null
            nowPlayingCenter.playbackState = MPNowPlayingPlaybackStateStopped
            return
        }
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to current.title,
            MPMediaItemPropertyArtist to current.subtitle,
            MPNowPlayingInfoPropertyExternalContentIdentifier to current.mediaId,
            MPNowPlayingInfoPropertyMediaType to MPNowPlayingInfoMediaTypeAudio,
            MPNowPlayingInfoPropertyPlaybackRate to if (snapshot.state == NaviampExternalPlaybackState.Playing) 1.0 else 0.0,
        )
        current.description.takeIf(String::isNotBlank)?.let { info[MPMediaItemPropertyAlbumTitle] = it }
        snapshot.durationMillis?.let { info[MPMediaItemPropertyPlaybackDuration] = it / 1_000.0 }
        snapshot.positionMillis?.let { info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = it / 1_000.0 }
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        nowPlayingCenter.nowPlayingInfo = info
        nowPlayingCenter.playbackState = when (snapshot.state) {
            NaviampExternalPlaybackState.Playing -> MPNowPlayingPlaybackStatePlaying
            NaviampExternalPlaybackState.Paused, NaviampExternalPlaybackState.Loading -> MPNowPlayingPlaybackStatePaused
            NaviampExternalPlaybackState.Idle -> MPNowPlayingPlaybackStateStopped
        }
    }

    private fun updateRemoteCommandAvailability(snapshot: NaviampExternalPlaybackSnapshot) {
        // Play and pause are both supported whenever Core exposes playback control. MediaPlayer
        // selects the active presentation from the published playback state.
        commandCenter.playCommand.enabled = snapshot.canPlayPause
        commandCenter.pauseCommand.enabled = snapshot.canPlayPause
        // The canonical play/pause pair covers toggle events without advertising an overlapping
        // third command to Apple system surfaces.
        commandCenter.togglePlayPauseCommand.enabled = false
        commandCenter.stopCommand.enabled = snapshot.current != null
        commandCenter.previousTrackCommand.enabled = snapshot.hasPrevious
        commandCenter.nextTrackCommand.enabled = snapshot.hasNext
        commandCenter.changePlaybackPositionCommand.enabled = snapshot.current != null && snapshot.durationMillis != null
        commandCenter.changeShuffleModeCommand.enabled = snapshot.current != null
        commandCenter.changeShuffleModeCommand.currentShuffleType =
            if (snapshot.shuffleActive) MPShuffleType.MPShuffleTypeItems else MPShuffleType.MPShuffleTypeOff
        commandCenter.changeRepeatModeCommand.enabled = snapshot.current != null
        commandCenter.changeRepeatModeCommand.currentRepeatType = when (snapshot.repeatMode) {
            NaviampRepeatMode.Off -> MPRepeatType.MPRepeatTypeOff
            NaviampRepeatMode.Queue -> MPRepeatType.MPRepeatTypeAll
            NaviampRepeatMode.Track -> MPRepeatType.MPRepeatTypeOne
        }
        commandCenter.likeCommand.enabled = snapshot.canFavorite
        commandCenter.likeCommand.active = snapshot.favorite
    }

    private fun clearArtwork() {
        artworkJob?.cancel()
        artworkJob = null
        artworkMediaId = null
        artworkUrl = null
        artwork = null
    }

    private fun NSNotification.number(key: String?): NSNumber? = userInfo?.get(key) as? NSNumber

    private fun ByteArray.toImage(): UIImage? = usePinned { pinned ->
        UIImage(data = NSData.create(bytes = pinned.addressOf(0), length = size.toULong()))
    }
}
