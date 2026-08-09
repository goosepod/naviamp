package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

@Stable
class NaviampTextInputFocusRegistry {
    private val focusedInputs = mutableStateMapOf<Any, Unit>()

    val hasFocusedTextInput: Boolean
        get() = focusedInputs.isNotEmpty()

    internal fun update(id: Any, focused: Boolean) {
        if (focused) focusedInputs[id] = Unit else focusedInputs.remove(id)
    }

    internal fun remove(id: Any) {
        focusedInputs.remove(id)
    }
}

val LocalNaviampTextInputFocusRegistry = compositionLocalOf { NaviampTextInputFocusRegistry() }

@Composable
fun rememberNaviampTextInputFocusRegistry(): NaviampTextInputFocusRegistry =
    remember { NaviampTextInputFocusRegistry() }

fun Modifier.naviampTextInputFocus(): Modifier = composed {
    val registry = LocalNaviampTextInputFocusRegistry.current
    val id = remember { Any() }
    DisposableEffect(registry, id) {
        onDispose { registry.remove(id) }
    }
    onFocusChanged { focusState -> registry.update(id, focusState.isFocused) }
}
