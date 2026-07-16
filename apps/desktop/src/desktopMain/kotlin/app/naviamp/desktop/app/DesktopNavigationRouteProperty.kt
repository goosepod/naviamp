package app.naviamp.desktop

import androidx.compose.runtime.mutableIntStateOf
import app.naviamp.app.NaviampNavigationController
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal enum class DesktopNavigationField {
    CurrentRoute,
    LastContentRoute,
}

/** Compose-observable Desktop route view backed by the shared navigation controller. */
internal class DesktopNavigationRouteProperty(
    private val controller: NaviampNavigationController,
    private val field: DesktopNavigationField,
) : ReadWriteProperty<Any?, DesktopAppRoute> {
    private val revision = mutableIntStateOf(0)

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): DesktopAppRoute {
        revision.intValue
        val state = controller.state.value
        return when (field) {
            DesktopNavigationField.CurrentRoute -> state.route
            DesktopNavigationField.LastContentRoute -> state.lastContentRoute
        }.toDesktopAppRoute()
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: DesktopAppRoute) {
        val route = value.toNaviampRoute()
        val current = controller.state.value
        val unchanged = when (field) {
            DesktopNavigationField.CurrentRoute -> current.route == route
            DesktopNavigationField.LastContentRoute -> current.lastContentRoute == route
        }
        if (unchanged) return
        when (field) {
            DesktopNavigationField.CurrentRoute -> controller.navigate(route)
            DesktopNavigationField.LastContentRoute -> controller.updateLastContentRoute(route)
        }
        revision.intValue += 1
    }
}
