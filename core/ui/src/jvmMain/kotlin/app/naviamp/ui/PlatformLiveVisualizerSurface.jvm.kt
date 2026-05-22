package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier,
) {
    var frameMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, visualizer) {
        if (!active) {
            frameMillis = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis { frameMillis = it }
        }
    }

    val effect = remember(visualizer) {
        runCatching { RuntimeEffect.makeForShader(visualizer.shaderSource) }.getOrNull()
    }
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
                visualizerColors = visualizerColors,
                timeSeconds = frameMillis / 1000f,
            )
        }
    }
}

private class ShaderVisualizerRenderer(effect: RuntimeEffect) : AutoCloseable {
    private val builder = RuntimeShaderBuilder(effect)
    private val paint = Paint()
    private val uniformBands = FloatArray(VisualizerShaderBandCount)
    private val smoothBands = FloatArray(VisualizerShaderBandCount)
    private var shader: Shader? = null

    fun draw(
        canvas: org.jetbrains.skia.Canvas,
        width: Float,
        height: Float,
        bands: List<Float>,
        visibleBands: Int,
        active: Boolean,
        colors: NaviampColors,
        visualizerColors: NaviampPlayerColors,
        timeSeconds: Float,
    ) {
        repeat(VisualizerShaderBandCount) { index ->
            val sourceIndex = if (VisualizerShaderBandCount == 1 || bands.isEmpty()) {
                0
            } else {
                ((index / (VisualizerShaderBandCount - 1f)) * (bands.size - 1)).toInt()
            }
            val target = bands.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
            val rise = if (target > smoothBands[index]) 0.42f else 0.18f
            smoothBands[index] += (target - smoothBands[index]) * rise
            uniformBands[index] = smoothBands[index].coerceIn(0f, 1f)
        }
        val bass = uniformBands.take(8).average().toFloat().coerceIn(0f, 1f)
        val mids = uniformBands.drop(8).take(12).average().toFloat().coerceIn(0f, 1f)
        val highs = uniformBands.drop(20).average().toFloat().coerceIn(0f, 1f)

        builder.uniform("iResolution", width, height)
        builder.uniform("iTime", timeSeconds)
        builder.uniform("iAccent", visualizerColors.accent.red, visualizerColors.accent.green, visualizerColors.accent.blue, visualizerColors.accent.alpha)
        builder.uniform("iColorA", visualizerColors.backgroundStart.red, visualizerColors.backgroundStart.green, visualizerColors.backgroundStart.blue, 1f)
        builder.uniform("iColorB", visualizerColors.backgroundMid.red, visualizerColors.backgroundMid.green, visualizerColors.backgroundMid.blue, 1f)
        builder.uniform("iColorC", visualizerColors.backgroundEnd.red, visualizerColors.backgroundEnd.green, visualizerColors.backgroundEnd.blue, 1f)
        builder.uniform("iReadable", colors.primaryText.red, colors.primaryText.green, colors.primaryText.blue, colors.primaryText.alpha)
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
        builder.uniform("iEnergy", bass, mids, highs, uniformBands.average().toFloat().coerceIn(0f, 1f))
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

internal val NaviampVisualizer.shaderSource: String
    get() = when (this) {
        NaviampVisualizer.ReactiveBars -> ReactiveBarsShaderSkSL
        NaviampVisualizer.FluidGradient -> FluidGradientShaderSkSL
        NaviampVisualizer.AudioSphere -> AudioSphereShaderSkSL
        NaviampVisualizer.AudioTunnel -> AudioTunnelShaderSkSL
        NaviampVisualizer.RibbonTrail -> RibbonTrailShaderSkSL
        NaviampVisualizer.FrequencyTerrain -> FrequencyTerrainShaderSkSL
        NaviampVisualizer.ParticleField -> ParticleFieldShaderSkSL
        NaviampVisualizer.WaveInterference -> WaveInterferenceShaderSkSL
        NaviampVisualizer.VinylGroove -> VinylGrooveShaderSkSL
    }

private const val CommonShaderHeader = """
uniform float2 iResolution;
uniform float iTime;
uniform float4 iAccent;
uniform float4 iColorA;
uniform float4 iColorB;
uniform float4 iColorC;
uniform float4 iReadable;
uniform float4 iIdle;
uniform float iActive;
uniform float iVisibleBands;
uniform float iSourceBands;
uniform float4 iEnergy;
uniform float iBands[32];

float hash21(float2 p) {
    p = fract(p * float2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float bandAtIndex(int bandSlot) {
    if (bandSlot <= 0) return clamp(iBands[0], 0.0, 1.0);
    if (bandSlot == 1) return clamp(iBands[1], 0.0, 1.0);
    if (bandSlot == 2) return clamp(iBands[2], 0.0, 1.0);
    if (bandSlot == 3) return clamp(iBands[3], 0.0, 1.0);
    if (bandSlot == 4) return clamp(iBands[4], 0.0, 1.0);
    if (bandSlot == 5) return clamp(iBands[5], 0.0, 1.0);
    if (bandSlot == 6) return clamp(iBands[6], 0.0, 1.0);
    if (bandSlot == 7) return clamp(iBands[7], 0.0, 1.0);
    if (bandSlot == 8) return clamp(iBands[8], 0.0, 1.0);
    if (bandSlot == 9) return clamp(iBands[9], 0.0, 1.0);
    if (bandSlot == 10) return clamp(iBands[10], 0.0, 1.0);
    if (bandSlot == 11) return clamp(iBands[11], 0.0, 1.0);
    if (bandSlot == 12) return clamp(iBands[12], 0.0, 1.0);
    if (bandSlot == 13) return clamp(iBands[13], 0.0, 1.0);
    if (bandSlot == 14) return clamp(iBands[14], 0.0, 1.0);
    if (bandSlot == 15) return clamp(iBands[15], 0.0, 1.0);
    if (bandSlot == 16) return clamp(iBands[16], 0.0, 1.0);
    if (bandSlot == 17) return clamp(iBands[17], 0.0, 1.0);
    if (bandSlot == 18) return clamp(iBands[18], 0.0, 1.0);
    if (bandSlot == 19) return clamp(iBands[19], 0.0, 1.0);
    if (bandSlot == 20) return clamp(iBands[20], 0.0, 1.0);
    if (bandSlot == 21) return clamp(iBands[21], 0.0, 1.0);
    if (bandSlot == 22) return clamp(iBands[22], 0.0, 1.0);
    if (bandSlot == 23) return clamp(iBands[23], 0.0, 1.0);
    if (bandSlot == 24) return clamp(iBands[24], 0.0, 1.0);
    if (bandSlot == 25) return clamp(iBands[25], 0.0, 1.0);
    if (bandSlot == 26) return clamp(iBands[26], 0.0, 1.0);
    if (bandSlot == 27) return clamp(iBands[27], 0.0, 1.0);
    if (bandSlot == 28) return clamp(iBands[28], 0.0, 1.0);
    if (bandSlot == 29) return clamp(iBands[29], 0.0, 1.0);
    if (bandSlot == 30) return clamp(iBands[30], 0.0, 1.0);
    return clamp(iBands[31], 0.0, 1.0);
}

float bandAt(float x) {
    int bandSlot = int(clamp(floor(x * 31.0 + 0.0001), 0.0, 31.0));
    return bandAtIndex(bandSlot);
}

float3 palette(float t) {
    float3 ab = mix(iColorA.rgb, iColorB.rgb, smoothstep(0.0, 0.62, t));
    float3 base = mix(ab, iColorC.rgb, smoothstep(0.30, 1.0, t));
    float3 boosted = mix(base, iAccent.rgb, 0.24 + 0.18 * sin(t * 6.2831853 + iTime * 0.7));
    float luma = dot(boosted, float3(0.299, 0.587, 0.114));
    float3 saturated = clamp(mix(float3(luma), boosted, 1.65), 0.0, 1.0);
    float contrastLift = luma < 0.58 ? 0.34 : 0.24;
    return clamp(mix(saturated, iReadable.rgb, contrastLift), 0.0, 1.0);
}

float edgeMask(float2 uv, float radius, float width) {
    return 1.0 - smoothstep(width, width * 1.7, abs(length(uv) - radius));
}

half4 premul(float3 color, float alpha) {
    float clippedAlpha = clamp(alpha, 0.0, 1.0);
    return half4(clamp(color, 0.0, 1.0) * clippedAlpha, clippedAlpha);
}

float lineMask(float2 p, float2 a, float2 b, float width) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return 1.0 - smoothstep(width, width * 1.75, length(pa - ba * h));
}

"""

private const val ReactiveBarsShaderSkSL = CommonShaderHeader + """
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
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float visibleBands = max(iVisibleBands, 1.0);
    float step = iResolution.x / visibleBands;
    float bandIndex = clamp(floor(coord.x / step), 0.0, visibleBands - 1.0);
    float sourceIndex = visibleBands <= 1.0 ? 0.0 : floor((bandIndex / (visibleBands - 1.0)) * (iSourceBands - 1.0) + 0.0001);
    int bandSlot = int(clamp(floor((sourceIndex / max(iSourceBands - 1.0, 1.0)) * 31.0 + 0.0001), 0.0, 31.0));
    float amplitude = bandAtIndex(bandSlot);
    float topPadding = max(5.0, iResolution.y * 0.08);
    float usableHeight = max(2.0, iResolution.y - topPadding * 2.0);
    float barHeight = min(2.0 + amplitude * usableHeight, usableHeight);
    float strokeWidth = clamp(step * 0.48, 1.2, 3.2);
    float x = bandIndex * step + step * 0.5;
    float mask = capsuleMask(
        coord,
        float2(x, centerY - barHeight * 0.5),
        float2(x, centerY + barHeight * 0.5),
        strokeWidth * 0.5
    );
    float3 color = mix(iReadable.rgb, palette(amplitude), 0.72);
    float alpha = (0.42 + amplitude * 0.52) * mask;
    return premul(color, alpha);
}
"""

private const val FluidGradientShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 uv = coord / iResolution;
    float2 p = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float flow = sin((p.x + p.y * 0.55) * 7.0 + iTime * (0.7 + bass * 1.5));
    flow += sin((p.x * -0.8 + p.y) * 10.0 - iTime * (0.55 + mids * 1.8)) * 0.65;
    flow += sin(length(p + float2(sin(iTime * 0.18), cos(iTime * 0.15)) * 0.16) * 18.0 - iTime * 1.4) * (0.28 + bass * 0.42);
    float sparkleCells = hash21(floor((uv + flow * 0.018) * (36.0 + highs * 32.0)) + floor(iTime * 7.0));
    float sparkle = smoothstep(0.965 - highs * 0.06, 1.0, sparkleCells) * (0.14 + highs * 0.44);
    float vignette = smoothstep(0.78, 0.12, length(p));
    float alpha = clamp((0.24 + iEnergy.w * 0.36) * vignette + sparkle, 0.0, 0.74);
    float3 color = palette(fract(flow * 0.19 + uv.x * 0.45 + uv.y * 0.28 + iTime * 0.035));
    color = mix(color, iReadable.rgb, sparkle * 0.34);
    return premul(color, alpha);
}
"""

private const val AudioSphereShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 uv = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    float radius = length(uv);
    float angle = atan(uv.y, uv.x);
    if (iActive < 0.5) {
        float ring = 1.0 - smoothstep(0.012, 0.026, abs(radius - 0.32));
        return premul(iIdle.rgb, iIdle.a * ring);
    }

    float band = bandAt(fract((angle + 3.14159265) / 6.2831853));
    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float wave = sin(angle * 8.0 + iTime * (1.4 + mids * 2.4)) * 0.018 * mids;
    float shimmer = (hash21(float2(floor(angle * 42.0), floor(radius * 42.0)) + iTime) - 0.5) * highs * 0.026;
    float sphereRadius = 0.36 + bass * 0.16 + band * 0.11 + wave + shimmer;
    float shell = 1.0 - smoothstep(0.0, 0.045, abs(radius - sphereRadius));
    float surface = smoothstep(sphereRadius, sphereRadius - 0.22, radius);
    float body = surface * smoothstep(sphereRadius + 0.018, sphereRadius - 0.018, radius);
    float glow = (1.0 - smoothstep(0.0, 0.10 + bass * 0.10, abs(radius - sphereRadius))) * (0.16 + bass * 0.22);
    float3 normalColor = palette(fract((angle / 6.2831853) + 0.5 + highs * 0.18 + band * 0.12));
    float highlight = pow(max(0.0, 1.0 - length(uv - float2(-0.12, -0.16)) * 2.2), 3.0);
    float rim = edgeMask(uv, sphereRadius, 0.018 + highs * 0.018);
    float alpha = clamp(body * 0.60 + shell * 0.48 + glow + rim * 0.18, 0.0, 0.96);
    float3 color = mix(normalColor * (0.70 + band * 0.72), iReadable.rgb, rim * 0.26 + highlight * 0.34);
    color = mix(color, iAccent.rgb, highlight * 0.36 + highs * 0.18);
    return premul(color, alpha);
}
"""

private const val AudioTunnelShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 p = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    if (iActive < 0.5) {
        float ring = edgeMask(p, 0.32, 0.016);
        return premul(iIdle.rgb, iIdle.a * ring);
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float angle = atan(p.y, p.x);
    float radius = length(p);
    float tunnel = 0.0;
    float3 color = float3(0.0);
    for (int i = 0; i < 7; ++i) {
        float fi = float(i);
        float depth = fract(fi * 0.145 + iTime * (0.16 + bass * 0.24));
        float ringRadius = mix(0.08, 0.58, depth);
        float wobble = sin(angle * (5.0 + fi) + iTime * (0.8 + mids * 1.6) + fi) * (0.012 + mids * 0.026);
        float sides = abs(cos(angle * 3.0 + fi + iTime * 0.22));
        float wire = edgeMask(p, ringRadius + wobble + sides * 0.018 * highs, 0.010 + depth * 0.014);
        float fade = (1.0 - depth) * (0.24 + highs * 0.26);
        tunnel += wire * fade;
        color += palette(fract(depth + fi * 0.13 + angle / 6.2831853)) * wire * fade;
    }
    float spoke = (1.0 - smoothstep(0.0, 0.014, abs(fract((angle / 6.2831853) * 12.0 + iTime * 0.16) - 0.5))) *
        smoothstep(0.06, 0.42, radius) * smoothstep(0.62, 0.34, radius) * (0.04 + highs * 0.10);
    float alpha = clamp(tunnel + spoke, 0.0, 0.86);
    color = alpha > 0.001 ? color / max(tunnel, 0.001) : iAccent.rgb;
    color = mix(color, iReadable.rgb, spoke * 0.42 + highs * 0.10);
    return premul(color, alpha);
}
"""

private const val RibbonTrailShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 p = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    if (iActive < 0.5) {
        float line = lineMask(p, float2(-0.34, 0.0), float2(0.34, 0.0), 0.012);
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float alpha = 0.0;
    float3 color = float3(0.0);
    for (int i = 0; i < 4; ++i) {
        float fi = float(i);
        float phase = iTime * (0.22 + mids * 0.55) + fi * 1.7;
        float y = sin(p.x * (3.0 + fi * 0.8) + phase) * (0.10 + mids * 0.13);
        y += sin(p.x * (7.0 + fi) - phase * 1.35) * (0.025 + highs * 0.035);
        y += (fi - 1.5) * 0.085;
        float width = 0.016 + bass * 0.035 + fi * 0.002;
        float ribbon = 1.0 - smoothstep(width, width * 2.8, abs(p.y - y));
        float flow = smoothstep(-0.62, -0.18, p.x) * smoothstep(0.62, 0.18, p.x);
        float a = ribbon * flow * (0.18 + bass * 0.24 + highs * 0.14);
        alpha += a;
        color += mix(palette(fract(fi * 0.23 + p.x * 0.35 + iTime * 0.03)), iReadable.rgb, 0.14 + highs * 0.12) * a;
    }
    alpha = clamp(alpha, 0.0, 0.88);
    color = alpha > 0.001 ? color / alpha : iAccent.rgb;
    return premul(color, alpha);
}
"""

private const val FrequencyTerrainShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 uv = coord / iResolution;
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float sceneMask = smoothstep(0.02, 0.12, uv.x) *
        smoothstep(0.98, 0.88, uv.x) *
        smoothstep(0.12, 0.24, uv.y) *
        smoothstep(0.88, 0.76, uv.y);
    float depth = smoothstep(0.18, 0.86, uv.y);
    float perspective = 1.0 - depth;
    float gridZ = fract(depth * 11.0 + iTime * (0.42 + iEnergy.x * 0.7));
    float freq = clamp(uv.x, 0.0, 0.999);
    float band = bandAt(freq);
    float baseY = mix(0.72, 0.42, perspective);
    float lift = band * (0.16 + perspective * 0.24);
    float terrainY = clamp(baseY - lift - sin(freq * 18.0 + iTime * 1.2) * 0.018 * iEnergy.y, 0.20, 0.78);
    float ridge = 1.0 - smoothstep(0.006, 0.036, abs(uv.y - terrainY));
    float shadow = smoothstep(terrainY + 0.018, terrainY + 0.17, uv.y) * smoothstep(0.86, 0.42, uv.y) * band * 0.20;
    float grid = (1.0 - smoothstep(0.0, 0.014, abs(gridZ - 0.5))) * depth * 0.12;
    float vertical = (1.0 - smoothstep(0.0, 0.006, abs(fract(uv.x * 18.0) - 0.5))) * depth * 0.05;
    float alpha = clamp(ridge * (0.66 + band * 0.34) + shadow + grid + vertical, 0.0, 0.9) * sceneMask;
    float3 color = mix(palette(fract(freq + gridZ * 0.08)), iReadable.rgb, ridge * 0.20 + grid * 0.18);
    color = mix(color, iAccent.rgb, band * 0.22 + iEnergy.z * 0.16);
    color *= 0.72 + perspective * 0.76 + band * 0.22;
    return premul(color, alpha);
}
"""

private const val WaveInterferenceShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 p = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float wave = 0.0;
    for (int i = 0; i < 5; ++i) {
        float fi = float(i);
        float2 source = float2(cos(fi * 2.1 + iTime * 0.17), sin(fi * 1.7 - iTime * 0.13)) * (0.18 + fi * 0.055);
        wave += sin(length(p - source) * (16.0 + fi * 2.2 + mids * 8.0) - iTime * (1.1 + bass * 2.2) - fi);
    }
    wave /= 5.0;
    float contour = 1.0 - smoothstep(0.018 + highs * 0.008, 0.060 + highs * 0.020, abs(wave));
    float glow = smoothstep(0.62, 0.12, length(p)) * (0.10 + bass * 0.22);
    float alpha = clamp(contour * (0.34 + mids * 0.22) + glow, 0.0, 0.76);
    float3 color = mix(palette(fract(wave * 0.35 + length(p) * 0.7 + iTime * 0.025)), iReadable.rgb, contour * 0.18 + highs * 0.14);
    return premul(color, alpha);
}
"""

