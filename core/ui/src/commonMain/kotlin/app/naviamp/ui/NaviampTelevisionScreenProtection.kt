package app.naviamp.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsPropertyKey
import kotlinx.coroutines.delay

internal const val TelevisionScreenProtectionDelayMillis = 120_000L
internal const val TelevisionScreenProtectionStepMillis = 60_000L
internal const val TelevisionScreenProtectionDeepDimMillis = 600_000L
internal val TelevisionScreenProtectionBrightness = SemanticsPropertyKey<Float>("TV screen protection brightness")

/** Offsets stay inside Now Playing's 36dp horizontal / 24dp vertical content margins. */
internal data class TelevisionScreenProtection(
    val brightness: Float = 1f,
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
)

/** Input time, rather than track or progress changes, determines prolonged static exposure. */
internal fun televisionScreenProtection(idleMillis: Long): TelevisionScreenProtection {
    if (idleMillis < TelevisionScreenProtectionDelayMillis) return TelevisionScreenProtection()
    val step = ((idleMillis - TelevisionScreenProtectionDelayMillis) / TelevisionScreenProtectionStepMillis % 8).toInt()
    val x = when (step) { 0, 1, 7 -> 8; 3, 4, 5 -> -8; else -> 0 }
    val y = when (step) { 1, 2, 3 -> 8; 5, 6, 7 -> -8; else -> 0 }
    return TelevisionScreenProtection(
        brightness = if (idleMillis >= TelevisionScreenProtectionDeepDimMillis) 0.35f else 0.55f,
        offsetXDp = x,
        offsetYDp = y,
    )
}

@Composable
internal fun rememberTelevisionScreenProtection(active: Boolean, interactionSequence: Int): TelevisionScreenProtection {
    var idleMillis by remember(active, interactionSequence) { mutableLongStateOf(0L) }
    LaunchedEffect(active, interactionSequence) {
        if (!active) return@LaunchedEffect
        delay(TelevisionScreenProtectionDelayMillis)
        idleMillis = TelevisionScreenProtectionDelayMillis
        while (true) {
            delay(TelevisionScreenProtectionStepMillis)
            idleMillis = (idleMillis + TelevisionScreenProtectionStepMillis).coerceAtLeast(idleMillis)
        }
    }
    return televisionScreenProtection(idleMillis)
}

/** Shared rendering keeps exposure mitigation independent of the playback and queue owners. */
@Composable
internal fun TelevisionScreenProtectionSurface(
    protection: TelevisionScreenProtection,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transitionMillis = if (protection.brightness == 1f) 0 else 2_000
    val brightness by animateFloatAsState(protection.brightness, tween(transitionMillis), label = "TV protection dimming")
    val offsetX by animateDpAsState(protection.offsetXDp.dp, tween(transitionMillis), label = "TV protection horizontal shift")
    val offsetY by animateDpAsState(protection.offsetYDp.dp, tween(transitionMillis), label = "TV protection vertical shift")
    Box(modifier.semantics { this[TelevisionScreenProtectionBrightness] = brightness }.drawWithContent {
        drawContent()
        drawRect(Color.Black, alpha = 1f - brightness)
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = offsetX.toPx()
            translationY = offsetY.toPx()
        }, content = content)
    }
}
