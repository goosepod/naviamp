package app.naviamp.ui

/** Only the three visually inspected effects enter the new path during this migration. */
internal fun naviampGpuVisualizerShader(visualizer: NaviampVisualizer): NaviampGpuVisualizerShader? {
    val glsl = when (visualizer) {
        NaviampVisualizer.AudioSphere -> sphereDirectGlsl()
        NaviampVisualizer.AnalogSignalFailure, NaviampVisualizer.OceanOfInk ->
            requireNotNull(visualizer.nativeShaderDefinition).fragmentSource
        else -> return null
    }
    val metal = NativeMetalShaderTranslator.translateFragmentShader(glsl)
        .replace("packed_float2 albumArtSize;", "packed_float2 albumArtSize;\n    packed_float4 idle;")
        .replace("u_idle", "u.idle")
        .replace("atan(uv.y, uv.x)", "atan2(uv.y, uv.x)")
        .replace("float3 spherePalette(float t) {", "float3 spherePalette(constant NaviampVisualizerUniforms& u, float t) {")
        .replace(" = spherePalette(", " = spherePalette(u, ")
        .replace("float sphereBandAt(float x) {", "float sphereBandAt(texture2d<float> u_frequencyTexture, sampler textureSampler, float x) {")
        .replace(" = sphereBandAt(", " = sphereBandAt(u_frequencyTexture, textureSampler, ")
        .replace(" return;", "")
    return NaviampGpuVisualizerShader(glsl, metal)
}

/** Reuse the authoritative Sphere body and palette; this conversion changes only shader syntax. */
private fun sphereDirectGlsl(): String {
    val helpers = CommonShaderHeader.substringAfter("float hash21(")
        .substringBefore("float bandAtIndex(").let { "float hash21($it" } +
        CommonShaderHeader.substringAfter("float3 palette(")
            .substringBefore("half4 premul(").let { "float3 palette($it" } +
        CommonShaderHeader.substringAfter("half4 premul(")
            .substringBefore("float lineMask(").let { "half4 premul($it" }
    val body = NaviampVisualizer.AudioSphere.shaderSource.substringAfter(CommonShaderHeader)
        .replace("half4 main(float2 coord) {", "void main() {\n    float2 sphereCoord = gl_FragCoord.xy;\n    sphereCoord.y = u_resolution.y - sphereCoord.y;")
        .replace(Regex("\\bcoord\\b"), "sphereCoord")
        .replace(Regex("return (.+);"), "outColor = $1; return;")
    var source = SphereDirectHeader + helpers + SphereBandSampling + body
    val uniforms = mapOf("iResolution" to "u_resolution", "iTime" to "u_time", "iActive" to "u_active",
        "iAccent" to "u_accent", "iReadable" to "u_readable", "iColorA" to "u_colorA",
        "iColorB" to "u_colorB", "iColorC" to "u_colorC", "iIdle" to "u_idle")
    source = source.replace("iEnergy.x", "u_bassLevel").replace("iEnergy.y", "u_midLevel")
        .replace("iEnergy.z", "u_trebleLevel").replace("iEnergy.w", "u_energyLevel")
    uniforms.forEach { (from, to) -> source = source.replace(Regex("\\b$from\\b"), to) }
    return source.replace("palette(", "spherePalette(").replace("bandAt(", "sphereBandAt(")
        .replace("hash21(", "sphereHash21(").replace("edgeMask(", "sphereEdgeMask(")
        .replace("premul(", "spherePremul(")
        .replace(Regex("\\bfloat2\\b"), "vec2").replace(Regex("\\bfloat3\\b"), "vec3")
        .replace(Regex("\\bhalf4\\b"), "vec4")
}

private const val SphereBandSampling = """
float bandAt(float x) {
    float slot = clamp(floor(x * 31.0 + 0.0001), 0.0, 31.0);
    return texture(u_frequencyTexture, vec2((slot + 0.5) / 32.0, 0.5)).r;
}
"""

private const val SphereDirectHeader = """#version 300 es
precision highp float;
out vec4 outColor;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_active;
uniform float u_bassLevel;
uniform float u_midLevel;
uniform float u_trebleLevel;
uniform float u_energyLevel;
uniform vec4 u_accent;
uniform vec4 u_readable;
uniform vec4 u_colorA;
uniform vec4 u_colorB;
uniform vec4 u_colorC;
uniform vec4 u_idle;
uniform sampler2D u_frequencyTexture;
"""
