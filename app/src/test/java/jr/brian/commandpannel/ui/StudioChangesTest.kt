package jr.brian.commandpannel.ui

import androidx.compose.ui.graphics.Color
import jr.brian.commandpannel.ui.settings.StudioCategory
import jr.brian.commandpannel.ui.settings.changedCategories
import jr.brian.commandpannel.ui.settings.resetAll
import jr.brian.commandpannel.ui.settings.resetCategory
import jr.brian.commandpannel.ui.theme.BackdropStyle
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.PinnedColors
import jr.brian.commandpannel.ui.theme.ShapeDef
import jr.brian.commandpannel.ui.theme.ShapeKind
import jr.brian.commandpannel.ui.theme.TccUiLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioChangesTest {
    private val base = CustomLook(id = "x", name = "Mine", baseLook = TccUiLook.TERMINAL)

    @Test
    fun `a fresh look changes nothing, whatever its base`() {
        assertTrue(base.changedCategories().isEmpty())
    }

    @Test
    fun `overrides mark their categories and resets clear them`() {
        val look = base.copy(
            backdrop = BackdropStyle.STARFIELD, tileShape = ShapeDef(ShapeKind.PILL, 0f),
            colors = PinnedColors(accent = Color.Red), outlineWidth = 2f,
        )
        assertEquals(
            setOf(StudioCategory.PARTS, StudioCategory.SHAPES, StudioCategory.COLORS, StudioCategory.EFFECTS),
            look.changedCategories(),
        )
        assertEquals(setOf(StudioCategory.PARTS, StudioCategory.SHAPES, StudioCategory.EFFECTS), look.resetCategory(StudioCategory.COLORS).changedCategories())
        val reset = look.resetAll()
        assertTrue(reset.changedCategories().isEmpty())
        assertEquals("Mine", reset.name)
        assertEquals(TccUiLook.TERMINAL, reset.baseLook)
    }

    @Test
    fun `blur settings belong to effects`() {
        val look = base.copy(blurBehind = true, blurRadius = 24f, blurTint = 0.2f)
        assertEquals(setOf(StudioCategory.EFFECTS), look.changedCategories())
        val reset = look.resetCategory(StudioCategory.EFFECTS)
        assertTrue(reset.changedCategories().isEmpty())
        assertNull(reset.blurBehind)
        assertNull(reset.blurRadius)
        assertNull(reset.blurTint)
    }
}
