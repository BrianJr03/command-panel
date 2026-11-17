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

    fun setFocusLock(value: Int)
    fun setPerformance(value: Int)
    fun setFanMode(value: Int)
    fun setFanSpeed(value: Int)
    fun setControllerStyle(value: Int)
    fun setThemeMode(value: Int)
    fun toggleAmbientLight()
    fun cleanMemory()
    fun setSuppressBottomTip(suppress: Boolean)

    object None : OverlayActions {
        override fun selectMode(mode: Int) {}
        override fun setTopBrightness(fraction: Float, committed: Boolean) {}
        override fun setBottomBrightness(fraction: Float, committed: Boolean) {}
        override fun setDualBrightness(fraction: Float, committed: Boolean) {}
        override fun setTopVolume(fraction: Float, committed: Boolean) {}
        override fun setBottomVolume(fraction: Float, committed: Boolean) {}
        override fun toggleRefreshRate() {}
        override fun onTileClick(id: String) {}
        override fun setFocusLock(value: Int) {}
        override fun setPerformance(value: Int) {}
        override fun setFanMode(value: Int) {}
        override fun setFanSpeed(value: Int) {}
        override fun setControllerStyle(value: Int) {}
        override fun setThemeMode(value: Int) {}
        override fun toggleAmbientLight() {}
        override fun cleanMemory() {}
        override fun setSuppressBottomTip(suppress: Boolean) {}
    }
}
