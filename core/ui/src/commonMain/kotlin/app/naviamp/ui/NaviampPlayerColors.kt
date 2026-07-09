package app.naviamp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class NaviampPlayerColors(
    val backgroundStart: Color,
    val backgroundMid: Color,
    val backgroundEnd: Color,
    val accent: Color,
) {
    val backgroundBrush: Brush
        get() = Brush.linearGradient(colors = listOf(backgroundStart, backgroundMid, backgroundEnd))

    fun backgroundBrush(end: Offset): Brush =
        Brush.linearGradient(
            colors = listOf(backgroundStart, backgroundMid, backgroundEnd),
            start = Offset.Zero,
            end = end,
        )

    val gradientColors: List<Color>
        get() = listOf(backgroundStart, backgroundMid, backgroundEnd)

    companion object {
        fun solid(color: Color): NaviampPlayerColors =
            NaviampPlayerColors(
                backgroundStart = color,
                backgroundMid = color,
                backgroundEnd = color,
                accent = color,
            )

        fun fallback(colors: NaviampColors): NaviampPlayerColors =
            from(NaviampAlbumPalette.fallback(colors.albumArtPlaceholder), colors)

        fun from(palette: NaviampAlbumPalette, colors: NaviampColors): NaviampPlayerColors {
            val secondary = if (palette.primary.hueDistance(palette.secondary) < 0.08f) {
                palette.secondary.shiftHue(0.09f)
            } else {
                palette.secondary
            }
            val artworkLift = maxOf(
                palette.primary.hsvValue(),
                palette.secondary.hsvValue(),
                palette.accent.hsvValue(),
            ).coerceIn(0f, 1f)
            val lightArtwork = ((artworkLift - 0.42f) / 0.42f).coerceIn(0f, 1f)
            val left = palette.primary
                .mix(Color.White, 0.04f + lightArtwork * 0.04f)
                .mix(Color.Black, 0.36f - lightArtwork * 0.14f)
                .mix(colors.background, 0.14f)
            val middle = palette.accent
                .mix(palette.primary, 0.28f)
                .mix(Color.White, lightArtwork * 0.05f)
                .mix(Color.Black, 0.44f - lightArtwork * 0.18f)
                .mix(colors.background, 0.08f)
            val right = secondary
                .mix(Color.White, lightArtwork * 0.03f)
                .mix(Color.Black, 0.62f - lightArtwork * 0.20f)
                .mix(colors.background, 0.10f)
            return NaviampPlayerColors(
                backgroundStart = left,
                backgroundMid = middle,
                backgroundEnd = right,
                accent = palette.accent.mix(Color.White, 0.08f),
            )
        }
    }
}

data class NaviampAlbumPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
) {
    companion object {
        fun fallback(color: Color): NaviampAlbumPalette =
            NaviampAlbumPalette(
                primary = color,
                secondary = color.mix(Color(0xFF7C1232), 0.45f),
                accent = color,
            )
    }
}

data class NaviampRgbSample(
    val red: Int,
    val green: Int,
    val blue: Int,
)

fun naviampAlbumPalette(samples: Iterable<NaviampRgbSample>): NaviampAlbumPalette? {
    val buckets = mutableMapOf<Int, ColorBucket>()
    samples.forEach { sample ->
        val hsv = rgbToHsv(sample.red, sample.green, sample.blue)
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (saturation > 0.06f && brightness in 0.12f..0.96f) {
            val key = ((sample.red / 32) shl 10) or ((sample.green / 32) shl 5) or (sample.blue / 32)
            buckets.getOrPut(key) { ColorBucket() }
                .add(sample.red, sample.green, sample.blue, saturation, brightness)
        }
    }

    val candidates = buckets.values
        .filter { it.count >= 2 }
        .sortedByDescending { it.score() }

    val primary = candidates.firstOrNull() ?: return null
    val secondary = candidates.firstOrNull { primary.colorDistance(it) > 0.045f }
        ?: candidates.firstOrNull { primary.hueDistance(it) > 0.08f }
        ?: candidates.getOrNull(1)
        ?: primary
    val accent = candidates
        .filter { primary.colorDistance(it) > 0.025f || primary.hueDistance(it) > 0.06f }
        .maxByOrNull { it.accentScore(primary) }
        ?: primary

    return NaviampAlbumPalette(
        primary = primary.color(),
        secondary = secondary.color(),
        accent = accent.color(),
    )
}

