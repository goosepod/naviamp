package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun DesktopCompactSearchField(
    value: String,
    placeholder: String,
    appColors: DesktopAppColors,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    showClear: Boolean = value.isNotBlank(),
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = appColors.primaryText),
        cursorBrush = SolidColor(appColors.primaryText),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions.Default,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(appColors.primaryText.copy(alpha = 0.13f)),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = DesktopNavigationIcons.Search,
                    contentDescription = null,
                    tint = appColors.secondaryText,
                    modifier = Modifier.size(15.dp),
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).padding(horizontal = 9.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = appColors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    innerTextField()
                }
                if (showClear) {
                    Icon(
                        imageVector = DesktopNavigationIcons.Close,
                        contentDescription = null,
                        tint = appColors.secondaryText,
                        modifier = Modifier
                            .size(16.dp)
                            .semantics { contentDescription = "Clear search" }
                            .clickable(onClick = onClear),
                    )
                }
            }
        },
    )
}
