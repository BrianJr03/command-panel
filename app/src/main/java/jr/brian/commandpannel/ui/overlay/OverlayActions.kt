package jr.brian.commandpannel.ui.overlay

/**
 * Controls the overlay exposes. The service's repository implements these; previews/tests pass [None].
 * `committed = false` during a slider drag (temporary write), `true` on release (persist).
 */
interface OverlayActions {
    fun selectMode(mode: Int)
    fun setTopBrightness(fraction: Float, committed: Boolean)
    fun setBottomBrightness(fraction: Float, committed: Boolean)
    fun setDualBrightness(fraction: Float, committed: Boolean)
    fun setTopVolume(fraction: Float, committed: Boolean)
    fun setBottomVolume(fraction: Float, committed: Boolean)
    fun toggleRefreshRate()
    fun onTileClick(id: String)

    object None : OverlayActions {
        override fun selectMode(mode: Int) {}
        override fun setTopBrightness(fraction: Float, committed: Boolean) {}
        override fun setBottomBrightness(fraction: Float, committed: Boolean) {}
        override fun setDualBrightness(fraction: Float, committed: Boolean) {}
        override fun setTopVolume(fraction: Float, committed: Boolean) {}
        override fun setBottomVolume(fraction: Float, committed: Boolean) {}
        override fun toggleRefreshRate() {}
        override fun onTileClick(id: String) {}
    }
}
