package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

@Composable
internal actual fun PlatformLiveVisualizerSurface(
    bandsProvider: () -> List<Float>,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier,
) {
    val effect = remember { runCatching { RuntimeEffect.makeForShader(VisualizerShaderSkSL) }.getOrNull() }
    if (effect == null) {
        CanvasVisualizerSurface(bandsProvider, active, colors, modifier)
        return
    }
    val renderer = remember(effect) { ShaderVisualizerRenderer(effect) }
    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
            effect.close()
        }
    }

    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(bands.size, (size.width / 6f).toInt().coerceAtLeast(16))
        if (visibleBands <= 0 || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }
        drawIntoCanvas { canvas ->
            renderer.draw(
                canvas = canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                bands = bands,
                visibleBands = visibleBands,
                active = active,
                colors = colors,
            )
        }
    }
}

private class ShaderVisualizerRenderer(effect: RuntimeEffect) : AutoCloseable {
    private val builder = RuntimeShaderBuilder(effect)
    private val paint = Paint()
    private val uniformBands = FloatArray(VisualizerShaderBandCount)
    private var shader: Shader? = null

    fun draw(
        canvas: org.jetbrains.skia.Canvas,
        width: Float,
        height: Float,
        bands: List<Float>,
        visibleBands: Int,
        active: Boolean,
        colors: NaviampColors,
    ) {
        repeat(VisualizerShaderBandCount) { index ->
            val sourceIndex = if (VisualizerShaderBandCount == 1 || bands.isEmpty()) {
                0
            } else {
                ((index / (VisualizerShaderBandCount - 1f)) * (bands.size - 1)).toInt()
            }
            uniformBands[index] = bands.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
        }

        builder.uniform("iResolution", width, height)
        builder.uniform("iAccent", colors.accent.red, colors.accent.green, colors.accent.blue, colors.accent.alpha)
        builder.uniform(
            "iIdle",
            colors.primaryText.red,
            colors.primaryText.green,
            colors.primaryText.blue,
            colors.primaryText.alpha * 0.16f,
        )
        builder.uniform("iActive", if (active) 1f else 0f)
        builder.uniform("iVisibleBands", visibleBands.toFloat())
        builder.uniform("iSourceBands", bands.size.toFloat().coerceAtLeast(1f))
        builder.uniform("iBands", uniformBands)

        shader?.close()
        shader = builder.makeShader()
        paint.shader = shader
        canvas.drawRect(Rect.makeWH(width, height), paint)
    }

    override fun close() {
        shader?.close()
        shader = null
        paint.close()
        builder.close()
    }
}

@Composable
private fun CanvasVisualizerSurface(
    bandsProvider: () -> List<Float>,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(bands.size, (size.width / 6f).toInt().coerceAtLeast(16))
        if (!active || visibleBands <= 0) {
            drawLine(
                color = colors.primaryText.copy(alpha = 0.16f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.4f,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val step = size.width / visibleBands.toFloat()
        val strokeWidth = (step * 0.48f).coerceIn(1.2f, 3.2f)
        val centerY = size.height / 2f
        repeat(visibleBands) { index ->
            val sourceIndex = if (visibleBands == 1) {
                0
            } else {
                ((index / (visibleBands - 1f)) * (bands.size - 1)).toInt()
            }
            val amplitude = bands[sourceIndex].coerceIn(0f, 1f)
            val barHeight = (2f + amplitude * (size.height - 2f)).coerceAtMost(size.height)
            val x = index * step + step / 2f
            drawLine(
                color = colors.accent.copy(alpha = 0.30f + amplitude * 0.55f),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val VisualizerShaderBandCount = 32

private const val VisualizerShaderSkSL = """
uniform float2 iResolution;
uniform float4 iAccent;
uniform float4 iIdle;
uniform float iActive;
uniform float iVisibleBands;
uniform float iSourceBands;
uniform float iBands[32];

float capsuleMask(float2 p, float2 a, float2 b, float radius) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return 1.0 - smoothstep(radius - 0.65, radius + 0.65, length(pa - ba * h));
}

half4 main(float2 coord) {
    float centerY = iResolution.y * 0.5;
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - centerY));
        return half4(iIdle.rgb, iIdle.a * line);
    }

    float visibleBands = max(iVisibleBands, 1.0);
    float step = iResolution.x / visibleBands;
    float bandIndex = clamp(floor(coord.x / step), 0.0, visibleBands - 1.0);
    float sourceIndex = visibleBands <= 1.0 ? 0.0 : floor((bandIndex / (visibleBands - 1.0)) * (iSourceBands - 1.0) + 0.0001);
    int bandSlot = int(clamp(floor((sourceIndex / max(iSourceBands - 1.0, 1.0)) * 31.0 + 0.0001), 0.0, 31.0));
    float amplitude = clamp(iBands[bandSlot], 0.0, 1.0);
    float barHeight = min(2.0 + amplitude * (iResolution.y - 2.0), iResolution.y);
    float strokeWidth = clamp(step * 0.48, 1.2, 3.2);
    float x = bandIndex * step + step * 0.5;
    float mask = capsuleMask(
        coord,
        float2(x, centerY - barHeight * 0.5),
        float2(x, centerY + barHeight * 0.5),
        strokeWidth * 0.5
    );
    float alpha = (0.30 + amplitude * 0.55) * iAccent.a * mask;
    return half4(iAccent.rgb, alpha);
}
"""
