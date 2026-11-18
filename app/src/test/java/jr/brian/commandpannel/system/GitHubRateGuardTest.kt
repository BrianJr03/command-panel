package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubRateGuardTest {

    private val hour = 60L * 60 * 1000
    private val t0 = 1_000_000_000L

    @Before
    fun reset() = GitHubRateGuard.resetForTest()

    @Test
    fun `syncs stop at the reserve and the rest is left for the update check`() {
        repeat(60 - GitHubRateGuard.SYNC_RESERVE) { GitHubRateGuard.acquire(GitHubRateGuard.SYNC_RESERVE, t0 + it) }
        assertEquals(0, GitHubRateGuard.available(GitHubRateGuard.SYNC_RESERVE, t0 + 100))
        val held = runCatching { GitHubRateGuard.acquire(GitHubRateGuard.SYNC_RESERVE, t0 + 100) }
        assertTrue(held.exceptionOrNull() is GitHubRateLimitedException)
        // The update check (no reserve) still has its five.
        assertEquals(GitHubRateGuard.SYNC_RESERVE, GitHubRateGuard.available(0, t0 + 100))
    }

    @Test
    fun `requests leave the window after an hour`() {
        repeat(60) { GitHubRateGuard.acquire(0, t0) }
        assertEquals(0, GitHubRateGuard.available(0, t0 + 1))
        assertEquals(t0 + hour, GitHubRateGuard.retryAt(t0 + 1))
        assertEquals(60, GitHubRateGuard.available(0, t0 + hour))
    }

    @Test
    fun `a full hour of requests is never exceeded`() {
        var sent = 0
        for (i in 0 until 200) {
            if (runCatching { GitHubRateGuard.acquire(0, t0 + i * 1000L) }.isSuccess) sent++
        }
        assertEquals(60, sent)
        assertFalse(GitHubRateGuard.available(0, t0 + 200_000) > 0)
    }
}
