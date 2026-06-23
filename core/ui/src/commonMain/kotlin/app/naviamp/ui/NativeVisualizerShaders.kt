package app.naviamp.ui

internal enum class NativeShaderDialect {
    GlslEs300,
}

internal data class NativeVisualizerShaderDefinition(
    val visualizer: NaviampVisualizer,
    val dialect: NativeShaderDialect,
    val canonicalName: String,
    val fragmentSource: String,
    val requiresNativeRenderer: Boolean = true,
)

internal val NaviampVisualizer.nativeShaderDefinition: NativeVisualizerShaderDefinition?
    get() = when (this) {
        NaviampVisualizer.AnalogSignalFailure -> NativeVisualizerShaderDefinition(
            visualizer = this,
            dialect = NativeShaderDialect.GlslEs300,
            canonicalName = "Analog Signal Failure.glsl",
            fragmentSource = NativeGlslShaderSources.AnalogSignalFailure,
        )
        NaviampVisualizer.FluidicNebulae -> NativeVisualizerShaderDefinition(
            visualizer = this,
            dialect = NativeShaderDialect.GlslEs300,
            canonicalName = "Fluidic Nebulae.glsl",
            fragmentSource = NativeGlslShaderSources.FluidicNebulae,
        )
        NaviampVisualizer.OceanHorizon -> NativeVisualizerShaderDefinition(
            visualizer = this,
            dialect = NativeShaderDialect.GlslEs300,
            canonicalName = "Ocean Horizon.glsl",
            fragmentSource = NativeGlslShaderSources.OceanHorizon,
        )
        NaviampVisualizer.OceanOfInk -> NativeVisualizerShaderDefinition(
            visualizer = this,
            dialect = NativeShaderDialect.GlslEs300,
            canonicalName = "Ocean of Ink.glsl",
            fragmentSource = NativeGlslShaderSources.OceanOfInk,
        )
        NaviampVisualizer.RaymarchedSphereLiquid -> NativeVisualizerShaderDefinition(
            visualizer = this,
            dialect = NativeShaderDialect.GlslEs300,
            canonicalName = "Raymarched Sphere Liquid.glsl",
            fragmentSource = NativeGlslShaderSources.RaymarchedSphereLiquid,
        )
        NaviampVisualizer.AlbumArtReactive,
        NaviampVisualizer.AudioSphere,
        NaviampVisualizer.AudioTunnel,
        NaviampVisualizer.FluidGradient,
        NaviampVisualizer.FrequencyTerrain,
        NaviampVisualizer.FftMountain,
        NaviampVisualizer.ParticleField,
        NaviampVisualizer.ParticleGalaxy,
        NaviampVisualizer.PixelMountain,
        NaviampVisualizer.PixelRidge,
        NaviampVisualizer.ReactiveBars,
        NaviampVisualizer.RibbonTrail,
        NaviampVisualizer.SpectralRidge,
        NaviampVisualizer.VinylGroove,
        NaviampVisualizer.WaveInterference -> null
    }

