package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import java.awt.Color
import javax.swing.JWindow
import java.awt.Component
import java.awt.EventQueue
import java.awt.Window
import java.io.File
import java.util.Locale
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** JAWT/X11 child-window attachment, cached pixel upload, and native surface lifetime only. */
internal class LinuxRasterPresenter(private val window: Window) : NaviampRasterPresenter {
    // AWT-owned top-level windows cannot guarantee stacking below every owned popup.
    // The shared raster environment already handles that presentation fallback.

    private val regions = mutableSetOf<LinuxRasterRegion>()
    override fun create(): NaviampRasterRegion = LinuxRasterRegion(window) { regions.remove(it) }.also(regions::add)
    fun reposition() = regions.toList().forEach(LinuxRasterRegion::reposition)

    private class LinuxRasterRegion(private val window: Window, private val removed: (LinuxRasterRegion) -> Unit) : NaviampRasterRegion {
        // X11 copies child pixels into their parent without blending. Preserve their alpha in
        // an ARGB top-level window, which the desktop compositor blends over the application.
        private var overlay: JWindow? = null
        private var clip = Rect.Zero
        private var scale = 1f
        private var handle = 0L
        private var cachedImages = emptyList<ImageBitmap>()
        private var sceneClock = NaviampRasterSceneClock()
        private var currentLayers = emptyList<NaviampRasterLayer>()

        override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
            check(EventQueue.isDispatchThread())
            if (!window.isShowing || bounds.isEmpty || clip.isEmpty) {
                release()
                return false
            }
            return try {
                val surface = overlay ?: JWindow(window).apply {
                    name = RasterOverlayWindowName
                    // X11 override-redirect avoids a WM frame with its own nonempty input
                    // region. The adapter-owned client keeps the native empty input shape.
                    type = Window.Type.POPUP
                    focusableWindowState = false
                    // This can fail on unsupported X11 visuals; let the shared owner fall back.
                    background = Color(0, 0, 0, 0)
                }.also { overlay = it }
                this.clip = clip
                scale = findSkiaLayer(window)?.contentScale?.coerceAtLeast(1f) ?: return false
                reposition()
                if (!surface.isDisplayable) surface.addNotify()
                if (handle == 0L) handle = LinuxRasterNative.create(surface)
                if (handle == 0L) { release(); return false }
                val restart = sceneClock.update(layers, bounds.width, bounds.height, System.nanoTime() / 1_000_000)
                val images = layers.map(NaviampRasterLayer::image)
                val pixels = images.mapIndexed { index, bitmap ->
                    if (images.size == cachedImages.size && cachedImages[index] === bitmap) null
                    else bitmap.toLinuxRasterPixels()
                }.toTypedArray()
                val placements = layers.map { it.placement(bounds.width, bounds.height) }
                val scalars = placements.flatMap { listOf(it.x, it.left, it.right) }
                LinuxRasterNative.present(
                    handle,
                    doubleArrayOf(
                        0.0, 0.0, clip.width.toDouble(), clip.height.toDouble(),
                        (bounds.left - clip.left).toDouble(), (bounds.top - clip.top).toDouble(),
                        bounds.width.toDouble(), bounds.height.toDouble(), cornerRadius.toDouble(),
                    ),
                    pixels.map { it?.bytes }.toTypedArray(),
                    pixels.mapIndexed { index, image -> image?.width ?: images[index].width }.toIntArray(),
                    pixels.mapIndexed { index, image -> image?.height ?: images[index].height }.toIntArray(),
                    placements.map {
                        doubleArrayOf(
                            it.x.value.toDouble(), it.y.toDouble(), it.left.value.toDouble(),
                            it.right.value.toDouble(), it.height.toDouble(),
                        )
                    }.toTypedArray(),
                    scalars.map { scalar ->
                        scalar.motion?.values?.map(Float::toDouble)?.toDoubleArray() ?: doubleArrayOf()
                    }.toTypedArray(),
                    scalars.map { scalar ->
                        scalar.motion?.timesMillis?.map(Long::toDouble)?.toDoubleArray() ?: doubleArrayOf()
                    }.toTypedArray(),
                    scalars.map { it.motion?.repeat == true }.toBooleanArray(),
                    restart,
                )
                // Window.show() raises an already-visible AWT window. Pixel updates neither
                // need another show nor change stacking; reposition() has already restacked it.
                if (!surface.isVisible) {
                    surface.isVisible = true
                    LinuxRasterNative.restack(handle, window)
                }
                cachedImages = images
                currentLayers = layers
                true
            } catch (failure: Exception) {
                println("NaviampRaster X11 unavailable: ${failure.message}")
                release()
                false
            } catch (failure: UnsatisfiedLinkError) {
                println("NaviampRaster X11 linkage: ${failure.message}")
                release()
                false
            }
        }

        override fun translationX(layerIndex: Int): Float? = currentLayers.getOrNull(layerIndex)?.translation
            ?.valueAt(System.nanoTime() / 1_000_000 - sceneClock.startedMillis)

