package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.interpretObjCPointer
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
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCAAnimationLinear
import platform.QuartzCore.kCAFillModeForwards
import platform.UIKit.UIImage
import platform.UIKit.UIScreen

/** UIKit supplies only a CALayer presentation boundary; shared Kotlin owns every motion value. */
@Composable
fun NaviampAppleRasterHost(rootLayer: () -> CALayer?, content: @Composable () -> Unit) {
    val presenter = remember(rootLayer) { AppleRasterPresenter(rootLayer) }
    CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter, content = content)
    DisposableEffect(presenter) { onDispose { presenter.close() } }
}

private class AppleRasterPresenter(private val rootLayer: () -> CALayer?) : NaviampRasterPresenter {
    private val regions = mutableSetOf<AppleRasterRegion>()
    override fun create(): NaviampRasterRegion = AppleRasterRegion(rootLayer) { regions.remove(it) }.also(regions::add)
    fun close() { regions.toList().forEach(AppleRasterRegion::close); regions.clear() }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class AppleRasterRegion(
    private val parent: () -> CALayer?,
    private val removed: (AppleRasterRegion) -> Unit,
) : NaviampRasterRegion {
    private val root = CALayer.layer()
    private var translationLayers = emptyList<CALayer>()
    private var decodedContents = emptyList<Pair<ImageBitmap, Any>>()
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
        translationLayers = emptyList()
        // Reuse decoded pixels when only reveal, motion or layout changed.
        val imageContents = layers.map { layer ->
            decodedContents.firstOrNull { it.first === layer.image }?.second ?: layer.image.toLayerContents() ?: return false
        }
        decodedContents = layers.map { it.image }.zip(imageContents)
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
        translationLayers = layers.mapIndexed { index, spec ->
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
                contents = imageContents[index]
            }
            clipLayer.addSublayer(pixels)
            root.addSublayer(clipLayer)
            val motion = spec.translation ?: spec.revealMotion
            if (spec.translation != null) animate(pixels, "transform.translation.x", motion, 1.0 / density, 0.0, begin)
            else {
                val inverse = spec.clipFromStart
                val placement = spec.placement(bounds.width, bounds.height)
                val left = placement.left.value / density
                val right = placement.right.value / density
                clipLayer.position = CGPointMake(localX + left, localY)
                clipLayer.bounds = platform.CoreGraphics.CGRectMake(
                    left, 0.0, right - left, viewportHeight,
                )
                if (motion != null) {
                    if (inverse) {
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

    override fun move(bounds: Rect, clip: Rect): Boolean {
        if (!attached || translationLayers.isEmpty() || bounds.isEmpty || clip.isEmpty) return false
        val density = scale
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        root.frame = platform.CoreGraphics.CGRectMake(
            clip.left / density, clip.top / density, clip.width / density, clip.height / density,
        )
        CATransaction.commit()
        return true
    }

    override fun translationX(layerIndex: Int): Float? =
        translationLayers.getOrNull(layerIndex)?.presentationLayer()?.transform?.useContents { m41 * scale }?.toFloat()

    override fun setOpacity(opacity: Float) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        root.opacity = opacity
        CATransaction.commit()
    }

    override fun close() {
        root.removeFromSuperlayer()
        attached = false
        translationLayers = emptyList()
        decodedContents = emptyList()
        removed(this)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ImageBitmap.toLayerContents(): Any? {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    val data = image.encodeToData() ?: return null
    val bytes = data.bytes
    return bytes.usePinned { pinned ->
        val image = UIImage(data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()))
        val cgImage = image.CGImage ?: return@usePinned null
        // CALayer expects a bridged CGImage object. Passing the typed ref as Any boxes its pointer.
        interpretObjCPointer<Any>(cgImage.rawValue)
    }
}
