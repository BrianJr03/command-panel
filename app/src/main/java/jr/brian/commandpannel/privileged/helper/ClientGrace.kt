package jr.brian.commandpannel.privileged.helper

/**
 * What the root helper does after its client (the app) dies. It waits up to a grace period for the app to come back
 * (a crash restart reattaches to the same helper); if the package is uninstalled, or the grace runs out, it restores
 * the stock assistant when recovery is armed and exits. Pure, so it is unit-tested without a device.
 */
object ClientGrace {
    enum class Step { WAIT, RECOVER_AND_EXIT, EXIT }

    fun next(reattached: Boolean, packageInstalled: Boolean, elapsedMs: Long, graceMs: Long, armed: Boolean): Step {
        if (reattached) return Step.WAIT
        if (packageInstalled && elapsedMs < graceMs) return Step.WAIT
        return if (armed) Step.RECOVER_AND_EXIT else Step.EXIT
    }
}
