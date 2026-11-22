package jr.brian.commandpannel.system

import android.content.Context
import android.content.Intent
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.RootShell
import jr.brian.commandpannel.ui.theme.LookRef
import java.io.File

/**
 * Debug builds only (`DEBUG_BACKGROUND`, for `scripts/verify-image-background.sh` and
 * `scripts/verify-gradient-background.sh`): sets the background image or a gradient without the pickers, and puts
 * things back afterwards.
 * - `--es path <file>`: imports the file (root copies it somewhere the app can read) through the normal import checks,
 *   including the Animated backgrounds gate, and sets it as the background image for every look (which also makes the
 *   image the active source). Logs the outcome.
 * - `--ez clear true`: removes the background image and deletes its file, and leaves no active source (image or
 *   gradient), so a debug gradient doesn't outlive the run.
 * - `--es look <overlay_look value>`: applies that look (a built-in name such as `AURORA_GLASS`, or `custom:<id>`).
 * - `--ez animations true|false`: sets the Experimental Animated backgrounds switch (before importing, with `path`).
 * - `--ez press_tile true`: presses the Background image tile (#58), the same path as a tap on it (the service does it).
 * - `--es gradient '<GradientJson>'` (#65): decodes the spec with [GradientJson], saves it with [GradientStore]
 *   (replacing one with the same id) and makes it the active source. Runs after `animations` and `look`. The log line
 *   prints what was decoded (style and motion keys, speed, angle, stop colors as hex), so the script can check the
 *   lenient decoder didn't fall back to defaults.
 * - `--es gradient_delete <id>`: deletes that gradient (the script's cleanup).
 */
object BackgroundImageDebug {
    private const val TAG = "LookBackground"
    const val EXTRA_PATH = "path"
    const val EXTRA_CLEAR = "clear"
    const val EXTRA_LOOK = "look"
    const val EXTRA_PRESS_TILE = "press_tile"
    const val EXTRA_ANIMATIONS = "animations"
    const val EXTRA_GRADIENT = "gradient"
    const val EXTRA_GRADIENT_DELETE = "gradient_delete"

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
        intent.getStringExtra(EXTRA_GRADIENT_DELETE)?.let { id ->
            GradientStore.delete(prefs, id)
            Log.i(TAG, "debug gradient deleted $id")
        }
        if (intent.getBooleanExtra(EXTRA_CLEAR, false)) {
            BackgroundImageSetting.write(prefs, null)
            // write(null) only drops an image source; drop a gradient one too.
            if (BackgroundSetting.activeGradientId(prefs) != null) prefs.edit().remove(BackgroundSetting.KEY_SOURCE).apply()
            BackgroundImageStore.prune(context, null)
            Log.i(TAG, "debug background cleared")
            return
        }
        intent.getStringExtra(EXTRA_GRADIENT)?.let { json ->
            val spec = GradientJson.decode(json)?.let { GradientStore.save(prefs, it) }
            if (spec == null) {
                Log.w(TAG, "debug gradient failed: not a valid gradient spec")
                return
            }
            BackgroundSetting.selectGradient(prefs, spec.id)
            val colors = spec.stops.joinToString(",") { "%08X".format(it.color) }
            Log.i(
                TAG,
                "debug gradient set ${spec.id} style=${spec.style.key} motion=${spec.motion.key} speed=${spec.speed} " +
                    "angle=${spec.angle} colors=$colors animations=${BackgroundImageSetting.animationsOn(prefs)}",
            )
            return
        }
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val staged = File(context.cacheDir, File(path).name)
        val src = RootShell.q(path)
        val dest = RootShell.q(staged.absolutePath)
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
