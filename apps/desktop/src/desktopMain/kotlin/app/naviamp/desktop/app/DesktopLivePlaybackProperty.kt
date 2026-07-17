package app.naviamp.desktop

import androidx.compose.runtime.mutableIntStateOf
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class DesktopLivePlaybackProperty<T>(
    private val controller: NaviampLivePlaybackController,
    private val read: (NaviampLivePlaybackState) -> T,
    private val write: (NaviampLivePlaybackController, T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    private val revision = mutableIntStateOf(0)

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        revision.intValue
        return read(controller.state.value)
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        write(controller, value)
        revision.intValue += 1
    }
}

internal fun desktopCurrentTrackProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty<Track?>(controller, NaviampLivePlaybackState::currentTrack) { owner, value ->
        owner.updateCurrentTrack(value)
    }

internal fun desktopCurrentStationProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty<InternetRadioStation?>(
        controller,
        NaviampLivePlaybackState::currentStation,
    ) { owner, value ->
        owner.updateCurrentStation(value)
    }

internal fun desktopPlaybackQueueProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty(controller, NaviampLivePlaybackState::queue) { owner, value: PlaybackQueue ->
        owner.updateQueue(value)
    }

internal fun desktopPlaybackProgressProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty(controller, NaviampLivePlaybackState::progress) { owner, value: PlaybackProgress ->
        owner.updateProgress(value)
    }

internal fun desktopPendingSeekPositionProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty(
        controller,
        NaviampLivePlaybackState::pendingSeekPositionSeconds,
    ) { owner, value: Double? ->
        owner.updatePendingSeek(value, owner.state.value.pendingSeekIssuedAtMillis)
    }

internal fun desktopPendingSeekIssuedAtProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty(
        controller,
        NaviampLivePlaybackState::pendingSeekIssuedAtMillis,
    ) { owner, value: Long? ->
        owner.updatePendingSeek(owner.state.value.pendingSeekPositionSeconds, value)
    }

internal fun desktopPlaybackStateProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty(controller, NaviampLivePlaybackState::playbackState) { owner, value: PlaybackState ->
        owner.updatePlaybackState(value)
    }

internal fun desktopRepeatModeProperty(controller: NaviampLivePlaybackController) =
    DesktopLivePlaybackProperty(controller, NaviampLivePlaybackState::repeatMode) { owner, value: RepeatMode ->
        owner.updateRepeatMode(value)
    }
