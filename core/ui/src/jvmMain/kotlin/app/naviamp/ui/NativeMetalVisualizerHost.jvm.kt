package app.naviamp.ui

import java.io.File
import java.util.Locale
import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal class NativeMetalVisualizerHost(
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
) : AutoCloseable {
    private val shaderSource = requireNotNull(visualizer.nativeShaderDefinition)
        .fragmentSourceForDialect(NativeShaderDialect.MetalShadingLanguage)
    private val uniformBands = FloatArray(VisualizerFrameBandCount)
    private val smoothBands = FloatArray(VisualizerFrameBandCount)
    private var nativeHandle = 0L
    private var failed = false
    private var pendingAlbumArt: NativeMetalAlbumArt? = null
    private var albumArtDirty = false

    fun updateAlbumArt(image: Image?) {
        pendingAlbumArt = image?.toNativeMetalAlbumArt()
        albumArtDirty = true
    }

    fun renderImage(
        width: Int,
        height: Int,
        bands: List<Float>,
        active: Boolean,
        visualizerColors: NaviampPlayerColors,
        colors: NaviampColors,
        timeSeconds: Float,
        tempoBpm: Int?,
    ): Image? {
        if (failed || width <= 0 || height <= 0) return null
        if (!ensureCreated()) return null
        uploadPendingAlbumArt()

        smoothVisualizerBands(bands, smoothBands, uniformBands)
        val shaderSpec = visualizer.nativeMetalShaderSpec(renderPolicy)
        return runCatching {
            val pixels = nativeRenderImage(
                nativeHandle,
                width,
                height,
                timeSeconds,
                active,
                tempoBpm?.toFloat() ?: 120f,
                shaderSpec.renderScale,
                shaderSpec.maxRaymarchSteps,
                uniformBands,
                visualizerColors.accent.toFloatArray(),
                colors.primaryText.toFloatArray(),
                visualizerColors.backgroundStart.toOpaqueFloatArray(),
                visualizerColors.backgroundMid.toOpaqueFloatArray(),
                visualizerColors.backgroundEnd.toOpaqueFloatArray(),
            )
            Image.makeRaster(
                ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                pixels,
                width * 4,
            )
        }.getOrElse { throwable ->
            failed = true
            println("NaviampVisualizerPerf ${visualizer.name} renderer=metal error=${throwable.message}")
            null
        }
    }

    private fun ensureCreated(): Boolean {
        if (nativeHandle != 0L) return true
        if (!libraryAvailable()) {
            failed = true
            return false
        }
        return runCatching {
            nativeHandle = nativeCreate(shaderSource)
            nativeHandle != 0L
        }.getOrElse { throwable ->
            failed = true
            println("NaviampVisualizerPerf ${visualizer.name} renderer=metal createError=${throwable.message}")
            false
        }
    }

    private fun uploadPendingAlbumArt() {
        if (!albumArtDirty || nativeHandle == 0L) return
        albumArtDirty = false
        val albumArt = pendingAlbumArt ?: return
        runCatching {
            nativeUpdateAlbumArt(nativeHandle, albumArt.width, albumArt.height, albumArt.rgbaPixels)
        }.onFailure { throwable ->
            println("NaviampVisualizerPerf ${visualizer.name} renderer=metal albumArtError=${throwable.message}")
        }
    }

    override fun close() {
        if (nativeHandle != 0L) {
            runCatching { nativeDispose(nativeHandle) }
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(fragmentSource: String): Long

    private external fun nativeRenderImage(
        handle: Long,
        width: Int,
        height: Int,
        timeSeconds: Float,
        active: Boolean,
        tempoBpm: Float,
        renderScale: Float,
        maxRaymarchSteps: Int,
        bands: FloatArray,
        accent: FloatArray,
        readable: FloatArray,
        colorA: FloatArray,
        colorB: FloatArray,
        colorC: FloatArray,
    ): ByteArray

    private external fun nativeDispose(handle: Long)

    private external fun nativeUpdateAlbumArt(handle: Long, width: Int, height: Int, rgbaPixels: ByteArray)

    companion object {
        private val loadResult: Result<Unit> by lazy {
            runCatching {
                val libraryFile = nativeMetalLibraryFile()
                    ?: error("Could not find ${nativeMetalLibraryName()} in desktop native resource paths.")
                if (nativeMetalDiagnosticsEnabled()) {
                    println("NaviampVisualizerMetal loading library=${libraryFile.absolutePath}")
                }
                System.load(libraryFile.absolutePath)
            }
        }

        fun libraryAvailable(): Boolean =
            loadResult.isSuccess

        fun libraryLoadFailureMessage(): String? =
            loadResult.exceptionOrNull()?.message

        fun libraryDiagnostics(): String {
            val libraryName = nativeMetalLibraryName()
            val candidates = nativeMetalCandidateDirectories()
                .map { it.absoluteFile.toPath().normalize().toFile().resolve(libraryName) }
                .distinctBy { it.absolutePath }
                .joinToString(separator = ";") { candidate ->
                    "${candidate.absolutePath}:${candidate.isFile}:${candidate.canExecute()}"
                }
            return "name=$libraryName candidates=$candidates"
        }
    }
}

private data class NativeMetalAlbumArt(
    val width: Int,
    val height: Int,
    val rgbaPixels: ByteArray,
)

private data class NativeMetalShaderSpec(
    val renderScale: Float,
    val maxRaymarchSteps: Int = 0,
)

private fun NaviampVisualizer.nativeMetalShaderSpec(renderPolicy: VisualizerRenderPolicy): NativeMetalShaderSpec =
    when (this) {
        NaviampVisualizer.OceanHorizon -> NativeMetalShaderSpec(
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 0.82f
                VisualizerRenderTier.Balanced -> 0.62f
                VisualizerRenderTier.Constrained -> 0.48f
            },
            maxRaymarchSteps = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 60
                VisualizerRenderTier.Balanced -> 60
                VisualizerRenderTier.Constrained -> 42
            },
        )
        NaviampVisualizer.RaymarchedSphereLiquid -> NativeMetalShaderSpec(
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 1.0f
                VisualizerRenderTier.Balanced -> 0.82f
                VisualizerRenderTier.Constrained -> 0.65f
            },
            maxRaymarchSteps = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 80
                VisualizerRenderTier.Balanced -> 64
                VisualizerRenderTier.Constrained -> 48
            },
        )
        NaviampVisualizer.AnalogSignalFailure,
        NaviampVisualizer.FluidicNebulae,
        NaviampVisualizer.OceanOfInk -> NativeMetalShaderSpec(
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 1.0f
                VisualizerRenderTier.Balanced -> 0.82f
                VisualizerRenderTier.Constrained -> 0.65f
            },
        )
        else -> NativeMetalShaderSpec(renderScale = 1.0f)
    }

