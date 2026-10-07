package app.naviamp.ui

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** GLES/SurfaceView adapter. Core supplies frames; there is no host frame loop or readback. */
internal class AndroidGpuVisualizerPresenter(private val context: Context) : NaviampGpuVisualizerPresenter {
    override fun create(shader: NaviampGpuVisualizerShader): NaviampGpuVisualizerRegion {
        return AndroidGpuVisualizerRegion(context, shader)
    }
    @Composable override fun Content(region: NaviampGpuVisualizerRegion) {
        AndroidView(factory = { (region as AndroidGpuVisualizerRegion).view }, modifier = Modifier.fillMaxSize())
    }
}

private class AndroidGpuVisualizerRegion(context: Context, private val shader: NaviampGpuVisualizerShader) :
    NaviampGpuVisualizerRegion, GLSurfaceView.Renderer {
    private val pending = AtomicReference<NaviampGpuVisualizerFrame?>()
    private val ready = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private var visible = false
    private var program = 0
    private var frequencyTexture = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    @Volatile private var bounds = Rect.Zero
    @Volatile private var clip = Rect.Zero
    private val frequencyBytes = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder()).asFloatBuffer()
    val view = object : GLSurfaceView(context) {
        override fun onTouchEvent(event: android.view.MotionEvent?) = false
    }.apply {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        preserveEGLContextOnPause = true
        setRenderer(this@AndroidGpuVisualizerRegion)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    override fun place(bounds: Rect, clip: Rect, cornerRadius: Float) { this.bounds = bounds; this.clip = clip }
    override fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (visible) {
            view.onResume()
            // SurfaceView creates its native surface during the next root pre-draw traversal.
            // The shared scene is deliberately idle, so mounting needs one native traversal.
            view.post { view.rootView.requestLayout(); view.rootView.invalidate() }
        } else view.onPause()
    }
    override fun submit(frame: NaviampGpuVisualizerFrame): NaviampGpuSubmission {
        if (failed.get()) return NaviampGpuSubmission.Failed
        if (!ready.get() || !visible) {
            if (visible) view.requestRender()
            return NaviampGpuSubmission.NotReady
        }
        if (!pending.compareAndSet(null, frame)) return NaviampGpuSubmission.Busy
        view.requestRender()
        return NaviampGpuSubmission.Accepted
    }
    private fun compile(type: Int, source: String): Int {
        val shader = GL.glCreateShader(type)
        GL.glShaderSource(shader, source); GL.glCompileShader(shader)
        val status = IntArray(1); GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) { val error = GL.glGetShaderInfoLog(shader); GL.glDeleteShader(shader); error(error) }
        return shader
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            val vertex = compile(GL.GL_VERTEX_SHADER, DirectGlVertex)
            val fragment = compile(GL.GL_FRAGMENT_SHADER, shader.glsl)
            program = GL.glCreateProgram(); GL.glAttachShader(program, vertex); GL.glAttachShader(program, fragment)
            GL.glLinkProgram(program); GL.glDeleteShader(vertex); GL.glDeleteShader(fragment)
            val linked = IntArray(1); GL.glGetProgramiv(program, GL.GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { GL.glGetProgramInfoLog(program) }
            val textures = IntArray(1); GL.glGenTextures(1, textures, 0); frequencyTexture = textures[0]
            GL.glBindTexture(GL.GL_TEXTURE_2D, frequencyTexture)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
            // GLES3 half-float texture storage supports linear filtering without float32 extensions.
            GL.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_R16F, 32, 1, 0, GL.GL_RED, GL.GL_FLOAT, null)
            ready.set(true)
        } catch (error: Throwable) { android.util.Log.e("NaviampGpuVisualizer", "GLES surface creation failed", error); failed.set(true) }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width; viewportHeight = height
        GL.glViewport(0, 0, width, height)
    }
    override fun onDrawFrame(gl: GL10?) {
        val frame = pending.get() ?: return
        try {
            GL.glDisable(GL.GL_SCISSOR_TEST); GL.glClearColor(0f, 0f, 0f, 0f); GL.glClear(GL.GL_COLOR_BUFFER_BIT)
            val b = bounds; val c = clip
            GL.glEnable(GL.GL_SCISSOR_TEST)
            GL.glScissor((c.left - b.left).toInt().coerceAtLeast(0),
                (viewportHeight - (c.bottom - b.top)).toInt().coerceAtLeast(0),
                c.width.toInt().coerceAtLeast(0), c.height.toInt().coerceAtLeast(0))
            GL.glUseProgram(program)
            GL.glEnable(GL.GL_BLEND); GL.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA)
            fun f(slot: Int) = Float.fromBits(frame.uniforms[slot])
            fun scalar(name: String, slot: Int) { GL.glUniform1f(GL.glGetUniformLocation(program, name), f(slot)) }
            scalar("u_time", 0); GL.glUniform2f(GL.glGetUniformLocation(program, "u_resolution"), f(1), f(2))
            scalar("u_energyLevel", 3); scalar("u_bassLevel", 4); scalar("u_midLevel", 5)
            scalar("u_trebleLevel", 6); scalar("u_spectralCentroid", 7); scalar("u_tempoBpm", 8)
            scalar("u_beatDetected", 9); scalar("u_active", 10); scalar("u_renderScale", 11)
            GL.glUniform1i(GL.glGetUniformLocation(program, "u_maxRaymarchSteps"), frame.uniforms[12])
            for ((name, slot) in listOf("u_accent" to 13, "u_readable" to 17, "u_colorA" to 21,
                "u_colorB" to 25, "u_colorC" to 29, "u_idle" to 35)) {
                GL.glUniform4f(GL.glGetUniformLocation(program, name), f(slot), f(slot+1), f(slot+2), f(slot+3))
            }
            GL.glActiveTexture(GL.GL_TEXTURE0); GL.glBindTexture(GL.GL_TEXTURE_2D, frequencyTexture)
            frequencyBytes.clear(); frequencyBytes.put(frame.bands); frequencyBytes.flip()
            GL.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, 32, 1, GL.GL_RED, GL.GL_FLOAT, frequencyBytes)
            GL.glUniform1i(GL.glGetUniformLocation(program, "u_frequencyTexture"), 0)
            GL.glDrawArrays(GL.GL_TRIANGLES, 0, 3)
        } catch (error: Throwable) { android.util.Log.e("NaviampGpuVisualizer", "GLES submission failed", error); failed.set(true) }
        finally { pending.compareAndSet(frame, null) }
    }
    override fun close() {
        visible = false; pending.set(null)
        view.queueEvent {
            if (program != 0) GL.glDeleteProgram(program)
            if (frequencyTexture != 0) GL.glDeleteTextures(1, intArrayOf(frequencyTexture), 0)
        }
        view.onPause()
    }
}

private const val DirectGlVertex = """#version 300 es
void main() {
    vec2 positions[3] = vec2[3](vec2(-1,-1), vec2(3,-1), vec2(-1,3));
    gl_Position = vec4(positions[gl_VertexID], 0, 1);
}
"""
