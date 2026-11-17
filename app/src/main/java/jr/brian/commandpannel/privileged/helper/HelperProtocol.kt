package jr.brian.commandpannel.privileged.helper

/**
 * Constants shared by the app and the root helper process. The helper is a second instance of this same APK, so
 * both sides load these from one class.
 */
object HelperProtocol {
    /** Bumped whenever [IRootHelper] gains methods. The app rejects a helper that reports a different version. */
    const val VERSION = 1

    /** Fully-qualified name of the helper's `main`, launched via `app_process`. */
    const val MAIN_CLASS = "jr.brian.commandpannel.privileged.helper.RootHelperMain"

    /** ServiceManager name the helper registers under; per-applicationId so debug and release never collide. */
    fun serviceName(appPackage: String): String = "$appPackage.root_helper"
}
