package app.naviamp.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.nunito_sans
import org.jetbrains.compose.resources.Font

@Composable
fun rememberNaviampTypography(): Typography {
    val nunitoSans = FontFamily(
        Font(Res.font.nunito_sans, weight = FontWeight.Normal),
        Font(Res.font.nunito_sans, weight = FontWeight.Medium),
        Font(Res.font.nunito_sans, weight = FontWeight.SemiBold),
        Font(Res.font.nunito_sans, weight = FontWeight.Bold),
    )
    return remember(nunitoSans) {
        Typography().withFontFamily(nunitoSans)
    }
}

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.withFontFamily(fontFamily),
    displayMedium = displayMedium.withFontFamily(fontFamily),
    displaySmall = displaySmall.withFontFamily(fontFamily),
    headlineLarge = headlineLarge.withFontFamily(fontFamily),
    headlineMedium = headlineMedium.withFontFamily(fontFamily),
    headlineSmall = headlineSmall.withFontFamily(fontFamily),
    titleLarge = titleLarge.withFontFamily(fontFamily),
    titleMedium = titleMedium.withFontFamily(fontFamily),
    titleSmall = titleSmall.withFontFamily(fontFamily),
    bodyLarge = bodyLarge.withFontFamily(fontFamily),
    bodyMedium = bodyMedium.withFontFamily(fontFamily),
    bodySmall = bodySmall.withFontFamily(fontFamily),
    labelLarge = labelLarge.withFontFamily(fontFamily),
    labelMedium = labelMedium.withFontFamily(fontFamily),
    labelSmall = labelSmall.withFontFamily(fontFamily),
)

private fun TextStyle.withFontFamily(fontFamily: FontFamily): TextStyle = copy(fontFamily = fontFamily)
