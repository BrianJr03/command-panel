package jr.brian.commandpannel.system

import android.app.ActivityManager
import android.content.Context
import android.view.Display
import jr.brian.commandpannel.privileged.PrivilegedAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Live hardware readings for the Settings tab. */
data class Telemetry(
    val tempC: Int = 0,
    val fanPercent: Int = 0,
    val cpuGhz: Float = 0f,
    val cpuFraction: Float = 0f,
    val gpuMhz: Int = 0,
    val gpuFraction: Float = 0f,
    /** Magnitude of the net battery power in watts (always >= 0); [powerCharging] gives the direction. */
    val powerW: Float = 0f,
    val powerFraction: Float = 0f,
    val powerCharging: Boolean = false,
    val ramGb: Float = 0f,
    val ramFraction: Float = 0f,
    val fps: Int = 0,
    /** True when [fps] is the compositor's count of every frame on both screens, not the game's own frames. */
    val fpsCompositor: Boolean = false,
)

/**
 * Polls the stock telemetry sources every [intervalMs] while started: sysfs nodes read directly (the probe showed
 * the app's own uid can read them), the fan via [FanProvider], RAM via [ActivityManager], and FPS from
 * the game task through the root helper's task FPS callback (frames that task actually presented, on whichever
 * display it is on). When that has no fresh reading (no game up, helper down, the callback unsupported), FPS falls
 * back to SurfaceFlinger's global composite counter (transaction 1013, which the app's uid is refused but root
 * allows), read over the helper binder or, without the helper, `service call` through the root shell as stock does.
 * That counter includes both screens and the overlay's own animations, so the reading is flagged
 * [Telemetry.fpsCompositor] and the panel labels it.
 *
 * The per-task source and that label are opt-in from the companion's Experimental section until they've had more
 * QA (#4): [taskFpsEnabled] reads the `experimental_task_fps` pref every sample. With it off, FPS is the global
 * counter alone, unlabeled, as before.
 */
