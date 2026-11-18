package jr.brian.commandpannel.ui.settings

import android.app.Application
import android.content.Intent
import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.ThorRgbController
import android.content.ComponentName
import jr.brian.commandpannel.service.AssistantService
import jr.brian.commandpannel.system.Prefs
import jr.brian.commandpannel.system.QuickLaunch
import jr.brian.commandpannel.system.QuickLaunchApp
import jr.brian.commandpannel.system.QuickLaunchDisplay
import jr.brian.commandpannel.system.SystemSettings
import jr.brian.commandpannel.system.CustomLookStore
import jr.brian.commandpannel.system.BdsaDetector
import jr.brian.commandpannel.system.BdsaStatus
import jr.brian.commandpannel.system.FontNeeds
import jr.brian.commandpannel.system.PackInstaller
import jr.brian.commandpannel.system.ThemeFolder
import jr.brian.commandpannel.system.ThemeSources
import jr.brian.commandpannel.ui.theme.LookPack
import jr.brian.commandpannel.ui.theme.LookPackJson
import jr.brian.commandpannel.system.UserFont
import jr.brian.commandpannel.system.UserFontStore
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.LookRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SettingsCategory(@StringRes val titleRes: Int) {
    GENERAL(R.string.settings_cat_general),
    NAV_BAR(R.string.settings_cat_nav_bar),
    QUICK_TOGGLES(R.string.settings_cat_quick_toggles),
    APPEARANCE(R.string.settings_cat_appearance),
    THEMES(R.string.settings_cat_themes),
    DIAGNOSTICS(R.string.settings_cat_diagnostics),
}

