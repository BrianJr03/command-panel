package jr.brian.commandpannel.plugin

import jr.brian.commandpannel.plugin.PanelPluginRegistry.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelPluginRegistryTest {

    private fun candidate(pkg: String, cls: String = ".Svc", version: Any? = 1, label: String = pkg) =
        Candidate(pkg, cls, version, label)

    private fun keys(list: List<PanelPluginRegistry.Selected>) = list.map { "${it.candidate.packageName}/${it.candidate.className} v${it.version}" }

    @Test
    fun `lists trusted plugins with a usable version`() {
        val found = PanelPluginRegistry.select(listOf(candidate("jr.brian.thorkbm"))) { true }
        assertEquals(listOf("jr.brian.thorkbm/.Svc v1"), keys(found))
    }

    @Test
    fun `untrusted packages are dropped`() {
        val found = PanelPluginRegistry.select(listOf(candidate("jr.brian.thorkbm"), candidate("evil.app"))) { it == "jr.brian.thorkbm" }
        assertEquals(listOf("jr.brian.thorkbm/.Svc v1"), keys(found))
    }

    @Test
    fun `missing, invalid or too new versions are dropped before the trust check`() {
        val asked = mutableListOf<String>()
        val found = PanelPluginRegistry.select(
            listOf(
                candidate("a", version = null),
                candidate("b", version = "x"),
                candidate("c", version = PanelPluginTrust.SUPPORTED_VERSION + 1),
                candidate("d", version = "1"),
            ),
        ) { asked += it; true }
        assertEquals(listOf("d/.Svc v1"), keys(found))
        assertEquals(listOf("d"), asked)
    }

    @Test
    fun `one entry per service, sorted by label`() {
        val found = PanelPluginRegistry.select(
            listOf(
                candidate("z.pkg", label = "Zed"),
                candidate("a.pkg", cls = ".Two", label = "alpha"),
                candidate("a.pkg", cls = ".One", label = "alpha"),
                candidate("z.pkg", label = "Zed"),
            ),
        ) { true }
        assertEquals(listOf("a.pkg/.One v1", "a.pkg/.Two v1", "z.pkg/.Svc v1"), keys(found))
    }
}
