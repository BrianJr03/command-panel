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

    @Test
    fun `a trusted plugin declaring a future or no version gets no tile`() {
        val asked = mutableListOf<String>()
        val found = PanelPluginRegistry.select(
            listOf(
                candidate("jr.brian.thorkbm", version = 2),
                candidate("jr.brian.thorkbm", cls = ".Str", version = "2"),
                candidate("jr.brian.thorkbm", cls = ".None", version = null),
                candidate("jr.brian.thorkbm", cls = ".Zero", version = 0),
            ),
        ) { asked += it; true }
        assertEquals(emptyList<String>(), keys(found))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `only the usable service of a package with several versions is listed`() {
        // The fake plugin's layout: one v1 service, one declaring v2, one with no version.
        val pkg = "jr.brian.commandpanel.fakeplugin"
        val found = PanelPluginRegistry.select(
            listOf(
                candidate(pkg, cls = ".FutureVersionService", version = 2, label = "Fake plugin (v2)"),
                candidate(pkg, cls = ".NoVersionService", version = null, label = "Fake plugin (no version)"),
                candidate(pkg, cls = ".FakePluginService", version = 1, label = "Fake plugin"),
            ),
        ) { true }
        assertEquals(listOf("$pkg/.FakePluginService v1"), keys(found))
    }

    @Test
    fun `an untrusted plugin with a future version is dropped either way`() {
        val found = PanelPluginRegistry.select(listOf(candidate("evil.app", version = 2))) { false }
        assertEquals(emptyList<String>(), keys(found))
    }
}
