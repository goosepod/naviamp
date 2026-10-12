package app.naviamp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal fun serviceIconResource(providerId: String): DrawableResource? = when (providerId.trim().lowercase()) {
    ProviderIdNavidrome -> Res.drawable.service_navidrome
    ProviderIdSubsonic -> Res.drawable.service_opensubsonic
    ProviderIdJellyfin -> Res.drawable.service_jellyfin
    ProviderIdBandcamp -> Res.drawable.service_bandcamp
    else -> null
}

/** Decorative branding alongside a readable service name; keep original colors and proportions. */
@Composable
internal fun NaviampServiceIcon(providerId: String, fallbackColor: Color, size: Dp = 24.dp) {
    val resource = serviceIconResource(providerId)
    if (resource != null) {
        Image(painterResource(resource), contentDescription = null,
            modifier = Modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        Icon(NaviampIcons.Globe, contentDescription = null, tint = fallbackColor, modifier = Modifier.size(size))
    }
}