data class SettingsState(
    val replaceStock: Boolean = false,
    val batteryEstimate: Boolean = false,
    val hideNetSpeed: Boolean = false,
    val hideTileLabels: Boolean = false,
    val keepOnScreen: Boolean = true,
    val backupMessage: String? = null,
    val themeMode: Int = 4,              // tcc_theme_mode
    val rgbAccentSync: Boolean = false,
    /** Ambient-LED accent (opaque ARGB) when [rgbAccentSync] is on; themes the companion when it follows the look. */
    val accentOverride: Int? = null,
    /** When on, the companion screen uses the overlay's look, theme mode and accent instead of its default style. */
    val companionFollowLook: Boolean = false,
    val enabledTiles: Set<String> = emptySet(),
    val orderedEnabledTiles: List<String> = emptyList(),
    val quickLaunchLabel: String? = null,
    val quickLaunchDisplay: QuickLaunchDisplay = QuickLaunchDisplay.TOP,
    /** Show the Quick Launch app's icon and name on its nav button instead of the stock glyph and "Launch". */
    val quickLaunchShowApp: Boolean = true,
    val quickLaunchComponent: ComponentName? = null,
    /** Nav bar button keys in display order. */
    val navOrder: List<String> = jr.brian.commandpannel.system.NavBarButton.entries.map { it.key },
    val overlayLayout: jr.brian.commandpannel.system.OverlayLayout = jr.brian.commandpannel.system.OverlayLayout.DEFAULT,
    /** The base look of the active selection (the look itself for built-ins). */
    val overlayLook: jr.brian.commandpannel.ui.theme.TccUiLook = jr.brian.commandpannel.ui.theme.TccUiLook.AUTO,
    /** What `overlay_look` points at: a built-in look or a saved Theme Studio look. */
    val overlayLookRef: LookRef = LookRef.BuiltIn(jr.brian.commandpannel.ui.theme.TccUiLook.AUTO),
    /** The active Theme Studio look, or null when a built-in look is selected. */
    val overlayCustomLook: CustomLook? = null,
    val customLooks: List<CustomLook> = emptyList(),
    val userFonts: List<UserFont> = emptyList(),
    /** Result of the last Theme Studio export / import / sync, shown in the Themes section. */
    val themeMessage: String? = null,
    /** `false` when the `TCP Themes` folder couldn't be created (no root shell). */
    val themeFolderReady: Boolean = true,
    /** Packs directly in `TCP Themes/themes` (fonts already re-linked for previews). */
    val folderPacks: List<jr.brian.commandpannel.system.FolderPack> = emptyList(),
    val themeSources: List<jr.brian.commandpannel.system.ThemeSource> = emptyList(),
    /** Fonts the last pack can download, waiting for the user to agree. */
    val pendingFonts: FontNeeds? = null,
    /** A sync or download is running. */
    val themeBusy: Boolean = false,
    /** Result of the last repo follow / sync, shown in the Theme repos card. */
    val repoMessage: String? = null,
    /** Better Dual Screen Assistant presence; while it's active, Command Panel stands by. */
    val bdsa: BdsaStatus = BdsaStatus.None,
    /** The BDSA package Command Panel disabled (offers "Re-enable"), or null. */
    val bdsaDisabledByUs: String? = null,
    /** Result of the last BDSA action. */
    val bdsaMessage: String? = null,
    /** The update / What's New dialog, or null when closed. */
    val updateDialog: UpdatePhase? = null,
    /** A check is running (manual button shows busy). */
    val updateChecking: Boolean = false,
    /** Result of the last manual check, shown in the Updates card. */
    val updateMessage: String? = null,
    /** When updates were last checked (ms), 0 if never. */
    val updateLastCheck: Long = 0L,
    val replayEnabled: Boolean = false,
    val replayWindowSeconds: Int = 30,
    val replayPreset: jr.brian.commandpannel.privileged.ReplayBitratePreset = jr.brian.commandpannel.privileged.ReplayBitratePreset.BALANCED,
    val replayMessage: String? = null,
    val appPickerOpen: Boolean = false,
    val apps: List<QuickLaunchApp> = emptyList(),
    val diagnostics: List<Pair<String, Boolean?>> = emptyList(),
    val diagnosticsRunning: Boolean = false,
    /** The companion tour's current step (an index into [TOUR_STEPS]), or null when the tour isn't running. */
    val tourStep: Int? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    app: Application,
    private val privileged: PrivilegedAccess,
) : AndroidViewModel(app) {

    private val prefs = Prefs.of(app)
    private val settings = SystemSettings(app.contentResolver, privileged)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val app get() = getApplication<Application>()

    private fun load() = SettingsState(
        replaceStock = prefs.getBoolean("replace_stock_assistant", false),
        batteryEstimate = prefs.getBoolean("battery_estimate", false),
        hideNetSpeed = prefs.getBoolean("hide_net_speed", false),
        hideTileLabels = prefs.getBoolean("qs_hide_labels", false),
        keepOnScreen = prefs.getBoolean("clear_tasks_keep_on_screen", true),
        themeMode = settings.getInt(SystemSettings.Keys.THEME_MODE, 4),
        rgbAccentSync = prefs.getBoolean("rgb_accent_sync", false),
        accentOverride = ThorRgbController.accentOverride(app, prefs.getBoolean("rgb_accent_sync", false)),
        companionFollowLook = prefs.getBoolean("companion_follow_look", false),
        enabledTiles = jr.brian.commandpannel.system.QuickSettingTileId
            .enabledKeys(prefs.getString("qs_enabled_tiles", null)).toSet(),
        orderedEnabledTiles = jr.brian.commandpannel.system.QuickSettingTileId
            .enabledKeys(prefs.getString("qs_enabled_tiles", null), prefs.getString("qs_tile_order", null)),
        quickLaunchLabel = QuickLaunch.savedLabel(getApplication()),
        quickLaunchDisplay = QuickLaunch.savedDisplay(getApplication()),
        quickLaunchShowApp = prefs.getBoolean("quick_launch_show_app", true),
        quickLaunchComponent = QuickLaunch.savedComponent(getApplication()),
        navOrder = jr.brian.commandpannel.system.NavBarButton.ordered(prefs.getString("nav_bar_order", null)).map { it.key },
        overlayLayout = jr.brian.commandpannel.system.OverlayLayout.decode(prefs.getString("overlay_layout", null)),
        overlayLook = CustomLookStore.resolve(prefs).first,
        overlayLookRef = CustomLookStore.selected(prefs),
        overlayCustomLook = CustomLookStore.resolve(prefs).second,
        customLooks = CustomLookStore.all(prefs),
        userFonts = UserFontStore.all(getApplication()),
        replayEnabled = prefs.getBoolean("replay_enabled", false),
        replayWindowSeconds = jr.brian.commandpannel.privileged.ScreenRecorderEngine
            .normalizeReplayWindowSeconds(prefs.getInt("replay_window_seconds", 30)),
        replayPreset = jr.brian.commandpannel.privileged.ReplayBitratePreset.fromKey(prefs.getString("replay_bitrate", null)),
    )

    fun setReplayEnabled(enable: Boolean) {
        prefs.edit().putBoolean("replay_enabled", enable).apply()
        AssistantService.start(app)
        app.sendBroadcast(
            Intent(AssistantService.ACTION_REPLAY_SET_ENABLED).setPackage(app.packageName)
                .putExtra(AssistantService.EXTRA_ENABLE, enable),
        )
        _state.value = _state.value.copy(replayEnabled = enable, replayMessage = app.getString(if (enable) R.string.msg_replay_arming else R.string.msg_replay_off))
    }

    fun setReplayWindowSeconds(seconds: Int) {
        prefs.edit().putInt("replay_window_seconds", seconds).apply()
        _state.value = _state.value.copy(replayWindowSeconds = seconds, replayMessage = app.getString(R.string.msg_replay_next_arm))
    }

    fun setReplayPreset(preset: jr.brian.commandpannel.privileged.ReplayBitratePreset) {
        prefs.edit().putString("replay_bitrate", preset.key).apply()
        _state.value = _state.value.copy(replayPreset = preset, replayMessage = app.getString(R.string.msg_replay_next_arm))
    }

    fun saveReplayClip() {
        app.sendBroadcast(Intent(AssistantService.ACTION_REPLAY_SAVE).setPackage(app.packageName))
        _state.value = _state.value.copy(replayMessage = app.getString(R.string.msg_replay_saving))
        // Surface the result the service broadcasts back.
        val filter = android.content.IntentFilter(AssistantService.ACTION_REPLAY_SAVED)
        app.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) {
                val result = i.getStringExtra(AssistantService.EXTRA_REPLAY_RESULT).orEmpty()
                val msg = if (result.startsWith("error:")) app.getString(R.string.msg_save_failed, result.removePrefix("error:")) else app.getString(R.string.msg_saved_to, result)
                _state.value = _state.value.copy(replayMessage = msg)
                runCatching { c.unregisterReceiver(this) }
            }
        }, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
    }

    fun setLook(look: jr.brian.commandpannel.ui.theme.TccUiLook) = selectLook(LookRef.BuiltIn(look))

    /** Applies a built-in or saved look to the overlay (and the companion when it follows the look). */
    fun selectLook(ref: LookRef) {
        CustomLookStore.select(prefs, ref)
        refreshLooks()
    }

    /** Saves (inserts or replaces) a Theme Studio look; the overlay restyles live if it is the active one. */
    fun saveCustomLook(look: CustomLook, apply: Boolean = false) {
        CustomLookStore.save(prefs, look)
        if (apply) CustomLookStore.select(prefs, look.ref)
        refreshLooks()
    }

    fun deleteCustomLook(id: String) {
        CustomLookStore.delete(prefs, id)
        refreshLooks()
    }

    /** Imports a TTF/OTF picked with the system file picker; reports the outcome through [onResult]. */
    fun importFont(uri: android.net.Uri, onResult: (Result<UserFont>) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { UserFontStore.import(app, uri) }
            _state.value = _state.value.copy(userFonts = UserFontStore.all(app))
            onResult(result)
        }
    }

    /** Deletes an imported font; looks that used it fall back to the default font. */
    fun deleteFont(id: String) {
        UserFontStore.delete(app, id)
        _state.value = _state.value.copy(userFonts = UserFontStore.all(app))
    }

    // ---- theme packs, the TCP Themes folder and theme repos ----

    /** Lists the packs in `TCP Themes/themes` (creating the folder) and checks due repos for updates. */
    fun refreshThemeFolder(checkUpdates: Boolean = true) {
        viewModelScope.launch {
            val (ok, packs) = withContext(Dispatchers.IO) {
                val ok = ThemeFolder.ensure(app, privileged)
                // Synced repos keep copies in themes/<repo>/; those show under their repo, not as local packs.
                ok to if (ok) ThemeFolder.listPacks(privileged).filter { it.path.substringBeforeLast('/') == ThemeFolder.THEMES }
                    .map { fp -> fp.copy(pack = fp.pack?.let { pk -> pk.copy(looks = pk.looks.map { PackInstaller.relinked(app, it) }) }) }
                else emptyList()
            }
            _state.value = _state.value.copy(themeFolderReady = ok, folderPacks = packs, themeSources = ThemeSources.all(prefs))
            if (checkUpdates) ThemeSources.all(prefs).filter { ThemeSources.isDue(it) }.forEach { syncSource(it.id, quiet = true) }
        }
    }

    /** Adds [looks] from a pack as new saved looks; fonts come from `TCP Themes/fonts` or are offered for download. */
    fun addPackLooks(pack: LookPack, looks: List<CustomLook> = pack.looks) {
        viewModelScope.launch {
            val needs = withContext(Dispatchers.IO) {
                PackInstaller.prepareFonts(app, privileged, pack, looks).also { PackInstaller.install(app, prefs, looks) }
            }
            refreshLooks()
            reportFonts(app.resources.getQuantityString(R.plurals.msg_pack_added, looks.size, looks.size, pack.name), needs)
        }
    }

    /** Prepares a pack look's fonts, then hands back an unsaved copy to open in Theme Studio ("start from this"). */
    fun startFromPackLook(pack: LookPack, look: CustomLook, onReady: (CustomLook) -> Unit) {
        viewModelScope.launch {
            val needs = withContext(Dispatchers.IO) { PackInstaller.prepareFonts(app, privileged, pack, listOf(look)) }
            reportFonts(null, needs)
            onReady(PackInstaller.relinked(app, look).copy(id = java.util.UUID.randomUUID().toString(), source = null))
        }
    }

    /** Downloads the fonts the last pack offered, then re-links saved looks that use them. */
    fun downloadPendingFonts() {
        val needs = _state.value.pendingFonts ?: return
        _state.value = _state.value.copy(pendingFonts = null, themeBusy = true)
        viewModelScope.launch {
            val failed = withContext(Dispatchers.IO) {
                val failed = needs.downloadable.filter { (file, src) -> PackInstaller.downloadFont(app, privileged, file, src).isFailure }.keys
                PackInstaller.relinkSaved(app, prefs)
                failed
            }
            _state.value = _state.value.copy(themeBusy = false, userFonts = UserFontStore.all(app))
            refreshLooks()
            _state.value = _state.value.copy(
                themeMessage = if (failed.isEmpty()) app.resources.getQuantityString(R.plurals.msg_fonts_downloaded, needs.downloadable.size, needs.downloadable.size)
                else app.getString(R.string.msg_fonts_download_failed, failed.joinToString()),
            )
        }
    }

    fun dismissPendingFonts() {
        _state.value = _state.value.copy(pendingFonts = null)
    }

    private fun reportFonts(done: String?, needs: FontNeeds) {
        val stillMissing = needs.missing - needs.downloadable.keys
        val msg = when {
            stillMissing.isNotEmpty() -> app.getString(R.string.msg_fonts_missing, stillMissing.joinToString())
            else -> null
        }
        _state.value = _state.value.copy(
            userFonts = UserFontStore.all(app),
            pendingFonts = needs.takeIf { it.downloadable.isNotEmpty() },
            themeMessage = listOfNotNull(done, msg).joinToString(" ").ifBlank { _state.value.themeMessage },
        )
    }

    /** Writes [looks] as one pack in `TCP Themes/themes` (with their imported fonts in `fonts/`). */
    fun exportLooks(looks: List<CustomLook>, packName: String) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { PackInstaller.exportPack(app, privileged, packName, looks) }
            _state.value = _state.value.copy(
                themeMessage = path?.let { app.getString(R.string.msg_look_exported, it) } ?: app.getString(R.string.msg_look_export_failed),
            )
            refreshThemeFolder(checkUpdates = false)
        }
    }

    /** Imports a pack or single look picked with the system file picker (files outside the TCP Themes folder). */
    fun importLook(uri: android.net.Uri) {
        viewModelScope.launch {
            val pack = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                    LookPackJson.decode(text)
                }.getOrNull()
            }
            if (pack == null || pack.looks.isEmpty()) {
                _state.value = _state.value.copy(themeMessage = app.getString(R.string.msg_look_import_failed))
            } else {
                addPackLooks(pack)
            }
        }
    }

    /** Follows a theme repo link and syncs it right away. */
    fun addSource(link: String) {
        val source = ThemeSources.parse(link)
        if (source == null) {
            _state.value = _state.value.copy(repoMessage = app.getString(R.string.msg_source_invalid))
            return
        }
        ThemeSources.save(prefs, ThemeSources.all(prefs).firstOrNull { it.id == source.id } ?: source)
        _state.value = _state.value.copy(themeSources = ThemeSources.all(prefs))
        syncSource(source.id)
    }

    /** Pulls [id]'s latest packs; [quiet] skips the "up to date" message (automatic daily checks). */
    fun syncSource(id: String, quiet: Boolean = false) {
        val source = ThemeSources.all(prefs).firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(themeBusy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ThemeSources.sync(app, privileged, prefs, source) }
            refreshLooks()
            _state.value = _state.value.copy(themeBusy = false, themeSources = ThemeSources.all(prefs))
            result.onSuccess { r ->
                val msg = if (r.updatedFiles == 0 && r.removedLooks == 0) {
                    if (quiet) null else app.getString(R.string.msg_source_up_to_date, source.name)
                } else {
                    app.resources.getQuantityString(R.plurals.msg_source_synced, r.lookCount, r.lookCount, source.name)
                }
                if (msg != null) _state.value = _state.value.copy(repoMessage = msg)
                reportFonts(null, r.fontNeeds)
            }.onFailure { e ->
                // Automatic checks fail silently (offline, rate-limited); Update reports the error.
                if (!quiet) _state.value = _state.value.copy(repoMessage = app.getString(R.string.msg_source_failed, source.name, e.message ?: ""))
            }
        }
    }

    /** Stops following [id] and removes the looks it installed. */
    fun removeSource(id: String) {
        ThemeSources.remove(prefs, id)
        refreshLooks()
        _state.value = _state.value.copy(themeSources = ThemeSources.all(prefs))
    }

    private fun refreshLooks() {
        val (base, custom) = CustomLookStore.resolve(prefs)
        _state.value = _state.value.copy(
            overlayLook = base,
            overlayLookRef = CustomLookStore.selected(prefs),
            overlayCustomLook = custom,
            customLooks = CustomLookStore.all(prefs),
        )
    }

    private fun updateLayout(transform: (jr.brian.commandpannel.system.OverlayLayout) -> jr.brian.commandpannel.system.OverlayLayout) {
        val next = transform(_state.value.overlayLayout)
        prefs.edit().putString("overlay_layout", next.encode()).apply()
        _state.value = _state.value.copy(overlayLayout = next)
    }

    fun setNavBarTop(top: Boolean) = updateLayout {
        it.withNavBarPosition(
            if (top) jr.brian.commandpannel.system.NavBarPosition.TOP
            else jr.brian.commandpannel.system.NavBarPosition.BOTTOM
        )
    }
    fun resetLayout() = updateLayout { jr.brian.commandpannel.system.OverlayLayout.DEFAULT }

    /** Toggles the inverted sync: when on, the Thor ambient-LED color becomes the overlay accent. */
    fun setRgbAccentSync(enable: Boolean) {
        prefs.edit().putBoolean("rgb_accent_sync", enable).apply()
        // The overlay repository reads the LED color on the next refresh (its pref listener fires here).
        _state.value = _state.value.copy(rgbAccentSync = enable, accentOverride = ThorRgbController.accentOverride(app, enable))
    }

    /** Toggles whether the companion screen follows the overlay look (off keeps the default companion style). */
    fun setCompanionFollowLook(enable: Boolean) {
        prefs.edit().putBoolean("companion_follow_look", enable).apply()
        _state.value = _state.value.copy(companionFollowLook = enable)
    }

    fun openAppPicker() {
        _state.value = _state.value.copy(appPickerOpen = true)
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { QuickLaunch.listApps(app) }
            _state.value = _state.value.copy(apps = apps)
        }
    }

    fun closeAppPicker() {
        _state.value = _state.value.copy(appPickerOpen = false)
    }

    fun pickApp(component: ComponentName) {
        QuickLaunch.save(app, component, _state.value.quickLaunchDisplay)
        _state.value = _state.value.copy(
            appPickerOpen = false,
            quickLaunchLabel = QuickLaunch.savedLabel(app),
            quickLaunchComponent = component,
        )
    }

    fun setQuickLaunchDisplay(display: QuickLaunchDisplay) {
        QuickLaunch.savedComponent(app)?.let { QuickLaunch.save(app, it, display) }
        _state.value = _state.value.copy(quickLaunchDisplay = display)
    }

    fun clearQuickLaunch() {
        QuickLaunch.clear(app)
        _state.value = _state.value.copy(quickLaunchLabel = null, quickLaunchComponent = null)
    }

    fun refresh() { _state.value = load() }

    fun setBatteryEstimate(enable: Boolean) {
        prefs.edit().putBoolean("battery_estimate", enable).apply()
        _state.value = _state.value.copy(batteryEstimate = enable)
    }

    fun setHideNetSpeed(enable: Boolean) {
        prefs.edit().putBoolean("hide_net_speed", enable).apply()
        _state.value = _state.value.copy(hideNetSpeed = enable)
    }

    fun setHideTileLabels(enable: Boolean) {
        prefs.edit().putBoolean("qs_hide_labels", enable).apply()
        _state.value = _state.value.copy(hideTileLabels = enable)
    }

    fun exportBackup() {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                jr.brian.commandpannel.system.SettingsBackup.export(app, privileged)
            }
            _state.value = _state.value.copy(backupMessage = if (path != null) app.getString(R.string.msg_saved_to, path) else app.getString(R.string.msg_export_failed))
        }
    }

    fun restoreLatestBackup() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                val latest = jr.brian.commandpannel.system.SettingsBackup.list(privileged).firstOrNull()
                    ?: return@withContext -1
                jr.brian.commandpannel.system.SettingsBackup.import(app, privileged, latest)
            }
            val msg = when {
                n < 0 -> app.getString(R.string.msg_no_backup)
                n == 0 -> app.getString(R.string.msg_restore_failed)
                else -> app.resources.getQuantityString(R.plurals.msg_restored_settings, n, n)
            }
            _state.value = load().copy(backupMessage = msg)
        }
    }

    fun setKeepOnScreen(enable: Boolean) {
        prefs.edit().putBoolean("clear_tasks_keep_on_screen", enable).apply()
        _state.value = _state.value.copy(keepOnScreen = enable)
    }

    fun setTileEnabled(key: String, enable: Boolean) {
        val csv = jr.brian.commandpannel.system.QuickSettingTileId
            .toggle(prefs.getString("qs_enabled_tiles", null), key, enable)
        prefs.edit().putString("qs_enabled_tiles", csv).apply()
        _state.value = load()
    }

    /** Saves a drag-and-drop order of the overlay nav bar buttons. */
    fun reorderNavBar(keys: List<String>) {
        val order = jr.brian.commandpannel.system.NavBarButton.run {
            encode(keys.mapNotNull { fromKey(it) })
        }
        prefs.edit().putString("nav_bar_order", order).apply()
        _state.value = _state.value.copy(navOrder = order.split(","))
    }

    fun setQuickLaunchShowApp(enable: Boolean) {
        prefs.edit().putBoolean("quick_launch_show_app", enable).apply()
        _state.value = _state.value.copy(quickLaunchShowApp = enable)
    }

    /** Saves a drag-and-drop order of the enabled quick tiles. */
    fun reorderTiles(enabledOrder: List<String>) {
        val enabledCsv = prefs.getString("qs_enabled_tiles", null)
        val order = jr.brian.commandpannel.system.QuickSettingTileId.reorderEnabled(
            prefs.getString("qs_tile_order", null), enabledCsv, enabledOrder
        )
        prefs.edit().putString("qs_tile_order", order).apply()
        _state.value = _state.value.copy(
            orderedEnabledTiles = jr.brian.commandpannel.system.QuickSettingTileId.enabledKeys(enabledCsv, order),
        )
    }

    // ---- Better Dual Screen Assistant coexistence ----

    private val bdsa = BdsaDetector(app, privileged)

    // BDSA installed / removed / enabled / disabled while the companion is open (also by Command Panel's own buttons).
    private val bdsaPackageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            if (i.data?.schemeSpecificPart in BdsaDetector.PACKAGES) refreshBdsa(keepMessage = true)
        }
    }

    // The overlay re-themes when the ambient-LED color changes (its settings observer); this keeps the companion in step
    // when the color is changed elsewhere, e.g. from the overlay's Ambient LED toggle.
    private val ledColorObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val sync = _state.value.rgbAccentSync
            _state.value = _state.value.copy(accentOverride = ThorRgbController.accentOverride(app, sync))
        }
    }

    init {
        app.contentResolver.registerContentObserver(
            android.provider.Settings.System.getUriFor(ThorRgbController.PICKER_COLOR_KEY), false, ledColorObserver,
        )
        app.registerReceiver(bdsaPackageReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(bdsaPackageReceiver) }
        app.contentResolver.unregisterContentObserver(ledColorObserver)
        super.onCleared()
    }

    /**
     * Re-detects BDSA (companion resume, "Check again") and tells the service to re-check too. [keepMessage] keeps the
     * result of the action that triggered it; otherwise a message about the previous state is dropped once it changes.
     */
    fun refreshBdsa(keepMessage: Boolean = false) {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { bdsa.detect(force = true) }
            // A BDSA we disabled that's been re-enabled or removed some other way no longer needs the offer.
            val ours = prefs.getString(KEY_BDSA_DISABLED, null)?.takeIf { status.packageName == it && !status.active }
            if (ours == null) prefs.edit().remove(KEY_BDSA_DISABLED).apply()
            // A message about the previous state ("Re-enabled…") is stale once the state changes.
            val message = _state.value.bdsaMessage.takeIf { keepMessage || _state.value.bdsa.presence == status.presence }
            _state.value = _state.value.copy(bdsa = status, bdsaDisabledByUs = ours, bdsaMessage = message)
            recheckService()
        }
    }

    fun openBdsa() {
        val pkg = _state.value.bdsa.packageName ?: return
        app.packageManager.getLaunchIntentForPackage(pkg)?.let {
            runCatching { app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Disables BDSA via root; its helper restores the stock assistant and screens, then Command Panel takes over. */
    fun disableBdsa() {
        val pkg = _state.value.bdsa.packageName ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { bdsa.disable(pkg) }
            if (ok) prefs.edit().putString(KEY_BDSA_DISABLED, pkg).apply()
            _state.value = _state.value.copy(bdsaMessage = app.getString(if (ok) R.string.msg_bdsa_disabled else R.string.msg_bdsa_disable_failed))
            refreshBdsa(keepMessage = true)
        }
    }

    fun reenableBdsa() {
        val pkg = _state.value.bdsaDisabledByUs ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { bdsa.enable(pkg) }
            if (ok) prefs.edit().remove(KEY_BDSA_DISABLED).apply()
            _state.value = _state.value.copy(bdsaMessage = app.getString(if (ok) R.string.msg_bdsa_enabled else R.string.msg_bdsa_enable_failed))
            refreshBdsa(keepMessage = true)
        }
    }

    private fun recheckService() {
        runCatching {
            app.startService(Intent(app, AssistantService::class.java).setAction(AssistantService.ACTION_RECHECK_COEXISTENCE))
        }
    }

    fun setReplaceStock(enable: Boolean) {
        AssistantService.start(getApplication())
        getApplication<Application>().startService(
            Intent(getApplication(), AssistantService::class.java)
                .setAction(AssistantService.ACTION_SET_REPLACE_STOCK)
                .putExtra(AssistantService.EXTRA_ENABLE, enable)
        )
        _state.value = _state.value.copy(replaceStock = enable)
    }

    fun setThemeMode(mode: Int) {
        settings.putInt(SystemSettings.Keys.THEME_MODE, mode)
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun showOverlay() {
        AssistantService.start(getApplication())
        getApplication<Application>().startService(
            Intent(getApplication(), AssistantService::class.java).setAction(AssistantService.ACTION_SHOW_OVERLAY)
        )
    }

    fun runDiagnostics() {
        if (_state.value.diagnosticsRunning) return
        _state.value = _state.value.copy(diagnosticsRunning = true, diagnostics = emptyList())
        viewModelScope.launch {
            val r = privileged.initialize(getApplication())
            _state.value = _state.value.copy(
                diagnosticsRunning = false,
                diagnostics = buildList {
                    add(app.getString(R.string.diag_hidden_api) to r.hiddenApi)
                    add(app.getString(R.string.diag_root_shell) to r.rootShell)
                    add(app.getString(R.string.diag_helper) to r.helper)
                    r.helperIdentity?.let { add(it to true) }
                },
            )
        }
    }

    // ---- in-app updates ----

    private val updatePrefs = jr.brian.commandpannel.system.update.UpdatePrefs(app)
    /** This build's version without the `-debug` suffix. */
    private val currentVersion = jr.brian.commandpannel.BuildConfig.VERSION_NAME.substringBefore('-')
    /** The APK downloaded for the open update, kept for the Android-installer fallback. */
    private var downloadedApk: java.io.File? = null

    init {
        _state.value = _state.value.copy(updateLastCheck = updatePrefs.lastCheck)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { jr.brian.commandpannel.system.update.UpdateInstaller.cleanup(app) }
            showWhatsNewIfUpdated()
            // The tour shows once, after any What's New dialog; the automatic update check waits until it's done.
            if (!prefs.getBoolean(KEY_TOUR_SEEN, false)) {
                if (_state.value.updateDialog == null) startTour() else tourPending = true
                return@launch
            }
            autoCheckForUpdates()
        }
    }

    /** Automatic checks are for release builds; a debug build is a separate package a release can't update. */
    private fun autoCheckForUpdates() {
        if (!jr.brian.commandpannel.BuildConfig.DEBUG && updatePrefs.isAutoCheckDue() && _state.value.updateDialog == null) {
            checkForUpdates(manual = false)
        }
    }

    /** Checks GitHub for a newer release. Automatic checks are silent unless there's an update that wasn't skipped. */
    fun checkForUpdates(manual: Boolean = true) {
        if (_state.value.updateChecking) return
        _state.value = _state.value.copy(updateChecking = true, updateMessage = if (manual) null else _state.value.updateMessage)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { jr.brian.commandpannel.system.update.UpdateChecker.check(currentVersion) }
            updatePrefs.lastCheck = System.currentTimeMillis()
            var dialog = _state.value.updateDialog
            val message = when (result) {
                is jr.brian.commandpannel.system.update.UpdateCheck.Available -> {
                    if (manual || updatePrefs.skippedVersion != result.release.version) dialog = UpdatePhase.Offer(result.release)
                    app.getString(R.string.msg_update_available, result.release.version)
                }
                jr.brian.commandpannel.system.update.UpdateCheck.UpToDate -> app.getString(R.string.msg_update_up_to_date, currentVersion)
                is jr.brian.commandpannel.system.update.UpdateCheck.Failed -> app.getString(R.string.msg_update_failed, result.reason)
            }
            _state.value = _state.value.copy(
                updateChecking = false,
                updateDialog = dialog,
                updateMessage = if (manual) message else _state.value.updateMessage,
                updateLastCheck = updatePrefs.lastCheck,
            )
        }
    }

    /**
     * Downloads the offered release and installs it: silently through the root shell (Command Panel then restarts),
     * or with Android's installer when root isn't available. On debug builds a release without an APK installs the
     * local test APK instead (see [previewUpdate]).
     */
    fun downloadAndInstall() {
        val release = when (val d = _state.value.updateDialog) {
            is UpdatePhase.Offer -> d.release
            is UpdatePhase.Failed -> d.release
            else -> return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(updateDialog = UpdatePhase.Downloading(release, 0f))
            val testApk = jr.brian.commandpannel.system.update.UpdateInstaller.testApk(app)
            val useTest = release.apkUrl == null && jr.brian.commandpannel.BuildConfig.DEBUG && testApk.exists()
            val apk = withContext(Dispatchers.IO) {
                runCatching {
                    val file = if (useTest) testApk else jr.brian.commandpannel.system.update.UpdateInstaller.download(app, release) { p ->
                        _state.value = _state.value.copy(updateDialog = UpdatePhase.Downloading(release, p))
                    }
                    jr.brian.commandpannel.system.update.UpdateInstaller.verify(app, file, if (useTest) null else release.version)
                    file
                }
            }.getOrElse { e ->
                val reason = if (release.apkUrl == null) app.getString(R.string.msg_update_no_apk) else (e.message ?: e.javaClass.simpleName)
                _state.value = _state.value.copy(updateDialog = UpdatePhase.Failed(release, app.getString(R.string.msg_update_download_failed, reason), false))
                return@launch
            }
            downloadedApk = apk
            _state.value = _state.value.copy(updateDialog = UpdatePhase.Installing(release))
            val started = withContext(Dispatchers.IO) { jr.brian.commandpannel.system.update.UpdateInstaller.installWithRoot(app, privileged, apk) }
            if (!started) {
                installWithSystemInstaller()
                return@launch
            }
            // A successful install replaces (and kills) this app. Still running a while later means it failed.
            kotlinx.coroutines.delay(45_000)
            val log = withContext(Dispatchers.IO) { jr.brian.commandpannel.system.update.UpdateInstaller.installLog(privileged) }
            val detail = log?.lines()?.lastOrNull { it.isNotBlank() } ?: app.getString(R.string.msg_update_no_log)
            _state.value = _state.value.copy(
                updateDialog = UpdatePhase.Failed(release, app.getString(R.string.msg_update_install_failed, detail), true),
            )
        }
    }

    /** Fallback: hands the downloaded APK to Android's installer (asking for the "install unknown apps" permission first). */
    fun installWithSystemInstaller() {
        val apk = downloadedApk ?: return
        val release = when (val d = _state.value.updateDialog) {
            is UpdatePhase.Failed -> d.release
            is UpdatePhase.Installing -> d.release
            is UpdatePhase.Offer -> d.release
            else -> return
        }
        if (!jr.brian.commandpannel.system.update.UpdateInstaller.canUseSystemInstaller(app)) {
            runCatching { app.startActivity(jr.brian.commandpannel.system.update.UpdateInstaller.installerPermissionIntent(app)) }
            _state.value = _state.value.copy(
                updateDialog = UpdatePhase.Failed(release, app.getString(R.string.msg_update_allow_installs), true),
            )
            return
        }
        runCatching { app.startActivity(jr.brian.commandpannel.system.update.UpdateInstaller.systemInstallerIntent(app, apk)) }
            .onSuccess { _state.value = _state.value.copy(updateDialog = null, updateMessage = app.getString(R.string.msg_update_finish_in_installer)) }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    updateDialog = UpdatePhase.Failed(release, app.getString(R.string.msg_update_install_failed, e.message ?: ""), false),
                )
            }
    }

    fun skipUpdate() {
        (_state.value.updateDialog as? UpdatePhase.Offer)?.let { updatePrefs.skippedVersion = it.release.version }
        closeUpdateDialog()
    }

    /** Closes the dialog ("Later" / "Close" / "Done"); a What's New dialog counts as seen. */
    fun dismissUpdate() {
        if (_state.value.updateDialog is UpdatePhase.WhatsNew) updatePrefs.whatsNewSeen = currentVersion
        closeUpdateDialog()
    }

    private fun closeUpdateDialog() {
        _state.value = _state.value.copy(updateDialog = null)
        if (tourPending) {
            tourPending = false
            startTour()
        }
    }

    // ---- companion tour ----

    /** The first-run tour is waiting for the What's New dialog to close. */
    private var tourPending = false

    /** Starts the companion tour from its first step (also General → Updates → "Take the tour"). */
    fun startTour() {
        // The Nav Bar section's cards are hidden while its app picker is open.
        _state.value = _state.value.copy(tourStep = 0, appPickerOpen = false)
    }

    fun tourNext() {
        val step = _state.value.tourStep ?: return
        if (step >= TOUR_STEPS.lastIndex) endTour() else _state.value = _state.value.copy(tourStep = step + 1)
    }

    fun tourBack() {
        val step = _state.value.tourStep ?: return
        if (step > 0) _state.value = _state.value.copy(tourStep = step - 1)
    }

    /** Finishes or skips the tour; either way it doesn't start on its own again. */
    fun endTour() {
        if (_state.value.tourStep == null) return
        val firstRun = !prefs.getBoolean(KEY_TOUR_SEEN, false)
        prefs.edit().putBoolean(KEY_TOUR_SEEN, true).apply()
        _state.value = _state.value.copy(tourStep = null)
        if (firstRun) autoCheckForUpdates()
    }

    fun openReleasePage() {
        val url = when (val d = _state.value.updateDialog) {
            is UpdatePhase.Offer -> d.release.pageUrl
            is UpdatePhase.Failed -> d.release.pageUrl
            else -> null
        }?.takeIf { it.startsWith("https://") } ?: return
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Opens this version's patch notes (the Updates card's "What's new"). */
    fun showWhatsNew() {
        viewModelScope.launch {
            val notes = withContext(Dispatchers.IO) { bundledNotes() } ?: return@launch
            _state.value = _state.value.copy(updateDialog = UpdatePhase.WhatsNew(currentVersion, notes))
        }
    }

    /**
     * Shows the patch notes once after an update. The first install (and a downgrade) only records the version. Notes come from the
     * bundled patch_notes.md when it's for this version, otherwise from this version's GitHub release.
     */
    private suspend fun showWhatsNewIfUpdated() {
        val seen = updatePrefs.whatsNewSeen
        if (seen == null) {
            updatePrefs.whatsNewSeen = currentVersion
            return
        }
        if (seen == currentVersion) return
        // Only after an upgrade; a downgrade just records the version.
        if (!jr.brian.commandpannel.system.update.UpdateChecker.isNewer(currentVersion, seen)) {
            updatePrefs.whatsNewSeen = currentVersion
            return
        }
        val notes = withContext(Dispatchers.IO) {
            bundledNotes()?.takeIf { jr.brian.commandpannel.system.update.UpdateChecker.notesMatch(it, currentVersion) }
                ?: jr.brian.commandpannel.system.update.UpdateChecker.releaseNotes(currentVersion)
        }
        if (notes.isNullOrBlank()) updatePrefs.whatsNewSeen = currentVersion
        else _state.value = _state.value.copy(updateDialog = UpdatePhase.WhatsNew(currentVersion, notes))
    }

    private fun bundledNotes(): String? =
        runCatching { app.resources.openRawResource(R.raw.patch_notes).bufferedReader().use { it.readText() } }.getOrNull()

    /** Debug builds only: opens the update dialog with this build's notes, to try the dialog and the install path. */
    fun previewUpdate() {
        if (!jr.brian.commandpannel.BuildConfig.DEBUG) return
        viewModelScope.launch {
            val notes = withContext(Dispatchers.IO) { bundledNotes() }.orEmpty()
            val release = jr.brian.commandpannel.system.update.ReleaseInfo(
                version = "9.9.9", notes = notes, pageUrl = "https://github.com/BrianJr03/command-panel/releases",
                apkUrl = null, apkName = null, apkSize = 0L,
            )
            _state.value = _state.value.copy(updateDialog = UpdatePhase.Offer(release))
        }
    }

    private companion object {
        /** The BDSA package Command Panel disabled, so the companion can offer to re-enable it. Not backed up. */
        const val KEY_BDSA_DISABLED = "bdsa_disabled_by_command_panel"
        /** The companion tour was finished or skipped. Not backed up, so a restored backup still gets the tour. */
        const val KEY_TOUR_SEEN = "companion_tour_seen"
    }
}
