package jr.brian.commandpannel.system

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import jr.brian.commandpannel.BuildConfig
import jr.brian.commandpannel.plugin.PanelPluginTrust
import jr.brian.commandpannel.privileged.ThorRgbController
import jr.brian.commandpannel.ui.settings.companionPaletteFrom
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomLookJson
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.theme.PinnedColors
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.resolveLookPalette
import java.io.FileNotFoundException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Publishes the overlay's resolved colors, read-only, so another app (Jarn FE) can match them ([SharedThemeContract]).
 * It starts from the palette the companion gets when it follows the look, whether or not that setting is on, adds the
 * built-in look's colors ([publishedLookPalette]), and never uses Theme Studio's unsaved draft. Colors aren't secret,
 * so the palette is open to any app.
 *
 * It also publishes the applied look's fonts ([SharedFontsContract]). The fonts row is open too, but an imported font's
 * file is only opened for an app on [SharedFontReaders].
 *
 * Android creates providers before `Application.onCreate`, so nothing is read until the first query or the posted
 * [watch].
 */
class ThemeProvider : ContentProvider() {

    private val main = Handler(Looper.getMainLooper())
    private val activeLook = ActiveSharedLook()
    private val refreshQueued = AtomicBoolean(false)

    // One short-lived thread keeps theme reads off the main thread, so the overlay never waits on them.
    private val worker = ThreadPoolExecutor(1, 1, WORKER_IDLE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue())
        .apply { allowCoreThreadTimeOut(true) }

