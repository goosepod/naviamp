package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.CoreGraphics.CGPointMake
import platform.QuartzCore.CACurrentMediaTime
import platform.QuartzCore.CAKeyframeAnimation
import platform.QuartzCore.CALayer
import platform.QuartzCore.kCAAnimationLinear
import platform.QuartzCore.kCAFillModeForwards
import platform.UIKit.UIImage
import platform.UIKit.UIScreen

/** UIKit supplies only a CALayer presentation boundary; shared Kotlin owns every motion value. */
@Composable
fun NaviampIosRasterHost(rootLayer: () -> CALayer?, content: @Composable () -> Unit) {
    val presenter = remember(rootLayer) { IosRasterPresenter(rootLayer) }
    CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter, content = content)
    DisposableEffect(presenter) { onDispose { presenter.close() } }
}

private class IosRasterPresenter(private val rootLayer: () -> CALayer?) : NaviampRasterPresenter {
    private val regions = mutableSetOf<IosRasterRegion>()
    override fun create(): NaviampRasterRegion = IosRasterRegion(rootLayer) { regions.remove(it) }.also(regions::add)
    fun close() { regions.toList().forEach(IosRasterRegion::close); regions.clear() }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosRasterRegion(
    private val parent: () -> CALayer?,
    private val removed: (IosRasterRegion) -> Unit,
) : NaviampRasterRegion {
    private val root = CALayer.layer()
    private var translationLayers = emptyList<CALayer>()
    private var attached = false
    private val scale get() = UIScreen.mainScreen.scale

    override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
        val parent = parent() ?: return false
        if (bounds.isEmpty || clip.isEmpty) return false
        if (!attached) {
            root.anchorPoint = CGPointMake(0.0, 0.0)
            root.masksToBounds = true
            parent.addSublayer(root)
            attached = true
        }
        root.removeAllAnimations()
        root.sublayers?.toList()?.forEach { (it as? CALayer)?.removeFromSuperlayer() }
        val density = scale
        root.frame = platform.CoreGraphics.CGRectMake(
            clip.left / density, clip.top / density, clip.width / density, clip.height / density,
        )
        root.cornerRadius = cornerRadius / density
        val localX = (bounds.left - clip.left) / density
        val localY = (bounds.top - clip.top) / density
        val viewportWidth = bounds.width / density
        val viewportHeight = bounds.height / density
        val begin = CACurrentMediaTime()
        translationLayers = layers.map { spec ->
            val clipLayer = CALayer.layer().apply {
                anchorPoint = CGPointMake(0.0, 0.0)
                masksToBounds = true
                position = CGPointMake(localX.toDouble(), localY.toDouble())
                this.bounds = platform.CoreGraphics.CGRectMake(0.0, 0.0, viewportWidth.toDouble(), viewportHeight.toDouble())
            }
            val pixels = CALayer.layer().apply {
                anchorPoint = CGPointMake(0.0, 0.0)
                position = CGPointMake((spec.origin.x / density).toDouble(), (spec.origin.y / density).toDouble())
                this.bounds = platform.CoreGraphics.CGRectMake(
                    0.0, 0.0, spec.image.width / density, spec.image.height / density,
                )
                contentsScale = density
                contents = spec.image.toCgImage()
            }
            clipLayer.addSublayer(pixels)
            root.addSublayer(clipLayer)
            val motion = spec.translation ?: spec.revealMotion
            if (spec.translation != null) animate(pixels, "transform.translation.x", motion, 1.0 / density, 0.0, begin)
            else {
                val inverse = spec.clipFromStart
                val initial = spec.reveal * viewportWidth
                clipLayer.bounds = platform.CoreGraphics.CGRectMake(
                    if (inverse) initial.toDouble() else 0.0, 0.0,
                    if (inverse) (viewportWidth - initial).toDouble() else initial.toDouble(), viewportHeight.toDouble(),
                )
                if (motion != null) {
                    if (inverse) {
                        clipLayer.position = CGPointMake((localX + initial).toDouble(), localY.toDouble())
                        animate(clipLayer, "bounds.origin.x", motion, viewportWidth.toDouble(), 0.0, begin)
                        animate(clipLayer, "position.x", motion, viewportWidth.toDouble(), localX.toDouble(), begin)
                    }
                    animate(clipLayer, "bounds.size.width", motion,
                        if (inverse) -viewportWidth.toDouble() else viewportWidth.toDouble(),
                        if (inverse) viewportWidth.toDouble() else 0.0, begin)
                }
            }
            pixels
        }
        return true
    }

    private fun animate(layer: CALayer, key: String, motion: NaviampLayerMotion?, multiply: Double, add: Double, begin: Double) {
        if (motion == null) return
        val animation = CAKeyframeAnimation.animationWithKeyPath(key) as CAKeyframeAnimation
        animation.values = motion.values.map { NSNumber(it * multiply + add) }
        animation.keyTimes = motion.timesMillis.map { NSNumber(it.toDouble() / motion.durationMillis) }
        animation.duration = motion.durationMillis / 1000.0
        animation.beginTime = begin
        animation.calculationMode = kCAAnimationLinear
        animation.repeatCount = if (motion.repeat) Float.POSITIVE_INFINITY else 0f
        animation.removedOnCompletion = false
        animation.fillMode = kCAFillModeForwards
        layer.addAnimation(animation, key)
    }

    override fun translationX(layerIndex: Int): Float? =
        translationLayers.getOrNull(layerIndex)?.presentationLayer()?.transform?.useContents { m41 * scale }?.toFloat()

    override fun close() {
        root.removeFromSuperlayer()
        attached = false
        translationLayers = emptyList()
        removed(this)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun androidx.compose.ui.graphics.ImageBitmap.toCgImage(): Any? {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    val data = image.encodeToData() ?: return null
    val bytes = data.bytes
    return bytes.usePinned { pinned ->
        UIImage(data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())).CGImage
    }
}
