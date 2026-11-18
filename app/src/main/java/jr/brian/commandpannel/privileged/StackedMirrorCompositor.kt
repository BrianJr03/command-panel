package jr.brian.commandpannel.privileged

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Draws two display mirrors into one encoder input [output] (the Replay Buffer's both-screens capture). Each screen is
 * mirrored by the root helper into its own [SurfaceTexture] ([inputs]); a GL thread redraws the combined frame,
 * each source into its [regions] rect (top-left origin, [outWidth] x [outHeight] pixels, the rest black), whenever
 * either mirror updates, at most [fps] times a second.
 */
internal class StackedMirrorCompositor(
    private val output: Surface,
    private val outWidth: Int,
    private val outHeight: Int,
    private val regions: List<Rect>,
    fps: Int,
) {
    private companion object {
        const val TAG = "StackedMirror"
        const val EGL_RECORDABLE_ANDROID = 0x3142

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """
        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        /** A full-viewport quad as a triangle strip: x, y, u, v per corner. */
        val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
    }

    private val frameIntervalMs = 1000L / fps.coerceAtLeast(1)
    private val thread = HandlerThread("replay-compose")
    private lateinit var handler: Handler

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(QUAD.size * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(QUAD); position(0) }
    private val texMatrix = FloatArray(16)

    private val textures = IntArray(regions.size)
    private val sources = mutableListOf<SurfaceTexture>()

    /** One input per region, sized to it; hand these to the display mirrors. Valid after [start] returns true. */
    val inputs = mutableListOf<Surface>()

    private var drawScheduled = false
    private var lastDrawMs = 0L
    @Volatile private var released = false

    /** Sets up GL on the compositor thread; false (and everything released) if that fails. */
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

    private fun setUp() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "eglChooseConfig" }
        val config = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, output, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent" }

        program = linkProgram()
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")

        GLES20.glGenTextures(textures.size, textures, 0)
        regions.forEachIndexed { i, region ->
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[i])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val source = SurfaceTexture(textures[i]).apply {
                setDefaultBufferSize(region.width(), region.height())
                setOnFrameAvailableListener({ scheduleDraw() }, handler)
            }
            sources.add(source)
            inputs.add(Surface(source))
        }
    }

    /** Redraws soon after a mirror updates, but no more than once per frame interval. */
    private fun scheduleDraw() {
        if (released || drawScheduled) return
        drawScheduled = true
        val wait = (lastDrawMs + frameIntervalMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        handler.postDelayed(::draw, wait)
    }

    private fun draw() {
        drawScheduled = false
        if (released) return
        lastDrawMs = SystemClock.uptimeMillis()
        runCatching {
            GLES20.glViewport(0, 0, outWidth, outHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            quad.position(0)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aPosition)
            quad.position(2)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aTexCoord)
            sources.forEachIndexed { i, source ->
                // Latches this mirror's newest frame (a no-op if it hasn't changed since the last draw).
                source.updateTexImage()
                source.getTransformMatrix(texMatrix)
                val r = regions[i]
                // GL's viewport origin is the bottom-left corner.
                GLES20.glViewport(r.left, outHeight - r.bottom, r.width(), r.height())
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[i])
                GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, System.nanoTime())
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }.onFailure { Log.w(TAG, "draw failed", it) }
    }

    /** Stops drawing and frees the GL objects, inputs and thread. Stop the mirrors feeding [inputs] first. */
    fun release() {
        if (released) return
        released = true
        if (!thread.isAlive) return
        val done = CountDownLatch(1)
        handler.post {
            runCatching { tearDown() }.onFailure { Log.w(TAG, "teardown failed", it) }
            done.countDown()
        }
        done.await(1, TimeUnit.SECONDS)
        thread.quitSafely()
    }

    private fun tearDown() {
        inputs.forEach { it.release() }
        inputs.clear()
        sources.forEach { it.release() }
        sources.clear()
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (program != 0) GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(textures.size, textures, 0)
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }

    private fun linkProgram(): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
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
}