private class ColorBucket {
    var red = 0L
    var green = 0L
    var blue = 0L
    var saturation = 0.0
    var brightness = 0.0
    var count = 0

    fun add(red: Int, green: Int, blue: Int, saturation: Float, brightness: Float) {
        this.red += red
        this.green += green
        this.blue += blue
        this.saturation += saturation
        this.brightness += brightness
        count += 1
    }

    fun color(): Color =
        Color(
            red = (red / count).toInt(),
            green = (green / count).toInt(),
            blue = (blue / count).toInt(),
        )

    fun score(): Double {
        val saturationAverage = saturationAverage().toDouble()
        val brightnessAverage = brightnessAverage().toDouble()
        val brightnessScore = 1.0 - abs(brightnessAverage - 0.58).coerceAtMost(0.58) / 0.58
        return count.toDouble().pow(0.55) * (saturationAverage + 0.12) * (brightnessScore + 0.55)
    }

    fun accentScore(primary: ColorBucket): Double =
        score() * (1.0 + saturationAverage()) * (0.72 + brightnessAverage()) * (1.0 + colorDistance(primary))

    fun saturationAverage(): Float =
        (saturation / count).toFloat()

    private fun brightnessAverage(): Float =
        (brightness / count).toFloat()

    fun hueDistance(other: ColorBucket): Float =
        color().hueDistance(other.color())

    fun colorDistance(other: ColorBucket): Float =
        color().colorDistance(other.color())
}

fun Color.mix(other: Color, amount: Float): Color {
    val clamped = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * clamped,
        green = green + (other.green - green) * clamped,
        blue = blue + (other.blue - blue) * clamped,
        alpha = alpha + (other.alpha - alpha) * clamped,
    )
}

fun Color.shiftHue(amount: Float): Color {
    val hsv = rgbToHsv((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    val hue = ((hsv[0] + amount) % 1f + 1f) % 1f
    return hsvToColor(
        hue = hue,
        saturation = (hsv[1] * 1.08f).coerceIn(0f, 1f),
        value = (hsv[2] * 0.9f).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

private fun Color.hsvValue(): Float =
    rgbToHsv((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())[2]

fun Color.hueDistance(other: Color): Float {
    val hsv = rgbToHsv((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    val otherHsv = rgbToHsv((other.red * 255).toInt(), (other.green * 255).toInt(), (other.blue * 255).toInt())
    val distance = abs(hsv[0] - otherHsv[0])
    return minOf(distance, 1f - distance)
}

fun Color.colorDistance(other: Color): Float {
    val redDistance = red - other.red
    val greenDistance = green - other.green
    val blueDistance = blue - other.blue
    return redDistance * redDistance + greenDistance * greenDistance + blueDistance * blueDistance
}

private fun rgbToHsv(red: Int, green: Int, blue: Int): FloatArray {
    val r = red.coerceIn(0, 255) / 255f
    val g = green.coerceIn(0, 255) / 255f
    val b = blue.coerceIn(0, 255) / 255f
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta).mod(6f) / 6f
        max == g -> (((b - r) / delta) + 2f) / 6f
        else -> (((r - g) / delta) + 4f) / 6f
    }
    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, saturation, max)
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float): Color {
    val h = (hue.coerceIn(0f, 1f) * 6f).mod(6f)
    val c = value * saturation
    val x = c * (1f - abs((h.mod(2f)) - 1f))
    val m = value - c
    val (r, g, b) = when {
        h < 1f -> Triple(c, x, 0f)
        h < 2f -> Triple(x, c, 0f)
        h < 3f -> Triple(0f, c, x)
        h < 4f -> Triple(0f, x, c)
        h < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, alpha)
}