        fun reposition() {
            if (!window.isShowing || clip.isEmpty) return
            val parent = findSkiaLayer(window) ?: return
            val origin = parent.canvas.locationOnScreen
            overlay?.setBounds(
                origin.x + (clip.left / scale).toInt(), origin.y + (clip.top / scale).toInt(),
                (clip.width / scale).toInt().coerceAtLeast(1), (clip.height / scale).toInt().coerceAtLeast(1),
            )
            if (handle != 0L) LinuxRasterNative.restack(handle, window)
        }

        override fun close() {
            check(EventQueue.isDispatchThread())
            release()
            removed(this)
        }

        private fun release() {
            if (handle != 0L) LinuxRasterNative.close(handle)
            handle = 0L
            overlay?.dispose()
            overlay = null
            cachedImages = emptyList()
            currentLayers = emptyList()
            sceneClock = NaviampRasterSceneClock()
        }
    }

    companion object {
        private val loadResult: Result<Unit> by lazy {
            runCatching {
                val library = linuxRasterLibraryFile()
                    ?: error("Could not find libnaviamp_raster_x11.so in desktop native resource paths.")
                if (System.getenv("NAVIAMP_RASTER_DIAGNOSTICS") == "true") {
                    println("NaviampRaster X11 loading library=${library.absolutePath}")
                }
                System.load(library.absolutePath)
            }
        }

        fun libraryAvailable(): Boolean = loadResult.isSuccess
        fun libraryLoadFailureMessage(): String? = loadResult.exceptionOrNull()?.message
    }
}

private data class LinuxRasterPixels(val width: Int, val height: Int, val bytes: ByteArray)

private fun ImageBitmap.toLinuxRasterPixels(): LinuxRasterPixels {
    val info = ImageInfo(width.coerceAtLeast(1), height.coerceAtLeast(1), ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    val pixels = Bitmap()
    return try {
        check(pixels.allocPixels(info)) { "Could not allocate X11 raster pixels" }
        Image.makeFromBitmap(asSkiaBitmap()).use { image ->
            check(image.readPixels(pixels)) { "Could not read X11 raster pixels" }
        }
        LinuxRasterPixels(info.width, info.height,
            requireNotNull(pixels.readPixels(info, info.width * 4, 0, 0)) { "Could not copy X11 raster pixels" })
    } finally {
        pixels.close()
    }
}

private object LinuxRasterNative {
    external fun create(component: Component): Long
    external fun present(
        handle: Long,
        region: DoubleArray,
        pixels: Array<ByteArray?>,
        widths: IntArray,
        heights: IntArray,
        geometry: Array<DoubleArray>,
        values: Array<DoubleArray>,
        times: Array<DoubleArray>,
        repeats: BooleanArray,
        restart: Boolean,
    )
    external fun restack(handle: Long, owner: Component)
    external fun close(handle: Long)
}

private fun linuxRasterLibraryFile(): File? {
    val libraryName = "libnaviamp_raster_x11.so"
    return linuxRasterCandidateDirectories()
        .map { it.absoluteFile.toPath().normalize().toFile() }
        .distinctBy(File::getAbsolutePath)
        .firstNotNullOfOrNull { directory -> directory.resolve(libraryName).takeIf(File::isFile) }
}

private fun linuxRasterCandidateDirectories(): List<File> = buildList {
    System.getProperty("naviamp.raster.x11.dir")?.takeIf(String::isNotBlank)?.let { add(File(it)) }
    System.getenv("NAVIAMP_RASTER_X11_DIR")?.takeIf(String::isNotBlank)?.let { add(File(it)) }
    val platform = "linux-${when (System.getProperty("os.arch").lowercase(Locale.US)) {
        "aarch64", "arm64" -> "arm64"
        "x86_64", "amd64" -> "x64"
        else -> System.getProperty("os.arch").lowercase(Locale.US).filter(Char::isLetterOrDigit)
    }}"
    linuxRasterSearchRoots().forEach { root ->
        listOf(
            "resources/playback/bass/$platform",
            "playback/bass/$platform",
            "../app/playback/bass/$platform",
            "platforms/desktop/build/generated/desktopNativeResources/playback/bass/$platform",
        ).forEach { add(File(root, it)) }
    }
}

private fun linuxRasterSearchRoots(): List<File> {
    val codeSource = LinuxRasterPresenter::class.java.protectionDomain.codeSource?.location?.toURI()?.let(::File)
    val codeSourceRoot = codeSource?.let { if (it.isFile) it.parentFile else it }
    val resourceRoot = System.getProperty("compose.application.resources.dir")?.takeIf(String::isNotBlank)?.let(::File)
    return buildList {
        resourceRoot?.let(::add)
        codeSourceRoot?.let { addAll(generateSequence(it, File::getParentFile).take(8)) }
        add(File(System.getProperty("user.dir")))
    }.distinctBy(File::getAbsolutePath)
}
