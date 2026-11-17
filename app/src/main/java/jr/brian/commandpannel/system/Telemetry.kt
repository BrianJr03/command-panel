package jr.brian.commandpannel.system

import android.app.ActivityManager
import android.content.Context
import android.view.Display
import jr.brian.commandpannel.privileged.PrivilegedAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Live hardware readings for the Settings tab. */
data class Telemetry(
    val tempC: Int = 0,
    val fanPercent: Int = 0,
    val cpuGhz: Float = 0f,
    val cpuFraction: Float = 0f,
    val gpuMhz: Int = 0,
    val gpuFraction: Float = 0f,
    val powerW: Float = 0f,
    val powerFraction: Float = 0.5f,
    val ramGb: Float = 0f,
    val ramFraction: Float = 0f,
    val fps: Int = 0,
)

/**
 * Polls the stock telemetry sources every [intervalMs] while started: sysfs nodes read directly (the probe showed
 * the app's own uid can read them), the fan via [FanProvider], RAM via [ActivityManager], and FPS from
 * SurfaceFlinger's global composite counter (transaction 1013, which the app's uid is refused but root allows) via
 * `service call` through the root shell, as stock does. With [NEW_FPS_SOURCES] on, FPS instead comes from the game
 * task on the top display through the root helper's task FPS callback (frames that task actually presented),
 * falling back to the global counter read over the helper binder when no game is up.
 */
class TelemetryMonitor(
    private val context: Context,
    private val privileged: PrivilegedAccess,
    private val refreshRateCap: () -> Int,
    private val intervalMs: Long = 1500L,
) {
    companion object {
        /**
         * Off: FPS is the stock method only (`service call SurfaceFlinger 1013` through the root shell). On: the
         * per-task game FPS from the root helper, with the global counter read over the helper binder as fallback.
         * Kept off until the new sources are verified on device.
         */
        const val NEW_FPS_SOURCES = false

        private const val TEMP = "/sys/devices/virtual/thermal/thermal_zone48/temp"
        private const val CPU_CUR = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_cur_freq"
        private const val CPU_MAX = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq"
        private const val GPU_CUR = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
        private const val GPU_MAX = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
        private const val CURRENT_NOW = "/sys/class/power_supply/battery/current_now"
        private const val VOLTAGE_NOW = "/sys/class/power_supply/battery/voltage_now"
    }

    private val _state = MutableStateFlow(Telemetry())
    val state: StateFlow<Telemetry> = _state.asStateFlow()

    private var job: Job? = null
    private var lastFrames: Long? = null
    private var lastFrameTime: Long = 0L

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                _state.value = sample()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        if (NEW_FPS_SOURCES) privileged.stopTaskFps()
    }

    private fun read(path: String): String = runCatching { File(path).readText() }.getOrDefault("")

    private fun sample(): Telemetry {
        val tempC = TelemetryReader.parseTempC(read(TEMP)) ?: 0
        val cpuGhz = TelemetryReader.kHzToGhz(read(CPU_CUR)) ?: 0f
        val cpuFrac = TelemetryReader.fraction(read(CPU_CUR), read(CPU_MAX)) ?: 0f
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
            powerW = watts, powerFraction = TelemetryReader.powerFraction(watts),
            ramGb = ramGb, ramFraction = ramFrac,
            fps = fps,
        )
    }

    private fun sampleFps(): Int {
        val cap = refreshRateCap()
        // The global counter is sampled every time so its delta is ready the moment the task source drops out.
        val globalFps = sampleGlobalFps(cap)
        if (!NEW_FPS_SOURCES) return globalFps
        val task = privileged.taskFps(Display.DEFAULT_DISPLAY)
        return TelemetryReader.chooseFps(task?.fps, task?.ageMs, globalFps, cap)
    }

    private fun sampleGlobalFps(cap: Int): Int {
        // The helper's direct binder transaction is sub-millisecond; the shell fallback forks `sh` + `service`.
        val frames = (if (NEW_FPS_SOURCES) privileged.surfaceFlingerFrameCount() else null)
            ?: privileged.shell("service call SurfaceFlinger 1013")?.let(TelemetryReader::parseSurfaceFlingerFrames)
            ?: return _state.value.fps
        val now = System.nanoTime() / 1_000_000L
        val prev = lastFrames
        val prevTime = lastFrameTime
        lastFrames = frames
        lastFrameTime = now
        if (prev == null) return _state.value.fps
        return TelemetryReader.fps(frames - prev, now - prevTime, cap)
    }
}
