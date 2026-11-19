package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.ScreenModePlan.BLANK_BOTTOM
import jr.brian.commandpannel.system.ScreenModePlan.BLANK_TOP
import jr.brian.commandpannel.system.ScreenModePlan.FocusSave
import jr.brian.commandpannel.system.ScreenModePlan.NONE
import jr.brian.commandpannel.system.ScreenModePlan.Panel
import jr.brian.commandpannel.system.ScreenModePlan.Saved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenModePlanTest {

    // ---- blank state (#35) ----

    @Test
    fun `fresh start in dual touches nothing`() {
        val plan = ScreenModePlan.plan(NONE, focusSaved = false, fromNone = true)!!
        assertNull(plan.cover)
        assertNull(plan.focusLock)
        assertFalse(plan.restoreFocus)
        assertEquals(FocusSave.NONE, plan.saveFocus)
    }

    @Test
    fun `only top screen covers the bottom and locks focus to the top`() {
        val plan = ScreenModePlan.plan(BLANK_BOTTOM, focusSaved = false, fromNone = true)!!
        assertEquals(Panel.BOTTOM, plan.cover)
        assertEquals(1, plan.focusLock)
        assertEquals(FocusSave.CURRENT, plan.saveFocus)
    }

    @Test
    fun `only bottom screen covers the top and locks focus to the bottom`() {
        val plan = ScreenModePlan.plan(BLANK_TOP, focusSaved = false, fromNone = true)!!
        assertEquals(Panel.TOP, plan.cover)
        assertEquals(2, plan.focusLock)
    }

    @Test
    fun `back to dual restores focus lock only if we saved it`() {
        assertTrue(ScreenModePlan.plan(NONE, focusSaved = true, fromNone = false)!!.restoreFocus)
        assertFalse(ScreenModePlan.plan(NONE, focusSaved = false, fromNone = false)!!.restoreFocus)
    }

    @Test
    fun `switching straight between blanks keeps the focus saved from dual`() {
        // Top covered -> bottom covered: the values saved on leaving dual are still the user's, don't overwrite them.
        val plan = ScreenModePlan.plan(BLANK_TOP, focusSaved = true, fromNone = false)!!
        assertEquals(FocusSave.NONE, plan.saveFocus)
        assertEquals(Panel.TOP, plan.cover)
    }

    @Test
    fun `a service restart while blanked covers the panel again without touching what was saved`() {
        val plan = ScreenModePlan.plan(BLANK_BOTTOM, focusSaved = true, fromNone = false)!!
        assertEquals(Panel.BOTTOM, plan.cover)
        assertEquals(FocusSave.NONE, plan.saveFocus)
    }

    @Test
    fun `starting straight into a blank with nothing saved restores 0 later`() {
        assertEquals(FocusSave.DEFAULT, ScreenModePlan.plan(BLANK_BOTTOM, focusSaved = false, fromNone = false)!!.saveFocus)
    }

    @Test
    fun `an unknown state does nothing`() {
        assertNull(ScreenModePlan.plan(7, focusSaved = false, fromNone = true))
    }

    @Test
    fun `the long press blanks the bottom, and wakes either blanked screen`() {
        assertEquals(BLANK_BOTTOM, ScreenModePlan.afterLongPress(NONE))
        assertEquals(NONE, ScreenModePlan.afterLongPress(BLANK_BOTTOM))
        assertEquals(NONE, ScreenModePlan.afterLongPress(BLANK_TOP))
    }

    @Test
    fun `a vendor mode of 1 or 2 is adopted, dual and unknown values are not`() {
        assertEquals(BLANK_BOTTOM, ScreenModePlan.adoptVendorMode(1))
        assertEquals(BLANK_TOP, ScreenModePlan.adoptVendorMode(2))
        assertNull(ScreenModePlan.adoptVendorMode(0))
        assertNull(ScreenModePlan.adoptVendorMode(5))
    }

    @Test
    fun `blank numbers match the Mode tab`() {
        // The Mode tab passes 0 / 1 / 2 (dual, only top, only bottom) straight through.
        assertEquals(Panel.BOTTOM, ScreenModePlan.blankedPanel(1))
        assertEquals(Panel.TOP, ScreenModePlan.blankedPanel(2))
        assertNull(ScreenModePlan.blankedPanel(0))
    }

    // ---- levels saved by older versions (#36) ----

    @Test
    fun `saved levels are restored once, bottom first, whatever is blanked now`() {
        assertEquals(listOf(Panel.BOTTOM, Panel.TOP), ScreenModePlan.legacyRestores(Saved(top = true, bottom = true)))
        assertEquals(listOf(Panel.TOP), ScreenModePlan.legacyRestores(Saved(top = true)))
        assertTrue(ScreenModePlan.legacyRestores(Saved()).isEmpty())
    }

    @Test
    fun `a saved level is forgotten only once it was applied`() {
        assertTrue("helper down: keep it", ScreenModePlan.keepSavedLevel(0.4f, applied = false))
        assertFalse("restored: forget it", ScreenModePlan.keepSavedLevel(0.4f, applied = true))
        // Nothing usable to come back to: dropping it is safe, and keeping it would retry forever.
        assertFalse(ScreenModePlan.keepSavedLevel(Float.NaN, applied = false))
        assertFalse(ScreenModePlan.keepSavedLevel(0f, applied = false))
    }

    @Test
    fun `only lit finite levels are restorable`() {
        assertFalse(ScreenModePlan.isRestorable(null))
        assertFalse(ScreenModePlan.isRestorable(Float.NaN))
        assertFalse(ScreenModePlan.isRestorable(0f))
        assertFalse(ScreenModePlan.isRestorable(-1f))
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
