package jr.brian.commandpannel.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
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

/**
 * Samples battery data every [intervalMs] while enabled and feeds the pure [BatteryEstimator] (fork feature). Reads
 * charge counter / current / full capacity from `BatteryManager` with sysfs fallbacks, derives the charge direction
 * from status + plug state (never the current's sign), and exposes the short display string.
 */
class BatteryEstimateMonitor(
    private val context: Context,
    private val settings: SystemSettings,
    private val enabled: () -> Boolean,
    private val intervalMs: Long = 10_000L,
) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val estimator = BatteryEstimator()

    companion object {
        /** Charge direction from `BatteryManager` status + plug state (never the current's sign). */
        fun flowFor(status: Int, plugged: Int): BatteryFlow? = when {
            status == BatteryManager.BATTERY_STATUS_UNKNOWN || status < 0 -> null
            status == BatteryManager.BATTERY_STATUS_CHARGING -> BatteryFlow.CHARGING
            plugged != 0 -> BatteryFlow.PLUGGED_IDLE // full, 80% limit, or charging separation
            else -> BatteryFlow.DISCHARGING
        }
    }

    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                _text.value = if (enabled()) BatteryEstimator.formatShort(sample()) else { estimator.reset(); null }
                delay(intervalMs)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    private fun sysfs(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()

    private fun sample(): BatteryEstimate {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryEstimate.Hidden
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val percent = if (level >= 0) level * 100 / scale else return BatteryEstimate.Hidden

        val flow = flowFor(status, plugged)

        val chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0 } ?: sysfs("/sys/class/power_supply/battery/charge_counter") ?: -1L
        val currentNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != 0L } ?: sysfs("/sys/class/power_supply/battery/current_now") ?: 0L
        val chargeFull = sysfs("/sys/class/power_supply/battery/charge_full") ?: -1L
        val limit = settings.getInt("percent_80_charge_limit", 0) == 1
        val systemRemaining = runCatching { bm.computeChargeTimeRemaining() }.getOrDefault(-1L)

        return estimator.update(
            BatteryEstimator.Sample(
                elapsedMs = SystemClock.elapsedRealtime(),
                flow = flow,
                levelPercent = percent,
                chargeCounterUah = chargeCounter,
                chargeFullUah = chargeFull,
                currentRaw = currentNow,
                currentUnit = BatteryCurrentUnit.MICROAMPS,
                chargeLimitEnabled = limit,
                systemChargeTimeRemainingMs = systemRemaining,
            )
        )
    }

    suspend fun snapshot(): String? = withContext(Dispatchers.IO) {
        if (enabled()) BatteryEstimator.formatShort(sample()) else null
    }
}
