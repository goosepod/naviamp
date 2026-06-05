package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NaviampTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: NaviampColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.secondaryText) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
    )
}

@Composable
internal fun PrimaryButton(label: String, colors: NaviampColors, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.controlSurface,
            disabledContentColor = colors.mutedText,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
internal fun SmallPill(label: String, colors: NaviampColors, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        modifier = Modifier.height(40.dp),
    ) {
        Text(label)
    }
}

@Composable
fun SharedTransportControls(
    colors: NaviampColors,
    isPlaying: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    shuffleEnabled: Boolean = false,
    repeatEnabled: Boolean = false,
    onShuffle: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
) {
    val transportSize = if (compact) 38.dp else 46.dp
    val sideSize = if (compact) 32.dp else 36.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        TransportIconButton(
            icon = NaviampTransportIcons.Shuffle,
            contentDescription = "Shuffle",
            colors = colors,
            onClick = onShuffle ?: {},
            enabled = onShuffle != null,
            selected = shuffleEnabled,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Previous,
            contentDescription = "Previous",
            colors = colors,
            onClick = onPrevious ?: {},
            enabled = onPrevious != null,
            size = sideSize,
        )
        TransportIconButton(
            icon = if (isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            colors = colors,
            onClick = if (isPlaying) onPause else onResume,
            size = transportSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Stop,
            contentDescription = "Stop",
            colors = colors,
            onClick = onStop,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Next,
            contentDescription = "Next",
            colors = colors,
            onClick = onNext ?: {},
            enabled = onNext != null,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Repeat,
            contentDescription = "Repeat",
            colors = colors,
            onClick = onRepeat ?: {},
            enabled = onRepeat != null,
            selected = repeatEnabled,
            size = sideSize,
        )
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    colors: NaviampColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 46.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(
                when {
                    !enabled -> colors.controlSurface.copy(alpha = 0.55f)
                    selected -> colors.primaryText
                    else -> colors.accent
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> colors.mutedText
                selected -> colors.background
                else -> colors.onAccent
            },
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
internal fun MiniPlayerIconButton(
    colors: NaviampColors,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.primaryText else colors.mutedText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun SectionHeader(text: String, colors: NaviampColors) {
    Text(text, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
internal fun PlaceholderTile(text: String, colors: NaviampColors) {
    Text(
        text,
        color = colors.secondaryText,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(12.dp),
    )
}

@Composable
fun SharedBottomNavigationBar(
    colors: NaviampColors,
    selectedRoute: SharedRoute,
    onRouteSelected: (SharedRoute) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(vertical = 2.dp),
    ) {
        SharedRoute.entries.forEach { route ->
            IconButton(onClick = { onRouteSelected(route) }, modifier = Modifier.size(42.dp)) {
                Icon(
                    route.icon,
                    contentDescription = route.label,
                    tint = if (route == selectedRoute) colors.primaryText else colors.mutedText,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
fun NaviampDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
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

data class NaviampRowMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
fun NaviampRowOverflowMenu(
    colors: NaviampColors,
    items: List<NaviampRowMenuItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp),
        ) {
            Text("⋮", color = colors.mutedText, fontSize = 15.sp)
        }
        NaviampDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                NaviampDropdownMenuItem(
                    label = item.label,
                    icon = item.icon,
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

private val MenuBackground = Color(0xFF292C36)
private val MenuText = Color(0xFFE7E8EF)
