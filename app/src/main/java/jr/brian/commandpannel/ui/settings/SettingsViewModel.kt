package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.system.BackgroundImage
import jr.brian.commandpannel.system.BackgroundImageSetting
import jr.brian.commandpannel.system.BackgroundImageStore
import jr.brian.commandpannel.system.BackgroundImportException
import jr.brian.commandpannel.ui.overlay.OverlayOnboarding
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
import jr.brian.commandpannel.system.QuickLaunchAfter
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The companion's sections, in their default rail order; [key] is what the saved order (`rail_order`) stores. */
enum class SettingsCategory(val key: String, @StringRes val titleRes: Int) {
    GENERAL("general", R.string.settings_cat_general),
    NAV_BAR("nav_bar", R.string.settings_cat_nav_bar),
    QUICK_TOGGLES("tiles", R.string.settings_cat_quick_toggles),
    APPEARANCE("appearance", R.string.settings_cat_appearance),
    THEMES("themes", R.string.settings_cat_themes),
    REPLAY("replay", R.string.settings_cat_replay),
    KEYBOARD("keyboard", R.string.virtual_keyboard_text),
    DIAGNOSTICS("diagnostics", R.string.settings_cat_diagnostics),
    EXPERIMENTAL("experimental", R.string.settings_cat_experimental);

    companion object {
        fun fromKey(key: String): SettingsCategory? = entries.firstOrNull { it.key == key }

        /**
         * The rail order saved as a CSV of keys. Unknown keys and repeats are dropped; sections missing from it (new
         * ones, or an older saved order) go in at their default position relative to the saved ones.
         */
        fun ordered(csv: String?): List<SettingsCategory> = tolerantOrder(csv, entries, ::fromKey)
    }
}

