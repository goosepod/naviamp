package app.naviamp.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NaviampPageTitle(
    title: String,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = colors.primaryText,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .height(40.dp)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
