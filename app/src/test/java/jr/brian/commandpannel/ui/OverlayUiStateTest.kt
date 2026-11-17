package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.overlay.QuickTile
import jr.brian.commandpannel.ui.theme.TempColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the stock-matching defaults the overlay renders from. */
class OverlayUiStateTest {

    @Test
    fun stockDefaultsAreTheTenStockTilesInOrder() {
        val ids = QuickTile.STOCK_DEFAULTS.map { it.id }
        assertEquals(
            listOf(
                "focus_lock", "performance", "fan", "clear_memory", "controller",
                "charging_sep", "vibration", "ambient_led", "force_landscape", "theme",
            ),
            ids,
        )
    }

    @Test
    fun tempThresholdsMatchStock() {
        assertEquals(TempColors.Normal, TempColors.forCelsius(50))
        assertEquals(TempColors.Warm, TempColors.forCelsius(70))
        assertEquals(TempColors.Hot, TempColors.forCelsius(85))
        assertEquals(TempColors.VeryHot, TempColors.forCelsius(86))
    }
}