data class SettingsState(
    val replaceStock: Boolean = false,
    val batteryEstimate: Boolean = false,
    /** Experimental: per-game FPS from the root helper instead of the compositor's count (#4). */
    val taskFps: Boolean = false,
    val hideNetSpeed: Boolean = false,
    val hideTileLabels: Boolean = false,
    val keepOnScreen: Boolean = true,
    /** The "new" corner dots and rail bubbles (#54). Off hides them without marking anything seen. */
    val showNewDots: Boolean = true,
    val backupMessage: String? = null,
    val themeMode: Int = 4,              // tcc_theme_mode
    val rgbAccentSync: Boolean = false,
    /** The panel's background image for every look (Appearance, #61), or null. */
    val backgroundImage: BackgroundImage? = null,
    /** Experimental (#61): GIF and animated WebP backgrounds play; off, they're refused on import and show still. */
    val animatedBackgrounds: Boolean = false,
    /** The last background image change's outcome ("Background image set", or why it was refused). */
    val backgroundMessage: String? = null,
    /** Saved gradient backgrounds (#64), for Appearance's card and the gradient library (#67). */
    val gradients: List<jr.brian.commandpannel.system.GradientSpec> = emptyList(),
    /** The panel's Background tile (#58, #72): off hides whatever background is set (#78 says so in settings). */
    val backgroundTileOn: Boolean = true,
    /** The gradient the panel shows (the active background source), or null when it's the image or the backdrop. */
    val activeGradientId: String? = null,
    /** The last gradient library action's outcome ("Gradient applied", …), shown in the library. */
    val gradientMessage: String? = null,
    /** Ambient-LED accent (opaque ARGB) when [rgbAccentSync] is on; themes the companion when it follows the look. */
    val accentOverride: Int? = null,
    /** When on, the companion screen uses the overlay's look, theme mode and accent instead of its default style. */
    val companionFollowLook: Boolean = false,
    val enabledTiles: Set<String> = emptySet(),
    val orderedEnabledTiles: List<String> = emptyList(),
    val quickLaunchLabel: String? = null,
    val quickLaunchDisplay: QuickLaunchDisplay = QuickLaunchDisplay.TOP,
    val quickLaunchAfter: QuickLaunchAfter = QuickLaunchAfter.CLOSE,
    /** Show the Quick Launch app's icon and name on its nav button instead of the stock glyph and "Launch". */
    val quickLaunchShowApp: Boolean = true,
    val quickLaunchComponent: ComponentName? = null,
    /** Nav bar button keys in display order. */
    val navOrder: List<String> = jr.brian.commandpannel.system.NavBarButton.entries.map { it.key },
    val overlayLayout: jr.brian.commandpannel.system.OverlayLayout = jr.brian.commandpannel.system.OverlayLayout.DEFAULT,
    /** The base look of the active selection (the look itself for built-ins). */
    val overlayLook: jr.brian.commandpannel.ui.theme.TccUiLook = jr.brian.commandpannel.ui.theme.TccUiLook.DEFAULT,
    /** What `overlay_look` points at: a built-in look or a saved Theme Studio look. */
    val overlayLookRef: LookRef = LookRef.BuiltIn(jr.brian.commandpannel.ui.theme.TccUiLook.DEFAULT),
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
    /** The other build's (debug / release) name while it replaces the stock assistant and this one stands by, or null. */
    val siblingOwner: String? = null,
    /** The update / What's New dialog, or null when closed. */
    val updateDialog: UpdatePhase? = null,
    /** A check is running (manual button shows busy). */
    val updateChecking: Boolean = false,
    /** Result of the last manual check, shown in the Updates card. */
    val updateMessage: String? = null,
    /** When updates were last checked (ms), 0 if never. */
    val updateLastCheck: Long = 0L,
    /** The companion rail's groups and their sections, in the user's order (sections move with their handles). */
    val railLayout: RailLayout = RailLayout.DEFAULT,
    /** The rail groups that are open. */
    val railExpanded: Set<RailGroup> = emptySet(),
    /** General's "One rail group open at a time" (#82): opening a group closes the others. */
    val railOneOpen: Boolean = true,
    /** Which "new" bubbles show on the rail and in the panes. */
    val newBadges: NewBadgeState = NewBadgeState(),
    /** TCP Save Clip (the Replay Buffer shortcut): installed, and paired with this build. */
    val saveClip: jr.brian.commandpannel.system.update.SaveClipStatus = jr.brian.commandpannel.system.update.SaveClipStatus.Missing,
    /** A TCP Save Clip download or install is running. */
    val saveClipBusy: Boolean = false,
    /** Result of the last TCP Save Clip install, shown in its card. */
    val saveClipMessage: String? = null,
    /** ThorKBM (the keyboard and mouse app): which builds are installed. */
    val thorKbm: jr.brian.commandpannel.system.update.ThorKbmStatus = jr.brian.commandpannel.system.update.ThorKbmStatus(),
    /** A ThorKBM download or install is running. */
    val thorKbmBusy: Boolean = false,
    /** Result of the last ThorKBM install, shown in its card. */
    val thorKbmMessage: String? = null,
    /** Keys of the listed ThorKBM Panel plugins (plugin mode); empty when none is. */
    val thorKbmPluginKeys: List<String> = emptyList(),
    /** Plugins whose nav bar button is hidden ([jr.brian.commandpannel.plugin.PluginNavPrefs]). */
    val navHiddenPlugins: Set<String> = emptySet(),
    val replayEnabled: Boolean = false,
    val replayWindowSeconds: Int = 30,
    val replayPreset: jr.brian.commandpannel.privileged.ReplayBitratePreset = jr.brian.commandpannel.privileged.ReplayBitratePreset.BALANCED,
    val replayMessage: String? = null,
    /** Why the last folder picked for clips was refused (#74), shown under Save location; null after a good pick or Default. */
    val replayFolderMessage: String? = null,
    val replayTarget: jr.brian.commandpannel.privileged.ReplayTarget = jr.brian.commandpannel.privileged.ReplayTarget.DEFAULT,
    val replayDoublePress: Boolean = false,
    val replayGameAudio: Boolean = true,
    val replayMic: Boolean = false,
    /** The root helper (which records the screen and sound) isn't connected or connecting. */
    val replayHelperDown: Boolean = false,
    /** The armed capture asked for sound but couldn't start it, so clips are video only. */
    val replaySoundVideoOnly: Boolean = false,
    /** Custom clips folder (internal storage or, since #75, an SD card or USB drive), or `null` for TCP Clips. */
    val replayClipsLocation: jr.brian.commandpannel.privileged.ReplayClipsLocation? = null,
    /** The "Saving to" line under Save location: where clips go, with the card's name for a removable volume. */
    val replayLocationLine: String = "",
    /** The chosen folder's card isn't mounted, so clips go to TCP Clips until it's back (#75). */
    val replayClipsCardMissing: Boolean = false,
    /** "Auto-delete old clips" in the Replay Buffer section (#50). */
    val replayAutoDelete: jr.brian.commandpannel.replay.studio.AutoDeleteRules = jr.brian.commandpannel.replay.studio.AutoDeleteRules(),
    val appPickerOpen: Boolean = false,
    val apps: List<QuickLaunchApp> = emptyList(),
    val diagnostics: List<Pair<String, Boolean?>> = emptyList(),
    val diagnosticsRunning: Boolean = false,
    /** The companion tour's current step (an index into [tourStepsFor] of [tourWhatsNew]), or null when it isn't running. */
    val tourStep: Int? = null,
    /**
     * The version whose what's-new tour is running (or last ran), or null for the full tour. Kept after the tour ends
     * so its last step stays on screen while the layer fades out.
     */
    val tourWhatsNew: String? = null,
    /** The look Theme Studio is editing (possibly not saved yet), or null when it isn't open. */
    val studioDraft: jr.brian.commandpannel.ui.theme.CustomLook? = null,
    /** Studio was opened on [unsavedDraft] (Resume), so it starts with unsaved changes. */
    val studioResumed: Boolean = false,
    /** Unsaved Studio work kept from a session that didn't end normally (the app was closed or killed). */
    val unsavedDraft: jr.brian.commandpannel.ui.theme.CustomLook? = null,
    /** The gradient Gradient Studio is editing (#68, possibly not saved yet), or null when it isn't open. */
    val gradientDraft: jr.brian.commandpannel.system.GradientSpec? = null,
    /** Gradient Studio was opened on [unsavedGradientDraft] (Resume), so it starts with unsaved changes. */
    val gradientDraftResumed: Boolean = false,
    /** Unsaved Gradient Studio work kept from a session that didn't end normally. */
    val unsavedGradientDraft: jr.brian.commandpannel.system.GradientSpec? = null,
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
        taskFps = prefs.getBoolean(jr.brian.commandpannel.system.TelemetryMonitor.PREF_TASK_FPS, false),
        hideNetSpeed = prefs.getBoolean("hide_net_speed", false),
        hideTileLabels = prefs.getBoolean("qs_hide_labels", false),
        keepOnScreen = prefs.getBoolean("clear_tasks_keep_on_screen", true),
        showNewDots = prefs.getBoolean(KEY_SHOW_NEW_DOTS, true),
        themeMode = settings.getInt(SystemSettings.Keys.THEME_MODE, 4),
        rgbAccentSync = prefs.getBoolean("rgb_accent_sync", false),
        backgroundImage = BackgroundImageSetting.read(prefs),
        animatedBackgrounds = BackgroundImageSetting.animationsOn(prefs),
        gradients = jr.brian.commandpannel.system.GradientStore.all(prefs),
        activeGradientId = jr.brian.commandpannel.system.BackgroundSetting.activeGradientId(prefs),
        backgroundTileOn = prefs.getBoolean(jr.brian.commandpannel.system.BackgroundImageTile.PREF, true),
        unsavedGradientDraft = jr.brian.commandpannel.system.GradientJson.decode(prefs.getString(KEY_GRADIENT_DRAFT, null)),
        accentOverride = ThorRgbController.accentOverride(app, prefs.getBoolean("rgb_accent_sync", false)),
        companionFollowLook = prefs.getBoolean("companion_follow_look", false),
        enabledTiles = jr.brian.commandpannel.system.QuickSettingTileId
            .enabledKeys(prefs.getString("qs_enabled_tiles", null)).toSet(),
        orderedEnabledTiles = jr.brian.commandpannel.system.QuickSettingTileId
            .enabledKeys(prefs.getString("qs_enabled_tiles", null), prefs.getString("qs_tile_order", null)),
        quickLaunchLabel = QuickLaunch.savedLabel(getApplication()),
        quickLaunchDisplay = QuickLaunch.savedDisplay(getApplication()),
        quickLaunchAfter = QuickLaunch.savedAfter(getApplication()),
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
        replayTarget = jr.brian.commandpannel.privileged.ReplayTarget.fromKey(prefs.getString("replay_target", null)),
        replayDoublePress = prefs.getBoolean(AssistantService.PREF_DOUBLE_PRESS_REPLAY, false),
        replayGameAudio = prefs.getBoolean(jr.brian.commandpannel.system.ReplayBufferController.PREF_GAME_AUDIO, true),
        replayMic = prefs.getBoolean(jr.brian.commandpannel.system.ReplayBufferController.PREF_MIC, false),
        replayClipsLocation = readReplayClipsLocation(),
        replayLocationLine = replayLocationLine(readReplayClipsLocation()),
        replayClipsCardMissing = replayCardMissing(readReplayClipsLocation()),
        replayAutoDelete = jr.brian.commandpannel.replay.studio.AutoDeleteRules.read(prefs),
        saveClip = jr.brian.commandpannel.system.update.SaveClipInstaller.status(getApplication()),
        thorKbm = jr.brian.commandpannel.system.update.ThorKbmInstaller.status(getApplication()),
        thorKbmPluginKeys = thorKbmPluginKeys(),
        navHiddenPlugins = jr.brian.commandpannel.plugin.PluginNavPrefs.parse(
            prefs.getString(jr.brian.commandpannel.plugin.PluginNavPrefs.KEY, null),
        ),
        railLayout = RailLayout.read { prefs.getString(it, null) },
        railExpanded = RailLayout.read { prefs.getString(it, null) }.let { layout ->
            RailExpanded.limit(RailExpanded.parse(prefs.getString(RailExpanded.KEY, null)), railOneOpen(), layout.groups)
        },
        railOneOpen = railOneOpen(),
        newBadges = loadNewBadges(),
    )

    /** Auto-delete rules are read on each save and when Replay Studio opens, so saving them is enough. */
    fun setReplayAutoDelete(rules: jr.brian.commandpannel.replay.studio.AutoDeleteRules) {
        rules.save(prefs)
        _state.value = _state.value.copy(replayAutoDelete = rules)
    }

    /** The service reads this pref on each AYN press, so saving it is enough. */
    fun setReplayDoublePress(enable: Boolean) {
        prefs.edit().putBoolean(AssistantService.PREF_DOUBLE_PRESS_REPLAY, enable).apply()
        _state.value = _state.value.copy(replayDoublePress = enable)
    }

    fun setReplayEnabled(enable: Boolean) {
        prefs.edit().putBoolean("replay_enabled", enable).apply()
        AssistantService.start(app)
        app.sendBroadcast(
            Intent(AssistantService.ACTION_REPLAY_SET_ENABLED).setPackage(app.packageName)
                .putExtra(AssistantService.EXTRA_ENABLE, enable),
        )
        _state.value = _state.value.copy(replayEnabled = enable, replayMessage = app.getString(if (enable) R.string.msg_replay_arming else R.string.msg_replay_off))
    }

    /**
     * After a capture setting changes: asks the service to restart a running buffer with it, and returns the message
     * to show meanwhile (replaced by the outcome when the service answers). An off buffer just picks it up next arm.
     */
    private fun replaySettingMessage(): String {
        if (!_state.value.replayEnabled) return app.getString(R.string.msg_replay_next_arm)
        app.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) {
                val armed = i.getBooleanExtra(AssistantService.EXTRA_REPLAY_ARMED, false)
                val msg = app.getString(if (armed) R.string.msg_replay_rearmed else R.string.msg_replay_rearm_failed)
                _state.value = _state.value.copy(replayMessage = msg)
                runCatching { c.unregisterReceiver(this) }
            }
        }, android.content.IntentFilter(AssistantService.ACTION_REPLAY_REARMED), android.content.Context.RECEIVER_NOT_EXPORTED)
        app.sendBroadcast(Intent(AssistantService.ACTION_REPLAY_REARM).setPackage(app.packageName))
        return app.getString(R.string.msg_replay_rearming)
    }

    fun setReplayWindowSeconds(seconds: Int) {
        prefs.edit().putInt("replay_window_seconds", seconds).apply()
        _state.value = _state.value.copy(replayWindowSeconds = seconds, replayMessage = replaySettingMessage())
    }

    fun setReplayPreset(preset: jr.brian.commandpannel.privileged.ReplayBitratePreset) {
        prefs.edit().putString("replay_bitrate", preset.key).apply()
        _state.value = _state.value.copy(replayPreset = preset, replayMessage = replaySettingMessage())
    }

    fun setReplayGameAudio(enable: Boolean) {
        prefs.edit().putBoolean(jr.brian.commandpannel.system.ReplayBufferController.PREF_GAME_AUDIO, enable).apply()
        _state.value = _state.value.copy(replayGameAudio = enable, replayMessage = replaySettingMessage())
    }

    fun setReplayMic(enable: Boolean) {
        prefs.edit().putBoolean(jr.brian.commandpannel.system.ReplayBufferController.PREF_MIC, enable).apply()
        _state.value = _state.value.copy(replayMic = enable, replayMessage = replaySettingMessage())
    }

    fun setReplayTarget(target: jr.brian.commandpannel.privileged.ReplayTarget) {
        prefs.edit().putString("replay_target", target.key).apply()
        _state.value = _state.value.copy(replayTarget = target, replayMessage = replaySettingMessage())
    }

    private fun readReplayClipsLocation(): jr.brian.commandpannel.privileged.ReplayClipsLocation? =
        jr.brian.commandpannel.privileged.ReplayClipsLocation.parse(prefs.getString(jr.brian.commandpannel.replay.studio.ClipRepository.PREF_CLIPS_DIR, null))

    /** Whether [location] is on a removable volume that isn't mounted and writable now. */
    private fun replayCardMissing(location: jr.brian.commandpannel.privileged.ReplayClipsLocation?): Boolean =
        location?.volume?.let { jr.brian.commandpannel.system.ReplayStorage.state(app, it) != jr.brian.commandpannel.privileged.ReplayVolumes.State.MOUNTED } == true

    /** "Saving to ..." for [location]: the full path on internal storage, the folder and the card's name on a card. */
    private fun replayLocationLine(location: jr.brian.commandpannel.privileged.ReplayClipsLocation?): String {
        val loc = location ?: jr.brian.commandpannel.privileged.ReplayClipsLocation.DEFAULT
        val volume = loc.volume ?: return app.getString(R.string.settings_replay_location_current, loc.path)
        val card = jr.brian.commandpannel.system.ReplayStorage.description(app, volume)
            ?: app.getString(R.string.settings_replay_location_card_fallback, volume)
        return app.getString(R.string.settings_replay_location_current_card, loc.relativeDir, card)
    }

    /** The Save location state for [location] (the folder, its "Saving to" line, whether its card is missing). */
    private fun withReplayLocation(state: SettingsState, location: jr.brian.commandpannel.privileged.ReplayClipsLocation?) =
        state.copy(
            replayClipsLocation = location,
            replayLocationLine = replayLocationLine(location),
            replayClipsCardMissing = replayCardMissing(location),
        )

    /** Saves clips to [location], or back to the default folder (TCP Clips) when it's `null`. */
    fun setReplayClipsLocation(location: jr.brian.commandpannel.privileged.ReplayClipsLocation?) {
        prefs.edit().apply {
            if (location == null) remove(jr.brian.commandpannel.replay.studio.ClipRepository.PREF_CLIPS_DIR) else putString(jr.brian.commandpannel.replay.studio.ClipRepository.PREF_CLIPS_DIR, location.encode())
        }.apply()
        val next = withReplayLocation(_state.value, location)
        _state.value = next.copy(replayFolderMessage = null, replayMessage = next.replayLocationLine)
    }

    /**
     * Opens the clips folder in the system file manager, creating it first if no clip has been saved yet. A custom folder
     * deleted outside the app falls back to TCP Clips, the same as saving does; one on a card that isn't mounted (#75)
     * opens TCP Clips, where clips go meanwhile, and stays chosen.
     */
    fun openReplayClipsFolder() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val volumes = jr.brian.commandpannel.privileged.ReplayVolumes
            val custom = _state.value.replayClipsLocation
            val resolved = jr.brian.commandpannel.system.ReplayStorage.resolve(app, custom)
            if (custom != null && !resolved.fellBack &&
                privileged.shell("[ -d ${volumes.shellQuote(custom.path)} ] && echo yes || echo no")?.trim() == "no"
            ) {
                prefs.edit().remove(jr.brian.commandpannel.replay.studio.ClipRepository.PREF_CLIPS_DIR).apply()
                _state.value = withReplayLocation(_state.value, null)
            }
            val loc = if (resolved.fellBack) resolved.location
                else _state.value.replayClipsLocation ?: jr.brian.commandpannel.privileged.ReplayClipsLocation.DEFAULT
            privileged.shell(volumes.mkdirsCommand(loc.root, loc.path))
            val docVolume = loc.volume ?: volumes.PRIMARY
            val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "$docVolume:${loc.relativeDir}")
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                // No URI grant flag: the app holds no permission on this document, so asking to pass one on throws.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Prefer the system Files app: other file managers (Super File) can't read a storage document link and
            // report an invalid path. Without Files, let Android offer whatever can open it.
            val files = listOf("com.android.documentsui", "com.google.android.documentsui").firstOrNull {
                app.packageManager.resolveActivity(Intent(intent).setPackage(it), 0) != null
            }
            if (files != null) intent.setPackage(files)
            runCatching { app.startActivity(intent) }.onFailure {
                android.util.Log.w("SettingsViewModel", "couldn't open the clips folder", it)
                _state.value = _state.value.copy(replayMessage = app.getString(R.string.msg_replay_folder_open_failed))
            }
        }
    }

    /**
     * A folder picked in the system folder picker. Internal storage and (#75) a mounted, writable SD card or USB drive
     * are accepted; anything else is refused with a message shown under Save location (#74).
     */
    fun onReplayFolderPicked(uri: android.net.Uri) {
        val volumes = jr.brian.commandpannel.privileged.ReplayVolumes
        val docId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        // Only the system's storage provider names real volumes (Downloads, Drive and others use their own ids).
        val storage = uri.authority == "com.android.externalstorage.documents"
        val pick = if (docId != null && storage) volumes.fromTreeDocId(docId) else jr.brian.commandpannel.privileged.ReplayVolumes.Pick.Invalid
        val location = (pick as? jr.brian.commandpannel.privileged.ReplayVolumes.Pick.Ok)?.location
        val why = when {
            location == null -> R.string.msg_replay_folder_invalid
            location.volume == null -> null
            else -> when (jr.brian.commandpannel.system.ReplayStorage.state(app, location.volume)) {
                jr.brian.commandpannel.privileged.ReplayVolumes.State.MOUNTED -> null
                jr.brian.commandpannel.privileged.ReplayVolumes.State.READ_ONLY -> R.string.msg_replay_folder_card_read_only
                jr.brian.commandpannel.privileged.ReplayVolumes.State.MISSING -> R.string.msg_replay_folder_card_missing
            }
        }
        if (location == null || why != null) {
            _state.value = _state.value.copy(replayFolderMessage = app.getString(why ?: R.string.msg_replay_folder_invalid))
        } else {
            setReplayClipsLocation(location)
        }
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
                // The service drops a custom folder that was deleted, so re-read it to show where clips go now.
                _state.value = withReplayLocation(_state.value, readReplayClipsLocation()).copy(replayMessage = msg)
                runCatching { c.unregisterReceiver(this) }
            }
        }, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
    }

    fun setLook(look: jr.brian.commandpannel.ui.theme.TccUiLook) = selectLook(LookRef.BuiltIn(look))

    /** Hands Theme Studio the look to edit; a new one (Mix, Customize, Start from) isn't saved until the user saves. */
    fun openStudio(look: jr.brian.commandpannel.ui.theme.CustomLook) {
        _state.value = _state.value.copy(studioDraft = look, studioResumed = look == _state.value.unsavedDraft)
    }

    /** Keeps Studio's unsaved [look] (or forgets it, for null) in case the app closes before it's saved. */
    fun persistStudioDraft(look: jr.brian.commandpannel.ui.theme.CustomLook?) {
        if (look == null) prefs.edit().remove(KEY_STUDIO_DRAFT).apply()
        else prefs.edit().putString(KEY_STUDIO_DRAFT, jr.brian.commandpannel.ui.theme.CustomLookJson.encode(look).toString()).apply()
        _state.value = _state.value.copy(unsavedDraft = look)
    }

    private fun readStudioDraft(): jr.brian.commandpannel.ui.theme.CustomLook? = prefs.getString(KEY_STUDIO_DRAFT, null)?.let {
        runCatching { jr.brian.commandpannel.ui.theme.CustomLookJson.decode(org.json.JSONObject(it)) }.getOrNull()
    }

    fun closeStudio() {
        _state.value = _state.value.copy(studioDraft = null)
    }

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

    /**
     * Sets the panel's background image (Appearance, #61) from one picked with the system file picker, for every look.
     * The previous file is deleted. The outcome shows as [SettingsState.backgroundMessage].
     */
    fun importBackgroundImage(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { BackgroundImageStore.import(app, uri, _state.value.animatedBackgrounds) }
            result.onSuccess { image ->
                BackgroundImageSetting.write(prefs, image)
                withContext(Dispatchers.IO) { BackgroundImageStore.prune(app, image) }
            }
            _state.value = _state.value.copy(
                backgroundImage = BackgroundImageSetting.read(prefs),
                // Picking an image makes it the active source, so a gradient stops showing.
                activeGradientId = jr.brian.commandpannel.system.BackgroundSetting.activeGradientId(prefs),
                backgroundMessage = result.fold(
                    { app.getString(R.string.settings_background_image_set) },
                    { e -> app.getString((e as? BackgroundImportException)?.messageRes ?: R.string.msg_background_invalid) },
                ),
            )
        }
    }

    /** The overlay re-reads prefs on every change, so a playing GIF stops (or starts) as soon as this is saved. */
    fun setAnimatedBackgrounds(enable: Boolean) {
        prefs.edit().putBoolean(BackgroundImageSetting.KEY_ANIMATIONS_ON, enable).apply()
        _state.value = _state.value.copy(animatedBackgrounds = enable)
    }

    /** Re-reads the saved gradients and the active source (opening the library, or back in the companion). */
    fun refreshGradients() {
        _state.value = _state.value.copy(
            gradients = jr.brian.commandpannel.system.GradientStore.all(prefs),
            activeGradientId = jr.brian.commandpannel.system.BackgroundSetting.activeGradientId(prefs),
            backgroundImage = BackgroundImageSetting.read(prefs),
            backgroundTileOn = prefs.getBoolean(jr.brian.commandpannel.system.BackgroundImageTile.PREF, true),
        )
    }

    /**
     * "Turn it back on" (#78): turns the panel's Background tile on, the same pref the tile's press flips, so the
     * background that's set shows again.
     */
    fun turnOnBackgroundTile() {
        prefs.edit().putBoolean(jr.brian.commandpannel.system.BackgroundImageTile.PREF, true).apply()
        val msg = app.getString(R.string.msg_background_tile_on)
        _state.value = _state.value.copy(backgroundTileOn = true, gradientMessage = msg, backgroundMessage = msg)
    }

    /** Hands Gradient Studio (#68) the gradient to edit; a new one isn't saved until the user saves. */
    fun openGradientStudio(spec: jr.brian.commandpannel.system.GradientSpec) {
        _state.value = _state.value.copy(gradientDraft = spec, gradientDraftResumed = spec == _state.value.unsavedGradientDraft)
    }

    fun closeGradientStudio() {
        _state.value = _state.value.copy(gradientDraft = null)
    }

    /** Keeps Gradient Studio's unsaved [spec] (or forgets it, for null) in case the app closes before it's saved. */
    fun persistGradientDraft(spec: jr.brian.commandpannel.system.GradientSpec?) {
        if (spec == null) prefs.edit().remove(KEY_GRADIENT_DRAFT).apply()
        else prefs.edit().putString(KEY_GRADIENT_DRAFT, jr.brian.commandpannel.system.GradientJson.encode(spec).toString()).apply()
        _state.value = _state.value.copy(unsavedGradientDraft = spec)
    }

    /**
     * Saves (inserts or replaces) a gradient from Gradient Studio, and with [apply] also shows it behind the panel.
     * Returns what was saved (normalized), or null when it isn't a valid gradient.
     */
    fun saveGradient(spec: jr.brian.commandpannel.system.GradientSpec, apply: Boolean = false): jr.brian.commandpannel.system.GradientSpec? {
        val saved = jr.brian.commandpannel.system.GradientStore.save(prefs, spec) ?: return null
        if (apply) jr.brian.commandpannel.system.BackgroundSetting.selectGradient(prefs, saved.id)
        refreshGradients()
        _state.value = _state.value.copy(
            gradientMessage = if (apply) app.getString(R.string.msg_gradient_applied) else app.getString(R.string.msg_gradient_saved, saved.name),
        )
        return saved
    }

    /** Clears the library's status line (it's shown until the next action or until the library is opened again). */
    fun clearGradientMessage() {
        _state.value = _state.value.copy(gradientMessage = null)
    }

    /** Shows saved gradient [id] behind the panel on every look (#67). The stored image is kept to switch back to. */
    fun applyGradient(id: String) {
        if (jr.brian.commandpannel.system.GradientStore.find(prefs, id) == null) return refreshGradients()
        jr.brian.commandpannel.system.BackgroundSetting.selectGradient(prefs, id)
        refreshGradients()
        _state.value = _state.value.copy(gradientMessage = app.getString(R.string.msg_gradient_applied))
    }

    /** Goes back from a gradient to the stored background image ("Use my image instead", #67). */
    fun useBackgroundImage() {
        if (BackgroundImageSetting.read(prefs) == null) return refreshGradients()
        jr.brian.commandpannel.system.BackgroundSetting.selectImage(prefs)
        refreshGradients()
        val msg = app.getString(R.string.settings_background_image_set)
        _state.value = _state.value.copy(gradientMessage = msg, backgroundMessage = msg)
    }

    /** Saves a copy of gradient [id] ("<name> copy") at the end of the library; returns the copy's id, or null. */
    fun duplicateGradient(id: String): String? {
        val all = jr.brian.commandpannel.system.GradientStore.all(prefs)
        val spec = all.firstOrNull { it.id == id } ?: return null.also { refreshGradients() }
        val copy = GradientLibrary.duplicate(
            spec, all, java.util.UUID.randomUUID().toString(),
            fallbackName = app.getString(R.string.gradient_unnamed),
            copy = { app.getString(R.string.theme_copy_name, it) },
            copyN = { n, i -> app.getString(R.string.theme_copy_name_n, n, i) },
        )
        val saved = jr.brian.commandpannel.system.GradientStore.save(prefs, copy)
        refreshGradients()
        _state.value = _state.value.copy(
            gradientMessage = saved?.let { app.getString(R.string.msg_gradient_duplicated, it.name) },
        )
        return saved?.id
    }

    /**
     * "Use" on a built-in preset (#69): saves an editable copy of [preset] (already named and, for "Match my look",
     * already in the look's colors) into the user's gradients and shows it. Returns the copy's id, or null.
     */
    fun usePreset(preset: jr.brian.commandpannel.system.GradientSpec): String? {
        val saved = GradientLibrary.usePreset(
            prefs, preset, java.util.UUID.randomUUID().toString(),
            copy = { app.getString(R.string.theme_copy_name, it) },
            copyN = { n, i -> app.getString(R.string.theme_copy_name_n, n, i) },
        )
        refreshGradients()
        _state.value = _state.value.copy(
            gradientMessage = saved?.let { app.getString(R.string.msg_gradient_preset_used, it.name) },
        )
        return saved?.id
    }

    /** Deletes gradient [id]; if it was showing, the panel goes back to the stored image (or the backdrop). */
    fun deleteGradient(id: String) {
        jr.brian.commandpannel.system.GradientStore.delete(prefs, id)
        refreshGradients()
        _state.value = _state.value.copy(gradientMessage = app.getString(R.string.msg_gradient_deleted))
    }

    /** Removes the background image; every look shows its own backdrop again. */
    fun removeBackgroundImage() {
        BackgroundImageSetting.write(prefs, null)
        viewModelScope.launch(Dispatchers.IO) { BackgroundImageStore.prune(app, null) }
        _state.value = _state.value.copy(backgroundImage = null, backgroundMessage = app.getString(R.string.settings_background_image_removed))
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
            // Opening Themes re-checks repos last synced over an hour ago; Sync repos and Update don't wait.
            if (checkUpdates) syncAllSources(quiet = true, onlyDue = true)
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

    /** Repos with a sync in flight, so opening Themes twice in a row doesn't sync the same repo twice. */
    private val syncing = mutableSetOf<String>()

    /** Pulls [id]'s latest packs (the repo card's Update). */
    fun syncSource(id: String) {
        val source = ThemeSources.all(prefs).firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val result = runSync(source) ?: return@launch
            result.onSuccess { r ->
                val msg = when {
                    r.limitedUntilMs != null -> rateLimitMessage(r.limitedUntilMs)
                    r.updatedFiles == 0 && r.removedLooks == 0 -> app.getString(R.string.msg_source_up_to_date, source.name)
                    else -> app.resources.getQuantityString(R.plurals.msg_source_synced, r.lookCount, r.lookCount, source.name)
                }
                _state.value = _state.value.copy(repoMessage = msg)
                reportFonts(null, r.fontNeeds)
            }.onFailure { e -> _state.value = _state.value.copy(repoMessage = syncFailedMessage(source, e)) }
        }
    }

    /**
     * Syncs every followed repo, one at a time, and reports once. [onlyDue] skips repos synced within the hour (the
     * automatic check when Themes opens); [quiet] says nothing unless looks changed. Stops at GitHub's hourly limit.
     */
    fun syncAllSources(quiet: Boolean = false, onlyDue: Boolean = false) {
        val sources = ThemeSources.all(prefs).filter { !onlyDue || ThemeSources.isDue(it) }
        if (sources.isEmpty()) return
        viewModelScope.launch {
            var changedLooks = 0
            var limitedUntil: Long? = null
            var failure: String? = null
            for (source in sources) {
                val result = runSync(source) ?: continue
                result.onSuccess { r ->
                    if (r.updatedFiles > 0 || r.removedLooks > 0) changedLooks += r.packs.sumOf { it.looks.size } + r.removedLooks
                    reportFonts(null, r.fontNeeds)
                    limitedUntil = r.limitedUntilMs
                }.onFailure { e ->
                    if (e is jr.brian.commandpannel.system.GitHubRateLimitedException) limitedUntil = e.retryAtMs
                    else if (failure == null) failure = syncFailedMessage(source, e)
                }
                if (limitedUntil != null) break
            }
            // Automatic checks only speak up when something changed (offline or rate-limited stays silent).
            val msg = when {
                limitedUntil != null && !quiet -> rateLimitMessage(limitedUntil!!)
                failure != null && !quiet -> failure
                changedLooks > 0 -> app.resources.getQuantityString(R.plurals.msg_sources_synced, changedLooks, changedLooks)
                !quiet -> app.getString(R.string.msg_sources_up_to_date)
                else -> null
            }
            if (msg != null) _state.value = _state.value.copy(repoMessage = msg)
        }
    }

    /** Runs one repo's sync off the main thread, or returns null when that repo is already syncing. */
    private suspend fun runSync(source: jr.brian.commandpannel.system.ThemeSource): Result<jr.brian.commandpannel.system.SyncResult>? {
        if (!syncing.add(source.id)) return null
        _state.value = _state.value.copy(themeBusy = true)
        val result = withContext(Dispatchers.IO) { ThemeSources.sync(app, privileged, prefs, source) }
        syncing.remove(source.id)
        refreshLooks()
        _state.value = _state.value.copy(themeBusy = syncing.isNotEmpty(), themeSources = ThemeSources.all(prefs))
        return result
    }

    private fun syncFailedMessage(source: jr.brian.commandpannel.system.ThemeSource, e: Throwable): String =
        if (e is jr.brian.commandpannel.system.GitHubRateLimitedException) rateLimitMessage(e.retryAtMs)
        else app.getString(R.string.msg_source_failed, source.name, e.message ?: "")

    /** "GitHub's hourly limit…, try again in N minutes" for a sync the rate guard held back. */
    private fun rateLimitMessage(retryAtMs: Long): String {
        val minutes = ((retryAtMs - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(1)
        return app.resources.getQuantityString(R.plurals.msg_source_rate_limited, minutes, minutes)
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

    fun setQuickLaunchAfter(after: QuickLaunchAfter) {
        QuickLaunch.saveAfter(app, after)
        _state.value = _state.value.copy(quickLaunchAfter = after)
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

    /** The overlay's telemetry reads this pref every sample, so saving it is enough. */
    fun setTaskFps(enable: Boolean) {
        prefs.edit().putBoolean(jr.brian.commandpannel.system.TelemetryMonitor.PREF_TASK_FPS, enable).apply()
        _state.value = _state.value.copy(taskFps = enable)
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

    fun setShowNewDots(enable: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NEW_DOTS, enable).apply()
        _state.value = _state.value.copy(showNewDots = enable)
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

    /** Saves a new section order inside one rail group. */
    fun reorderRailSections(group: RailGroup, keys: List<String>) =
        saveRailLayout(_state.value.railLayout.withSectionOrder(group, keys))

    /** Saves a new order for the rail's groups (General stays on top). */
    fun reorderRailGroups(keys: List<String>) = saveRailLayout(_state.value.railLayout.withGroupOrder(keys))

    private fun saveRailLayout(layout: RailLayout) {
        val edit = prefs.edit()
        layout.prefs().forEach { (k, v) -> edit.putString(k, v) }
        edit.apply()
        _state.value = _state.value.copy(railLayout = layout)
    }

    /**
     * The "new" bubbles. The first time this runs it stores a baseline: an update from a version before bubbles shows
     * this release's new things, a fresh install shows none of them.
     */
    private fun loadNewBadges(): NewBadgeState {
        if (!prefs.contains(NewBadgeState.KEY_BASELINE)) {
            val info = runCatching { getApplication<Application>().run { packageManager.getPackageInfo(packageName, 0) } }.getOrNull()
            // Updated in place since it was installed, or used the companion before (its tour was seen).
            val upgraded = (info != null && info.lastUpdateTime > info.firstInstallTime) || prefs.contains(KEY_TOUR_SEEN)
            val code = info?.longVersionCode?.toInt() ?: jr.brian.commandpannel.BuildConfig.VERSION_CODE
            prefs.edit().putInt(NewBadgeState.KEY_BASELINE, NewBadgeState.firstBaseline(code, upgraded)).apply()
        }
        return NewBadgeState(
            baseline = prefs.getInt(NewBadgeState.KEY_BASELINE, Int.MAX_VALUE),
            seen = NewBadgeState.parseSeen(prefs.getString(NewBadgeState.KEY_SEEN, null)),
        )
    }

    /** Clears the bubble at [place] (focused or clicked); parents clear once their new children are all seen. */
    fun markNewSeen(place: NewPlace) {
        // Hidden dots stay unseen, so turning them back on shows what's still new.
        if (!_state.value.showNewDots) return
        val badges = _state.value.newBadges
        val keys = badges.keysAt(place)
        if (keys.isEmpty()) return
        val seen = badges.seen + keys
        prefs.edit().putString(NewBadgeState.KEY_SEEN, NewBadgeState.encodeSeen(seen)).apply()
        _state.value = _state.value.copy(newBadges = badges.copy(seen = seen))
    }

    private fun railOneOpen(): Boolean = prefs.getBoolean(RailExpanded.KEY_ONE_OPEN, true)

    /** Opens or closes a rail group, and remembers it. With one group open at a time (#82), opening closes the rest. */
    fun setRailGroupExpanded(group: RailGroup, expanded: Boolean) {
        val now = RailExpanded.toggle(_state.value.railExpanded, group, expanded, _state.value.railOneOpen)
        if (now == _state.value.railExpanded) return
        prefs.edit().putString(RailExpanded.KEY, RailExpanded.encode(now)).apply()
        _state.value = _state.value.copy(railExpanded = now)
    }

    /** General's "One rail group open at a time" (#82). Turning it on keeps [keep] (the shown section's group) open. */
    fun setRailOneOpen(enable: Boolean, keep: RailGroup?) {
        prefs.edit().putBoolean(RailExpanded.KEY_ONE_OPEN, enable).apply()
        val now = RailExpanded.limit(_state.value.railExpanded, enable, _state.value.railLayout.groups, keep)
        if (now != _state.value.railExpanded) prefs.edit().putString(RailExpanded.KEY, RailExpanded.encode(now)).apply()
        _state.value = _state.value.copy(railOneOpen = enable, railExpanded = now)
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
    private val sibling = jr.brian.commandpannel.system.SiblingBuild(app, privileged)

    // BDSA installed / removed / enabled / disabled while the companion is open (also by Command Panel's own buttons).
    private val bdsaPackageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            val pkg = i.data?.schemeSpecificPart
            if (pkg in BdsaDetector.PACKAGES || pkg == sibling.siblingPackage) refreshBdsa(keepMessage = true)
            if (pkg == jr.brian.commandpannel.system.update.SaveClipInstaller.PACKAGE) refreshSaveClip()
            if (pkg == jr.brian.commandpannel.system.update.ThorKbmInstaller.PACKAGE ||
                pkg == jr.brian.commandpannel.system.update.ThorKbmInstaller.DEBUG_PACKAGE
            ) refreshThorKbm()
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
        // The other build took over (or let go of) the stock replacement; the service turns this one's switch off.
        sibling.observe { refreshSibling() }
        // The service shares this process, so the helper's state and the armed capture's sound status are live here.
        viewModelScope.launch {
            jr.brian.commandpannel.privileged.RootHelper.state.collect { s ->
                val down = s == jr.brian.commandpannel.privileged.RootHelper.State.DISCONNECTED ||
                    s == jr.brian.commandpannel.privileged.RootHelper.State.UNAVAILABLE
                _state.value = _state.value.copy(replayHelperDown = down)
            }
        }
        viewModelScope.launch {
            jr.brian.commandpannel.privileged.ReplaySoundStatus.videoOnly.collect { videoOnly ->
                _state.value = _state.value.copy(replaySoundVideoOnly = videoOnly)
            }
        }
    }

    override fun onCleared() {
        sibling.stop()
        runCatching { app.unregisterReceiver(bdsaPackageReceiver) }
        app.contentResolver.unregisterContentObserver(ledColorObserver)
        prefs.unregisterOnSharedPreferenceChangeListener(onboardingListener)
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
            refreshSibling()
            recheckService()
        }
    }

    /** Re-reads whether the other build owns the stock replacement, and this build's switch (which it may have turned off). */
    private fun refreshSibling() {
        viewModelScope.launch {
            val owner = withContext(Dispatchers.IO) { if (sibling.siblingOwns()) sibling.siblingLabel() ?: sibling.siblingPackage else null }
            // The service turns the switch off when the other build claims (it may still be doing so). Only ever forced
            // off here: this build's own claim lands before its pref does, and must not flip the switch back.
            _state.value = _state.value.copy(siblingOwner = owner, replaceStock = _state.value.replaceStock && owner == null)
        }
    }

    fun openSibling() {
        app.packageManager.getLaunchIntentForPackage(sibling.siblingPackage)?.let {
            runCatching { app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
        // Turning it on takes over from the other build, which stands down.
        _state.value = _state.value.copy(replaceStock = enable, siblingOwner = _state.value.siblingOwner.takeUnless { enable })
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

    /**
     * The overlay's onboarding is on the bottom screen right now (its layer listens for the keys the companion
     * forwards). It only shows once the overlay really is up, so it can run long after first launch.
     */
    private val overlayOnboardingOnScreen: Boolean get() = OverlayOnboarding.commands.subscriptionCount.value > 0

    /** The companion tour step paused while the overlay's onboarding is on screen; it carries on from here after. */
    private var pausedTourStep: Int? = null
    /** Which tour [pausedTourStep] belongs to ([SettingsState.tourWhatsNew]). */
    private var pausedTourWhatsNew: String? = null

    /** Gives the overlay a moment to come up with its onboarding (it's shown as the companion opens). */
    private suspend fun awaitOverlayOnboarding() {
        kotlinx.coroutines.withTimeoutOrNull(3_000) { OverlayOnboarding.commands.subscriptionCount.first { it > 0 } }
    }

    private val onboardingListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // The onboarding's theme step changes the look from the overlay; keep the Themes section in step.
        if (key == "overlay_look") refreshLooks()
        // The Background tile pressed on the panel while settings are open (#78): the hints follow it.
        if (key == jr.brian.commandpannel.system.BackgroundImageTile.PREF) {
            _state.value = _state.value.copy(backgroundTileOn = prefs.getBoolean(jr.brian.commandpannel.system.BackgroundImageTile.PREF, true))
        }
    }

    init {
        // A fresh install (no first-run guide seen yet) starts out following the TCP Themes repo.
        ThemeSources.seedDefault(
            prefs, fresh = !prefs.getBoolean(KEY_TOUR_SEEN, false) && !prefs.getBoolean(OverlayOnboarding.KEY_SEEN, false),
        )
        prefs.registerOnSharedPreferenceChangeListener(onboardingListener)
        _state.value = _state.value.copy(updateLastCheck = updatePrefs.lastCheck, unsavedDraft = readStudioDraft())
        // One guide at a time: the companion tour pauses while the overlay's onboarding is on screen, then resumes.
        viewModelScope.launch {
            OverlayOnboarding.commands.subscriptionCount.map { it > 0 }.distinctUntilChanged().collect { onScreen ->
                if (onScreen) {
                    _state.value.tourStep?.let {
                        pausedTourStep = it
                        pausedTourWhatsNew = _state.value.tourWhatsNew
                        _state.value = _state.value.copy(tourStep = null)
                    }
                } else {
                    pausedTourStep?.let { step ->
                        pausedTourStep = null
                        val whatsNew = pausedTourWhatsNew
                        if (whatsNew != null) {
                            // A what's-new tour was started from the dialog, so no dialog is in the way.
                            _state.value = _state.value.copy(tourStep = step, tourWhatsNew = whatsNew)
                        } else if (_state.value.updateDialog == null) {
                            _state.value = _state.value.copy(tourStep = step, tourWhatsNew = null)
                        } else {
                            tourPending = true
                        }
                    }
                }
            }
        }
        if (jr.brian.commandpannel.BuildConfig.DEBUG) viewModelScope.launch {
            jr.brian.commandpannel.CompanionPresence.requestedWhatsNew.collect { requested ->
                if (!requested) return@collect
                jr.brian.commandpannel.CompanionPresence.requestedWhatsNew.value = false
                showWhatsNew()
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { jr.brian.commandpannel.system.update.UpdateInstaller.cleanup(app) }
            showWhatsNewIfUpdated()
            // The tour shows once, after any What's New dialog; the automatic update check waits until it's done. If the
            // overlay comes up with its onboarding, that goes first (the tour waits, paused); if it doesn't come up, the
            // tour runs as normal and the overlay's onboarding waits for the overlay's first real show.
            if (!prefs.getBoolean(KEY_TOUR_SEEN, false)) {
                if (!prefs.getBoolean(OverlayOnboarding.KEY_SEEN, false)) awaitOverlayOnboarding()
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

    // ---- TCP Save Clip ----

    /** Re-reads whether TCP Save Clip is installed and paired (companion resume, package changes). */
    fun refreshSaveClip() {
        val status = jr.brian.commandpannel.system.update.SaveClipInstaller.status(app)
        // A message about the previous state ("doesn't include…") is stale once it changes, unless an install is running.
        val message = _state.value.saveClipMessage.takeIf { _state.value.saveClip == status || _state.value.saveClipBusy }
        _state.value = _state.value.copy(saveClip = status, saveClipMessage = message)
    }

    /**
     * Downloads TCP Save Clip from the release matching this version and installs it like an update: through the root
     * shell, or else Android's installer. A copy without the permission (another key, or installed before Command
     * Panel) is removed first, so the fresh install gets it.
     */
    fun installSaveClip() {
        if (_state.value.saveClipBusy) return
        // Already installed and paired: pressing the card just says so.
        if (_state.value.saveClip is jr.brian.commandpannel.system.update.SaveClipStatus.Ready) {
            android.widget.Toast.makeText(app, R.string.msg_save_clip_already_installed, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val replace = _state.value.saveClip is jr.brian.commandpannel.system.update.SaveClipStatus.NoPermission
        _state.value = _state.value.copy(saveClipBusy = true, saveClipMessage = null)
        viewModelScope.launch {
            val installer = jr.brian.commandpannel.system.update.SaveClipInstaller
            var handOff: java.io.File? = null
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    val asset = runCatching { installer.asset(currentVersion) }.getOrElse { e ->
                        if (e is jr.brian.commandpannel.system.HttpStatusException && e.code == 404) null else throw e
                    } ?: return@runCatching app.getString(R.string.msg_save_clip_not_in_release, currentVersion)
                    val apk = installer.download(app, asset)
                    val out = installer.installWithRoot(privileged, apk, replace)
                    when {
                        out == null -> { handOff = apk; null }
                        out.lines().any { it.trim() == "Success" } -> null
                        else -> app.getString(R.string.msg_save_clip_failed, out.lines().lastOrNull { it.isNotBlank() } ?: "")
                    }
                }.getOrElse { e -> app.getString(R.string.msg_save_clip_failed, e.message ?: e.javaClass.simpleName) }
            }
            val message = failure ?: handOff?.let(::installSaveClipWithSystemInstaller) ?: run {
                val status = installer.status(app)
                app.getString(
                    if (status is jr.brian.commandpannel.system.update.SaveClipStatus.Ready) R.string.msg_save_clip_installed
                    else R.string.settings_save_clip_no_permission,
                )
            }
            _state.value = _state.value.copy(saveClipBusy = false, saveClipMessage = message, saveClip = installer.status(app))
        }
    }

    /** No root shell: hands the APK to Android's installer (asking for "install unknown apps" first). Returns the message. */
    private fun installSaveClipWithSystemInstaller(apk: java.io.File): String {
        val updates = jr.brian.commandpannel.system.update.UpdateInstaller
        if (!updates.canUseSystemInstaller(app)) {
            runCatching { app.startActivity(updates.installerPermissionIntent(app)) }
            return app.getString(R.string.msg_save_clip_allow_installs)
        }
        return runCatching { app.startActivity(updates.systemInstallerIntent(app, apk)) }
            .fold({ app.getString(R.string.msg_save_clip_finish_in_installer) }) { e ->
                app.getString(R.string.msg_save_clip_failed, e.message ?: e.javaClass.simpleName)
            }
    }

    // ---- ThorKBM ----

    /** Re-reads whether ThorKBM is installed (companion resume, package changes). */
    fun refreshThorKbm() {
        val status = jr.brian.commandpannel.system.update.ThorKbmInstaller.status(app)
        val s = _state.value
        val message = jr.brian.commandpannel.system.update.ThorKbmInstaller.messageAfterRefresh(
            old = s.thorKbm, new = status, oldMessage = s.thorKbmMessage, busy = s.thorKbmBusy,
            handedOff = thorKbmHandedOff, installedMessage = app.getString(R.string.msg_thorkbm_installed),
        )
        if (status.releaseInstalled) thorKbmHandedOff = false
        _state.value = s.copy(thorKbm = status, thorKbmMessage = message, thorKbmPluginKeys = thorKbmPluginKeys())
    }

    /** The listed ThorKBM plugins (the ones with a nav bar button), as the panel lists them. */
    private fun thorKbmPluginKeys(): List<String> = runCatching {
        jr.brian.commandpannel.plugin.PanelPluginRegistry(app, viewModelScope).query(allowUntrusted = false)
            .filter { it.component.packageName in jr.brian.commandpannel.system.KeyboardTile.THORKBM_PACKAGES }
            .map { it.key }
    }.getOrDefault(emptyList())

    /** Keyboard section: shows or hides ThorKBM's nav bar button (#43). The Keyboard tile still opens it either way. */
    fun setThorKbmNavShown(shown: Boolean) {
        val keys = _state.value.thorKbmPluginKeys
        val hidden = jr.brian.commandpannel.plugin.PluginNavPrefs.parse(
            prefs.getString(jr.brian.commandpannel.plugin.PluginNavPrefs.KEY, null),
        )
        val next = jr.brian.commandpannel.plugin.PluginNavPrefs.setShown(hidden, keys, shown)
        prefs.edit().putString(jr.brian.commandpannel.plugin.PluginNavPrefs.KEY, jr.brian.commandpannel.plugin.PluginNavPrefs.encode(next)).apply()
        _state.value = _state.value.copy(navHiddenPlugins = next)
    }

    /** An install was handed to Android's installer and hasn't shown up yet (see [refreshThorKbm]). */
    private var thorKbmHandedOff = false

    /**
     * Installs ThorKBM from its latest GitHub release, like TCP SC: through the root shell, or else Android's
     * installer. First install only; once it's there, ThorKBM's own updater takes over.
     */
    fun installThorKbm() {
        if (_state.value.thorKbmBusy || _state.value.thorKbm.releaseInstalled) return
        _state.value = _state.value.copy(thorKbmBusy = true, thorKbmMessage = null)
        viewModelScope.launch {
            val installer = jr.brian.commandpannel.system.update.ThorKbmInstaller
            var handOff: java.io.File? = null
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    val release = installer.latest(app) ?: return@runCatching app.getString(R.string.msg_thorkbm_no_release)
                    val apk = installer.download(app, release)
                    val out = installer.installWithRoot(app, privileged, apk)
                    when {
                        out == null -> { handOff = apk; null }
                        out.lines().any { it.trim() == "Success" } -> null
                        else -> app.getString(R.string.msg_thorkbm_failed, out.lines().lastOrNull { it.isNotBlank() } ?: "")
                    }
                }.getOrElse { e ->
                    if (e is jr.brian.commandpannel.system.GitHubRateLimitedException) {
                        val minutes = ((e.retryAtMs - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(1)
                        app.resources.getQuantityString(R.plurals.msg_thorkbm_rate_limited, minutes, minutes)
                    } else {
                        app.getString(R.string.msg_thorkbm_failed, e.message ?: e.javaClass.simpleName)
                    }
                }
            }
            val message = failure ?: handOff?.let(::installThorKbmWithSystemInstaller) ?: app.getString(R.string.msg_thorkbm_installed)
            _state.value = _state.value.copy(thorKbmBusy = false, thorKbmMessage = message, thorKbm = installer.status(app))
        }
    }

    /** No root shell: hands the APK to Android's installer (asking for "install unknown apps" first). Returns the message. */
    private fun installThorKbmWithSystemInstaller(apk: java.io.File): String {
        val updates = jr.brian.commandpannel.system.update.UpdateInstaller
        if (!updates.canUseSystemInstaller(app)) {
            runCatching { app.startActivity(updates.installerPermissionIntent(app)) }
            return app.getString(R.string.msg_thorkbm_allow_installs)
        }
        return runCatching { app.startActivity(updates.systemInstallerIntent(app, apk)) }
            .fold({ thorKbmHandedOff = true; app.getString(R.string.msg_thorkbm_finish_in_installer) }) { e ->
                app.getString(R.string.msg_thorkbm_failed, e.message ?: e.javaClass.simpleName)
            }
    }

    /** Opens ThorKBM on the top screen. */
    fun openThorKbm() {
        val pkg = _state.value.thorKbm.openPackage ?: return
        if (!jr.brian.commandpannel.system.update.ThorKbmInstaller.open(app, pkg)) {
            _state.value = _state.value.copy(thorKbmMessage = app.getString(R.string.msg_thorkbm_open_failed))
        }
    }

    /** Opens ThorKBM's GitHub page in the browser on the top screen. */
    fun openThorKbmGitHub() {
        if (!jr.brian.commandpannel.system.update.ThorKbmInstaller.openRepoPage(app)) {
            _state.value = _state.value.copy(thorKbmMessage = app.getString(R.string.msg_thorkbm_no_browser))
        }
    }

    // ---- companion tour ----

    /** The first-run tour is waiting for the What's New dialog to close. */
    private var tourPending = false

    /**
     * General → Updates → "Take the tour": replays the overlay's onboarding on the bottom screen, then this tour.
     * Clearing the onboarding's seen flag restarts it in the overlay; an explicit show brings the overlay up for it.
     */
    fun replayTour() {
        prefs.edit().remove(OverlayOnboarding.KEY_SEEN).apply()
        runCatching {
            app.startService(Intent(app, AssistantService::class.java).setAction(AssistantService.ACTION_SHOW_OVERLAY))
        }
        viewModelScope.launch {
            awaitOverlayOnboarding()
            startTour()
        }
    }

    /** Starts the companion tour from its first step (also General → Updates → "Take the tour"). */
    fun startTour() = beginTour(whatsNew = null)

    /** Whether [version] has a what's-new tour, so the What's new dialog shows its Tour button. */
    fun hasWhatsNewTour(version: String): Boolean = whatsNewTourFor(version) != null

    /**
     * The What's new dialog's Tour button: closes the dialog (What's new counts as seen) and runs this version's short
     * tour. It doesn't count as the full tour, which still runs on its own afterwards if it hasn't been seen.
     */
    fun startWhatsNewTour() {
        val phase = _state.value.updateDialog as? UpdatePhase.WhatsNew ?: return
        if (whatsNewTourFor(phase.version) == null) return
        updatePrefs.whatsNewSeen = currentVersion
        _state.value = _state.value.copy(updateDialog = null)
        beginTour(whatsNew = phase.version)
    }

    private fun beginTour(whatsNew: String?) {
        // Waits (paused at its first step) while the overlay's onboarding is on screen.
        if (overlayOnboardingOnScreen) { pausedTourStep = 0; pausedTourWhatsNew = whatsNew; return }
        // The Nav Bar section's cards are hidden while its app picker is open.
        _state.value = _state.value.copy(tourStep = 0, tourWhatsNew = whatsNew, appPickerOpen = false)
    }

    fun tourNext() {
        val step = _state.value.tourStep ?: return
        val next = tourNextStep(step, tourStepsFor(_state.value.tourWhatsNew).size)
        if (next == null) endTour() else _state.value = _state.value.copy(tourStep = next)
    }

    fun tourBack() {
        val step = _state.value.tourStep ?: return
        // Back on the first step leaves the tour.
        val back = tourBackStep(step)
        if (back == null) endTour() else _state.value = _state.value.copy(tourStep = back)
    }

    /**
     * Finishes or skips the tour; either way it doesn't start on its own again. A what's-new tour leaves the full
     * tour's seen flag alone, and hands over to the full tour if that was waiting behind the dialog.
     */
    fun endTour() {
        if (_state.value.tourStep == null) return
        if (_state.value.tourWhatsNew != null) {
            _state.value = _state.value.copy(tourStep = null)
            if (tourPending) {
                tourPending = false
                startTour()
            }
            return
        }
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
        /** Pref for "Show New Dots" (#54); on unless turned off. */
        const val KEY_SHOW_NEW_DOTS = "show_new_dots"
        /** Theme Studio's unsaved draft, autosaved while it's open. Not backed up. */
        const val KEY_STUDIO_DRAFT = "studio_draft"
        /** Gradient Studio's unsaved draft (#68), autosaved while it's open. Not backed up. */
        const val KEY_GRADIENT_DRAFT = "gradient_studio_draft"
        /** The companion rail's section order, as a CSV of [SettingsCategory] keys. Backed up. */
    }
}