class TelemetryMonitor(
    private val context: Context,
    private val privileged: PrivilegedAccess,
    private val refreshRateCap: () -> Int,
    taskFpsEnabled: () -> Boolean,
    private val intervalMs: Long = 1500L,
) {
    companion object {
        private const val TEMP = "/sys/devices/virtual/thermal/thermal_zone48/temp"
        private const val CPU_CUR = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_cur_freq"
        /** World-readable on firmware where [CPU_CUR] is root-only (#87). */
        private const val CPU_SCALING_CUR = "/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq"
        private const val CPU_MAX = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq"
        private const val GPU_CUR = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
        private const val GPU_MAX = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
        private const val CURRENT_NOW = "/sys/class/power_supply/battery/current_now"
        private const val VOLTAGE_NOW = "/sys/class/power_supply/battery/voltage_now"

        /** An FPS baseline older than this many intervals spans a pause, so it gives no reading (#92). */
        private const val MAX_BASELINE_INTERVALS = 4

        /** Pref for per-game FPS from the task callback, with the "compositor FPS" label on fallback (#4). */
        const val PREF_TASK_FPS = "experimental_task_fps"
    }

    private val _state = MutableStateFlow(Telemetry())
    val state: StateFlow<Telemetry> = _state.asStateFlow()

    private var job: Job? = null
    private val taskFps = TaskFpsSwitch(taskFpsEnabled, stop = privileged::stopTaskFps)
    private var lastFrames: Long? = null
    private var lastFrameTime: Long = 0L

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        // Stopped since the last run (the screen was off, #92): no FPS from a frame count taken before the gap.
        lastFrames = null
        _state.value = _state.value.copy(fps = 0)
        job = scope.launch {
            while (isActive) {
                // File reads, binder calls and (without the helper) a root shell: never on the caller's main thread.
                _state.value = withContext(Dispatchers.IO) { sample() }
                PollTrace.tick("telemetry")
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        taskFps.release()
    }

    private fun read(path: String): String = runCatching { File(path).readText() }.getOrDefault("")

    private suspend fun sample(): Telemetry {
        val tempC = TelemetryReader.parseTempC(read(TEMP)) ?: 0
        val cpuCur = TelemetryReader.firstReading(read(CPU_CUR), read(CPU_SCALING_CUR))
        val cpuGhz = TelemetryReader.kHzToGhz(cpuCur) ?: 0f
        val cpuFrac = TelemetryReader.fraction(cpuCur, read(CPU_MAX)) ?: 0f
        val gpuMhz = TelemetryReader.hzToMhz(read(GPU_CUR)) ?: 0
        val gpuFrac = TelemetryReader.fraction(read(GPU_CUR), read(GPU_MAX)) ?: 0f
        val watts = TelemetryReader.powerWatts(read(CURRENT_NOW), read(VOLTAGE_NOW)) ?: 0f

        val mem = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        val usedBytes = (mem.totalMem - mem.availMem).toFloat()
        val ramGb = usedBytes / 1_000_000_000f
        val ramFrac = (usedBytes / mem.totalMem.toFloat()).coerceIn(0f, 1f)

        val fan = FanProvider.dutyPercent() ?: _state.value.fanPercent
        val fps = sampleFps()

        return Telemetry(
            tempC = tempC,
            fanPercent = fan,
            cpuGhz = cpuGhz, cpuFraction = cpuFrac,
            gpuMhz = gpuMhz, gpuFraction = gpuFrac,
            powerW = abs(watts), powerFraction = TelemetryReader.powerFraction(watts),
            powerCharging = TelemetryReader.isCharging(watts),
            ramGb = ramGb, ramFraction = ramFrac,
            fps = fps.fps, fpsCompositor = fps.compositor,
        )
    }

    private suspend fun sampleFps(): TelemetryReader.FpsReading {
        val cap = refreshRateCap()
        // The global counter is sampled every time so its delta is ready the moment the task source drops out.
        val globalFps = sampleGlobalFps(cap)
        if (!taskFps.sample()) return TelemetryReader.FpsReading(globalFps, compositor = false)
        // The game is usually on the top screen; the helper also checks the focused task and the bottom screen.
        val task = privileged.taskFps(Display.DEFAULT_DISPLAY)
        return TelemetryReader.chooseFps(task?.fps, task?.ageMs, globalFps, cap)
    }

    private suspend fun sampleGlobalFps(cap: Int): Int {
        // Timestamp each read at the midpoint of the call itself, so binder or fork latency doesn't skew the interval.
        // The helper's direct binder transaction is sub-millisecond; the shell fallback forks `sh` + `service`.
        val (frames, now) = timed { privileged.surfaceFlingerFrameCount() }
            ?: timed { privileged.run("service", "call", "SurfaceFlinger", "1013")?.let(TelemetryReader::parseSurfaceFlingerFrames) }
            ?: return _state.value.fps
        val delta = TelemetryReader.frameDelta(lastFrames, frames)
        val dt = now - lastFrameTime
        lastFrames = frames
        lastFrameTime = now
        // A baseline far older than one interval spans a pause (a sample still running as the poller stopped).
        if (delta != null && dt > intervalMs * MAX_BASELINE_INTERVALS) return 0
        return if (delta == null) _state.value.fps else TelemetryReader.fps(delta, dt, cap)
    }

    /** Runs [read] and pairs a non-null result with the midpoint of the call in milliseconds (monotonic clock). */
    private inline fun timed(read: () -> Long?): Pair<Long, Long>? {
        val t0 = System.nanoTime()
        val v = read() ?: return null
        val t1 = System.nanoTime()
        return v to (t0 + t1) / 2 / 1_000_000L
    }
}

/**
 * Tracks the per-game FPS switch across samples, so the root helper's task FPS callback is stopped once when the
 * switch turns off (or the monitor stops while it was on), and never touched while it stays off.
 */
internal class TaskFpsSwitch(private val enabled: () -> Boolean, private val stop: () -> Unit) {
    private var wasOn = false

    /** Reads the switch for this sample: true to use the task source. Stops the callback on an on-to-off change. */
    fun sample(): Boolean {
        val on = enabled()
        if (wasOn && !on) stop()
        wasOn = on
        return on
    }

    /** The monitor is stopping: stops the callback if the last sample used it. */
    fun release() {
        if (wasOn) stop()
        wasOn = false
    }
}
