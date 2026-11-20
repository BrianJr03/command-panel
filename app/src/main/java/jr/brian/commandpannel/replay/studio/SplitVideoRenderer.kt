package jr.brian.commandpannel.replay.studio

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import jr.brian.commandpannel.privileged.ScreenRecorderEngine.Region
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Splits a Both clip's frames back onto the two screens (#47). The player decodes into [input] (one decoder, so the
 * halves never drift apart); a GL thread crops each frame's top region onto output slot [SLOT_TOP] and its bottom
 * region onto [SLOT_LOWER], each fit inside its surface with black around it. The GL setup follows
 * [jr.brian.commandpannel.privileged.StackedMirrorCompositor], which does the reverse for the Replay Buffer.
 *
 * Outputs come and go with their surfaces ([setOutput]); without any, frames are just latched so the decoder never
 * stalls. Every failure is logged and drawing stops, nothing throws to the caller.
 */
class SplitVideoRenderer {

    private val thread = HandlerThread("replay-split")
    private lateinit var handler: Handler

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    /** 1x1 pbuffer, current whenever no output is, so the texture can be latched. */
    private var idleSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var texture = 0
    private var source: SurfaceTexture? = null
    private val texMatrix = FloatArray(16)
    private var hasFrame = false

    private class Output(val surface: EGLSurface, val width: Int, val height: Int)
    private val outputs = arrayOfNulls<Output>(2)
    /** Per slot: x, y, s, t for the four corners of a triangle strip. */
    private val quads = Array(2) { ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer() }
    private val crops = arrayOfNulls<Region>(2)
    private var videoWidth = 0
    private var videoHeight = 0

    @Volatile private var released = false

    /** The decoder's output surface; valid after [start] returned true. */
    var input: Surface? = null
        private set

    /** Sets up GL; false (and everything released) if that fails. */
    fun start(): Boolean {
        thread.start()
        handler = Handler(thread.looper)
        val ready = CountDownLatch(1)
        var ok = false
        handler.post {
            ok = runCatching { setUp() }.onFailure { Log.w(TAG, "GL setup failed", it) }.isSuccess
            ready.countDown()
        }
        if (!ready.await(2, TimeUnit.SECONDS) || !ok) {
            release()
            return false
        }
        return true
    }

    /** The frame size and the two regions to crop out of it. */
    fun setCrop(videoWidth: Int, videoHeight: Int, top: Region, bottom: Region) = post {
        this.videoWidth = videoWidth
        this.videoHeight = videoHeight
        crops[SLOT_TOP] = top
        crops[SLOT_LOWER] = bottom
        for (slot in 0..1) updateQuad(slot)
        drawAll()
    }

    /**
     * Attaches [surface] ([width] x [height]) to [slot], or detaches the slot when [surface] is null. A detach waits
     * for the GL thread, so it's safe to call from `surfaceDestroyed`.
     */
    fun setOutput(slot: Int, surface: Surface?, width: Int, height: Int) {
        if (released || !::handler.isInitialized) return
        val done = CountDownLatch(1)
        handler.post {
            runCatching {
                dropOutput(slot)
                if (surface != null && surface.isValid) {
                    val egl = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
                    check(egl != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface" }
                    outputs[slot] = Output(egl, width, height)
                    updateQuad(slot)
                    drawAll()
                }
            }.onFailure { Log.w(TAG, "output $slot failed", it) }
            done.countDown()
        }
        if (surface == null) done.await(1, TimeUnit.SECONDS)
    }

    private fun post(block: () -> Unit) {
        if (released || !::handler.isInitialized) return
        handler.post { runCatching(block).onFailure { Log.w(TAG, "GL call failed", it) } }
    }

    private fun setUp() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "eglChooseConfig" }
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
        idleSurface = EGL14.eglCreatePbufferSurface(
            eglDisplay, eglConfig, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        check(idleSurface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface" }
        check(EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)) { "eglMakeCurrent" }

        program = linkProgram()
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val st = SurfaceTexture(texture).apply { setOnFrameAvailableListener({ onFrame() }, handler) }
        source = st
        input = Surface(st)
    }

    private fun onFrame() {
        if (released) return
        runCatching {
            // Latching needs a current context; any surface of it will do.
            EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)
            source?.updateTexImage()
            source?.getTransformMatrix(texMatrix)
            hasFrame = true
            drawAll()
        }.onFailure { Log.w(TAG, "frame failed", it) }
    }

    private fun updateQuad(slot: Int) {
        val crop = crops[slot] ?: return
        if (videoWidth <= 0 || videoHeight <= 0) return
        val (s0, t0, s1, t1) = regionTexCoords(crop, videoWidth, videoHeight).toList()
        quads[slot].apply {
            clear()
            put(floatArrayOf(-1f, -1f, s0, t0, 1f, -1f, s1, t0, -1f, 1f, s0, t1, 1f, 1f, s1, t1))
            position(0)
        }
    }

    private fun drawAll() {
        if (!hasFrame) return
        for (slot in 0..1) draw(slot)
    }

    private fun draw(slot: Int) {
        val out = outputs[slot] ?: return
        val crop = crops[slot] ?: return
        if (!EGL14.eglMakeCurrent(eglDisplay, out.surface, out.surface, eglContext)) return
        GLES20.glViewport(0, 0, out.width, out.height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val (x, y, w, h) = fitViewport(crop.width, crop.height, out.width, out.height).toList()
        GLES20.glViewport(x, y, w, h)
        GLES20.glUseProgram(program)
        val quad = quads[slot]
        quad.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPosition)
        quad.position(2)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGL14.eglSwapBuffers(eglDisplay, out.surface)
    }

    private fun dropOutput(slot: Int) {
        val out = outputs[slot] ?: return
        outputs[slot] = null
        EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)
        EGL14.eglDestroySurface(eglDisplay, out.surface)
    }

    /** Stops drawing and frees GL, the input surface and the thread. Detach [input] from the player first. */
    fun release() {
        if (released) return
        released = true
        if (!thread.isAlive || !::handler.isInitialized) return
        val done = CountDownLatch(1)
        handler.post {
            runCatching { tearDown() }.onFailure { Log.w(TAG, "teardown failed", it) }
            done.countDown()
        }
        done.await(1, TimeUnit.SECONDS)
        thread.quitSafely()
    }

    private fun tearDown() {
        input?.release()
        input = null
        source?.release()
        source = null
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            for (slot in 0..1) dropOutput(slot)
            if (program != 0) GLES20.glDeleteProgram(program)
            if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (idleSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, idleSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }

    private fun linkProgram(): Int {
        fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { "shader compile: ${GLES20.glGetShaderInfoLog(shader)}" }
            return shader
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "program link: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    companion object {
        private const val TAG = "ReplaySplit"
        const val SLOT_TOP = 0
        const val SLOT_LOWER = 1

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
