package app.naviamp.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DesktopNaviampDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    app.naviamp.ui.NaviampDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun DesktopNaviampDropdownMenuItem(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    app.naviamp.ui.NaviampDropdownMenuItem(
        label = label,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun DesktopPageOverflowMenu(
    appColors: DesktopAppColors,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(34.dp),
        ) {
            Text("⋮", color = appColors.primaryText, fontSize = 17.sp)
        }
        DesktopNaviampDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DesktopNaviampDropdownMenuItem(
                label = "Refresh",
                icon = DesktopNavigationIcons.Refresh,
                enabled = refreshEnabled,
                onClick = {
                    expanded = false
                    onRefresh()
                },
            )
        }
    }
}
