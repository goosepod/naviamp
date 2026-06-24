package app.naviamp.ui

internal object NativeMetalShaderTranslator {
    private val scalarUniformPattern = Regex("""^\s*uniform\s+(float|int|vec2|vec3|vec4)\s+u_\w+\s*;\s*$""")
    private val samplerUniformPattern = Regex("""^\s*uniform\s+sampler2D\s+u_\w+\s*;\s*$""")
    private val uniformReplacements = mapOf(
        "u_time" to "u.time",
        "u_resolution" to "u.resolution",
        "u_energyLevel" to "u.energyLevel",
        "u_bassLevel" to "u.bassLevel",
        "u_midLevel" to "u.midLevel",
        "u_trebleLevel" to "u.trebleLevel",
        "u_spectralCentroid" to "u.spectralCentroid",
        "u_tempoBpm" to "u.tempoBpm",
        "u_beatDetected" to "u.beatDetected",
        "u_active" to "u.active",
        "u_renderScale" to "u.renderScale",
        "u_maxRaymarchSteps" to "u.maxRaymarchSteps",
        "u_accent" to "u.accent",
        "u_readable" to "u.readable",
        "u_colorA" to "u.colorA",
        "u_colorB" to "u.colorB",
        "u_colorC" to "u.colorC",
        "u_albumArtSize" to "u.albumArtSize",
    )

    fun translateFragmentShader(glslSource: String): String {
        val body = glslSource
            .lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed == "#version 300 es" ||
                    trimmed == "precision highp float;" ||
                    trimmed == "in vec2 v_uv;" ||
                    trimmed == "out vec4 outColor;" ||
                    scalarUniformPattern.matches(line) ||
                    samplerUniformPattern.matches(line)
            }
            .joinToString("\n")
            .replaceTextureCalls()
            .replaceGlslTokens()
            .patchResourceHelperFunctions()
            .replace(
                "void main() {",
                """
                fragment float4 visualizerFragment(
                    NaviampRasterizerData in [[stage_in]],
                    constant NaviampVisualizerUniforms& u [[buffer(0)]],
                    texture2d<float> u_frequencyTexture [[texture(0)]],
                    texture2d<float> u_albumArtTexture [[texture(1)]],
                    sampler textureSampler [[sampler(0)]]
                ) {
                """.trimIndent(),
            )
            .replace(Regex("""\boutColor\s*=\s*"""), "return ")

