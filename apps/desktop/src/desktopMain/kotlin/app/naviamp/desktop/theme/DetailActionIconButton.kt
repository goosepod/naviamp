package app.naviamp.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DetailActionIconButton(
    appColors: DesktopAppColors,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) appColors.primaryText else appColors.secondaryText.copy(alpha = 0.45f),
            modifier = Modifier.size(iconSize),
        )
    }
}