internal object NativeGlslShaderSources {
    const val AnalogSignalFailure = """#version 300 es
precision highp float;

uniform float u_time;
uniform vec2 u_resolution;
uniform float u_energyLevel;
uniform float u_bassLevel;
uniform float u_trebleLevel;
uniform float u_beatDetected;
uniform vec4 u_accent;
uniform vec4 u_readable;
uniform vec4 u_colorA;
uniform vec4 u_colorB;
uniform vec4 u_colorC;
uniform sampler2D u_frequencyTexture;
uniform sampler2D u_albumArtTexture;
uniform vec2 u_albumArtSize;

out vec4 outColor;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = gl_FragCoord.xy / max(u_resolution.xy, vec2(1.0));
    vec2 originalUv = uv;
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);
    float barrelAmount = 1.0 + pow(dist, 2.0) * (0.2 + u_bassLevel * 0.3);
    uv = center + (uv - center) / barrelAmount;

    float rollSpeed = u_bassLevel * u_bassLevel * 2.0;
    uv.y += sin(u_time * 0.5 + uv.x * 3.0) * rollSpeed * 0.1;
    uv.y = fract(uv.y);

    float onsetSignal = clamp(u_beatDetected + u_trebleLevel * 0.45, 0.0, 1.0);
    float onsetShear = (sin(uv.y * 20.0 + u_time * 10.0) * 0.05) * onsetSignal;
    float signalR = texture(u_frequencyTexture, vec2(clamp(uv.x + onsetShear * 2.0, 0.0, 1.0), 0.5)).r;
    float signalG = texture(u_frequencyTexture, vec2(clamp(uv.x, 0.0, 1.0), 0.5)).r;
    float signalB = texture(u_frequencyTexture, vec2(clamp(uv.x - onsetShear * 2.0, 0.0, 1.0), 0.5)).r;

    float traceWidth = 0.018 + u_trebleLevel * 0.018;
    float signalLift = 0.18 + u_energyLevel * 0.18;
    float waveR = 0.5 + (signalR - 0.5) * 0.82 + sin(uv.x * 42.0 + u_time * 7.0) * signalLift * 0.10;
    float waveG = 0.5 + (signalG - 0.5) * 0.76 + sin(uv.x * 36.0 - u_time * 5.0) * signalLift * 0.08;
    float waveB = 0.5 + (signalB - 0.5) * 0.70 + sin(uv.x * 50.0 + u_time * 3.0) * signalLift * 0.07;
    float lineR = smoothstep(traceWidth, 0.0, abs(uv.y - waveR));
    float lineG = smoothstep(traceWidth, 0.0, abs(uv.y - waveG));
    float lineB = smoothstep(traceWidth, 0.0, abs(uv.y - waveB));
    float baseline = smoothstep(0.035, 0.0, abs(uv.y - 0.5)) * (0.10 + u_energyLevel * 0.16);

    vec3 channelR = mix(u_accent.rgb, u_readable.rgb, 0.18);
    vec3 channelG = mix(u_colorB.rgb, u_accent.rgb, 0.38);
    vec3 channelB = mix(u_colorC.rgb, u_readable.rgb, 0.12);
    vec3 color = channelR * lineR + channelG * lineG + channelB * lineB;
    color += mix(u_colorA.rgb, u_readable.rgb, 0.22) * baseline;

    color += u_beatDetected * mix(u_accent.rgb, u_readable.rgb, 0.35) * 0.35;
    float snow = rand(uv + mod(u_time, 1.0)) * u_trebleLevel * 0.65;
    color += snow * mix(u_readable.rgb, u_accent.rgb, 0.25);

    float ghostSignal = texture(u_frequencyTexture, vec2(originalUv.x, 0.5)).r;
    float ghostLine = smoothstep(0.12, 0.0, abs(originalUv.y - 0.5 - (ghostSignal - 0.5) * 0.8));
    color += ghostLine * mix(u_colorA.rgb, u_colorB.rgb, 0.55) * (0.16 + u_energyLevel * 0.22);

    float signalColumn = max(max(signalR, signalG), signalB);
    float tearing = step(0.985, rand(vec2(floor(originalUv.y * 160.0), floor(u_time * 18.0)))) * (0.12 + onsetSignal * 0.24);
    color += mix(u_accent.rgb, u_readable.rgb, 0.45) * tearing * smoothstep(0.18, 0.90, signalColumn);

    vec3 albumColor = texture(u_albumArtTexture, clamp(originalUv, 0.0, 1.0)).rgb;
    vec3 backing = mix(mix(u_colorA.rgb, u_colorB.rgb, originalUv.x), u_colorC.rgb, originalUv.y);
    backing = mix(backing, albumColor, 0.16 + u_energyLevel * 0.10) * (0.18 + u_energyLevel * 0.10);
    float vignette = clamp(1.0 - pow(dist * 1.1, 2.5), 0.0, 1.0);
    float scanline = 0.8 + fract(gl_FragCoord.y * 0.3) * 0.4;
    color = (backing + color) * vignette * scanline;
    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    private const val NoiseHeader = """#version 300 es
precision highp float;

uniform float u_time;
uniform vec2 u_resolution;
uniform float u_energyLevel;
uniform float u_bassLevel;
uniform float u_midLevel;
uniform float u_trebleLevel;
uniform float u_spectralCentroid;
uniform float u_tempoBpm;
uniform float u_beatDetected;
uniform float u_active;
uniform float u_renderScale;
uniform int u_maxRaymarchSteps;
uniform vec4 u_accent;
uniform vec4 u_readable;
uniform vec4 u_colorA;
uniform vec4 u_colorB;
uniform vec4 u_colorC;
uniform sampler2D u_frequencyTexture;

in vec2 v_uv;
out vec4 outColor;

vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float snoise(vec3 v) {
    const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
    const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);
    vec3 i = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);
    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy;
    vec3 x3 = x0 - D.yyy;
    i = mod289(i);
    vec4 p = permute(permute(permute(
        i.z + vec4(0.0, i1.z, i2.z, 1.0))
        + i.y + vec4(0.0, i1.y, i2.y, 1.0))
        + i.x + vec4(0.0, i1.x, i2.x, 1.0));
    float n_ = 0.142857142857;
    vec3 ns = n_ * D.wyz - D.xzx;
    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);
    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);
    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);
    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);
    vec4 s0 = floor(b0) * 2.0 + 1.0;
    vec4 s1 = floor(b1) * 2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));
    vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;
    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);
    vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;
    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}

