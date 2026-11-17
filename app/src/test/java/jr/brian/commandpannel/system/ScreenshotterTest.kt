package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotterTest {

    @Test
    fun parsesDisplayIdsByPort() {
        val dump = """
            Display 4630946441858561667 (HWC display 0): port=131 pnpId=QCM displayName=""
            Display 4630946482288158084 (HWC display 3): port=132 pnpId=QCM displayName=""
        """.trimIndent()
        val ids = Screenshotter.parseDisplayIds(dump)
        assertEquals(4630946441858561667L, ids[131])  // top
        assertEquals(4630946482288158084L, ids[132])  // bottom
    }
}
