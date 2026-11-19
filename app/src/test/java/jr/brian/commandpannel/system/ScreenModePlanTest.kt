package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.ScreenModePlan.FocusSave
import jr.brian.commandpannel.system.ScreenModePlan.Panel
import jr.brian.commandpannel.system.ScreenModePlan.Plan
import jr.brian.commandpannel.system.ScreenModePlan.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenModePlanTest {

    private val nothing = State()

    /** Applies a plan's flag changes the way the controller does (assuming every snapshot succeeds). */
    private fun State.after(plan: Plan) = State(
        topBlanked = (topBlanked && Panel.TOP !in plan.restore) || plan.blank == Panel.TOP,
        bottomBlanked = (bottomBlanked && Panel.BOTTOM !in plan.restore) || plan.blank == Panel.BOTTOM,
        focusSaved = (focusSaved || plan.saveFocus != FocusSave.NONE) && !plan.restoreFocus,
    )

    @Test
    fun `fresh start in dual touches nothing`() {
        // The #12 bug: opening the app (service start) in dual restored a stale or full brightness.
        val plan = ScreenModePlan.plan(nothing, mode = 0, fromDual = false)!!
        assertNull(plan.blank)
        assertTrue(plan.restore.isEmpty())
        assertFalse(plan.restoreFocus)
        assertEquals(FocusSave.NONE, plan.saveFocus)
    }

    @Test
    fun `dual to top only blanks the bottom and saves its level and focus`() {
        val plan = ScreenModePlan.plan(nothing, mode = 1, fromDual = true)!!
        assertEquals(Panel.BOTTOM, plan.blank)
        assertTrue(plan.snapshot)
        assertTrue(plan.restore.isEmpty())
        assertEquals(FocusSave.CURRENT, plan.saveFocus)
    }

    @Test
    fun `back to dual restores only what was blanked`() {
        val state = State(bottomBlanked = true, focusSaved = true)
        val plan = ScreenModePlan.plan(state, mode = 0, fromDual = false)!!
        assertEquals(listOf(Panel.BOTTOM), plan.restore)
        assertTrue(plan.restoreFocus)
        assertEquals(nothing, state.after(plan))
    }

    @Test
    fun `switching single modes hands the blank over and keeps flags right`() {
        var state = nothing
        val toTopOnly = ScreenModePlan.plan(state, mode = 1, fromDual = true)!!
        state = state.after(toTopOnly)
        assertEquals(State(bottomBlanked = true, focusSaved = true), state)

        val toBottomOnly = ScreenModePlan.plan(state, mode = 2, fromDual = false)!!
        assertEquals(Panel.TOP, toBottomOnly.blank)
        assertTrue("top is still lit, so its level is the user's", toBottomOnly.snapshot)
        assertEquals(listOf(Panel.BOTTOM), toBottomOnly.restore)
        assertEquals("focus saved on leaving dual is kept", FocusSave.NONE, toBottomOnly.saveFocus)
        state = state.after(toBottomOnly)
        assertEquals(State(topBlanked = true, focusSaved = true), state)

        val back = ScreenModePlan.plan(state, mode = 1, fromDual = false)!!
        assertEquals(listOf(Panel.TOP), back.restore)
        assertTrue(back.snapshot)
        state = state.after(back)

        val dual = ScreenModePlan.plan(state, mode = 0, fromDual = false)!!
        assertEquals(listOf(Panel.BOTTOM), dual.restore)
        assertEquals(nothing, state.after(dual))
    }

    @Test
    fun `restart while blanked re-drops without overwriting the saved level`() {
        val state = State(bottomBlanked = true, focusSaved = true)
        val plan = ScreenModePlan.plan(state, mode = 1, fromDual = false)!!
        assertEquals(Panel.BOTTOM, plan.blank)
        assertFalse("bottom is at 0 now; snapshotting would lose the real level", plan.snapshot)
        assertTrue(plan.restore.isEmpty())
        assertEquals(FocusSave.NONE, plan.saveFocus)
    }

    @Test
    fun `fresh start straight into single mode saves default focus`() {
        val plan = ScreenModePlan.plan(nothing, mode = 2, fromDual = false)!!
        assertEquals(Panel.TOP, plan.blank)
        assertTrue(plan.snapshot)
        assertEquals(FocusSave.DEFAULT, plan.saveFocus)
    }

    @Test
    fun `restore order is bottom first`() {
        val plan = ScreenModePlan.plan(State(topBlanked = true, bottomBlanked = true), mode = 0, fromDual = false)!!
        assertEquals(listOf(Panel.BOTTOM, Panel.TOP), plan.restore)
    }

    @Test
    fun `unknown mode does nothing`() {
        assertNull(ScreenModePlan.plan(nothing, mode = 7, fromDual = true))
    }

    @Test
    fun `only lit finite levels are restorable`() {
        assertFalse(ScreenModePlan.isRestorable(null))
        assertFalse(ScreenModePlan.isRestorable(Float.NaN))
        assertFalse(ScreenModePlan.isRestorable(0f))
        assertFalse(ScreenModePlan.isRestorable(0.005f))
        assertFalse(ScreenModePlan.isRestorable(1.5f))
        assertTrue(ScreenModePlan.isRestorable(0.3f))
        assertTrue(ScreenModePlan.isRestorable(1f))
    }

    @Test
    fun `top level maps to screen_brightness like the slider`() {
        assertEquals(255, ScreenModePlan.toScreenBrightnessSetting(1f))
        assertEquals(76, ScreenModePlan.toScreenBrightnessSetting(0.3f))
        assertEquals(1, ScreenModePlan.toScreenBrightnessSetting(0.0001f))
    }
}