vec3 playerBackground(vec2 uv) {
    float radial = smoothstep(0.90, 0.06, distance(uv, vec2(0.5)));
    vec3 gradient = mix(mix(u_colorA.rgb, u_colorB.rgb, uv.x), u_colorC.rgb, uv.y * 0.64);
    vec3 album = texture(u_albumArtTexture, clamp(uv, 0.0, 1.0)).rgb;
    gradient = mix(gradient, album, 0.18 + radial * 0.12);
    return mix(gradient * (0.50 + radial * 0.30), u_accent.rgb, radial * 0.08);
}

vec3 albumArtColor(vec2 uv) {
    return texture(u_albumArtTexture, clamp(uv, 0.0, 1.0)).rgb;
}
"""

    const val FluidicNebulae = NoiseHeader + """
void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution.xy) / max(u_resolution.y, 1.0);
    float time = u_time * 0.1;
    vec2 warpUv = uv * (1.5 + u_bassLevel * 0.5);
    vec2 q = vec2(
        snoise(vec3(warpUv, time)),
        snoise(vec3(warpUv.x, warpUv.y + 2.0, time))
    );
    vec2 r = vec2(
        snoise(vec3(warpUv * (2.0 + u_trebleLevel * 3.0) + q * u_energyLevel, time)),
        snoise(vec3(warpUv * 2.0 + q, time + 5.0))
    );

    float noise = snoise(vec3(warpUv + r * 0.5, time * 0.5));
    noise = (noise + 1.0) * 0.5;
    noise = pow(noise, 2.5);

    vec3 color1 = mix(u_accent.rgb, u_readable.rgb, 0.18);
    vec3 color2 = mix(u_colorA.rgb, u_colorB.rgb, 0.45);
    vec3 color3 = mix(u_colorC.rgb, u_accent.rgb, 0.42);
    vec3 palette = mix(mix(color2, color1, u_spectralCentroid), color3, u_spectralCentroid - 0.5);
    palette = mix(palette, albumArtColor(gl_FragCoord.xy / max(u_resolution.xy, vec2(1.0))), 0.18 + u_energyLevel * 0.10);
    vec3 color = noise * palette;
    color = mix(color, u_accent.rgb * noise, 0.10 + u_trebleLevel * 0.10);
    outColor = vec4(color, 1.0);
}
"""

    const val OceanHorizon = NoiseHeader + """
float getWaterHeight(vec2 pos, float bass, float treble, float beatDecay) {
    float waves = snoise(vec3(pos * 0.2, u_time * 0.1)) * 0.8 * bass;
    waves += snoise(vec3(pos * 1.2, u_time * 0.4)) * 0.15 * (0.5 + treble);

    float freqIndex = fract(pos.x * 0.1);
    float spectralEnergy = texture(u_frequencyTexture, vec2(freqIndex, 0.5)).r;
    waves += sin(pos.y * 2.0 + u_time) * spectralEnergy * 0.3;

    float distFromCenter = length(pos);
    float beatRadius = beatDecay * 9.0;
    float shockwave = exp(-abs(distFromCenter - beatRadius) * 0.8) * 0.5 * beatDecay;
    return waves + shockwave;
}

