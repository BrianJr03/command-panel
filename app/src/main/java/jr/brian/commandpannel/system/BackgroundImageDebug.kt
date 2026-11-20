package jr.brian.commandpannel.system

import android.content.Context
import android.content.Intent
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.ui.theme.LookRef
import java.io.File

/**
 * Debug builds only (`DEBUG_BACKGROUND`, for `scripts/verify-image-background.sh`): sets the background image without
 * the file picker, and puts things back afterwards.
 * - `--es path <file>`: imports the file (root copies it somewhere the app can read) through the normal import checks,
 *   including the Animated backgrounds gate, and sets it as the background image for every look. Logs the outcome.
 * - `--ez clear true`: removes the background image and deletes its file.
 * - `--es look <overlay_look value>`: applies that look (a built-in name such as `AURORA_GLASS`, or `custom:<id>`).
 * - `--ez animations true|false`: sets the Experimental Animated backgrounds switch (before importing, with `path`).
 * - `--ez press_tile true`: presses the Background image tile (#58), the same path as a tap on it (the service does it).
 */
object BackgroundImageDebug {
    private const val TAG = "LookBackground"
    const val EXTRA_PATH = "path"
    const val EXTRA_CLEAR = "clear"
    const val EXTRA_LOOK = "look"
    const val EXTRA_PRESS_TILE = "press_tile"
    const val EXTRA_ANIMATIONS = "animations"

    fun handle(context: Context, privileged: PrivilegedAccess, intent: Intent) {
        val prefs = Prefs.of(context)
        if (intent.hasExtra(EXTRA_ANIMATIONS)) {
            val on = intent.getBooleanExtra(EXTRA_ANIMATIONS, false)
            prefs.edit().putBoolean(BackgroundImageSetting.KEY_ANIMATIONS_ON, on).apply()
            Log.i(TAG, "debug background animations=$on")
        }
        intent.getStringExtra(EXTRA_LOOK)?.let { key ->
            CustomLookStore.select(prefs, LookRef.parse(key.ifBlank { null }))
            Log.i(TAG, "debug background look=$key")
        }
        if (intent.getBooleanExtra(EXTRA_CLEAR, false)) {
            BackgroundImageSetting.write(prefs, null)
            BackgroundImageStore.prune(context, null)
            Log.i(TAG, "debug background cleared")
            return
        }
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val staged = File(context.cacheDir, File(path).name)
        val src = ThemeFolder.q(path)
        val dest = ThemeFolder.q(staged.absolutePath)
        val copied = privileged.shell("cp $src $dest && chmod 644 $dest && echo ok")?.trim() == "ok"
        val result = if (copied) BackgroundImageStore.importFile(context, staged, BackgroundImageSetting.animationsOn(prefs))
        else Result.failure(IllegalStateException("copy failed"))
        staged.delete()
        result.onSuccess { image ->
            BackgroundImageSetting.write(prefs, image)
            BackgroundImageStore.prune(context, image)
            Log.i(TAG, "debug background set ${image.fileName} animated=${image.animated}")
        }.onFailure { e ->
            val reason = (e as? BackgroundImportException)?.let { context.getString(it.messageRes) } ?: e.message
            Log.w(TAG, "debug background failed: $reason")
        }
    }
}
