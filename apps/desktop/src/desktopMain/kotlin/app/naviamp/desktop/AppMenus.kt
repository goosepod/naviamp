package app.naviamp.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NaviampDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(4.dp),
        containerColor = MenuBackground,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(min = 220.dp),
        content = content,
    )
}

@Composable
fun NaviampDropdownMenuItem(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                fontSize = 13.sp,
            )
        },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) MenuText else MenuText.copy(alpha = 0.42f),
                    modifier = Modifier.size(17.dp),
                )
            }
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = MenuText,
            disabledTextColor = MenuText.copy(alpha = 0.42f),
        ),
        onClick = onClick,
    )
}

private val MenuBackground = Color(0xFF292C36)
private val MenuText = Color(0xFFE7E8EF)