float mapWater(vec3 p, float bass, float treble, float beatDecay) {
    return p.y - getWaterHeight(p.xz, bass, treble, beatDecay);
}

vec3 waterNormal(vec3 p, float bass, float treble, float beatDecay) {
    vec2 e = vec2(0.005, 0.0);
    return normalize(vec3(
        mapWater(p - e.xyy, bass, treble, beatDecay) - mapWater(p + e.xyy, bass, treble, beatDecay),
        2.0 * e.x,
        mapWater(p - e.yxy, bass, treble, beatDecay) - mapWater(p + e.yxy, bass, treble, beatDecay)
    ));
}

vec3 skyColor(vec3 rd, float spectralMix) {
    float sunGlow = pow(max(0.0, dot(rd, normalize(vec3(0.5, 0.2, 0.5)))), 10.0);
    vec3 skyGradient = mix(vec3(0.1, 0.2, 0.4), vec3(0.5, 0.7, 1.0), max(0.0, rd.y));
    vec3 sunColor = mix(vec3(1.0, 0.2, 0.5), vec3(1.0, 0.8, 0.2), spectralMix);
    return skyGradient + sunColor * sunGlow;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / max(u_resolution, vec2(1.0));
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution.xy) / max(u_resolution.y, 1.0);
    float bass = u_bassLevel;
    float treble = u_trebleLevel;
    float spectralMix = u_spectralCentroid;
    float beatDecay = pow(1.0 - fract(u_time * 0.5), 8.0) * u_beatDetected;

    float horizon = 0.57 + bass * 0.035;
    float distanceFromHorizon = max(horizon - screenUv.y, 0.0);
    float perspective = 1.0 / (0.035 + distanceFromHorizon * 2.8);
    vec2 waterPos = vec2(
        uv.x * perspective * 1.8 + u_time * 0.16,
        perspective * 1.35 + u_time * 0.22
    );

    float surface = getWaterHeight(waterPos, 0.55 + bass * 0.85, treble, beatDecay);
    float fineInk = snoise(vec3(waterPos * vec2(1.6, 0.55), u_time * 0.18));
    float freq = texture(u_frequencyTexture, vec2(fract(waterPos.x * 0.055 + fineInk * 0.08), 0.5)).r;
    float lane = sin(waterPos.y * 4.8 + surface * 2.4 + u_time * 1.2);
    float foam = smoothstep(0.55, 0.98, lane * 0.5 + 0.5) * (0.12 + freq * 0.70 + treble * 0.28);
    float spec = pow(max(0.0, snoise(vec3(waterPos * 2.4, u_time * 0.35)) * 0.5 + 0.5), 8.0) * (0.12 + treble * 0.88);

    float waterMask = smoothstep(horizon + 0.035, horizon - 0.035, screenUv.y + surface * 0.018);
    vec3 sky = mix(vec3(0.05, 0.08, 0.18), vec3(0.38, 0.53, 0.80), smoothstep(horizon, 1.0, screenUv.y));
    sky = mix(sky, albumArtColor(vec2(screenUv.x, 0.12 + screenUv.y * 0.30)), 0.10);
    sky += mix(vec3(0.55, 0.10, 0.35), vec3(1.0, 0.72, 0.20), spectralMix) *
        pow(max(0.0, 1.0 - length(uv - vec2(0.18, 0.24)) * 1.8), 4.0) *
        (0.10 + treble * 0.35);

    vec3 deepInk = mix(vec3(0.0, 0.025, 0.075), vec3(0.16, 0.045, 0.0), spectralMix);
    vec3 shallowInk = mix(vec3(0.02, 0.14, 0.24), vec3(0.34, 0.10, 0.03), spectralMix);
    float depthFade = smoothstep(0.0, 0.78, distanceFromHorizon);
    vec3 water = mix(shallowInk, deepInk, depthFade);
    water = mix(water, albumArtColor(vec2(screenUv.x, 0.55 + screenUv.y * 0.35)), 0.08 + freq * 0.06);
    water += mix(vec3(0.15, 0.72, 1.0), vec3(1.0, 0.70, 0.25), spectralMix) * foam;
    water += vec3(1.0, 0.92, 0.72) * spec * (0.18 + freq * 0.72);
    water += u_accent.rgb * freq * (0.05 + bass * 0.10);

    float horizonGlow = exp(-abs(screenUv.y - horizon) * 42.0) * (0.18 + bass * 0.24);
    vec3 color = mix(sky, water, waterMask);
    color += mix(u_accent.rgb, vec3(1.0, 0.75, 0.36), spectralMix) * horizonGlow;
    color += vec3(1.0, 0.85, 0.55) * beatDecay * smoothstep(0.44, 0.0, length(uv)) * 0.40;
    color = pow(max(color, vec3(0.0)), vec3(0.4545));
    outColor = vec4(color, 1.0);
}
"""

    const val OceanOfInk = """#version 300 es
