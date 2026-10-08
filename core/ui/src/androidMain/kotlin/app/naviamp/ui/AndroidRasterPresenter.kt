package app.naviamp.ui

import android.app.Activity
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.MutableStateFlow

/** Android publishes only its visible lifecycle and hardware-compositor presentation boundary. */
@Composable
fun NaviampAndroidRasterHost(content: @Composable () -> Unit) {
    val activity = LocalContext.current as? Activity
    val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    val gpu = remember(activity) { activity?.let(::AndroidGpuVisualizerPresenter) }
    val presenter = remember(activity) {
        activity?.takeIf { Build.VERSION.SDK_INT >= 29 }?.let(::AndroidRasterPresenter)
    }
    val visibility = remember(lifecycle) {
        MutableStateFlow(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true)
    }
    val visible by visibility.collectAsState()
    CompositionLocalProvider(LocalNaviampGpuVisualizerPresenter provides gpu) {
        NaviampRasterEnvironment(presenter, visible, false, false, visibility, content)
    }
    DisposableEffect(lifecycle, presenter) {
        val observer = LifecycleEventObserver { _, _ ->
            visibility.value = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); presenter?.close() }
    }
}

private class AndroidRasterPresenter(private val activity: Activity) : NaviampRasterPresenter, Choreographer.FrameCallback {
    private val regions = mutableSetOf<AndroidRasterRegion>()
    private val transaction = SurfaceControl.Transaction()
    private val frameSchedule = NaviampRasterFrameSchedule()

    override fun create(): NaviampRasterRegion = AndroidRasterRegion(activity, ::scheduleFrame) {
        regions.remove(it)
        scheduleFrame()
    }.also(regions::add)

    @Composable
    override fun Content(region: NaviampRasterRegion) {
        AndroidView(factory = { (region as AndroidRasterRegion).root }, modifier = Modifier.fillMaxSize())
    }

