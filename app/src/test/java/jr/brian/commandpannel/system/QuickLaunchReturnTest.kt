package jr.brian.commandpannel.system

import jr.brian.commandpannel.privileged.ClearTasksPolicy
import jr.brian.commandpannel.privileged.ClearTasksPolicy.TaskSnapshot
import jr.brian.commandpannel.system.QuickLaunchReturnTracker.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickLaunchReturnTest {

    private val app = "com.example.game"
    private val launcher = "com.android.launcher3"
    private val secondHome = "jr.brian.home"

    private fun tracker(target: Int = 0) =
        QuickLaunchReturnTracker(app, target, ignoredPackages = setOf(launcher, secondHome, "com.android.systemui"))

    private fun task(id: Int, pkg: String, display: Int = 0, visible: Boolean = true, type: Int = ClearTasksPolicy.ACTIVITY_TYPE_STANDARD) =
        TaskSnapshot(taskId = id, displayId = display, packageName = pkg, uid = null, activityType = type, visible = visible)

    private val bottomHome = task(9, secondHome, display = 4)
    private val appUp = listOf(task(1, app), task(2, launcher, visible = false, type = ClearTasksPolicy.ACTIVITY_TYPE_HOME), bottomHome)
    private val homeUp = listOf(task(2, launcher, type = ClearTasksPolicy.ACTIVITY_TYPE_HOME), task(1, app, visible = false), bottomHome)

    private fun QuickLaunchReturnTracker.tick(tasks: List<TaskSnapshot>?, ms: Long = 1_000, awake: Boolean = true, panel: Boolean = false) =
        onTick(tasks, ms, awake, panel)

    @Test
    fun `reopens after the app leaves and home shows for two ticks`() {
        val t = tracker()
        assertEquals(Verdict.WAIT, t.tick(appUp))
        assertEquals(0, t.watchedDisplayId)
        assertEquals(Verdict.WAIT, t.tick(homeUp))
        assertEquals(Verdict.REOPEN, t.tick(homeUp))
    }

    @Test
    fun `a single gone snapshot mid-transition does not reopen`() {
        val t = tracker()
        t.tick(appUp)
        assertEquals(Verdict.WAIT, t.tick(homeUp))
        assertEquals(Verdict.WAIT, t.tick(appUp))
        assertEquals(Verdict.WAIT, t.tick(homeUp))
        assertEquals(Verdict.REOPEN, t.tick(homeUp))
    }

    @Test
    fun `waits for the launch, then gives up after the grace period`() {
        val t = tracker()
        assertEquals(Verdict.WAIT, t.tick(homeUp, ms = 1_000))
        assertNull(t.watchedDisplayId)
        assertEquals(Verdict.STOP, t.tick(homeUp, ms = QuickLaunchReturnTracker.LAUNCH_GRACE_MS))
    }

    @Test
    fun `another app taking the screen keeps waiting until home returns`() {
        val t = tracker()
        t.tick(appUp)
        val gameFromFrontend = listOf(task(5, "com.retroarch"), task(1, app, visible = false), bottomHome)
        repeat(5) { assertEquals(Verdict.WAIT, t.tick(gameFromFrontend)) }
        assertEquals(Verdict.WAIT, t.tick(homeUp))
        assertEquals(Verdict.REOPEN, t.tick(homeUp))
    }

    @Test
    fun `recents on the watched screen counts as busy`() {
        val t = tracker()
        t.tick(appUp)
        val recents = listOf(task(7, launcher, type = ClearTasksPolicy.ACTIVITY_TYPE_RECENTS), bottomHome)
        repeat(3) { assertEquals(Verdict.WAIT, t.tick(recents)) }
    }

    @Test
    fun `watches the display the app really opened on`() {
        // Asked for the upper screen, but the app came up on the lower one.
        val t = tracker(target = 0)
        val onBottom = listOf(task(2, launcher, type = ClearTasksPolicy.ACTIVITY_TYPE_HOME), task(1, app, display = 4))
        assertEquals(Verdict.WAIT, t.tick(onBottom))
        assertEquals(4, t.watchedDisplayId)
        // The lower screen's secondary home is a plain standard task, but it's a launcher: the app has closed.
        val bottomHomeBack = listOf(task(2, launcher, type = ClearTasksPolicy.ACTIVITY_TYPE_HOME), bottomHome)
        assertEquals(Verdict.WAIT, t.tick(bottomHomeBack))
        assertEquals(Verdict.REOPEN, t.tick(bottomHomeBack))
    }

    @Test
    fun `prefers the target display when the app is visible on both`() {
        val t = tracker(target = 4)
        t.tick(listOf(task(1, app, display = 0), task(3, app, display = 4)))
        assertEquals(4, t.watchedDisplayId)
    }

    @Test
    fun `an app on the other screen does not keep the watched screen busy`() {
        val t = tracker(target = 0)
        t.tick(appUp)
        val otherScreenApp = listOf(task(2, launcher, type = ClearTasksPolicy.ACTIVITY_TYPE_HOME), task(8, "com.other", display = 4))
        assertEquals(Verdict.WAIT, t.tick(otherScreenApp))
        assertEquals(Verdict.REOPEN, t.tick(otherScreenApp))
    }

    @Test
    fun `stops when the screen is off, the panel is showing, or the watch times out`() {
        tracker().let { it.tick(appUp); assertEquals(Verdict.STOP, it.tick(homeUp, awake = false)) }
        tracker().let { it.tick(appUp); assertEquals(Verdict.STOP, it.tick(homeUp, panel = true)) }
        tracker().let { it.tick(appUp); assertEquals(Verdict.STOP, it.tick(appUp, ms = QuickLaunchReturnTracker.TIMEOUT_MS)) }
    }

    @Test
    fun `stops after repeated unreadable dumps, but tolerates one`() {
        val t = tracker()
        t.tick(appUp)
        assertEquals(Verdict.WAIT, t.tick(null))
        assertEquals(Verdict.WAIT, t.tick(appUp))
        assertEquals(Verdict.WAIT, t.tick(null))
        assertEquals(Verdict.WAIT, t.tick(null))
        assertEquals(Verdict.STOP, t.tick(null))
    }

    @Test
    fun `reads a real activities dump`() {
        val dump = """
            Display #0 (activities from top to bottom):
              * Task{1a2b3c4 #40 type=home A=10100:com.android.launcher3 U=0 visible=true visibleRequested=true mode=fullscreen sz=1}
                * Hist  #0: ActivityRecord{aa11bb2 u0 com.android.launcher3/.Launcher} t40}
              * Task{5d6e7f8 #41 type=standard A=10207:com.example.game U=0 visible=false visibleRequested=false mode=fullscreen sz=1}
                * Hist  #0: ActivityRecord{cc33dd4 u0 com.example.game/.MainActivity} t41}
            Display #4 (activities from top to bottom):
              * Task{d2d5f00 #33 type=standard A=10215:jr.brian.home U=0 visible=true visibleRequested=true sz=1}
                * Hist  #0: ActivityRecord{49e0d7e u0 jr.brian.home/.MainActivity} t33}
        """.trimIndent()
        val t = tracker()
        t.tick(appUp)
        val tasks = ClearTasksPolicy.parseActivitiesDump(dump)
        assertEquals(Verdict.WAIT, t.tick(tasks))
        assertEquals(Verdict.REOPEN, t.tick(tasks))
    }

    @Test
    fun `keep open falls back to close on the panel's own screen`() {
        assertEquals(QuickLaunchAfter.KEEP_OPEN, QuickLaunchAfter.KEEP_OPEN.effectiveFor(QuickLaunchDisplay.TOP))
        assertEquals(QuickLaunchAfter.CLOSE, QuickLaunchAfter.KEEP_OPEN.effectiveFor(QuickLaunchDisplay.BOTTOM))
        assertEquals(QuickLaunchAfter.REOPEN, QuickLaunchAfter.REOPEN.effectiveFor(QuickLaunchDisplay.BOTTOM))
        assertEquals(QuickLaunchAfter.CLOSE, QuickLaunchAfter.CLOSE.effectiveFor(QuickLaunchDisplay.TOP))
    }

    @Test
    fun `unknown or missing keys read as close`() {
        assertEquals(QuickLaunchAfter.CLOSE, QuickLaunchAfter.fromKey(null))
        assertEquals(QuickLaunchAfter.CLOSE, QuickLaunchAfter.fromKey("bogus"))
        QuickLaunchAfter.entries.forEach { assertEquals(it, QuickLaunchAfter.fromKey(it.key)) }
    }
}