precision highp float;

out vec4 outColor;

uniform float u_time;
uniform vec2 u_resolution;
uniform float u_bassLevel;
uniform float u_trebleLevel;
uniform float u_spectralCentroid;
uniform float u_beatDetected;
uniform sampler2D u_frequencyTexture;
uniform sampler2D u_albumArtTexture;
uniform vec2 u_albumArtSize;

vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x*34.0)+1.0)*x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
float snoise(vec3 v) {
    const vec2 C = vec2(1.0/6.0, 1.0/3.0); const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);
    vec3 i  = floor(v + dot(v, C.yyy)); vec3 x0 = v - i + dot(i, C.xxx);
    vec3 g = step(x0.yzx, x0.xyz); vec3 l = 1.0 - g; vec3 i1 = min( g.xyz, l.zxy ); vec3 i2 = max( g.xyz, l.zxy );
    vec3 x1 = x0 - i1 + C.xxx; vec3 x2 = x0 - i2 + C.yyy; vec3 x3 = x0 - D.yyy;
    i = mod289(i);
    vec4 p = permute( permute( permute( i.z + vec4(0.0, i1.z, i2.z, 1.0 )) + i.y + vec4(0.0, i1.y, i2.y, 1.0 )) + i.x + vec4(0.0, i1.x, i2.x, 1.0 ));
    float n_ = 0.142857142857; vec3 ns = n_ * D.wyz - D.xzx;
    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);
    vec4 x_ = floor(j * ns.z); vec4 y_ = floor(j - 7.0 * x_ );
    vec4 x = x_ *ns.x + ns.yyyy; vec4 y = y_ *ns.x + ns.yyyy; vec4 h = 1.0 - abs(x) - abs(y);
    vec4 b0 = vec4( x.xy, y.xy ); vec4 b1 = vec4( x.zw, y.zw );
    vec4 s0 = floor(b0)*2.0 + 1.0; vec4 s1 = floor(b1)*2.0 + 1.0; vec4 sh = -step(h, vec4(0.0));
    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy; vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;
    vec3 p0 = vec3(a0.xy,h.x); vec3 p1 = vec3(a0.zw,h.y); vec3 p2 = vec3(a1.xy,h.z); vec3 p3 = vec3(a1.zw,h.w);
    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2,p2), dot(p3,p3)));
    p0 *= norm.x; p1 *= norm.y; p2 *= norm.z; p3 *= norm.w;
    vec4 m = max(0.6 - vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)), 0.0);
    m = m * m; return 42.0 * dot( m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)) );
}

float getWaterHeight(vec2 pos, float s_bass, float s_treble, float s_beat_decay) {
    float waves = snoise(vec3(pos * 0.2, u_time * 0.1)) * 0.8 * s_bass;
    waves += snoise(vec3(pos * 1.2, u_time * 0.4)) * 0.15 * (0.5 + s_treble);
    float freq_index = fract(pos.x * 0.1);
    float spectral_energy = texture(u_frequencyTexture, vec2(freq_index, 0.5)).r;
    waves += sin(pos.y * 2.0 + u_time) * spectral_energy * 0.3;
    float dist_from_center = length(pos);
    float beat_radius = s_beat_decay * 10.0;
    float shockwave = exp(-abs(dist_from_center - beat_radius) * 0.8) * 0.5 * s_beat_decay;
    return waves + shockwave;
}

