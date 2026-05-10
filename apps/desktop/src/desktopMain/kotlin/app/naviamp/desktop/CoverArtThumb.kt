package app.naviamp.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun CoverArtThumb(
    appColors: AppColors,
    coverArtUrl: String?,
    size: Dp,
    cornerRadius: Dp = 5.dp,
) {
    var image by remember(coverArtUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(coverArtUrl) {
        image = coverArtUrl?.let { url ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = DesktopCaches.session.imageBytes(url)
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(appColors.albumArtPlaceholder),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
