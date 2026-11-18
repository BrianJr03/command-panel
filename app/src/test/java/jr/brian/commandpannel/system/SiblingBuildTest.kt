package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.SiblingBuild.Owner
import org.junit.Assert.assertEquals
import org.junit.Test

class SiblingBuildTest {

    private val release = "jr.brian.commandpannel"
    private val debug = "jr.brian.commandpannel.debug"

    @Test
    fun `debug and release map to each other`() {
        assertEquals(debug, SiblingBuild.siblingPackage(release))
        assertEquals(release, SiblingBuild.siblingPackage(debug))
    }

    @Test
    fun `owner record names self, the sibling, or nobody`() {
        assertEquals(Owner.SELF, SiblingBuild.ownerState(release, release, debug, siblingInstalled = true))
        assertEquals(Owner.SIBLING, SiblingBuild.ownerState(debug, release, debug, siblingInstalled = true))
        assertEquals(Owner.NONE, SiblingBuild.ownerState(null, release, debug, siblingInstalled = true))
    }

    @Test
    fun `an uninstalled sibling or an unknown package owns nothing`() {
        assertEquals(Owner.NONE, SiblingBuild.ownerState(debug, release, debug, siblingInstalled = false))
        assertEquals(Owner.NONE, SiblingBuild.ownerState("com.example.other", release, debug, siblingInstalled = true))
    }
}
