package jr.brian.commandpannel.system

import java.net.HttpURLConnection

/** A GitHub API request the [GitHubRateGuard] held back; [retryAtMs] is when one frees up. */
internal class GitHubRateLimitedException(val retryAtMs: Long) : IllegalStateException("GitHub rate limit")

/**
 * Keeps the app under GitHub's unauthenticated API limit (60 requests per hour per IP). Every `api.github.com` request
 * through [Http] is counted in a rolling hour, and GitHub's own `X-RateLimit-Remaining` / `X-RateLimit-Reset` headers
 * correct the count (the limit is per IP, so other devices on the network use it too). Theme syncs stop
 * [SYNC_RESERVE] requests early, so the app's update check still gets through.
 *
 * In memory only: the service keeps the process alive, and after a restart the first response's headers catch up.
 */
internal object GitHubRateGuard {
    const val HOST = "api.github.com"
    private const val LIMIT = 60
    /** Requests theme syncs leave unused for the update check. */
    const val SYNC_RESERVE = 5
    private const val WINDOW_MS = 60L * 60 * 1000

    private val sent = ArrayDeque<Long>()
    private var serverRemaining: Int? = null
    private var serverResetMs = 0L

    /** Requests left this hour, after keeping [reserve] back. */
    @Synchronized
    fun available(reserve: Int = 0, now: Long = System.currentTimeMillis()): Int {
        prune(now)
        val local = LIMIT - sent.size
        val server = serverRemaining?.takeIf { serverResetMs > now } ?: LIMIT
        return (minOf(local, server) - reserve).coerceAtLeast(0)
    }

    /** When the next request frees up: the oldest one leaving the window, or GitHub's reset time. */
    @Synchronized
    fun retryAt(now: Long = System.currentTimeMillis()): Long {
        prune(now)
        val local = sent.firstOrNull()?.plus(WINDOW_MS) ?: now
        val server = if (serverResetMs > now && serverRemaining == 0) serverResetMs else now
        return maxOf(local, server)
    }

    /** Counts one request, or throws [GitHubRateLimitedException] when none is left past [reserve]. */
    @Synchronized
    fun acquire(reserve: Int = 0, now: Long = System.currentTimeMillis()) {
        if (available(reserve, now) < 1) throw GitHubRateLimitedException(retryAt(now))
        sent.addLast(now)
    }

    /** Reads GitHub's rate-limit headers from a finished response (including a 403 / 429 when over the limit). */
    @Synchronized
    fun observe(conn: HttpURLConnection) {
        val remaining = conn.getHeaderField("X-RateLimit-Remaining")?.toIntOrNull() ?: return
        val reset = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull() ?: return
        serverRemaining = remaining
        serverResetMs = reset * 1000
    }

    private fun prune(now: Long) {
        while (sent.isNotEmpty() && now - sent.first() >= WINDOW_MS) sent.removeFirst()
    }

    @Synchronized
    internal fun resetForTest() {
        sent.clear(); serverRemaining = null; serverResetMs = 0L
    }
}
