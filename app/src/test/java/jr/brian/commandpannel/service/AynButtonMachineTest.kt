package jr.brian.commandpannel.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the AYN button three-state logic, mirroring the stock assistant's behavior. */
class AynButtonMachineTest {

    private class Recorder {
        var toggles = 0
        var hides = 0
        var blanks = 0
        var wakes = 0
        var keyguard = false
        var blanked = false
        var clock = 0L
        var doublePress = false
        var doubles = 0
        /** The fake scheduler's one pending block; [fire] runs it as if the window ran out. */
        var pending: (() -> Unit)? = null

        fun fire() { pending?.also { pending = null }?.invoke() }

        fun machine(debounceMs: Long = 400L) = AynButtonMachine(
            isKeyguardLocked = { keyguard },
            isBottomBlanked = { blanked },
            onToggleOverlay = { toggles++ },
            onHideOverlay = { hides++ },
            onBlankBottom = { blanks++; blanked = true },
            onWakeBottom = { wakes++; blanked = false },
            now = { clock },
            debounceMs = debounceMs,
            doublePressEnabled = { doublePress },
            onDoublePress = { doubles++ },
            schedule = { _, block -> pending = block; { pending = null } },
        )
    }

    private fun down(m: AynButtonMachine, long: Boolean = false) = m.onEvent(down = true, longPress = long)
    private fun up(m: AynButtonMachine) = m.onEvent(down = false, longPress = false)

    @Test
    fun shortPress_togglesOverlayOnKeyUpOnly() {
        val r = Recorder(); val m = r.machine()
        down(m)
        assertEquals("key-down must not toggle", 0, r.toggles)
        up(m)
        assertEquals(1, r.toggles)
    }

    @Test
    fun longPress_hidesAndBlanks_andSwallowsTheFollowingKeyUp() {
        val r = Recorder(); val m = r.machine()
        down(m, long = true)
        assertEquals(1, r.hides)
        assertEquals(1, r.blanks)
        up(m)
        assertEquals("key-up after long press is a no-op", 0, r.toggles)
        assertEquals(0, r.wakes)
    }

    @Test
    fun keyUp_whileBottomBlanked_wakesInsteadOfToggling() {
        val r = Recorder().apply { blanked = true }; val m = r.machine()
        up(m)
        assertEquals(1, r.wakes)
        assertEquals(0, r.toggles)
    }

    @Test
    fun keyUp_onKeyguard_doesNothing() {
        val r = Recorder().apply { keyguard = true }; val m = r.machine()
        up(m)
        assertEquals(0, r.toggles)
    }

    @Test
    fun rapidKeyUps_areDebounced() {
        val r = Recorder(); val m = r.machine(debounceMs = 400L)
        r.clock = 1000; up(m)
        r.clock = 1200; up(m) // within 400ms -> ignored
        r.clock = 1500; up(m) // after 400ms -> toggles
        assertEquals(2, r.toggles)
    }

    @Test
    fun doublePressOff_togglesRightAway() {
        val r = Recorder(); val m = r.machine()
        up(m)
        assertEquals(1, r.toggles)
        assertEquals(null, r.pending)
    }

    @Test
    fun doublePressOn_singlePress_togglesOnlyAfterTheWindow() {
        val r = Recorder().apply { doublePress = true }; val m = r.machine()
        down(m); up(m)
        assertEquals("waits for a possible second press", 0, r.toggles)
        r.fire()
        assertEquals(1, r.toggles)
        assertEquals(0, r.doubles)
    }

    @Test
    fun doublePressOn_twoQuickPresses_fireDoublePressAndNeverToggle() {
        val r = Recorder().apply { doublePress = true }; val m = r.machine()
        down(m); up(m)
        down(m); up(m)
        assertEquals(1, r.doubles)
        assertEquals("pending single press is canceled", null, r.pending)
        r.fire()
        assertEquals(0, r.toggles)
    }

    @Test
    fun doublePressOn_thirdPressStartsANewSinglePress() {
        val r = Recorder().apply { doublePress = true }; val m = r.machine()
        up(m); up(m); up(m)
        assertEquals(1, r.doubles)
        r.fire()
        assertEquals(1, r.toggles)
    }