float map(vec3 p, float s_bass, float s_treble, float s_beat_decay) {
    return p.y - getWaterHeight(p.xz, s_bass, s_treble, s_beat_decay);
}

vec3 getNormal(vec3 p, float s_bass, float s_treble, float s_beat_decay) {
    vec2 e = vec2(0.005, 0.0);
    return normalize(vec3(
        map(p - e.xyy, s_bass, s_treble, s_beat_decay) - map(p + e.xyy, s_bass, s_treble, s_beat_decay),
        2.0 * e.x,
        map(p - e.yxy, s_bass, s_treble, s_beat_decay) - map(p + e.yxy, s_bass, s_treble, s_beat_decay)
    ));
}

vec3 getSkyColor(vec3 rd, float s_centroid) {
    float sun_glow = pow(max(0.0, dot(rd, normalize(vec3(0.5, 0.2, 0.5)))), 10.0);
    vec3 sky_gradient = mix(vec3(0.1, 0.2, 0.4), vec3(0.5, 0.7, 1.0), max(0.0, rd.y));
    vec3 sun_color = mix(vec3(1.0, 0.2, 0.5), vec3(1.0, 0.8, 0.2), s_centroid);
    return sky_gradient + sun_color * sun_glow;
}

vec3 albumArtColor(vec2 uv) {
    return texture(u_albumArtTexture, clamp(uv, 0.0, 1.0)).rgb;
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution.xy) / u_resolution.y;
    vec2 screenUv = gl_FragCoord.xy / max(u_resolution.xy, vec2(1.0));

    float s_bass = u_bassLevel;
    float s_treble = u_trebleLevel;
    float s_centroid = u_spectralCentroid;
    float s_beat_decay = pow(1.0 - fract(u_time * 0.5), 8.0) * u_beatDetected;

    vec3 ro = vec3(u_time * 0.2, 1.0, 0.0);
    vec3 look_at = ro + vec3(0.0, -0.2, -1.0);
    vec3 forward = normalize(look_at - ro);
    vec3 right = normalize(cross(vec3(0,1,0), forward));
    vec3 up = cross(forward, right);
    mat3 viewMatrix = mat3(right, up, forward);
    vec3 rd = viewMatrix * normalize(vec3(uv, 1.8));

    vec3 color = vec3(0.0);
    float t = 0.0;
    for(int i=0; i < 60; i++) {
        vec3 p = ro + rd * t;
        float d = map(p, s_bass, s_treble, s_beat_decay);

        if (d < 0.005) {
            vec3 n = getNormal(p, s_bass, s_treble, s_beat_decay);
            vec3 reflected_dir = reflect(rd, n);
            vec3 reflection_color = getSkyColor(reflected_dir, s_centroid);
            vec3 deep_color = mix(vec3(0.0, 0.1, 0.2), vec3(0.3, 0.1, 0.0), s_centroid);
            deep_color = mix(deep_color, albumArtColor(vec2(screenUv.x, 0.58 + screenUv.y * 0.28)), 0.12);
            reflection_color = mix(reflection_color, albumArtColor(vec2(screenUv.x, 0.12 + screenUv.y * 0.34)), 0.10);
            float fresnel = 0.05 + 0.95 * pow(1.0 - max(0.0, dot(-rd, n)), 5.0);
            color = mix(deep_color, reflection_color, fresnel);
            float specular = pow(max(0.0, dot(reflected_dir, normalize(vec3(0.5, 0.5, 0.5)))), 64.0);
            color += vec3(1.0) * specular * (1.0 + s_treble);
            float foam_amount = pow(1.0 - n.y, 4.0) * (0.5 + s_treble);
            color = mix(color, vec3(1.0), foam_amount);
            break;
        }
        if (t > 30.0) {
             color = getSkyColor(rd, s_centroid);
             color = mix(color, albumArtColor(vec2(screenUv.x, 0.10 + screenUv.y * 0.30)), 0.10);
             break;
        }
        t += d * 0.9;
    }

    color = pow(color, vec3(0.4545));
    outColor = vec4(color, 1.0);
}
"""

    const val RaymarchedSphereLiquid = """#version 300 es
