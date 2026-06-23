package app.naviamp.ui

internal val NaviampVisualizer.shaderSource: String
    get() = when (this) {
        NaviampVisualizer.ReactiveBars -> ReactiveBarsShaderSkSL
        NaviampVisualizer.FluidGradient -> FluidGradientShaderSkSL
        NaviampVisualizer.AudioSphere -> AudioSphereShaderSkSL
        NaviampVisualizer.AudioTunnel -> AudioTunnelShaderSkSL
        NaviampVisualizer.RibbonTrail -> RibbonTrailShaderSkSL
        NaviampVisualizer.SpectralRidge -> SpectralRidgeShaderSkSL
        NaviampVisualizer.FftMountain -> FftMountainShaderSkSL
        NaviampVisualizer.PixelRidge -> PixelRidgeShaderSkSL
        NaviampVisualizer.PixelMountain -> PixelMountainShaderSkSL
        NaviampVisualizer.FrequencyTerrain -> FrequencyTerrainShaderSkSL
        NaviampVisualizer.ParticleField -> ParticleFieldShaderSkSL
        NaviampVisualizer.ParticleGalaxy -> ParticleGalaxyShaderSkSL
        NaviampVisualizer.AlbumArtReactive -> AlbumArtReactiveShaderSkSL
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
uniform float iTempo;
uniform float2 iAlbumArtSize;
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
    float2 path = float2(
        sin(iTime * 0.10) * 0.020,
        cos(iTime * 0.08) * 0.016
    );
    float2 q = p - path;
    float radius = max(length(q), 0.001);
    float depth = 1.0 / (radius + 0.095);
    float bend = sin(depth * 0.22 - iTime * 0.18) * (0.10 + mids * 0.16);
    float angle = atan(q.y, q.x) + bend;
    float angle01 = fract(angle / 6.2831853 + 0.5);
    float band = bandAt(angle01);
    float bandNear = max(bandAt(fract(angle01 - 0.045)), bandAt(fract(angle01 + 0.045)));
    float response = max(band, bandNear * 0.72);
    float tempoSpeed = smoothstep(80.0, 180.0, clamp(iTempo, 60.0, 220.0));
    float speed = mix(0.34, 0.72, tempoSpeed);
    float travel = depth * 0.42 + iTime * speed;

    float laneCount = 14.0;
    float lanePhase = fract(angle01 * laneCount + sin(depth * 0.18 + iTime * 0.12) * 0.10);
    float laneDistance = min(lanePhase, 1.0 - lanePhase);
    float rail = 1.0 - smoothstep(0.010, 0.050 + response * 0.020, laneDistance);
    float railCore = 1.0 - smoothstep(0.006, 0.018 + response * 0.010, laneDistance);

    float dashPhase = fract(travel * 1.55);
    float dash = 1.0 - smoothstep(0.025, 0.20, min(dashPhase, 1.0 - dashPhase));
    float ribPhase = fract(travel * 1.55 + 0.08);
    float ribDistance = min(ribPhase, 1.0 - ribPhase);
    float rib = 1.0 - smoothstep(0.020, 0.082, ribDistance);
    float ribCore = 1.0 - smoothstep(0.007, 0.024, ribDistance);

    float panel = pow(0.5 + 0.5 * sin(angle * 7.0 + depth * 0.35 - iTime * 0.16), 1.8) *
        (0.030 + response * 0.080);
    float wallMask = smoothstep(0.060, 0.22, radius) * smoothstep(0.82, 0.48, radius) *
        smoothstep(6.5, 1.2, depth);
    float tunnelWall = (rail * (0.20 + response * 0.20) + railCore * dash * (0.22 + highs * 0.22) +
        rib * 0.12 + ribCore * (0.070 + rail * 0.08) + panel) * wallMask;
    float centerOpening = smoothstep(0.035, 0.16, radius);
    float alpha = clamp(tunnelWall * centerOpening, 0.0, 0.88);
    float3 color = palette(fract(angle01 * 0.62 + depth * 0.035 + response * 0.18));
    color = mix(color, iReadable.rgb, railCore * dash * 0.22 + ribCore * 0.14 + highs * 0.08);
    color = mix(color, iAccent.rgb, bass * 0.06 + rib * 0.04);
    color *= 0.68 + response * 0.48 + dash * railCore * 0.28 + ribCore * 0.18;
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

private const val SpectralRidgeShaderSkSL = CommonShaderHeader + """
uniform shader iHistory;

float historyAt(float freq, float depth) {
    float2 sampleCoord = float2(clamp(freq, 0.0, 1.0) * 31.0, clamp(depth, 0.0, 1.0) * 31.0);
    return clamp(iHistory.eval(sampleCoord).r, 0.0, 1.0);
}

half4 main(float2 coord) {
    float2 uv = coord / iResolution;
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float sceneMask = smoothstep(0.02, 0.12, uv.x) *
        smoothstep(0.98, 0.88, uv.x) *
        smoothstep(0.10, 0.22, uv.y) *
        smoothstep(0.94, 0.76, uv.y);
    float x = clamp(uv.x, 0.0, 1.0);
    float z = smoothstep(0.12, 0.92, uv.y);
    float perspective = 1.0 - z;
    float centerPull = 1.0 - abs(x - 0.5) * 2.0;
    float freq = clamp((x - 0.5) / (0.42 + perspective * 0.34) + 0.5, 0.0, 1.0);
    float historyDepth = clamp(z + sin(x * 7.0 + iTime * 0.9) * 0.015 * iEnergy.y, 0.0, 1.0);
    float amp = historyAt(freq, historyDepth);
    float ampLeft = historyAt(clamp(freq - 0.035, 0.0, 1.0), historyDepth);
    float ampRight = historyAt(clamp(freq + 0.035, 0.0, 1.0), historyDepth);
    float ampBack = historyAt(freq, clamp(historyDepth + 0.045, 0.0, 1.0));
    float slope = abs(ampRight - ampLeft) + abs(amp - ampBack);
    float floorY = mix(0.82, 0.36, perspective);
    float ridgeY = floorY - amp * (0.18 + perspective * 0.36) - centerPull * amp * 0.055;
    ridgeY = clamp(ridgeY, 0.16, 0.86);
    float ridge = 1.0 - smoothstep(0.004, 0.026 + perspective * 0.028, abs(uv.y - ridgeY));
    float fill = smoothstep(ridgeY + 0.018, ridgeY + 0.24, uv.y) *
        smoothstep(0.90, 0.36, uv.y) * (0.10 + amp * 0.28);
    float wireZ = 1.0 - smoothstep(0.0, 0.014, abs(fract(z * 15.0 - iTime * (0.25 + iEnergy.x * 0.42)) - 0.5));
    float wireX = 1.0 - smoothstep(0.0, 0.006, abs(fract(freq * 17.0) - 0.5));
    float wire = (wireZ * 0.16 + wireX * 0.08) * smoothstep(0.08, 0.9, z);
    float fog = smoothstep(0.98, 0.18, z);
    float light = clamp(0.48 + amp * 0.72 + slope * 1.6 + perspective * 0.28, 0.0, 1.45);
    float alpha = clamp(ridge * (0.56 + amp * 0.36) + fill + wire, 0.0, 0.94) * sceneMask * fog;
    float3 color = palette(fract(freq * 0.74 + historyDepth * 0.32 + amp * 0.18 + iTime * 0.018));
    color = mix(color, iReadable.rgb, ridge * 0.18 + slope * 0.22);
    color = mix(color, iAccent.rgb, iEnergy.z * 0.16 + amp * 0.10);
    color *= light;
    return premul(color, alpha);
}
"""

private const val PixelRidgeShaderSkSL = CommonShaderHeader + """
uniform shader iHistory;

float historyAt(float freq, float depth) {
    float2 sampleCoord = float2(clamp(freq, 0.0, 1.0) * 31.0, clamp(depth, 0.0, 1.0) * 31.0);
    return clamp(iHistory.eval(sampleCoord).r, 0.0, 1.0);
}

half4 main(float2 coord) {
    float2 uv = coord / iResolution;
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float sceneMask = smoothstep(0.02, 0.12, uv.x) *
        smoothstep(0.98, 0.88, uv.x) *
        smoothstep(0.10, 0.22, uv.y) *
        smoothstep(0.94, 0.76, uv.y);
    float x = clamp(uv.x, 0.0, 1.0);
    float travel = smoothstep(0.12, 0.92, uv.y);
    float perspective = travel;
    float centerPull = 1.0 - abs(x - 0.5) * 2.0;
    float freq = clamp((x - 0.5) / (0.42 + perspective * 0.34) + 0.5, 0.0, 1.0);
    float historyDepth = clamp((1.0 - travel) + sin(x * 7.0 + iTime * 0.9) * 0.015 * iEnergy.y, 0.0, 1.0);
    float amp = historyAt(freq, historyDepth);
    float ampLeft = historyAt(clamp(freq - 0.035, 0.0, 1.0), historyDepth);
    float ampRight = historyAt(clamp(freq + 0.035, 0.0, 1.0), historyDepth);
    float ampBack = historyAt(freq, clamp(historyDepth + 0.045, 0.0, 1.0));
    float slope = abs(ampRight - ampLeft) + abs(amp - ampBack);
    float floorY = mix(0.36, 0.82, perspective);
    float ridgeY = floorY - amp * (0.18 + perspective * 0.36) - centerPull * amp * 0.055;
    ridgeY = clamp(ridgeY, 0.16, 0.86);
    float ridge = 1.0 - smoothstep(0.004, 0.026 + perspective * 0.028, abs(uv.y - ridgeY));
    float fill = smoothstep(ridgeY + 0.018, ridgeY + 0.24, uv.y) *
        smoothstep(0.90, 0.36, uv.y) * (0.10 + amp * 0.28);
    float wireZ = 1.0 - smoothstep(0.0, 0.014, abs(fract(travel * 15.0 + iTime * (0.25 + iEnergy.x * 0.42)) - 0.5));
    float wireX = 1.0 - smoothstep(0.0, 0.006, abs(fract(freq * 17.0) - 0.5));
    float wire = (wireZ * 0.16 + wireX * 0.08) * smoothstep(0.08, 0.9, travel);
    float fog = smoothstep(0.04, 0.88, perspective);
    float light = clamp(0.48 + amp * 0.72 + slope * 1.6 + perspective * 0.28, 0.0, 1.45);
    float alpha = clamp(ridge * (0.56 + amp * 0.36) + fill + wire, 0.0, 0.94) * sceneMask * fog;
    float3 color = palette(fract(freq * 0.74 + historyDepth * 0.32 + amp * 0.18 + iTime * 0.018));
    color = mix(color, iReadable.rgb, ridge * 0.18 + slope * 0.22);
    color = mix(color, iAccent.rgb, iEnergy.z * 0.16 + amp * 0.10);
    color *= light;
    return premul(color, alpha);
}
"""

private const val FftMountainShaderSkSL = CommonShaderHeader + """
uniform shader iHistory;

float historyAt(float freq, float depth) {
    float2 sampleCoord = float2(clamp(freq, 0.0, 1.0) * 31.0, clamp(depth, 0.0, 1.0) * 31.0);
    return clamp(iHistory.eval(sampleCoord).r, 0.0, 1.0);
}

half4 main(float2 coord) {
    float2 uv = coord / iResolution;
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float plotLeft = 0.18;
    float plotRight = 0.82;
    float plotTop = 0.17;
    float plotBottom = 0.85;
    float plotWidth = plotRight - plotLeft;
    float plotHeight = plotBottom - plotTop;
    float sceneMask = smoothstep(plotLeft - 0.06, plotLeft + 0.01, uv.x) *
        smoothstep(plotRight + 0.06, plotRight - 0.01, uv.x) *
        smoothstep(plotTop - 0.04, plotTop + 0.02, uv.y) *
        smoothstep(plotBottom + 0.06, plotBottom - 0.02, uv.y);
    float freq = clamp((uv.x - plotLeft) / plotWidth, 0.0, 1.0);
    float localY = (uv.y - plotTop) / plotHeight;
    float alpha = 0.0;
    float3 color = float3(0.0);

    for (int row = 0; row < 32; ++row) {
        float fr = float(row) / 31.0;
        float depth = fr;
        float perspective = 1.0 - fr;
        float amp = historyAt(freq, depth);
        float ampL = historyAt(clamp(freq - 0.026, 0.0, 1.0), depth);
        float ampR = historyAt(clamp(freq + 0.026, 0.0, 1.0), depth);
        float center = 1.0 - smoothstep(0.02, 0.78, abs(freq - 0.50));
        float sideFalloff = smoothstep(0.0, 0.08, freq) * smoothstep(1.0, 0.92, freq);
        float base = 0.13 + fr * 0.78;
        float profile = pow(amp, 0.62) * (0.16 + perspective * 0.18) * sideFalloff;
        profile += center * amp * (0.045 + perspective * 0.060);
        float yLine = base - profile;
        float slope = abs(ampR - ampL);
        float line = 1.0 - smoothstep(0.0035, 0.011 + perspective * 0.004, abs(localY - yLine));
        float bright = (0.30 + perspective * 0.38 + amp * 0.34 + slope * 0.85);
        float rowFade = smoothstep(1.0, 0.76, fr) * smoothstep(0.0, 0.08, fr);
        float a = line * bright * rowFade;
        alpha += a;
        float3 rowColor = mix(palette(fract(freq * 0.56 + fr * 0.34 + amp * 0.18)), iReadable.rgb, 0.30 + slope * 0.22);
        color += rowColor * a;
    }

    float centerGlow = smoothstep(0.42, 0.0, abs(freq - 0.5)) *
        smoothstep(0.88, 0.24, localY) *
        iEnergy.w * 0.08;
    alpha = clamp(alpha + centerGlow, 0.0, 0.92) * sceneMask;
    color = alpha > 0.001 ? color / max(alpha, 0.001) : iReadable.rgb;
    color = mix(color, iAccent.rgb, iEnergy.z * 0.10);
    return premul(color, alpha);
}
"""

private const val PixelMountainShaderSkSL = CommonShaderHeader + """
uniform shader iHistory;

float historyAt(float freq, float depth) {
    float2 sampleCoord = float2(clamp(freq, 0.0, 1.0) * 31.0, clamp(depth, 0.0, 1.0) * 31.0);
    return clamp(iHistory.eval(sampleCoord).r, 0.0, 1.0);
}

half4 main(float2 coord) {
    float2 uv = coord / iResolution;
    if (iActive < 0.5) {
        float line = 1.0 - smoothstep(0.7, 1.9, abs(coord.y - iResolution.y * 0.5));
        return premul(iIdle.rgb, iIdle.a * line);
    }

    float plotLeft = 0.18;
    float plotRight = 0.82;
    float plotTop = 0.17;
    float plotBottom = 0.85;
    float plotWidth = plotRight - plotLeft;
    float plotHeight = plotBottom - plotTop;
    float sceneMask = smoothstep(plotLeft - 0.06, plotLeft + 0.01, uv.x) *
        smoothstep(plotRight + 0.06, plotRight - 0.01, uv.x) *
        smoothstep(plotTop - 0.04, plotTop + 0.02, uv.y) *
        smoothstep(plotBottom + 0.06, plotBottom - 0.02, uv.y);
    float freq = clamp((uv.x - plotLeft) / plotWidth, 0.0, 1.0);
    float localY = (uv.y - plotTop) / plotHeight;
    float alpha = 0.0;
    float3 color = float3(0.0);

    for (int row = 0; row < 32; ++row) {
        float fr = float(row) / 31.0;
        float depth = fr;
        float perspective = 1.0 - fr;
        float amp = historyAt(freq, depth);
        float ampL = historyAt(clamp(freq - 0.026, 0.0, 1.0), depth);
        float ampR = historyAt(clamp(freq + 0.026, 0.0, 1.0), depth);
        float center = 1.0 - smoothstep(0.02, 0.78, abs(freq - 0.50));
        float sideFalloff = smoothstep(0.0, 0.08, freq) * smoothstep(1.0, 0.92, freq);
        float base = 0.91 - fr * 0.78;
        float profile = pow(amp, 0.62) * (0.16 + perspective * 0.18) * sideFalloff;
        profile += center * amp * (0.045 + perspective * 0.060);
        float yLine = base - profile;
        float slope = abs(ampR - ampL);
        float line = 1.0 - smoothstep(0.0035, 0.011 + perspective * 0.004, abs(localY - yLine));
        float bright = (0.30 + perspective * 0.38 + amp * 0.34 + slope * 0.85);
        float rowFade = smoothstep(1.0, 0.76, fr) * smoothstep(0.0, 0.08, fr);
        float a = line * bright * rowFade;
        alpha += a;
        float3 rowColor = mix(palette(fract(freq * 0.56 + fr * 0.34 + amp * 0.18)), iReadable.rgb, 0.30 + slope * 0.22);
        color += rowColor * a;
    }

    float centerGlow = smoothstep(0.42, 0.0, abs(freq - 0.5)) *
        smoothstep(0.88, 0.24, localY) *
        iEnergy.w * 0.08;
    alpha = clamp(alpha + centerGlow, 0.0, 0.92) * sceneMask;
    color = alpha > 0.001 ? color / max(alpha, 0.001) : iReadable.rgb;
    color = mix(color, iAccent.rgb, iEnergy.z * 0.10);
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

private const val ParticleGalaxyShaderSkSL = CommonShaderHeader + """
half4 main(float2 coord) {
    float2 uv = (coord - iResolution * 0.5) / min(iResolution.x, iResolution.y);
    if (iActive < 0.5) {
        float core = smoothstep(0.20, 0.0, length(uv)) * 0.16;
        float dust = smoothstep(0.46, 0.06, length(uv)) * 0.045;
        return premul(iIdle.rgb, iIdle.a * (core + dust));
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float energy = iEnergy.w;
    float radius = length(uv);
    float angle = atan(uv.y, uv.x);
    float spin = iTime * (0.18 + mids * 0.36);
    float2 rotated = float2(
        uv.x * cos(spin) - uv.y * sin(spin),
        uv.x * sin(spin) + uv.y * cos(spin)
    );
    float diskRadius = length(rotated / float2(1.0, 0.64));
    float spiralA = angle * 2.0 + radius * (15.0 + mids * 10.0) - iTime * (0.46 + mids * 0.88);
    float spiralB = angle * -3.0 + radius * (20.0 + highs * 8.0) + iTime * (0.20 + bass * 0.46);
    float armA = pow(1.0 - smoothstep(0.06, 0.58, abs(sin(spiralA))), 1.55);
    float armB = pow(1.0 - smoothstep(0.05, 0.52, abs(sin(spiralB))), 1.9);
    float dustLane = pow(smoothstep(0.16, 0.02, abs(sin(spiralA * 0.5 + radius * 9.0))), 1.6);
    float diskMask = smoothstep(0.72, 0.05, diskRadius);
    float coreGlow = smoothstep(0.28 + bass * 0.06, 0.0, radius) * (0.36 + bass * 0.42);
    float nebula = diskMask * (0.055 + bass * 0.22) * (0.45 + armA * 1.05 + armB * 0.50);
    float dustShadow = diskMask * dustLane * (0.06 + mids * 0.10);
    float alpha = coreGlow + nebula;
    float3 color = palette(fract(angle * 0.12 + radius * 1.25 + iTime * 0.035)) * nebula;
    color += mix(iAccent.rgb, iReadable.rgb, 0.18 + highs * 0.22) * coreGlow;
    color *= 1.0 - dustShadow * 0.38;

    float2 attractorA = float2(cos(iTime * 0.22), sin(iTime * 0.18)) * float2(0.23, 0.15);
    float2 attractorB = float2(cos(iTime * -0.16 + 2.1), sin(iTime * -0.20 + 1.3)) * float2(0.33, 0.22);
    float2 attractorC = float2(cos(iTime * 0.12 + 4.0), sin(iTime * 0.15 + 3.4)) * float2(0.18, 0.31);
    float attractGlowA = smoothstep(0.20, 0.0, length(uv - attractorA)) * (0.08 + bass * 0.12);
    float attractGlowB = smoothstep(0.16, 0.0, length(uv - attractorB)) * (0.06 + mids * 0.10);
    float attractGlowC = smoothstep(0.14, 0.0, length(uv - attractorC)) * (0.05 + highs * 0.11);
    float attractGlow = attractGlowA + attractGlowB + attractGlowC;
    alpha += attractGlow;
    color += mix(palette(fract(iTime * 0.04 + 0.20)), iReadable.rgb, 0.16) * attractGlowA;
    color += mix(palette(fract(iTime * 0.04 + 0.48)), iReadable.rgb, 0.14) * attractGlowB;
    color += mix(palette(fract(iTime * 0.04 + 0.76)), iReadable.rgb, 0.22) * attractGlowC;

    for (int i = 0; i < 48; ++i) {
        float fi = float(i);
        float seed = hash21(float2(fi * 1.17, fi * 2.41));
        float seedB = hash21(float2(fi * 3.11 + 8.0, fi * 0.83));
        float seedC = hash21(float2(fi * 0.57, fi * 4.19 + 2.0));
        float lane = seed < 0.5 ? -1.0 : 1.0;
        float orbit = iTime * (0.12 + mids * 0.68 + seedC * 0.24) * lane + seed * 6.2831853;
        float starRadius = 0.055 + pow(seedB, 1.45) * (0.43 + bass * 0.055);
        float armOffset = lane * (0.62 + seedC * 0.55) + sin(starRadius * 13.0 + iTime * 0.38) * (0.08 + highs * 0.05);
        float theta = orbit + starRadius * (5.7 + mids * 3.2) + armOffset;
        float flatten = 0.52 + 0.24 * sin(seed * 9.0);
        float2 star = float2(cos(theta), sin(theta) * flatten) * starRadius;
        float clusterPick = fract(seed * 3.0);
        float2 attractor = clusterPick < 0.34 ? attractorA : (clusterPick < 0.67 ? attractorB : attractorC);
        float clusterStrength = smoothstep(0.28, 0.96, seedC) * (0.18 + bass * 0.18 + highs * 0.10);
        star = mix(star, attractor + (star - attractor) * (0.36 + seedB * 0.62), clusterStrength);
        float drift = sin(iTime * (0.62 + seed) + seed * 18.0) * (0.010 + highs * 0.014);
        star += float2(cos(theta * 2.0 + seed), sin(theta * 1.7)) * drift;
        float band = bandAt(fract(seed + starRadius * 1.7));
        float depth = 0.58 + seedC * 0.72;
        float size = (0.0048 + highs * 0.008 + band * 0.009 + (1.0 - seed) * 0.005) * depth;
        float dist = length(uv - star);
        float spark = 1.0 - smoothstep(0.001, size, dist);
        float halo = 1.0 - smoothstep(size, size * (4.2 + bass * 3.2), dist);
        float streak = lineMask(uv, star - normalize(float2(-star.y, star.x) + 0.001) * size * (1.5 + mids * 3.0), star + normalize(float2(-star.y, star.x) + 0.001) * size * (1.5 + mids * 3.0), size * 0.36);
        float twinkle = 0.72 + 0.28 * sin(iTime * (5.0 + seedB * 8.0) + seed * 30.0);
        float a = (spark * (0.70 + highs * 0.60) + halo * (0.08 + bass * 0.16) + streak * highs * 0.18) *
            (0.22 + band * 0.86) * twinkle * smoothstep(0.57, 0.03, length(star));
        alpha += a;
        color += mix(palette(fract(seed + seedC * 0.25 + iTime * 0.035 + band * 0.25)), iReadable.rgb, 0.16 + highs * 0.20) * a;
    }

    float rim = edgeMask(uv, 0.47 + bass * 0.018, 0.018 + highs * 0.012) * (0.04 + mids * 0.08);
    float outerDust = smoothstep(0.60, 0.28, radius) * smoothstep(0.04, 0.26, radius) *
        (0.025 + energy * 0.050) * (0.55 + armA);
    alpha = clamp(alpha + rim + outerDust, 0.0, 0.96);
    color += palette(fract(iTime * 0.05 + mids * 0.30)) * rim;
    color += palette(fract(angle * 0.18 + iTime * 0.025)) * outerDust;
    color = alpha > 0.001 ? color / alpha : iAccent.rgb;
    color = mix(color, iAccent.rgb, highs * 0.16);
    color = clamp(color * (1.02 + bass * 0.10 + highs * 0.08), 0.0, 1.0);
    return premul(color, alpha);
}
"""

private const val AlbumArtReactiveShaderSkSL = CommonShaderHeader + """
uniform shader iAlbumArt;

float2 albumCoverCoord(float2 uv, float2 offset) {
    float2 imageSize = max(iAlbumArtSize, float2(1.0, 1.0));
    float surfaceAspect = iResolution.x / max(iResolution.y, 1.0);
    float imageAspect = imageSize.x / max(imageSize.y, 1.0);
    float2 coverUv = uv;
    if (imageAspect > surfaceAspect) {
        float scale = surfaceAspect / imageAspect;
        coverUv.x = (uv.x - 0.5) * scale + 0.5;
    } else {
        float scale = imageAspect / surfaceAspect;
        coverUv.y = (uv.y - 0.5) * scale + 0.5;
    }
    coverUv = clamp(coverUv + offset, 0.0, 1.0);
    return coverUv * imageSize;
}

float3 albumAt(float2 uv, float2 offset) {
    return clamp(iAlbumArt.eval(albumCoverCoord(uv, offset)).rgb, 0.0, 1.0);
}

float lumaOf(float3 color) {
    return dot(color, float3(0.299, 0.587, 0.114));
}

half4 main(float2 coord) {
    float2 uv = coord / iResolution;
    if (iActive < 0.5) {
        float3 still = albumAt(uv, float2(0.0, 0.0));
        float alphaStill = smoothstep(0.02, 0.15, uv.x) * smoothstep(0.98, 0.85, uv.x) *
            smoothstep(0.02, 0.15, uv.y) * smoothstep(0.98, 0.85, uv.y);
        return premul(mix(still, iReadable.rgb, 0.04), alphaStill * 0.58);
    }

    float bass = iEnergy.x;
    float mids = iEnergy.y;
    float highs = iEnergy.z;
    float energy = iEnergy.w;
    float2 centered = uv - 0.5;
    float radius = length(centered);
    float angle = atan(centered.y, centered.x);
    float flowA = sin(centered.y * (12.0 + mids * 12.0) + iTime * (0.72 + bass * 0.65));
    float flowB = cos(centered.x * (10.0 + highs * 8.0) - iTime * (0.55 + mids * 0.75));
    float radial = sin(radius * (24.0 + bass * 18.0) - iTime * (1.10 + highs * 0.55));
    float2 tangent = normalize(float2(-centered.y, centered.x) + 0.0001);
    float2 outward = normalize(centered + 0.0001);
    float2 warp = tangent * (flowA * 0.012 + radial * 0.010) * (0.35 + mids * 1.2);
    warp += outward * (flowB * 0.009 + bass * 0.018 * sin(angle * 3.0 + iTime)) * (0.35 + bass);

    float3 artBase = albumAt(uv, warp);
    float3 artRed = albumAt(uv, warp + tangent * (0.006 + highs * 0.010));
    float3 artBlue = albumAt(uv, warp - tangent * (0.006 + highs * 0.010));
    float luma = lumaOf(artBase);
    float lumaL = lumaOf(albumAt(uv, warp - float2(0.010, 0.0)));
    float lumaR = lumaOf(albumAt(uv, warp + float2(0.010, 0.0)));
    float lumaU = lumaOf(albumAt(uv, warp - float2(0.0, 0.010)));
    float lumaD = lumaOf(albumAt(uv, warp + float2(0.0, 0.010)));
    float edge = clamp(abs(lumaR - lumaL) + abs(lumaD - lumaU), 0.0, 1.0);
    float depth = smoothstep(0.14, 0.88, luma);
    float shimmer = smoothstep(0.82 - highs * 0.16, 1.0, luma + edge * 0.65);
    float3 chroma = float3(artRed.r, artBase.g, artBlue.b);
    float3 color = mix(artBase, chroma, 0.26 + highs * 0.22);
    color = mix(color, palette(fract(luma + iTime * 0.035 + energy * 0.18)), 0.16 + mids * 0.12);
    color += iReadable.rgb * edge * (0.12 + highs * 0.16);
    color += iAccent.rgb * shimmer * (0.12 + highs * 0.18);
    color *= 0.82 + depth * (0.26 + bass * 0.18);

    float alpha = 0.60 + energy * 0.14 + depth * 0.12;
    float vignette = smoothstep(0.82, 0.24, radius);
    alpha *= 0.78 + vignette * 0.22;

    for (int i = 0; i < 72; ++i) {
        float fi = float(i);
        float seed = hash21(float2(fi * 1.91, fi * 0.71 + 4.0));
        float seedB = hash21(float2(fi * 0.47 + 9.0, fi * 2.63));
        float2 cell = float2(fract(seed * 7.13 + iTime * (0.015 + mids * 0.035)), fract(seedB * 5.37 + iTime * (0.010 + bass * 0.020)));
        float2 emitUv = cell;
        float3 emitColor = albumAt(emitUv, warp * 0.30);
        float emitLuma = lumaOf(emitColor);
        float brightnessGate = smoothstep(0.46 - highs * 0.10, 0.92, emitLuma);
        float band = bandAt(fract(seed + seedB * 0.7));
        float rise = fract(iTime * (0.10 + bass * 0.20 + band * 0.16) + seed);
        float2 path = emitUv + tangent * 0.0;
        path += float2(cos(seed * 6.2831853), sin(seedB * 6.2831853)) * rise * (0.035 + bass * 0.070);
        path += float2(sin(iTime * 0.8 + seed * 9.0), cos(iTime * 0.7 + seedB * 8.0)) * 0.012 * mids;
        float particleSize = 0.006 + band * 0.010 + highs * 0.012;
        float dist = length(uv - path);
        float spark = 1.0 - smoothstep(0.001, particleSize, dist);
        float halo = 1.0 - smoothstep(particleSize, particleSize * (3.0 + bass * 2.0), dist);
        float particleAlpha = (spark * 0.75 + halo * 0.18) * brightnessGate * (0.20 + band * 0.80);
        alpha += particleAlpha;
        color += mix(emitColor, iReadable.rgb, 0.18 + highs * 0.16) * particleAlpha;
    }

    float scan = sin((uv.y + luma * 0.08) * (54.0 + mids * 32.0) + iTime * (2.0 + bass * 2.0));
    color += palette(fract(luma + angle * 0.08 + iTime * 0.05)) * max(scan, 0.0) * edge * (0.035 + highs * 0.055);
    color = alpha > 0.001 ? color / max(alpha, 0.001) : artBase;
    color = clamp(mix(color, artBase, 0.42), 0.0, 1.0);
    return premul(color, clamp(alpha, 0.0, 0.94));
}
"""
