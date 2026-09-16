package app.naviamp.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Android publishes only its visible lifecycle and hardware-compositor presentation boundary. */
@Composable
fun NaviampAndroidRasterHost(content: @Composable () -> Unit) {
    val activity = LocalContext.current as? Activity
    val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    val presenter = remember(activity) {
        activity?.takeIf { Build.VERSION.SDK_INT >= 29 }?.let(::AndroidRasterPresenter)
    }
    var visible by remember(lifecycle) {
        mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true)
    }
    CompositionLocalProvider(
        LocalNaviampRasterPresenter provides presenter,
        LocalNaviampAnimationVisible provides visible,
        content = content,
    )
    DisposableEffect(lifecycle, presenter) {
        val observer = LifecycleEventObserver { _, _ ->
            visible = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); presenter?.close() }
    }
}

private class AndroidRasterPresenter(private val activity: Activity) : NaviampRasterPresenter, Choreographer.FrameCallback {
    private companion object { const val PresentationIntervalMillis = 16L }
    private val regions = mutableSetOf<AndroidRasterRegion>()
    private val transaction = SurfaceControl.Transaction()
    private var frameScheduled = false

    override fun create(): NaviampRasterRegion = AndroidRasterRegion(activity, ::scheduleFrame) {
        regions.remove(it)
    }.also(regions::add)

    private fun scheduleFrame() {
        if (!frameScheduled && regions.any(AndroidRasterRegion::isAnimating)) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallbackDelayed(this, PresentationIntervalMillis)
        } else if (!frameScheduled && regions.any(AndroidRasterRegion::hasReadySurface)) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        var changed = false
        regions.forEach { changed = it.appendUpdate(transaction) || changed }
        if (changed) transaction.apply()
        scheduleFrame()
    }

    fun close() {
        Choreographer.getInstance().removeFrameCallback(this)
        frameScheduled = false
        regions.toList().forEach(AndroidRasterRegion::close)
        regions.clear()
        transaction.close()
    }
}

/** Each cached bitmap is drawn once; animation submits only SurfaceFlinger geometry transactions. */
private class AndroidRasterRegion(
    private val activity: Activity,
    private val invalidate: () -> Unit,
    private val removed: (AndroidRasterRegion) -> Unit,
) : NaviampRasterRegion {
    private data class PresentedLayer(
        val view: SurfaceView,
        val spec: NaviampRasterLayer,
        val source: AndroidRect = AndroidRect(),
        val destination: AndroidRect = AndroidRect(),
        var ready: Boolean = false,
        var visible: Boolean = false,
        var ordered: Boolean = false,
    )

    private val root = FrameLayout(activity).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private var layers = emptyList<PresentedLayer>()
    private var attached = false
    private var startedAt = 0L
    private var bounds = Rect.Zero
    private var clip = Rect.Zero
    private var running = false
    private var needsUpdate = false

    override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
        if (bounds.isEmpty || clip.isEmpty) return false
        val decor = activity.window.decorView as? ViewGroup ?: return false
        if (!attached) {
            decor.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            attached = true
        }
        running = false
        root.removeAllViews()
        this.bounds = bounds
        this.clip = clip
        startedAt = SystemClock.uptimeMillis()
        this.layers = layers.map { spec ->
            val bitmap = spec.image.asAndroidBitmap()
            val surface = SurfaceView(activity).apply {
                setZOrderOnTop(true)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            val shown = PresentedLayer(surface, spec)
            surface.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    val canvas = holder.surface.lockHardwareCanvas()
                    try {
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                    } finally {
                        holder.surface.unlockCanvasAndPost(canvas)
                    }
                    shown.ready = true
                    needsUpdate = true
                    invalidate()
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) { shown.ready = false }
            })
            root.addView(surface, FrameLayout.LayoutParams(bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1)))
            shown
        }
        running = this.layers.any { it.spec.translation != null || it.spec.revealMotion != null }
        needsUpdate = true
        invalidate()
        return true
    }

    fun isAnimating(): Boolean = running && attached
    fun hasReadySurface(): Boolean = needsUpdate && attached && layers.any { it.ready }

    fun appendUpdate(transaction: SurfaceControl.Transaction): Boolean {
        if (!attached || (!running && !needsUpdate)) return false
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val changed = updateSurfaces(transaction, elapsed)
        needsUpdate = false
        running = layers.any {
            val motion = it.spec.translation ?: it.spec.revealMotion
            motion != null && (motion.repeat || elapsed < motion.durationMillis)
        }
        return changed
    }

    private fun updateSurfaces(transaction: SurfaceControl.Transaction, elapsed: Long): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        var changed = false
        layers.forEachIndexed { index, layer ->
                if (!layer.ready || !layer.view.surfaceControl.isValid) return@forEachIndexed
                val spec = layer.spec
                val dx = spec.translation?.valueAt(elapsed) ?: 0f
                val imageLeft = bounds.left + spec.origin.x + dx
                val imageTop = bounds.top + spec.origin.y
                var visibleLeft = max(clip.left, imageLeft)
                var visibleRight = min(clip.right, imageLeft + spec.image.width)
                val visibleTop = max(clip.top, imageTop)
                val visibleBottom = min(clip.bottom, imageTop + spec.image.height)
                val reveal = spec.revealMotion?.valueAt(elapsed) ?: spec.reveal
                val edge = bounds.left + bounds.width * reveal
                if (spec.translation == null) {
                    if (spec.clipFromStart) visibleLeft = max(visibleLeft, edge)
                    else visibleRight = min(visibleRight, edge)
                }
                if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) {
                    if (layer.visible) {
                        transaction.setVisibility(layer.view.surfaceControl, false)
                        layer.visible = false
                        changed = true
                    }
                    return@forEachIndexed
                }
                layer.source.set(
                    floor(visibleLeft - imageLeft).toInt(), floor(visibleTop - imageTop).toInt(),
                    ceil(visibleRight - imageLeft).toInt(), ceil(visibleBottom - imageTop).toInt(),
                )
                layer.destination.set(
                    floor(visibleLeft).toInt(), floor(visibleTop).toInt(),
                    ceil(visibleRight).toInt(), ceil(visibleBottom).toInt(),
                )
                if (!layer.visible) {
                    transaction.setVisibility(layer.view.surfaceControl, true)
                    layer.visible = true
                }
                if (!layer.ordered) {
                    transaction.setLayer(layer.view.surfaceControl, 10_000 + index)
                    layer.ordered = true
                }
                transaction.setGeometry(layer.view.surfaceControl, layer.source, layer.destination,
                    SurfaceControl.BUFFER_TRANSFORM_IDENTITY)
                changed = true
        }
        return changed
    }

    override fun translationX(layerIndex: Int): Float? {
        val motion = layers.getOrNull(layerIndex)?.spec?.translation ?: return 0f
        return motion.valueAt(SystemClock.uptimeMillis() - startedAt)
    }

    override fun close() {
        running = false
        needsUpdate = false
        (root.parent as? ViewGroup)?.removeView(root)
        root.removeAllViews()
        layers = emptyList()
        attached = false
        removed(this)
    }
}
