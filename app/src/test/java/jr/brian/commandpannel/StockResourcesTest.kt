package jr.brian.commandpannel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the stock resource import: every themed icon keeps its pure-black twin, and no apktool artifacts remain. */
class StockResourcesTest {

    private val drawables = File("src/main/res/drawable").listFiles().orEmpty().map { it.nameWithoutExtension }.toSet()

    @Test
    fun everyBlackVariantHasABaseIcon() {
        val orphans = drawables.filter { it.endsWith("_black") && it.removeSuffix("_black") !in drawables }
        assertTrue("black variants without a base icon: $orphans", orphans.isEmpty())
    }

    @Test
    fun noApktoolInlineReferencesRemain() {
        val leftovers = File("src/main/res/drawable").listFiles().orEmpty()
            .filter { it.extension == "xml" && it.readText().contains("@drawable/$") }
            .map { it.name }
        assertTrue("unresolved apktool references in: $leftovers", leftovers.isEmpty())
    }
}
