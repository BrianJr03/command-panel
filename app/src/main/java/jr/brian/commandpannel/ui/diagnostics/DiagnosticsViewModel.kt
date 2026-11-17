package jr.brian.commandpannel.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jr.brian.commandpannel.privileged.PrivilegedAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsState(
    val running: Boolean = false,
    val lines: List<Pair<String, Boolean?>> = emptyList(),
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    app: Application,
    private val privileged: PrivilegedAccess,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun run() {
        if (_state.value.running) return
        _state.value = DiagnosticsState(running = true)
        viewModelScope.launch {
            val r = privileged.initialize(getApplication())
            val lines = buildList {
                add("Hidden-API exemptions" to r.hiddenApi)
                add("PServerBinder root shell" to r.rootShell)
                r.grant?.let {
                    add("Permissions held already: ${it.alreadyHeld}" to true)
                    if (it.granted.isNotEmpty()) add("Granted: ${it.granted.joinToString()}" to true)
                    if (it.failed.isNotEmpty()) add("Failed: ${it.failed.joinToString()}" to false)
                }
                add("Root helper connected" to r.helper)
                r.helperIdentity?.let { add(it to true) }
                // Round-trip proof: run a command through the helper and read a display's brightness.
                if (r.helper) {
                    val whoami = privileged.shell("id -u")?.trim()
                    add("helper shell id -u => $whoami" to (whoami == "0"))
                    val b = privileged.getDisplayBrightness(0)
                    add("display 0 brightness => $b" to (b != null && !b.isNaN()))
                }
            }
            _state.value = DiagnosticsState(running = false, lines = lines)
        }
    }
}
