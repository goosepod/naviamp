package app.naviamp.ui

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@OptIn(ExperimentalFoundationApi::class)
actual fun NaviampTooltip(
    text: String,
    colors: NaviampColors,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
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
        },
        delayMillis = 500,
        content = content,
    )
}
