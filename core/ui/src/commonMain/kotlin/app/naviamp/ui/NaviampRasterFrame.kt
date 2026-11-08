package app.naviamp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Pixel geometry for a sampled compositor. Unchanged pixels need no native transaction.
 * One instance belongs to one native layer lifetime; replacing that layer starts a fresh frame.
 * Sampling still follows the original motion clock, including subpixel movement and end points.
 */
internal class NaviampRasterFrame {
    var visible = false; private set
    var sourceLeft = 0; private set
    var sourceTop = 0; private set
    var sourceRight = 0; private set
    var sourceBottom = 0; private set
    var destinationLeft = 0; private set
    var destinationTop = 0; private set
    var destinationRight = 0; private set
    var destinationBottom = 0; private set
    var scaleX = 1f; private set
    var scaleY = 1f; private set
    var positionX = 0f; private set
    var positionY = 0f; private set
    var sourceChanged = false; private set
    var scaleChanged = false; private set
    var positionChanged = false; private set

    fun update(layer: NaviampRasterLayer, bounds: Rect, clip: Rect, elapsedMillis: Long): Boolean =
        update(layer.image.width, layer.image.height, layer.origin, layer.translation,
            layer.reveal, layer.revealMotion, layer.clipFromStart, bounds, clip, elapsedMillis)

    internal fun update(
        imageWidth: Int, imageHeight: Int, origin: Offset, translation: NaviampLayerMotion?,
        reveal: Float, revealMotion: NaviampLayerMotion?, clipFromStart: Boolean,
        bounds: Rect, clip: Rect, elapsedMillis: Long,
    ): Boolean {
        sourceChanged = false
        scaleChanged = false
        positionChanged = false
        val imageLeft = bounds.left + origin.x + (translation?.valueAt(elapsedMillis) ?: 0f)
        val imageTop = bounds.top + origin.y
        var left = max(clip.left, imageLeft)
        var right = min(clip.right, imageLeft + imageWidth)
        val top = max(clip.top, imageTop)
        val bottom = min(clip.bottom, imageTop + imageHeight)
        if (translation == null) {
            val edge = bounds.left + bounds.width * (revealMotion?.valueAt(elapsedMillis) ?: reveal)
            if (clipFromStart) left = max(left, edge) else right = min(right, edge)
        }
        if (bounds.isEmpty || clip.isEmpty || right <= left || bottom <= top) {
            val changed = visible
            visible = false
            return changed
        }
        val sl = floor(left - imageLeft).toInt()
        val st = floor(top - imageTop).toInt()
        val sr = ceil(right - imageLeft).toInt()
        val sb = ceil(bottom - imageTop).toInt()
        val dl = floor(left - bounds.left).toInt()
        val dt = floor(top - bounds.top).toInt()
        val dr = ceil(right - bounds.left).toInt()
        val db = ceil(bottom - bounds.top).toInt()
        val sx = (dr - dl).toFloat() / (sr - sl)
        val sy = (db - dt).toFloat() / (sb - st)
        val px = dl - sl * sx
        val py = dt - st * sy
        sourceChanged = !visible || sourceLeft != sl || sourceTop != st || sourceRight != sr || sourceBottom != sb
        scaleChanged = !visible || scaleX != sx || scaleY != sy
        positionChanged = !visible || positionX != px || positionY != py
        val changed = !visible || sourceLeft != sl || sourceTop != st || sourceRight != sr ||
            sourceBottom != sb || destinationLeft != dl || destinationTop != dt ||
            destinationRight != dr || destinationBottom != db
        visible = true
        sourceLeft = sl; sourceTop = st; sourceRight = sr; sourceBottom = sb
        destinationLeft = dl; destinationTop = dt; destinationRight = dr; destinationBottom = db
        scaleX = sx; scaleY = sy
        positionX = px; positionY = py
        return changed
    }
}