    private fun scheduleFrame() {
        val now = SystemClock.uptimeMillis()
        when (val request = frameSchedule.request(regions.mapNotNull { it.nextFrameDelay(now) }, now)) {
            NaviampRasterFrameRequest.Unchanged -> Unit
            NaviampRasterFrameRequest.Cancel -> Choreographer.getInstance().removeFrameCallback(this)
            is NaviampRasterFrameRequest.Schedule -> {
                Choreographer.getInstance().removeFrameCallback(this)
                Choreographer.getInstance().postFrameCallbackDelayed(this, request.delayMillis)
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameSchedule.delivered()
        var changed = false
        regions.forEach { changed = it.appendUpdate(transaction) || changed }
        if (changed) transaction.apply()
        scheduleFrame()
    }

    fun close() {
        Choreographer.getInstance().removeFrameCallback(this)
        frameSchedule.delivered()
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
        val surface: Surface?,
        var spec: NaviampRasterLayer,
        var hardwareBitmap: Bitmap? = null,
        var bufferChanged: Boolean = false,
        val source: AndroidRect = AndroidRect(),
        val destination: AndroidRect = AndroidRect(),
        val cropState: NaviampRasterPixelCropState = NaviampRasterPixelCropState(),
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
    private var needsUpdate = false

    init {
        host.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                attached = true
                hostReady = true
                // Current Android does not present a manually parented child hierarchy until
                // the SurfaceView's own buffer queue has latched at least one frame.
                val canvas = holder.surface.lockHardwareCanvas()
                try {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                } finally {
                    holder.surface.unlockCanvasAndPost(canvas)
                }
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
        needsUpdate = true
        if (!hostReady || this.layers.isEmpty()) return false
        return true
    }

    override fun synchronizeDraw() {
        if (!needsUpdate || !hostReady || layers.isEmpty()) return
        // The SurfaceView render thread owns parent movement. Each child transaction contains
        // complete current geometry, so layout/image handoffs can commit without waiting on a
        // root draw that some Android compositors never deliver.
        SurfaceControl.Transaction().use { transaction ->
            val changed = updateSurfaces(transaction, SystemClock.uptimeMillis() - startedAt)
            needsUpdate = false
            if (changed) transaction.apply()
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
                if (reusable.spec.image !== spec.image) upload(reusable, spec)
                reusable.spec = spec
                return@mapIndexed reusable
            }
            val control = SurfaceControl.Builder().setName("Naviamp cached raster")
                .setParent(host.surfaceControl)
                .setBufferSize(bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1))
                .setFormat(PixelFormat.TRANSLUCENT).build()
            val layer = PresentedLayer(
                control = control,
                surface = if (Build.VERSION.SDK_INT >= 34) null else Surface(control),
                spec = spec,
            )
            upload(layer, spec)
            layer
        }
        retiredLayers += previous.filter { old -> layers.none { it === old } }
    }

    private fun upload(layer: PresentedLayer, spec: NaviampRasterLayer) {
        if (Build.VERSION.SDK_INT >= 34) {
            layer.hardwareBitmap?.recycle()
            layer.hardwareBitmap = requireNotNull(
                spec.image.asAndroidBitmap().copy(Bitmap.Config.HARDWARE, false),
            ) { "Android could not allocate a hardware raster buffer" }
            layer.bufferChanged = true
            return
        }
        val surface = requireNotNull(layer.surface)
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
        released.forEach {
            it.surface?.release()
            it.hardwareBitmap?.recycle()
            it.control.release()
        }
        retiredLayers.clear()
        layers = emptyList()
    }

    fun nextFrameDelay(nowMillis: Long): Long? {
        if (!attached || !hostReady || layers.isEmpty()) return null
        return if (needsUpdate) 0L else naviampRasterFrameDelay(layers.map { it.spec }, bounds.width, nowMillis - startedAt)
    }

    fun appendUpdate(transaction: SurfaceControl.Transaction): Boolean {
        if (!attached || needsUpdate) return false
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val changed = updateSurfaces(transaction, elapsed)
        needsUpdate = false
        return changed
    }

    private fun updateSurfaces(transaction: SurfaceControl.Transaction, elapsed: Long): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        var changed = retiredLayers.isNotEmpty()
        retiredLayers.forEach {
            if (it.control.isValid) transaction.reparent(it.control, null)
            it.surface?.release()
            it.hardwareBitmap?.recycle()
            it.control.release()
        }
        retiredLayers.clear()
        layers.forEachIndexed { index, layer ->
                if (!layer.control.isValid) return@forEachIndexed
                val spec = layer.spec
                val dx = spec.translation?.valueAt(elapsed) ?: 0f
                val imageLeft = bounds.left + spec.origin.x + dx
                val imageTop = bounds.top + spec.origin.y
                val reveal = spec.revealMotion?.valueAt(elapsed) ?: spec.reveal
                val crop = naviampRasterPixelCrop(
                    Rect(imageLeft, imageTop, imageLeft + spec.image.width, imageTop + spec.image.height),
                    bounds, clip, if (spec.translation == null) reveal else null, spec.clipFromStart,
                )
                if (!layer.cropState.update(crop, force = layer.bufferChanged || !layer.ordered)) return@forEachIndexed
                if (crop == null) {
                    if (layer.visible) {
                        transaction.setVisibility(layer.control, false)
                        layer.visible = false
                        changed = true
                    }
                    return@forEachIndexed
                }
                layer.source.set(
                    crop.source.left, crop.source.top, crop.source.right, crop.source.bottom,
                )
                layer.destination.set(
                    crop.destination.left, crop.destination.top, crop.destination.right, crop.destination.bottom,
                )
                if (!layer.visible) {
                    transaction.setVisibility(layer.control, true)
                    layer.visible = true
                }
                if (!layer.ordered) {
                    // Keep cached children above the host SurfaceView's own buffer. Android 15
                    // resolves a layer-zero tie in favor of the parent, hiding child pixels.
                    transaction.setLayer(layer.control, index + 1)
                    layer.ordered = true
                }
                if (Build.VERSION.SDK_INT >= 34 && layer.bufferChanged) {
                    transaction.setBuffer(layer.control, requireNotNull(layer.hardwareBitmap).hardwareBuffer)
                    layer.bufferChanged = false
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    val scaleX = layer.destination.width().toFloat() / layer.source.width()
                    val scaleY = layer.destination.height().toFloat() / layer.source.height()
                    transaction.setCrop(layer.control, layer.source)
                        .setBufferTransform(layer.control, SurfaceControl.BUFFER_TRANSFORM_IDENTITY)
                        .setScale(layer.control, scaleX, scaleY)
                        .setPosition(
                            layer.control,
                            layer.destination.left - layer.source.left * scaleX,
                            layer.destination.top - layer.source.top * scaleY,
                        )
                } else {
                    @Suppress("DEPRECATION")
                    transaction.setGeometry(layer.control, layer.source, layer.destination,
                        SurfaceControl.BUFFER_TRANSFORM_IDENTITY)
                }
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
        needsUpdate = false
        requestedLayers = emptyList()
        releaseLayers()
        root.removeAllViews()
        layers = emptyList()
        attached = false
        removed(this)
    }
}
