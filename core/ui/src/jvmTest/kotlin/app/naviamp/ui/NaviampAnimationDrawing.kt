package app.naviamp.ui

import androidx.compose.ui.graphics.drawscope.DrawScope

/** Core renders a frame and explicitly requests whether another presentation frame is needed. */
internal fun interface NaviampAnimationDrawing {
    fun drawFrame(scope: DrawScope, frameTimeNanos: Long): Boolean
}
