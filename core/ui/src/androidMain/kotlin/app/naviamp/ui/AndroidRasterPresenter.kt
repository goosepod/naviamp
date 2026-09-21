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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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

    @Composable
    override fun Content(region: NaviampRasterRegion) {
        AndroidView(factory = { (region as AndroidRasterRegion).root }, modifier = Modifier.fillMaxSize())
    }

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

    val root = FrameLayout(activity).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    // SurfaceView owns this parent and may reset its geometry at any time. Only our children
    // receive compositor transactions. View movement belongs to SurfaceView's render-thread
    // position tracking, which synchronizes its parent transform with the window buffer.
    private val host = SurfaceView(activity).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private var hostReady = false
    private var requestedLayers = emptyList<NaviampRasterLayer>()
    private var layers = emptyList<PresentedLayer>()
    private val retiredLayers = mutableListOf<PresentedLayer>()
    private var ready: () -> Unit = {}
    private var attached = false
    private var startedAt = 0L
    private var bounds = Rect.Zero
    private var clip = Rect.Zero
    private var running = false
    private var needsUpdate = false

    init {
        host.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                attached = true
                hostReady = true
                updateBuffers()
                needsUpdate = true
                ready()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                needsUpdate = true
                ready()
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                attached = false
                hostReady = false
                releaseLayers()
            }
        })
        root.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun whenReady(callback: () -> Unit) { ready = callback }

    override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
        if (bounds.isEmpty || clip.isEmpty) {
            requestedLayers = emptyList()
            running = false
            releaseLayers()
            return false
        }
        val contentChanged = requestedLayers != layers
        val layoutChanged = this.bounds != bounds || this.clip != clip
        if (!contentChanged && !layoutChanged && !needsUpdate && hostReady) return true
        if (contentChanged) startedAt = SystemClock.uptimeMillis()
        requestedLayers = layers
        this.bounds = bounds
        this.clip = clip
        updateBuffers()
        running = layers.any { it.translation != null || it.revealMotion != null }
        needsUpdate = true
        if (!hostReady || this.layers.isEmpty()) return false
        return true
    }

    override fun synchronizeDraw() {
        if (!needsUpdate || !hostReady || layers.isEmpty()) return
        // Layout and image handoffs join the Compose window's next buffer. Free-running
        // motion still uses compositor-only transactions and never invalidates that window.
        SurfaceControl.Transaction().use { transaction ->
            updateSurfaces(transaction, SystemClock.uptimeMillis() - startedAt)
            needsUpdate = false
            if (Build.VERSION.SDK_INT >= 31 && root.rootSurfaceControl != null) {
                if (root.rootSurfaceControl!!.applyTransactionOnDraw(transaction)) {
                    // This handoff is requested from the current draw. Guarantee the next root
                    // draw that commits it; free-running motion remains compositor-only.
                    root.postInvalidateOnAnimation()
                } else transaction.apply()
            } else transaction.apply()
        }
        invalidate()
    }

    private fun updateBuffers() {
        if (!hostReady) return
        if (layers.size == requestedLayers.size && layers.zip(requestedLayers).all { (old, new) -> old.spec.image === new.image }) {
            layers.zip(requestedLayers).forEach { (old, new) -> old.spec = new }
            return
        }
        val previous = layers
        layers = requestedLayers.mapIndexed { index, spec ->
            val bitmap = spec.image.asAndroidBitmap()
            val reusable = previous.getOrNull(index)?.takeIf {
                it.spec.image.width == bitmap.width && it.spec.image.height == bitmap.height
            }
            if (reusable != null) {
                if (reusable.spec.image !== spec.image) upload(reusable.surface, spec)
                reusable.spec = spec
                return@mapIndexed reusable
            }
            val control = SurfaceControl.Builder().setName("Naviamp cached raster")
                .setParent(host.surfaceControl)
                .setBufferSize(bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1))
                .setFormat(PixelFormat.TRANSLUCENT).build()
            val surface = Surface(control)
            upload(surface, spec)
            PresentedLayer(control, surface, spec)
        }
        retiredLayers += previous.filter { old -> layers.none { it === old } }
    }

    private fun upload(surface: Surface, spec: NaviampRasterLayer) {
        val canvas = surface.lockHardwareCanvas()
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(spec.image.asAndroidBitmap(), 0f, 0f, null)
        } finally { surface.unlockCanvasAndPost(canvas) }
    }

    private fun releaseLayers() {
        val released = layers + retiredLayers
        if (released.isEmpty()) return
        SurfaceControl.Transaction().use { transaction ->
            released.forEach { if (it.control.isValid) transaction.reparent(it.control, null) }
            transaction.apply()
        }
        released.forEach { it.surface.release(); it.control.release() }
        retiredLayers.clear()
        layers = emptyList()
    }

    fun isAnimating(): Boolean = running && attached && hostReady
    fun hasReadySurface(): Boolean = needsUpdate && attached && hostReady && layers.isNotEmpty()

    fun appendUpdate(transaction: SurfaceControl.Transaction): Boolean {
        if (!attached || needsUpdate || !running) return false
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
        var changed = retiredLayers.isNotEmpty()
        retiredLayers.forEach {
            if (it.control.isValid) transaction.reparent(it.control, null)
            it.surface.release()
            it.control.release()
        }
        retiredLayers.clear()
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
                    floor(visibleLeft - bounds.left).toInt(), floor(visibleTop - bounds.top).toInt(),
                    ceil(visibleRight - bounds.left).toInt(), ceil(visibleBottom - bounds.top).toInt(),
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
        ready = {}
        running = false
        needsUpdate = false
        requestedLayers = emptyList()
        releaseLayers()
        root.removeAllViews()
        layers = emptyList()
        attached = false
        removed(this)
    }
}
