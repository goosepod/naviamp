package app.naviamp.ui

import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NaviampCompactSearchField(
    value: String,
    placeholder: String,
    colors: NaviampColors,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    showClear: Boolean = value.isNotBlank(),
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryText),
        cursorBrush = SolidColor(colors.primaryText),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.primaryText.copy(alpha = 0.13f)),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = NaviampIcons.Search,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(17.dp),
                )
                Box(modifier = Modifier.weight(1f).padding(horizontal = 9.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                    innerTextField()
                }
                if (showClear) {
                    Icon(
                        imageVector = NaviampIcons.Close,
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = "Clear search" }
                            .clickable(onClick = onClear),
                    )
                }
            }
        },
    )
}

@Composable
internal fun NaviampTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: NaviampColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    isPassword: Boolean = false,
    forceFloatingLabel: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    val displayValue = if (forceFloatingLabel && value.isEmpty()) FloatingLabelSentinel else value
    OutlinedTextField(
        value = displayValue,
        onValueChange = { nextValue ->
            onValueChange(nextValue.replace(FloatingLabelSentinel, ""))
        },
        label = { Text(label, color = colors.secondaryText) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isPassword && value.isNotEmpty()) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        modifier = modifier.then(
            if (onSubmit != null) {
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                        onSubmit()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            },
        ),
    )
}

private const val FloatingLabelSentinel = "\u200B"

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
            contentDescription = stringResource(Res.string.transport_shuffle),
            colors = colors,
            onClick = onShuffle ?: {},
            enabled = onShuffle != null,
            selected = shuffleEnabled,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Previous,
            contentDescription = stringResource(Res.string.transport_previous),
            colors = colors,
            onClick = onPrevious ?: {},
            enabled = onPrevious != null,
            size = sideSize,
        )
        TransportIconButton(
            icon = if (isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
            contentDescription = if (isPlaying) {
                stringResource(Res.string.transport_pause)
            } else {
                stringResource(Res.string.transport_play)
            },
            colors = colors,
            onClick = if (isPlaying) onPause else onResume,
            size = transportSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Stop,
            contentDescription = stringResource(Res.string.transport_stop),
            colors = colors,
            onClick = onStop,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Next,
            contentDescription = stringResource(Res.string.transport_next),
            colors = colors,
            onClick = onNext ?: {},
            enabled = onNext != null,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Repeat,
            contentDescription = stringResource(Res.string.transport_repeat),
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
    NaviampTooltip(contentDescription, colors) {
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
}

@Composable
internal fun MiniPlayerIconButton(
    colors: NaviampColors,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    NaviampTooltip(contentDescription, colors) {
        IconButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(if (selected && enabled) colors.primaryText.copy(alpha = 0.14f) else Color.Transparent),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> colors.mutedText
                    else -> colors.primaryText
                },
                modifier = Modifier.size(22.dp),
            )
        }
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
    val bottomRoutes = listOf(
        SharedRoute.Home,
        SharedRoute.Playlists,
        SharedRoute.Library,
        SharedRoute.Search,
        SharedRoute.Radio,
        SharedRoute.Downloads,
        SharedRoute.Settings,
    )
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        // Keep the navigation chrome transparent so the page background remains continuous.
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        bottomRoutes.forEach { route ->
            NaviampTooltip(route.label, colors) {
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
    selected: Boolean = false,
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
        modifier = Modifier.background(
            if (selected) MenuText.copy(alpha = 0.12f) else Color.Transparent,
        ),
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

data class NaviampDetailAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean = false,
)

internal fun responsiveVisibleActionCount(
    availableWidthDp: Float,
    actionCount: Int,
    actionSlotWidthDp: Float = 44f,
): Int {
    if (actionCount <= 0 || availableWidthDp <= 0f) return 0
    val availableSlots = (availableWidthDp / actionSlotWidthDp).toInt().coerceAtLeast(1)
    return if (actionCount <= availableSlots) actionCount else (availableSlots - 1).coerceAtLeast(0)
}

@Composable
fun NaviampResponsiveActionRow(
    colors: NaviampColors,
    actions: List<NaviampDetailAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val visibleCount = responsiveVisibleActionCount(maxWidth.value, actions.size)
        val visibleActions = actions.take(visibleCount)
        val hiddenActions = actions.drop(visibleCount)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            visibleActions.forEach { action ->
                MiniPlayerIconButton(
                    colors = colors,
                    enabled = action.enabled,
                    icon = action.icon,
                    contentDescription = action.label,
                    onClick = action.onClick,
                    selected = action.selected,
                )
            }
            if (hiddenActions.isNotEmpty()) {
                NaviampRowOverflowMenu(
                    colors = colors,
                    items = hiddenActions.map { action ->
                        NaviampRowMenuItem(action.label, action.icon, action.onClick, action.enabled)
                    },
                    buttonSize = 38.dp,
                    iconSize = 22.dp,
                    selected = hiddenActions.any { it.selected },
                )
            }
        }
    }
}

@Composable
fun NaviampRowOverflowMenu(
    colors: NaviampColors,
    items: List<NaviampRowMenuItem>,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 28.dp,
    iconSize: androidx.compose.ui.unit.Dp = 17.dp,
    selected: Boolean = false,
) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        NaviampTooltip("More actions", colors) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .size(buttonSize)
                    .clip(RoundedCornerShape(buttonSize / 2))
                    .background(if (selected) colors.primaryText.copy(alpha = 0.14f) else Color.Transparent),
            ) {
                Icon(
                    imageVector = NaviampTransportIcons.MoreVertical,
                    contentDescription = "More actions",
                    tint = if (selected) colors.primaryText else colors.mutedText,
                    modifier = Modifier.size(iconSize),
                )
            }
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
