package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.description_less
import app.naviamp.ui.generated.resources.description_more
import org.jetbrains.compose.resources.stringResource

/** Shared reading typography and measured expansion for artist and album descriptions. */
@Composable
internal fun NaviampProviderDescription(description: String?, contentKey: String, colors: NaviampColors) {
    val text = remember(description) {
        description?.normalizedProviderDescription()?.toProviderRichText()
    }?.takeIf { it.text.isNotBlank() } ?: return
    var expanded by remember(contentKey, text) { mutableStateOf(false) }
    var overflows by remember(contentKey, text) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            color = colors.secondaryText,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (expanded || overflows) {
            Text(
                stringResource(if (expanded) Res.string.description_less else Res.string.description_more),
                color = colors.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
