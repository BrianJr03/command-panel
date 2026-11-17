package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BdsaDetectorTest {

    private fun prefs(body: String) = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="overlay_look">AURORA_GLASS</string>
$body
    <boolean name="replace_stock_assistant_other" value="false" />
</map>"""

    @Test
    fun `reads replace_stock_assistant true and false`() {
        assertEquals(true, BdsaDetector.parseReplaceStock(prefs("""    <boolean name="replace_stock_assistant" value="true" />""")))
        assertEquals(false, BdsaDetector.parseReplaceStock(prefs("""    <boolean name="replace_stock_assistant" value="false" />""")))
    }

    @Test
    fun `missing key, empty or garbled files are unknown (BDSA's default is on)`() {
        assertNull(BdsaDetector.parseReplaceStock(prefs("")))
        assertNull(BdsaDetector.parseReplaceStock(""))
        assertNull(BdsaDetector.parseReplaceStock("cat: No such file or directory"))
    }

    @Test
    fun `a similarly named key doesn't match`() {
        assertNull(BdsaDetector.parseReplaceStock("""<map><boolean name="replace_stock_assistant_other" value="false" /></map>"""))
    }
}