precision highp float;

out vec4 outColor;

uniform float u_time;
uniform vec2 u_resolution;
uniform float u_energyLevel;
uniform float u_bassLevel;
uniform float u_trebleLevel;
uniform float u_spectralCentroid;
uniform float u_beatDetected;
uniform int u_maxRaymarchSteps;
uniform vec4 u_accent;
uniform vec4 u_readable;
uniform vec4 u_colorA;
uniform vec4 u_colorB;
uniform vec4 u_colorC;

float sdSphere(vec3 p, float r) {
    return length(p) - r;
}

float map(vec3 p) {
    float sphere = sdSphere(p, 1.0 + u_bassLevel * 0.2);
    float wave1 = sin(p.x * 3.0 + p.y * 4.0 + u_time * 1.5) * 0.5;
    float wave2 = sin(p.y * 2.0 - p.z * 5.0 - u_time * 1.1) * 0.5;
    float fluidDeformation = (wave1 + wave2) * 0.5 * u_energyLevel * 0.4;
    float ripples = sin(p.z * 15.0 + u_time * 5.0) * sin(p.x * 12.0 - u_time * 7.0);
    ripples *= u_trebleLevel * 0.05;
    float beatShockwave = sin(p.x * 30.0 + p.y * 30.0) * u_beatDetected * 0.05;
    return sphere - fluidDeformation - ripples - beatShockwave;
}

vec3 getNormal(vec3 p) {
    const float h = 0.001;
    return normalize(vec3(
        map(p + vec3(h, 0.0, 0.0)) - map(p - vec3(h, 0.0, 0.0)),
        map(p + vec3(0.0, h, 0.0)) - map(p - vec3(0.0, h, 0.0)),
        map(p + vec3(0.0, 0.0, h)) - map(p - vec3(0.0, 0.0, h))
    ));
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution.xy) / min(u_resolution.y, u_resolution.x);
    vec3 ro = vec3(0.0, 0.0, 3.25);
    vec3 rd = normalize(vec3(uv, -1.0));

    float t = 0.0;
    bool hit = false;
    for (int i = 0; i < 80; i++) {
        if (i >= u_maxRaymarchSteps) break;
        vec3 p = ro + rd * t;
        float d = map(p);
        if (d < 0.001) {
            hit = true;
            break;
        }
        if (t > 20.0) break;
        t += d;
    }

    float radial = smoothstep(0.95, 0.08, length(uv));
    vec2 screenUv = gl_FragCoord.xy / max(u_resolution.xy, vec2(1.0));
    vec3 albumTint = texture(u_albumArtTexture, clamp(screenUv, 0.0, 1.0)).rgb;
    vec3 background = mix(mix(u_colorA.rgb, u_colorB.rgb, screenUv.x), u_colorC.rgb, screenUv.y);
    background = mix(background, albumTint, 0.16);
    background *= 0.24 + radial * 0.20;
    vec3 color = background;

    if (hit) {
        vec3 p = ro + rd * t;
        vec3 normal = getNormal(p);
        vec3 lightDir = normalize(vec3(0.8, 1.0, 0.5));
        float lighting = max(0.2, dot(normal, lightDir));
        float fresnel = pow(1.0 - abs(dot(normal, -rd)), 4.0);

        vec3 baseColor = mix(u_colorA.rgb, u_colorB.rgb, 0.35 + u_bassLevel * 0.35);
        baseColor = mix(baseColor, albumTint, 0.22 + u_spectralCentroid * 0.10);
        baseColor = mix(baseColor, u_accent.rgb, 0.30 + u_spectralCentroid * 0.25);
        vec3 reflectionColor = mix(u_readable.rgb, u_colorC.rgb, 0.35) * (0.55 + u_trebleLevel * 0.65);
        color = mix(baseColor, reflectionColor, fresnel);
        color *= lighting;
        color += u_accent.rgb * u_beatDetected * 0.35;
        color += reflectionColor * fresnel * (0.18 + u_trebleLevel * 0.32);
    }

    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""
}
