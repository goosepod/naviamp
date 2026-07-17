package app.naviamp.android

import androidx.compose.runtime.mutableIntStateOf
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Compose-observable Android view of state owned by [NaviampLivePlaybackController]. */
internal class AndroidLivePlaybackStateProperty(
    private val controller: NaviampLivePlaybackController,
) : ReadWriteProperty<Any?, NaviampLivePlaybackState> {
    private val revision = mutableIntStateOf(0)

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): NaviampLivePlaybackState {
        revision.intValue
        return controller.state.value
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: NaviampLivePlaybackState) {
        controller.replace(value)
        revision.intValue += 1
    }
}
