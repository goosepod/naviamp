package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalComposeUiApi::class)
actual fun NaviampTooltip(
    text: String,
    colors: NaviampColors,
    content: @Composable () -> Unit,
) {
    if (!LocalNaviampTooltipsEnabled.current) {
        content()
        return
    }

    var hovered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(hovered, text) {
        visible = false
        if (hovered && text.isNotBlank()) {
            delay(450)
            visible = true
        }
    }

    Box(
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) {
                hovered = false
                visible = false
            },
    ) {
        content()
        if (visible) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -34),
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    color = colors.controlSurface.copy(alpha = 0.98f),
                    contentColor = colors.primaryText,
                    shape = RoundedCornerShape(5.dp),
                    shadowElevation = 5.dp,
                ) {
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
