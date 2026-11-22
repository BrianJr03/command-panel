package jr.brian.commandpannel.privileged

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class StockSuppressorCancelTest {
    @Test
    fun `a cancel during the respawn wait still re-enables the service`() = runBlocking {
        val commands = CopyOnWriteArrayList<String>()
        val killed = CompletableDeferred<Unit>()
        val suppressor = StockSuppressor { command ->
            commands += command
            if (command.startsWith("'kill' '-9'")) killed.complete(Unit)
            if (command.startsWith("'pidof'")) "123" else ""
        }
        val job = launch(Dispatchers.Default) { suppressor.suppress() }
        killed.await()
        job.cancel()
        job.join()

        val kill = commands.indexOfFirst { it.startsWith("'kill' '-9'") }
        val enable = commands.indexOfLast { it == RootShell.command("pm", "enable", StockSuppressor.TCC_SERVICE) }
        assertTrue(commands.toString(), enable > kill)
    }
}
