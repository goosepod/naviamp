package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/** Use the readable foreground for the insertion caret, independently of the theme accent. */
@Composable
internal fun naviampOutlinedTextFieldColors(colors: NaviampColors? = null): TextFieldColors {
    val foreground = colors?.primaryText ?: MaterialTheme.colorScheme.onSurface
    return OutlinedTextFieldDefaults.colors(
        cursorColor = foreground,
        errorCursorColor = foreground,
    )
}