        return MetalShaderHeader + body.trim() + "\n"
    }

    private fun String.replaceGlslTokens(): String {
        var output = this
            .replace("gl_FragCoord.xy", "float2(in.position.x, u.resolution.y - in.position.y)")
            .replace("gl_FragCoord.y", "(u.resolution.y - in.position.y)")
            .replace(Regex("""\bvec2\b"""), "float2")
            .replace(Regex("""\bvec3\b"""), "float3")
            .replace(Regex("""\bvec4\b"""), "float4")
            .replace(Regex("""\bmat2\b"""), "float2x2")
            .replace(Regex("""\bmat3\b"""), "float3x3")
            .replace(Regex("""\bmat4\b"""), "float4x4")
            .replace(Regex("""\bmod\s*\("""), "fmod(")

        uniformReplacements.forEach { (glslName, metalName) ->
            output = output.replace(Regex("""\b$glslName\b"""), metalName)
        }
        return output
    }

    private fun String.replaceTextureCalls(): String {
        val output = StringBuilder(length)
        var index = 0
        while (index < length) {
            val callStart = indexOf("texture(", startIndex = index)
            if (callStart < 0) {
                output.append(substring(index))
                break
            }
            output.append(substring(index, callStart))
            val openParen = callStart + "texture".length
            val closeParen = findClosingParen(openParen)
            if (closeParen < 0) {
                output.append(substring(callStart))
                break
            }

            val args = substring(openParen + 1, closeParen).splitTopLevelComma()
            if (args.size == 2 && args[0].trim() in setOf("u_frequencyTexture", "u_albumArtTexture")) {
                output.append(args[0].trim())
                    .append(".sample(textureSampler, ")
                    .append(args[1].trim())
                    .append(")")
            } else {
                output.append(substring(callStart, closeParen + 1))
            }
            index = closeParen + 1
        }
        return output.toString()
    }

    private fun String.patchResourceHelperFunctions(): String {
        var output = this
            .replace(
                "float3 playerBackground(float2 uv) {",
                "float3 playerBackground(constant NaviampVisualizerUniforms& u, texture2d<float> u_albumArtTexture, sampler textureSampler, float2 uv) {",
            )
            .replace(
                "float3 albumArtColor(float2 uv) {",
                "float3 albumArtColor(constant NaviampVisualizerUniforms& u, float2 uv) {",
            )
            .replace(
                "float getWaterHeight(float2 pos, float bass, float treble, float beatDecay) {",
                "float getWaterHeight(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float2 pos, float bass, float treble, float beatDecay) {",
            )
            .replace(
                "float mapWater(float3 p, float bass, float treble, float beatDecay) {",
                "float mapWater(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float3 p, float bass, float treble, float beatDecay) {",
            )
            .replace(
                "float3 waterNormal(float3 p, float bass, float treble, float beatDecay) {",
                "float3 waterNormal(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float3 p, float bass, float treble, float beatDecay) {",
            )
            .replace(
                "float getWaterHeight(float2 pos, float s_bass, float s_treble, float s_beat_decay) {",
                "float getWaterHeight(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float2 pos, float s_bass, float s_treble, float s_beat_decay) {",
            )
            .replace(
                "float map(float3 p, float s_bass, float s_treble, float s_beat_decay) {",
                "float map(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float3 p, float s_bass, float s_treble, float s_beat_decay) {",
            )
            .replace(
                "float3 getNormal(float3 p, float s_bass, float s_treble, float s_beat_decay) {",
                "float3 getNormal(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float3 p, float s_bass, float s_treble, float s_beat_decay) {",
            )
            .replace(
                "float map(float3 p) {",
                "float map(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float3 p) {",
            )
            .replace(
                "float3 getNormal(float3 p) {",
                "float3 getNormal(constant NaviampVisualizerUniforms& u, texture2d<float> u_frequencyTexture, sampler textureSampler, float3 p) {",
            )

        val replacements = listOf(
            Regex("""(?<!float3 )\bplayerBackground\(""") to "playerBackground(u, u_albumArtTexture, textureSampler, ",
            Regex("""(?<!float3 )\balbumArtColor\(""") to "albumArtColor(u, ",
            Regex("""(?<!float )\bgetWaterHeight\(""") to "getWaterHeight(u, u_frequencyTexture, textureSampler, ",
            Regex("""(?<!float )\bmapWater\(""") to "mapWater(u, u_frequencyTexture, textureSampler, ",
            Regex("""(?<!float3 )\bwaterNormal\(""") to "waterNormal(u, u_frequencyTexture, textureSampler, ",
            Regex("""(?<!float3 )\bgetNormal\(""") to "getNormal(u, u_frequencyTexture, textureSampler, ",
            Regex("""(?<!float )\bmap\(""") to "map(u, u_frequencyTexture, textureSampler, ",
        )
        replacements.forEach { (pattern, replacement) ->
            output = pattern.replace(output, replacement)
        }

        return output
    }

    private fun String.findClosingParen(openParenIndex: Int): Int {
        var depth = 0
        for (index in openParenIndex until length) {
            when (this[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun String.splitTopLevelComma(): List<String> {
        var depth = 0
        forEachIndexed { index, char ->
            when (char) {
                '(' -> depth += 1
                ')' -> depth -= 1
                ',' -> if (depth == 0) {
                    return listOf(substring(0, index), substring(index + 1))
                }
            }
        }
        return listOf(this)
    }
}

private const val MetalShaderHeader = """#include <metal_stdlib>
using namespace metal;

struct NaviampRasterizerData {
    float4 position [[position]];
    float2 uv;
};

struct NaviampVisualizerUniforms {
    float time;
    packed_float2 resolution;
    float energyLevel;
    float bassLevel;
    float midLevel;
    float trebleLevel;
    float spectralCentroid;
    float tempoBpm;
    float beatDetected;
    float active;
    float renderScale;
    int maxRaymarchSteps;
    packed_float4 accent;
    packed_float4 readable;
    packed_float4 colorA;
    packed_float4 colorB;
    packed_float4 colorC;
    packed_float2 albumArtSize;
};

"""
