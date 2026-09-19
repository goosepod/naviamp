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
import android.view.Surface
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
        val control: SurfaceControl,
        val surface: Surface,
        var spec: NaviampRasterLayer,
        val source: AndroidRect = AndroidRect(),
        val destination: AndroidRect = AndroidRect(),
        var visible: Boolean = false,
        var ordered: Boolean = false,
    )

    private val root = FrameLayout(activity).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    // SurfaceView owns this parent and may reset its geometry at any time. Only our children
    // receive compositor transactions. The full-window parent also avoids clipping children
    // to a bitmap-sized SurfaceView at the window origin.
    private val host = SurfaceView(activity).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private val hostLocation = IntArray(2)
    private var hostReady = false
    private var requestedLayers = emptyList<NaviampRasterLayer>()
    private var layers = emptyList<PresentedLayer>()
    private var attached = false
    private var startedAt = 0L
    private var bounds = Rect.Zero
    private var clip = Rect.Zero
    private var running = false
    private var needsUpdate = false

    init {
        host.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                hostReady = true
                updateBuffers()
                needsUpdate = true
                invalidate()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                needsUpdate = true
                invalidate()
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                hostReady = false
                releaseLayers()
            }
        })
        root.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
        if (bounds.isEmpty || clip.isEmpty) {
            requestedLayers = emptyList()
            running = false
            releaseLayers()
            return false
        }
        val decor = activity.window.decorView as? ViewGroup ?: return false
        requestedLayers = layers
        this.bounds = bounds
        this.clip = clip
        startedAt = SystemClock.uptimeMillis()
        if (!attached) {
            attached = true
            decor.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        updateBuffers()
        running = layers.any { it.translation != null || it.revealMotion != null }
        needsUpdate = true
        invalidate()
        return true
    }

    private fun updateBuffers() {
        if (!hostReady) return
        if (layers.size == requestedLayers.size && layers.zip(requestedLayers).all { (old, new) -> old.spec.image === new.image }) {
            layers.zip(requestedLayers).forEach { (old, new) -> old.spec = new }
            return
        }
        releaseLayers()
        layers = requestedLayers.map { spec ->
            val bitmap = spec.image.asAndroidBitmap()
            val control = SurfaceControl.Builder().setName("Naviamp cached raster")
                .setParent(host.surfaceControl)
                .setBufferSize(bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1))
                .setFormat(PixelFormat.TRANSLUCENT).build()
            val surface = Surface(control)
            val canvas = surface.lockHardwareCanvas()
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            } finally { surface.unlockCanvasAndPost(canvas) }
            PresentedLayer(control, surface, spec)
        }
    }

    private fun releaseLayers() {
        if (layers.isEmpty()) return
        SurfaceControl.Transaction().use { transaction ->
            layers.forEach { if (it.control.isValid) transaction.reparent(it.control, null) }
            transaction.apply()
        }
        layers.forEach { it.surface.release(); it.control.release() }
        layers = emptyList()
    }

    fun isAnimating(): Boolean = running && attached && hostReady
    fun hasReadySurface(): Boolean = needsUpdate && attached && hostReady && layers.isNotEmpty()

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
        host.getLocationInWindow(hostLocation)
        var changed = false
        layers.forEachIndexed { index, layer ->
                if (!layer.control.isValid) return@forEachIndexed
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
                        transaction.setVisibility(layer.control, false)
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
                    floor(visibleLeft).toInt() - hostLocation[0], floor(visibleTop).toInt() - hostLocation[1],
                    ceil(visibleRight).toInt() - hostLocation[0], ceil(visibleBottom).toInt() - hostLocation[1],
                )
                if (!layer.visible) {
                    transaction.setVisibility(layer.control, true)
                    layer.visible = true
                }
                if (!layer.ordered) {
                    transaction.setLayer(layer.control, index)
                    layer.ordered = true
                }
                transaction.setGeometry(layer.control, layer.source, layer.destination,
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
        requestedLayers = emptyList()
        releaseLayers()
        (root.parent as? ViewGroup)?.removeView(root)
        root.removeAllViews()
        layers = emptyList()
        attached = false
        removed(this)
    }
}
