package jr.brian.commandpannel.plugin

import jr.brian.commandpannel.plugin.PanelPluginSwap.Effect
import jr.brian.commandpannel.plugin.PanelPluginSwap.EndReason
import jr.brian.commandpannel.plugin.PanelPluginSwap.Owner
import jr.brian.commandpannel.plugin.PanelPluginSwap.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelPluginSwapTest {

    private var clock = 1_000L
    private val swap = PanelPluginSwap(now = { clock })
    private val kbm = "jr.brian.thorkbm/.PanelPluginService"

    /** Starts a session and runs it to [Phase.ACTIVE]; returns its id. */
    private fun active(): Int {
        swap.start(kbm, 1)
        val id = swap.sessionId
        swap.onConnected(id)
        swap.onShown(id)
        return id
    }

    @Test
    fun `a session binds, sends MSG_SHOW, and hides the panel only after MSG_SHOWN`() {
        assertEquals(listOf(Effect.Bind(kbm)), swap.start(kbm, 1))
        assertEquals(Phase.BINDING, swap.phase)
        assertEquals(Owner.Panel, swap.owner)
        val id = swap.sessionId
        assertEquals(listOf(Effect.SendShow(1)), swap.onConnected(id))
        assertEquals(Phase.SHOWING, swap.phase)
        assertEquals(Owner.Panel, swap.owner)
        assertEquals(listOf(Effect.HidePanel), swap.onShown(id))
        assertEquals(Phase.ACTIVE, swap.phase)
        assertEquals(Owner.Plugin(kbm), swap.owner)
    }

    @Test
    fun `the session speaks the version it was started with`() {
        swap.start(kbm, 3)
        assertEquals(listOf(Effect.SendShow(3)), swap.onConnected(swap.sessionId))
    }

    @Test
    fun `a second start while busy is ignored`() {
        swap.start(kbm, 1)
        val id = swap.sessionId
        assertEquals(emptyList<Effect>(), swap.start("other/.Svc", 1))
        assertEquals(id, swap.sessionId)
        assertEquals(kbm, swap.key)
    }

    @Test
    fun `back from a swap the panel shows first, then MSG_HIDE, then unbind`() {
        active()
        assertEquals(listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.end(EndReason.AYN_PRESS))
        assertEquals(Phase.IDLE, swap.phase)
        assertEquals(Owner.Panel, swap.owner)
        assertFalse(swap.busy)
    }

    @Test
    fun `MSG_CLOSED brings the panel back and hides the plugin`() {
        val id = active()
        assertEquals(listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.onClosed(id))
        assertEquals(Phase.IDLE, swap.phase)
    }

    @Test
    fun `a dead plugin gets no MSG_HIDE but the panel comes back`() {
        val id = active()
        assertEquals(listOf(Effect.ShowPanel, Effect.Unbind), swap.onDied(id))
    }

    @Test
    fun `no MSG_SHOWN within the timeout keeps the panel and hides the plugin`() {
        swap.start(kbm, 1)
        swap.onConnected(swap.sessionId)
        assertEquals(clock + PanelPluginContract.SHOWN_TIMEOUT_MS, swap.nextDeadline)
        clock += PanelPluginContract.SHOWN_TIMEOUT_MS - 1
        assertEquals(emptyList<Effect>(), swap.onTick())
        clock += 1
        assertEquals(listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.onTick())
        assertEquals(Phase.IDLE, swap.phase)
    }

    @Test
    fun `a service that never connects is dropped without MSG_HIDE`() {
        swap.start(kbm, 1)
        clock += PanelPluginSwap.BIND_TIMEOUT_MS
        assertEquals(listOf(Effect.ShowPanel, Effect.Unbind), swap.onTick())
    }

    @Test
    fun `an active session has no timeout`() {
        active()
        assertEquals(null, swap.nextDeadline)
        clock += 60_000
        assertEquals(emptyList<Effect>(), swap.onTick())
        assertEquals(Phase.ACTIVE, swap.phase)
    }

    @Test
    fun `a late MSG_SHOWN after the timeout is ignored`() {
        swap.start(kbm, 1)
        val id = swap.sessionId
        swap.onConnected(id)
        clock += PanelPluginContract.SHOWN_TIMEOUT_MS
        swap.onTick()
        assertEquals(emptyList<Effect>(), swap.onShown(id))
        assertEquals(Owner.Panel, swap.owner)
    }

    @Test
    fun `callbacks from an older session are ignored`() {
        val old = active()
        swap.end(EndReason.AYN_PRESS)
        swap.start(kbm, 1)
        val current = swap.sessionId
        assertTrue(current != old)
        assertEquals(emptyList<Effect>(), swap.onConnected(old))
        assertEquals(emptyList<Effect>(), swap.onClosed(old))
        assertEquals(emptyList<Effect>(), swap.onDied(old))
        assertEquals(Phase.BINDING, swap.phase)
        assertEquals(listOf(Effect.SendShow(1)), swap.onConnected(current))
    }

    @Test
    fun `MSG_SHOWN before the connection, or twice, does nothing`() {
        swap.start(kbm, 1)
        val id = swap.sessionId
        assertEquals(emptyList<Effect>(), swap.onShown(id))
        swap.onConnected(id)
        swap.onShown(id)
        assertEquals(emptyList<Effect>(), swap.onShown(id))
    }

    @Test
    fun `ending while the plugin is still starting hides it, and the panel stays`() {
        swap.start(kbm, 1)
        swap.onConnected(swap.sessionId)
        assertEquals(listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.end(EndReason.AYN_PRESS))
    }

    @Test
    fun `ending while binding only unbinds`() {
        swap.start(kbm, 1)
        assertEquals(listOf(Effect.ShowPanel, Effect.Unbind), swap.end(EndReason.SCREEN_OFF))
    }

    @Test
    fun `ending when idle does nothing`() {
        assertEquals(emptyList<Effect>(), swap.end(EndReason.AYN_PRESS))
    }

    @Test
    fun `MSG_CLOSED while waiting for MSG_SHOWN keeps the panel and hides the plugin`() {
        swap.start(kbm, 1)
        val id = swap.sessionId
        swap.onConnected(id)
        assertEquals(listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.onClosed(id))
        assertEquals(Phase.IDLE, swap.phase)
        assertEquals(null, swap.nextDeadline)
        assertEquals(emptyList<Effect>(), swap.onShown(id))
        assertEquals(Owner.Panel, swap.owner)
    }

    @Test
    fun `a plugin that dies before MSG_SHOWN gets no MSG_HIDE and the panel stays`() {
        swap.start(kbm, 1)
        val id = swap.sessionId
        swap.onConnected(id)
        assertEquals(listOf(Effect.ShowPanel, Effect.Unbind), swap.onDied(id))
        assertEquals(Owner.Panel, swap.owner)
    }

    @Test
    fun `a second start while active is ignored and the plugin keeps the bottom screen`() {
        val id = active()
        assertEquals(emptyList<Effect>(), swap.start("other/.Svc", 1))
        assertEquals(id, swap.sessionId)
        assertEquals(Phase.ACTIVE, swap.phase)
        assertEquals(Owner.Plugin(kbm), swap.owner)
    }

    @Test
    fun `an AYN press while binding drops the session without MSG_HIDE`() {
        swap.start(kbm, 1)
        val id = swap.sessionId
        assertEquals(listOf(Effect.ShowPanel, Effect.Unbind), swap.end(EndReason.AYN_PRESS))
        assertEquals(Phase.IDLE, swap.phase)
        assertEquals(null, swap.nextDeadline)
        // The connection that lands after the press is from a dead session.
        assertEquals(emptyList<Effect>(), swap.onConnected(id))
        clock += PanelPluginSwap.BIND_TIMEOUT_MS
        assertEquals(emptyList<Effect>(), swap.onTick())
    }

    @Test
    fun `a late MSG_SHOWN from a timed-out session doesn't hide the panel during the next one`() {
        swap.start(kbm, 1)
        val old = swap.sessionId
        swap.onConnected(old)
        clock += PanelPluginContract.SHOWN_TIMEOUT_MS
        swap.onTick()
        swap.start(kbm, 1)
        val current = swap.sessionId
        swap.onConnected(current)
        clock += 1_000 // the old session's MSG_SHOWN arrives about 4 s after its MSG_SHOW
        assertEquals(emptyList<Effect>(), swap.onShown(old))
        assertEquals(Phase.SHOWING, swap.phase)
        assertEquals(Owner.Panel, swap.owner)
        assertEquals(listOf(Effect.HidePanel), swap.onShown(current))
    }

    @Test
    fun `the shown timeout starts at the connection, not at the start`() {
        swap.start(kbm, 1)
        clock += PanelPluginSwap.BIND_TIMEOUT_MS - 1
        swap.onConnected(swap.sessionId)
        clock += PanelPluginContract.SHOWN_TIMEOUT_MS - 1
        assertEquals(emptyList<Effect>(), swap.onTick())
        assertEquals(Phase.SHOWING, swap.phase)
        clock += 1
        assertEquals(listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.onTick())
    }

    // ---- owner rules ----

    @Test
    fun `AYN press, screen off, explicit show and the debug end bring the panel back before MSG_HIDE`() {
        for (reason in listOf(EndReason.AYN_PRESS, EndReason.SCREEN_OFF, EndReason.PANEL_REQUESTED, EndReason.DEBUG)) {
            active()
            assertEquals(reason.name, listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.end(reason))
        }
    }

    @Test
    fun `long press, a blanked screen, standby and a stopping service hide the plugin without flashing the panel`() {
        for (reason in listOf(EndReason.AYN_LONG_PRESS, EndReason.BOTTOM_BLANKED, EndReason.STANDBY, EndReason.SERVICE_STOPPED)) {
            active()
            assertEquals(reason.name, listOf(Effect.SendHide, Effect.Unbind), swap.end(reason))
        }
    }

    @Test
    fun `only an automatic show leaves an active plugin alone`() {
        assertFalse(swap.keepsPluginOnShow(auto = true))
        swap.start(kbm, 1)
        assertFalse(swap.keepsPluginOnShow(auto = true))
        swap.onConnected(swap.sessionId)
        swap.onShown(swap.sessionId)
        assertTrue(swap.keepsPluginOnShow(auto = true))
        assertFalse(swap.keepsPluginOnShow(auto = false))
    }

    @Test
    fun `the plugin owns the bottom screen only while active`() {
        assertEquals(Owner.Panel, swap.owner)
        val id = active()
        assertEquals(Owner.Plugin(kbm), swap.owner)
        swap.onClosed(id)
        assertEquals(Owner.Panel, swap.owner)
    }

    @Test
    fun `opened elsewhere ends the session but leaves the panel hidden`() {
        val id = active()
        assertEquals(
            listOf(Effect.SendHide, Effect.Unbind),
            swap.onClosed(id, PanelPluginContract.CLOSED_OPENED_ELSEWHERE),
        )
        assertEquals(Phase.IDLE, swap.phase)
        assertEquals(Owner.Panel, swap.owner)
        assertFalse(swap.busy)
    }

    @Test
    fun `closed by the user, an error, or an unknown reason brings the panel back`() {
        for (reason in listOf(PanelPluginContract.CLOSED_BY_USER, PanelPluginContract.CLOSED_ERROR, 99)) {
            val id = active()
            assertEquals("reason $reason", listOf(Effect.ShowPanel, Effect.SendHide, Effect.Unbind), swap.onClosed(id, reason))
        }
    }

    // ---- handback order: MSG_HIDE only once the panel is drawn ----

    @Test
    fun `a handback runs the panel show now and MSG_HIDE and unbind once it's visible`() {
        active()
        val (now, later) = PanelPluginSwap.splitAtShowPanel(swap.end(EndReason.AYN_PRESS))
        assertEquals(listOf(Effect.ShowPanel), now)
        assertEquals(listOf(Effect.SendHide, Effect.Unbind), later)
    }

    @Test
    fun `a dead plugin's unbind also waits for the panel`() {
        val id = active()
        val (now, later) = PanelPluginSwap.splitAtShowPanel(swap.onDied(id))
        assertEquals(listOf(Effect.ShowPanel), now)
        assertEquals(listOf(Effect.Unbind), later)
    }

    @Test
    fun `without a panel show everything runs now`() {
        active()
        val (now, later) = PanelPluginSwap.splitAtShowPanel(swap.end(EndReason.AYN_LONG_PRESS))
        assertEquals(listOf(Effect.SendHide, Effect.Unbind), now)
        assertEquals(emptyList<Effect>(), later)
    }

    @Test
    fun `a new session can start while the last handback waits`() {
        active()
        swap.end(EndReason.PLUGIN_CLOSED)
        assertEquals(listOf(Effect.Bind(kbm)), swap.start(kbm, 1))
    }
}
