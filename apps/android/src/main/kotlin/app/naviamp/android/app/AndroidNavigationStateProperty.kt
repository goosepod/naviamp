package app.naviamp.android

import androidx.compose.runtime.mutableIntStateOf
import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.app.NaviampNavigationState
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Compose-observable Android view of navigation state owned by [NaviampNavigationController]. */
internal class AndroidNavigationStateProperty(
    private val controller: NaviampNavigationController,
) : ReadWriteProperty<Any?, NaviampNavigationState> {
    private val revision = mutableIntStateOf(0)

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): NaviampNavigationState {
        revision.intValue
        return controller.state.value
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: NaviampNavigationState) {
        if (controller.state.value == value) return
        controller.replace(value)
        revision.intValue += 1
    }
}
