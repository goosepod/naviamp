package app.naviamp.ui

/**
 * Converts the authoritative native GLSL visualizers into SkSL for hosts whose Compose surface is
 * backed by Skia but which do not yet expose a direct native GPU surface.
 */
internal object NativeSkiaShaderTranslator {
    private val uniformPattern = Regex("""^\s*uniform\s+.+;\s*$""")

    fun translateFragmentShader(glslSource: String): String {
        val body = glslSource
            .lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed == "#version 300 es" ||
                    trimmed == "precision highp float;" ||
                    trimmed == "in vec2 v_uv;" ||
                    trimmed == "out vec4 outColor;" ||
                    uniformPattern.matches(line)
            }
            .joinToString("\n")
            .replaceTextureCalls()
            .replaceGlslTokens()
            .replace("void main() {", "half4 main(float2 coord) {")
            .replace(Regex("""\boutColor\s*=\s*float4\s*\("""), "return half4(")

        return CommonShaderHeader + NativeAnalysisUniforms + body.trim() + "\n"
    }

    private fun String.replaceGlslTokens(): String {
        var output = this
            .replace("gl_FragCoord.xy", "float2(coord.x, iResolution.y - coord.y)")
            .replace("gl_FragCoord.y", "(iResolution.y - coord.y)")
            .replace(Regex("""\bvec2\b"""), "float2")
            .replace(Regex("""\bvec3\b"""), "float3")
            .replace(Regex("""\bvec4\b"""), "float4")
            .replace(Regex("""\bmat2\b"""), "float2x2")
            .replace(Regex("""\bmat3\b"""), "float3x3")
            .replace(Regex("""\bmat4\b"""), "float4x4")

        UniformReplacements.forEach { (glslName, skslName) ->
            output = output.replace(Regex("""\b$glslName\b"""), skslName)
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
            if (args.size == 2 && args[0].trim() == "u_frequencyTexture") {
                output.append("float4(bandAt((")
                    .append(args[1].trim())
                    .append(").x))")
            } else {
                output.append(substring(callStart, closeParen + 1))
            }
            index = closeParen + 1
        }
        return output.toString()
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
                ',' -> if (depth == 0) return listOf(substring(0, index), substring(index + 1))
            }
        }
        return listOf(this)
    }

    private val UniformReplacements = mapOf(
        "u_time" to "iTime",
        "u_resolution" to "iResolution",
        "u_energyLevel" to "iEnergy.w",
        "u_bassLevel" to "iEnergy.x",
        "u_midLevel" to "iEnergy.y",
        "u_trebleLevel" to "iEnergy.z",
        "u_spectralCentroid" to "iAnalysis.x",
        "u_tempoBpm" to "iTempo",
        "u_beatDetected" to "iAnalysis.y",
        "u_active" to "iActive",
        "u_renderScale" to "iRenderScale",
        "u_maxRaymarchSteps" to "iMaxRaymarchSteps",
        "u_accent" to "iAccent",
        "u_readable" to "iReadable",
        "u_colorA" to "iColorA",
        "u_colorB" to "iColorB",
        "u_colorC" to "iColorC",
        "u_albumArtSize" to "iAlbumArtSize",
    )
}

private const val NativeAnalysisUniforms = """
uniform float2 iAnalysis;
uniform float iRenderScale;
uniform int iMaxRaymarchSteps;
"""