private const val VinylGrooveShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 p = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    float radius = length(p);
    if (iActive < 0.5) {
        float ring = edgeMask(p, 0.36, 0.012);
        return premul(iIdle.rgb, iIdle.a * ring);
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float angle = atan(p.y, p.x);
    float spin = angle + iTime * (0.16 + bass * 0.18);
    float groove = abs(fract(radius * (58.0 + mids * 28.0) - iTime * 0.18) - 0.5);
    float rings = (1.0 - smoothstep(0.012, 0.036, groove)) * smoothstep(0.08, 0.16, radius) * smoothstep(0.58, 0.46, radius);
    float shine = pow(max(0.0, cos(spin - 0.8)), 18.0) * smoothstep(0.18, 0.34, radius) * smoothstep(0.56, 0.42, radius);
    float dust = smoothstep(0.985 - highs * 0.04, 1.0, hash21(floor((p + 0.7) * 52.0) + floor(iTime * 3.0))) * (0.10 + highs * 0.22);
    float label = smoothstep(0.14 + bass * 0.035, 0.04, radius) * (0.20 + bass * 0.16);
    float alpha = clamp(rings * (0.32 + bass * 0.24) + shine * (0.32 + highs * 0.26) + dust + label, 0.0, 0.84);
    float3 color = mix(palette(fract(radius * 1.8 + spin * 0.08)), iReadable.rgb, shine * 0.32 + dust * 0.44);
    return premul(color, alpha);
}
"""

private const val ParticleFieldShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 uv = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float ring = edgeMask(uv, 0.18 + bass * 0.08, 0.018 + highs * 0.010) * (0.12 + mids * 0.18);
    ring += edgeMask(uv, 0.34 + bass * 0.06, 0.014 + highs * 0.014) * (0.10 + highs * 0.20);
    float alpha = ring;
    float3 color = mix(palette(fract(iTime * 0.05 + mids * 0.2)), iReadable.rgb, 0.22) * ring;
    for (int i = 0; i < 64; ++i) {
        float fi = float(i);
        float seed = hash21(float2(fi, fi * 1.73));
        float orbit = iTime * (0.22 + mids * 1.15) + seed * 6.2831853;
        float radius = 0.05 + seed * 0.32 + bass * 0.08;
        float2 p = float2(cos(orbit + seed * 2.0), sin(orbit * (0.72 + seed * 0.45))) * radius;
        float particleSize = 0.026 + highs * 0.042 + (1.0 - seed) * 0.018;
        float spark = 1.0 - smoothstep(0.002, particleSize, length(uv - p));
        float halo = 1.0 - smoothstep(particleSize, particleSize * 3.4, length(uv - p));
        float weight = 0.22 + bandAt(fract(seed + fi * 0.037)) * 0.78;
        float a = (spark * 0.92 + halo * 0.24) * weight * (0.46 + highs * 0.82);
        alpha += a;
        float3 sparkColor = mix(palette(fract(seed + highs * 0.36 + mids * 0.18)), iReadable.rgb, 0.20 + highs * 0.18);
        color += sparkColor * a;
    }
    float core = smoothstep(0.34 + bass * 0.20, 0.0, length(uv)) * (0.12 + bass * 0.16);
    alpha = clamp(alpha + core, 0.0, 0.92);
    color = alpha > 0.001 ? color / alpha : iAccent.rgb;
    color = mix(color, iAccent.rgb, highs * 0.24);
    return premul(color, alpha);
}
"""