private fun Color.toFloatArray(): FloatArray =
    floatArrayOf(red, green, blue, alpha)

private fun Color.toOpaqueFloatArray(): FloatArray =
    floatArrayOf(red, green, blue, 1f)

private fun Image.toNativeMetalAlbumArt(): NativeMetalAlbumArt? {
    val width = width.coerceAtLeast(1)
    val height = height.coerceAtLeast(1)
    val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val bitmap = Bitmap()
    return try {
        if (!bitmap.allocPixels(imageInfo)) return null
        if (!readPixels(bitmap)) return null
        NativeMetalAlbumArt(
            width = width,
            height = height,
            rgbaPixels = bitmap.readPixels(imageInfo, width * 4, 0, 0) ?: return null,
        )
    } finally {
        bitmap.close()
    }
}

private fun nativeMetalLibraryFile(): File? {
    val libraryName = nativeMetalLibraryName()
    return nativeMetalCandidateDirectories()
        .map { it.absoluteFile.toPath().normalize().toFile() }
        .distinctBy { it.absolutePath }
        .firstNotNullOfOrNull { directory ->
            directory.resolve(libraryName).takeIf { it.isFile }
        }
}

private fun nativeMetalLibraryName(): String =
    when (nativeMetalPlatformOs()) {
        "macos" -> "libnaviamp_visualizer_metal.dylib"
        "windows" -> "naviamp_visualizer_metal.dll"
        else -> "libnaviamp_visualizer_metal.so"
    }

private fun nativeMetalCandidateDirectories(): List<File> = buildList {
    System.getProperty("naviamp.visualizer.metal.dir")
        ?.takeIf { it.isNotBlank() }
        ?.let { add(File(it)) }
    System.getenv("NAVIAMP_VISUALIZER_METAL_DIR")
        ?.takeIf { it.isNotBlank() }
        ?.let { add(File(it)) }

    nativeMetalSearchRoots().forEach { root ->
        listOf(
            "resources/playback/bass/${nativeMetalPlatformId()}",
            "playback/bass/${nativeMetalPlatformId()}",
            "Contents/Resources/playback/bass/${nativeMetalPlatformId()}",
            "../Resources/playback/bass/${nativeMetalPlatformId()}",
            "../app/playback/bass/${nativeMetalPlatformId()}",
            "apps/desktop/build/generated/desktopBass/playback/bass/${nativeMetalPlatformId()}",
        ).forEach { relativePath ->
            add(File(root, relativePath))
        }
    }
}

private fun nativeMetalSearchRoots(): List<File> {
    val codeSource = NativeMetalVisualizerHost::class.java.protectionDomain.codeSource?.location
        ?.toURI()
        ?.let(::File)
    val codeSourceRoot = codeSource?.let { if (it.isFile) it.parentFile else it }
    val composeResourcesRoot = System.getProperty("compose.application.resources.dir")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)

    return buildList {
        composeResourcesRoot?.let(::add)
        codeSourceRoot?.ancestors(limit = 8)?.let(::addAll)
        add(File(System.getProperty("user.dir")))
    }.distinctBy { it.absolutePath }
}

private fun File.ancestors(limit: Int): List<File> =
    generateSequence(this) { it.parentFile }
        .take(limit)
        .toList()

private fun nativeMetalPlatformId(): String =
    "${nativeMetalPlatformOs()}-${nativeMetalPlatformArch()}"

private fun nativeMetalPlatformOs(): String {
    val osName = System.getProperty("os.name").lowercase(Locale.US)
    return when {
        osName.contains("mac") || osName.contains("darwin") -> "macos"
        osName.contains("win") -> "windows"
        osName.contains("linux") -> "linux"
        else -> osName.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
    }
}

private fun nativeMetalPlatformArch(): String {
    val arch = System.getProperty("os.arch").lowercase(Locale.US)
    return when (arch) {
        "aarch64", "arm64" -> "arm64"
        "x86_64", "amd64" -> "x64"
        else -> arch.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
    }
}

internal fun nativeMetalDiagnosticsEnabled(): Boolean =
    System.getProperty("naviamp.visualizer.metalDiagnostics").equals("true", ignoreCase = true)