    // Only touched on [worker].
    private var published: Map<String, Int>? = null
    private var publishedFonts: Map<String, Any?>? = null

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = queueRefresh()
    }

    // Held here because SharedPreferences only keeps a weak reference to its listeners.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in WATCHED_PREFS) queueRefresh()
    }

    override fun onCreate(): Boolean {
        main.post { worker.execute(::watch) }
        return true
    }

    /** Registers before taking the baseline, so a change landing between the two still gets compared. */
    private fun watch() {
        val ctx = context ?: return
        runCatching {
            Prefs.of(ctx).registerOnSharedPreferenceChangeListener(prefListener)
            WATCHED_SETTINGS.forEach {
                ctx.contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, observer)
            }
            published = currentRow(ctx)
            publishedFonts = currentFontsRow(ctx)
        }.onFailure { Log.w(TAG, "couldn't watch the theme", it) }
    }

    private fun queueRefresh() {
        if (!refreshQueued.compareAndSet(false, true)) return
        worker.execute {
            refreshQueued.set(false)
            publishIfChanged()
        }
    }

    /** Tells clients only when a row really changed: most LED and pref changes touch neither the palette nor the fonts. */
    private fun publishIfChanged() {
        val ctx = context ?: return
        runCatching { currentRow(ctx) }.onSuccess { row ->
            if (row != published) {
                published = row
                ctx.contentResolver.notifyChange(uri(ctx, SharedThemeContract.PATH), null)
            }
        }.onFailure { Log.w(TAG, "couldn't read the theme", it) }
        runCatching { currentFontsRow(ctx) }.onSuccess { row ->
            if (row != publishedFonts) {
                publishedFonts = row
                ctx.contentResolver.notifyChange(uri(ctx, SharedFontsContract.PATH), null)
            }
        }.onFailure { Log.w(TAG, "couldn't read the fonts", it) }
    }

    // Follow-system theme mode changes with the night setting.
    override fun onConfigurationChanged(newConfig: Configuration) = queueRefresh()

    /** The current row, or `null` (no cursor) if the theme can't be read. An unknown column is still the caller's error. */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        return when (uri.pathSegments) {
            listOf(SharedThemeContract.PATH) -> {
                val columns = projection ?: SharedThemeContract.COLUMNS.toTypedArray()
                val row = runCatching { currentRow(ctx) }
                    .getOrElse { Log.w(TAG, "couldn't read the theme", it); return null }
                MatrixCursor(columns, 1).apply { addRow(selectSharedThemeColumns(row, columns)) }
            }
            listOf(SharedFontsContract.PATH) -> {
                val columns = projection ?: SharedFontsContract.COLUMNS.toTypedArray()
                val row = runCatching { currentFontsRow(ctx) }
                    .getOrElse { Log.w(TAG, "couldn't read the fonts", it); return null }
                MatrixCursor(columns, 1).apply { addRow(selectSharedColumns(row, columns)) }
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = when {
        uri.pathSegments == listOf(SharedThemeContract.PATH) -> MIME_TYPE
        uri.pathSegments == listOf(SharedFontsContract.PATH) -> FONTS_MIME_TYPE
        sharedFontFileId(uri.pathSegments) != null -> SharedFontsContract.FILE_MIME_TYPE
        else -> null
    }

    /**
     * An imported font's file, read-only, for a trusted caller only ([SharedFontReaders]). Bundled fonts aren't served:
     * the client ships its own copies.
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException(uri.toString())
        if (mode != "r") throw SecurityException(READ_ONLY)
        val id = sharedFontFileId(uri.pathSegments) ?: throw FileNotFoundException(uri.toString())
        if (!isTrustedFontReader(ctx)) throw SecurityException("not allowed to read fonts")
        val file = UserFontStore.sharedFile(ctx, id) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun isTrustedFontReader(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val packages = pm.getPackagesForUid(Binder.getCallingUid())?.toList().orEmpty()
        return SharedFontReaders.isTrusted(packages, SharedFontReaders.allowed(BuildConfig.DEBUG)) { pkg ->
            val info = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            PanelPluginTrust.signerDigests(info)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException(READ_ONLY)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    /** The overlay's palette from its stored inputs, resolved the way the overlay and companion resolve it. */
    private fun currentRow(ctx: Context): Map<String, Int> {
        val prefs = Prefs.of(ctx)
        val themeMode = runCatching {
            Settings.System.getInt(ctx.contentResolver, SystemSettings.Keys.THEME_MODE, 4)
        }.getOrDefault(4)
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val accent = ThorRgbController.accentOverride(ctx, prefs.getBoolean(ACCENT_SYNC_PREF, false))
        return sharedThemeRow(publishedPalette(themeMode, night, accent, activeLook.of(prefs)))
    }

    private fun currentFontsRow(ctx: Context): Map<String, Any?> =
        sharedFontsRow(activeLook.of(Prefs.of(ctx)), { ctx.getString(it.labelRes) }) { UserFontStore.sha256(ctx, it) }

    private companion object {
        const val TAG = "ThemeProvider"
        const val WORKER_IDLE_SECONDS = 30L
        const val READ_ONLY = "the shared theme is read-only"
        const val MIME_TYPE = "vnd.android.cursor.item/vnd.jr.brian.commandpanel.theme"
        const val FONTS_MIME_TYPE = "vnd.android.cursor.item/vnd.jr.brian.commandpanel.fonts"
        const val ACCENT_SYNC_PREF = "rgb_accent_sync"
        val WATCHED_PREFS = setOf(CustomLookStore.LOOK_KEY, CustomLookStore.KEY, ACCENT_SYNC_PREF, UserFontStore.KEY)
        val WATCHED_SETTINGS = listOf(SystemSettings.Keys.THEME_MODE, ThorRgbController.PICKER_COLOR_KEY)

        fun uri(ctx: Context, path: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(ctx.packageName + SharedThemeContract.AUTHORITY_SUFFIX)
            .path(path)
            .build()
    }
}

/**
 * What the applied look adds to the shared theme: a [builtIn] look's colors, or a saved look's [pinned] ones. A saved
 * look also keeps its [custom] definition, for its fonts.
 */
internal data class SharedLook(val builtIn: TccUiLook?, val pinned: PinnedColors?, val custom: CustomLook? = null) {
    /** The contract's `look_id`: the built-in look's name or the saved look's `custom:<id>` key. */
    val id: String get() = custom?.ref?.key ?: (builtIn ?: TccUiLook.DEFAULT).name
}

/**
 * The applied look. Reads only the applied look and the saved looks, never Theme Studio's draft (`studio_draft`).
 * Decoding is remembered until either pref changes, so repeated queries stay cheap.
 */
internal class ActiveSharedLook {
    private class Decoded(val lookKey: String?, val looksJson: String?, val look: SharedLook)

    @Volatile
    private var last: Decoded? = null

    fun of(prefs: SharedPreferences): SharedLook {
        val lookKey = prefs.getString(CustomLookStore.LOOK_KEY, null)
        val looksJson = prefs.getString(CustomLookStore.KEY, null)
        last?.takeIf { it.lookKey == lookKey && it.looksJson == looksJson }?.let { return it.look }
        val look = when (val ref = LookRef.parse(lookKey)) {
            is LookRef.BuiltIn -> SharedLook(ref.look, null)
            is LookRef.Custom -> CustomLookJson.decodeList(looksJson).firstOrNull { it.id == ref.id }
                ?.let { SharedLook(null, it.colors, it) }
                ?: SharedLook(TccUiLook.DEFAULT, null)
        }
        last = Decoded(lookKey, looksJson, look)
        return look
    }
}

/** The palette to publish: theme mode, then the saved look's pins or the built-in look's colors, with the LED accent on top. */
internal fun publishedPalette(themeMode: Int, nightActive: Boolean, ledAccent: Int?, look: SharedLook): TccColors {
    val colors = resolveLookPalette(themeMode, nightActive, ledAccent, look.pinned)
    return look.builtIn?.let { publishedLookPalette(colors, it, ledAccent = ledAccent != null) } ?: colors
}

/** The contract's row for [colors], through the same mapping the companion uses. */
internal fun sharedThemeRow(colors: TccColors): Map<String, Int> {
    val palette = companionPaletteFrom(colors)
    return mapOf(
        SharedThemeContract.COLUMN_VERSION to SharedThemeContract.VERSION,
        SharedThemeContract.COLUMN_IS_DARK to if (colors.isDark) 1 else 0,
        SharedThemeContract.COLUMN_BG to palette.bg.toArgb(),
        SharedThemeContract.COLUMN_RAIL to palette.rail.toArgb(),
        SharedThemeContract.COLUMN_SURFACE to palette.surface.toArgb(),
        SharedThemeContract.COLUMN_SURFACE_HIGH to palette.surfaceHigh.toArgb(),
        SharedThemeContract.COLUMN_BORDER to palette.border.toArgb(),
        SharedThemeContract.COLUMN_ACCENT to palette.accent.toArgb(),
        SharedThemeContract.COLUMN_ACCENT_SOFT to palette.accentSoft.toArgb(),
        SharedThemeContract.COLUMN_ACCENT_FOCUSED to palette.accentFocused.toArgb(),
        SharedThemeContract.COLUMN_ON_ACCENT to palette.onAccent.toArgb(),
        SharedThemeContract.COLUMN_TEXT_PRIMARY to palette.textPrimary.toArgb(),
        SharedThemeContract.COLUMN_TEXT_SECONDARY to palette.textSecondary.toArgb(),
        SharedThemeContract.COLUMN_TEXT_TERTIARY to palette.textTertiary.toArgb(),
        SharedThemeContract.COLUMN_DANGER to palette.danger.toArgb(),
        SharedThemeContract.COLUMN_SUCCESS to palette.success.toArgb(),
    )
}

/** [row]'s values for [columns], in that order. An unknown column is the caller's error. */
internal fun selectSharedThemeColumns(row: Map<String, Int>, columns: Array<out String>): Array<Any> =
    Array(columns.size) { i -> row[columns[i]] ?: throw IllegalArgumentException("unknown column: ${columns[i]}") }
