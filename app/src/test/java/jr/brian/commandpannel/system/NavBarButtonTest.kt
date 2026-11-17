package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Test

class NavBarButtonTest {

    @Test
    fun defaultOrderIsTheStockLayout() {
        assertEquals(NavBarButton.entries, NavBarButton.ordered(null))
        assertEquals("mode,task,launch,settings,app", NavBarButton.encode(NavBarButton.entries))
    }

    @Test
    fun savedOrderIsKeptAndMissingButtonsAppended() {
        assertEquals(
            listOf(NavBarButton.SETTINGS, NavBarButton.APP, NavBarButton.MODE, NavBarButton.TASK, NavBarButton.LAUNCH),
            NavBarButton.ordered("settings,app"),
        )
    }

    @Test
    fun unknownAndDuplicateKeysAreDropped() {
        assertEquals(
            listOf(NavBarButton.APP, NavBarButton.MODE, NavBarButton.TASK, NavBarButton.LAUNCH, NavBarButton.SETTINGS),
            NavBarButton.ordered("app,bogus,app"),
        )
    }
}