    @Test
    fun doublePressOn_whileBlanked_singlePressStillWakesAfterTheWindow() {
        val r = Recorder().apply { doublePress = true; blanked = true }; val m = r.machine()
        up(m)
        assertEquals(0, r.wakes)
        r.fire()
        assertEquals(1, r.wakes)
        assertEquals(0, r.toggles)
    }

    @Test
    fun longPress_cancelsAPendingSinglePress() {
        val r = Recorder().apply { doublePress = true }; val m = r.machine()
        up(m)
        down(m, long = true); up(m)
        assertEquals(null, r.pending)
        assertEquals(0, r.toggles)
        assertEquals(0, r.doubles)
        assertEquals(1, r.blanks)
    }

    @Test
    fun doublePressOn_onKeyguard_doesNothing() {
        val r = Recorder().apply { doublePress = true; keyguard = true }; val m = r.machine()
        up(m); up(m)
        assertEquals(0, r.doubles)
        assertEquals(null, r.pending)
    }

    @Test
    fun synchronousScheduler_leavesNoStalePendingPress() {
        val r = Recorder().apply { doublePress = true }
        val m = AynButtonMachine(
            isKeyguardLocked = { false }, isBottomBlanked = { false },
            onToggleOverlay = { r.toggles++ }, onHideOverlay = {}, onBlankBottom = {}, onWakeBottom = {},
            now = { r.clock }, doublePressEnabled = { true }, onDoublePress = { r.doubles++ },
        )
        r.clock = 1000; up(m)
        r.clock = 2000; up(m)
        assertEquals(2, r.toggles)
        assertEquals(0, r.doubles)
    }

    // #14: a long press while the bottom is already blanked by us wakes it.

    @Test
    fun longPress_whileBlanked_wakesAndSwallowsTheKeyUp() {
        val r = Recorder(); val m = r.machine()
        r.clock = 1000; down(m, long = true); up(m)
        assertEquals(1, r.blanks)
        r.clock = 5000; down(m, long = true)
        assertEquals(1, r.wakes)
        assertEquals("no second blank", 1, r.blanks)
        up(m)
        assertEquals("release after the waking long press is swallowed", 0, r.toggles)
        assertEquals(false, r.blanked)
    }

    @Test
    fun longPress_repeatsWithinOneHold_neitherWakeNorReblank() {
        val r = Recorder(); val m = r.machine()
        r.clock = 1000; down(m, long = true)
        r.clock = 1050; down(m, long = true)
        r.clock = 1100; down(m, long = true)
        r.clock = 1150; down(m, long = true) // keeps refreshing, so the whole hold counts as one press
        r.clock = 1200; up(m)
        assertEquals(1, r.blanks)
        assertEquals(1, r.hides)
        assertEquals(0, r.wakes)
        assertEquals(0, r.toggles)
        assertEquals(true, r.blanked)
    }

    @Test
    fun firmwareThatOnlySendsDownLong_togglesBlankAndWake() {
        val r = Recorder(); val m = r.machine()
        r.clock = 1000; down(m, long = true)
        assertEquals(true, r.blanked)
        r.clock = 3000; down(m, long = true) // no key-up ever came
        assertEquals(1, r.wakes)
        assertEquals(false, r.blanked)
        r.clock = 5000; down(m, long = true)
        assertEquals(2, r.blanks)
        assertEquals(true, r.blanked)
    }

    @Test
    fun longPress_afterAReleasedLongPress_isNeverARepeat() {
        val r = Recorder(); val m = r.machine()
        r.clock = 1000; down(m, long = true); up(m)
        r.clock = 1100; down(m, long = true) // quick but after a key-up: a new press
        assertEquals(1, r.wakes)
    }

    @Test
    fun longPress_whileBlanked_cancelsAPendingSinglePress() {
        val r = Recorder().apply { doublePress = true; blanked = true }; val m = r.machine()
        up(m)
        down(m, long = true); up(m)
        assertEquals(null, r.pending)
        assertEquals(1, r.wakes)
        assertEquals(0, r.doubles)
        assertEquals(0, r.toggles)
    }

    @Test
    fun shortPresses_unaffectedByLongPressTracking() {
        val r = Recorder(); val m = r.machine()
        r.clock = 1000; down(m, long = true); up(m)
        r.clock = 3000; down(m); up(m) // wakes (blanked), as before
        assertEquals(1, r.wakes)
        r.clock = 5000; down(m); up(m)
        assertEquals(1, r.toggles)
    }
}
