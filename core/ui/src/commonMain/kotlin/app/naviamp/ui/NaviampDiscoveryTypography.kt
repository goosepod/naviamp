package app.naviamp.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Same 13sp body baseline as shared Library rows; sp inherits the existing font-size scope. */
@Composable
internal fun DiscoveryText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = 13.sp, fontWeight: FontWeight? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, style: TextStyle = LocalTextStyle.current) {
    Text(text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        lineHeight = if (lineHeight == TextUnit.Unspecified) fontSize * 1.35f else lineHeight,
        maxLines = maxLines, overflow = overflow, style = style)
}
