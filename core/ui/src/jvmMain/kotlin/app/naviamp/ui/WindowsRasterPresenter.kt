package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import java.awt.Component
import java.awt.EventQueue
import java.awt.Window
import org.jetbrains.skia.Image

/** JAWT HWND attachment, PNG/JNI conversion and DirectComposition resource lifetime only. */
internal class WindowsRasterPresenter(private val window: Window) : NaviampRasterPresenter {
    // The composition target is the main HWND; owned Compose popup HWNDs stack above it.
    override val contentBelowOwnedWindows = true
    override fun create(): NaviampRasterRegion = object : NaviampRasterRegion {
        private var handle = 0L
        private var cachedImages = emptyList<ImageBitmap>()
        private var sceneClock = NaviampRasterSceneClock()
        private var currentLayers = emptyList<NaviampRasterLayer>()

        override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
            check(EventQueue.isDispatchThread())
            if (!window.isShowing || bounds.isEmpty || clip.isEmpty) return false
            val canvas = findSkiaLayer(window)?.canvas ?: return false
            return try {
                if (handle == 0L) handle = WindowsRasterNative.create(canvas)
                if (handle == 0L) return false
                val restart = sceneClock.update(layers, bounds.width, bounds.height, System.nanoTime() / 1_000_000)
                val images = layers.map { it.image }
                val pngs = images.mapIndexed { index, bitmap ->
                    if (images.size == cachedImages.size && cachedImages[index] === bitmap) null
                    else Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { it.bytes }
                    }
                }.toTypedArray()
                val placements = layers.map { it.placement(bounds.width, bounds.height) }
                val scalars = placements.flatMap { listOf(it.x, it.left, it.right) }
                WindowsRasterNative.present(handle,
                    doubleArrayOf(clip.left.toDouble(), clip.top.toDouble(), clip.width.toDouble(), clip.height.toDouble(),
                        (bounds.left - clip.left).toDouble(), (bounds.top - clip.top).toDouble(),
                        bounds.width.toDouble(), bounds.height.toDouble(), cornerRadius.toDouble()),
                    pngs,
                    placements.map { doubleArrayOf(it.x.value.toDouble(), it.y.toDouble(),
                        it.left.value.toDouble(), it.right.value.toDouble(), it.height.toDouble()) }.toTypedArray(),
                    scalars.map { scalar -> scalar.motion?.values?.map(Float::toDouble)?.toDoubleArray() ?: doubleArrayOf() }.toTypedArray(),
                    scalars.map { scalar -> scalar.motion?.timesMillis?.map { it / 1000.0 }?.toDoubleArray() ?: doubleArrayOf() }.toTypedArray(),
                    scalars.map { it.motion?.repeat == true }.toBooleanArray(), restart)
                cachedImages = images
                currentLayers = layers
                true
            } catch (failure: Exception) {
                println("NaviampRaster DirectComposition unavailable: ${failure.message}")
                close()
                false
            } catch (failure: UnsatisfiedLinkError) {
                println("NaviampRaster DirectComposition linkage: ${failure.message}")
                close()
                false
            }
        }

        override fun translationX(layerIndex: Int): Float? = currentLayers.getOrNull(layerIndex)?.translation
            ?.valueAt(System.nanoTime() / 1_000_000 - sceneClock.startedMillis)

        override fun close() {
            check(EventQueue.isDispatchThread())
            if (handle != 0L) WindowsRasterNative.close(handle)
            handle = 0L
            cachedImages = emptyList()
            currentLayers = emptyList()
            sceneClock = NaviampRasterSceneClock()
        }
    }
}

private object WindowsRasterNative {
    external fun create(component: Component): Long
    external fun present(handle: Long, region: DoubleArray, pngs: Array<ByteArray?>,
        geometry: Array<DoubleArray>, values: Array<DoubleArray>, times: Array<DoubleArray>,
        repeats: BooleanArray, restart: Boolean)
    external fun close(handle: Long)
}
