package jr.brian.commandpannel.privileged.helper

/**
 * Constants shared by the app and the root helper process. The helper is a second instance of this same APK, so
 * both sides load these from one class.
 */
object HelperProtocol {
    /** Bumped whenever [IRootHelper] gains methods. The app rejects a helper that reports a different version. */
    const val VERSION = 8

    /** Fully-qualified name of the helper's `main`, launched via `app_process`. */
    const val MAIN_CLASS = "jr.brian.commandpannel.privileged.helper.RootHelperMain"

    /** ServiceManager name the helper registers under; per-applicationId so debug and release never collide. */
    fun serviceName(appPackage: String): String = "$appPackage.root_helper"

    /** How long the helper outlives its client (app crash, force-stop) before it recovers stock and exits. */
    const val GRACE_MS = 10_000L

    private const val BACKOFF_CAP_MS = 30_000L

    /** Delay before reconnect attempt [attempt] (0-based) after the helper dies: 1, 2, 4, 8, 16, then 30 s. */
    fun backoffMs(attempt: Int): Long =
        if (attempt >= 5) BACKOFF_CAP_MS else (1_000L shl attempt.coerceAtLeast(0)).coerceAtMost(BACKOFF_CAP_MS)
}
