package jr.brian.commandpannel.system

import android.content.Context
import android.content.Intent
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.theme.TccUiLook
import java.io.File

/**
 * Debug builds only (`DEBUG_BACKGROUND`, for `scripts/verify-image-background.sh`): seeds a test look with a background
 * image without the file picker, and puts things back afterwards.
 * - `--es path <file>`: imports the file (root copies it somewhere the app can read) through the normal import checks,
 *   saves it on the "Background test" look and applies that look. Logs the outcome.
 * - `--es restore_look <overlay_look value>`: deletes the test look, applies that look again and prunes its image.
 */
object BackgroundImageDebug {
    private const val TAG = "LookBackground"
    const val LOOK_ID = "debug-background"
    const val EXTRA_PATH = "path"
    const val EXTRA_RESTORE_LOOK = "restore_look"

    fun handle(context: Context, privileged: PrivilegedAccess, intent: Intent) {
        val prefs = Prefs.of(context)
        intent.getStringExtra(EXTRA_RESTORE_LOOK)?.let { key ->
            CustomLookStore.delete(prefs, LOOK_ID)
            CustomLookStore.select(prefs, LookRef.parse(key.ifBlank { null }))
            BackgroundImageStore.prune(context, CustomLookStore.all(prefs))
            Log.i(TAG, "debug background restored look=$key")
            return
        }
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val staged = File(context.cacheDir, File(path).name)
        val q = ThemeFolder::q
        val copied = privileged.shell("cp ${q(path)} ${q(staged.absolutePath)} && chmod 644 ${q(staged.absolutePath)} && echo ok")?.trim() == "ok"
        val result = if (copied) BackgroundImageStore.importFile(context, staged) else Result.failure(IllegalStateException("copy failed"))
        staged.delete()
        result.onSuccess { image ->
            val look = CustomLookStore.find(prefs, LOOK_ID)
                ?: CustomLook(id = LOOK_ID, name = "Background test", baseLook = TccUiLook.AURORA_GLASS)
            CustomLookStore.save(prefs, look.copy(backgroundImage = image))
            CustomLookStore.select(prefs, LookRef.Custom(LOOK_ID))
            Log.i(TAG, "debug background set ${image.fileName} animated=${image.animated}")
        }.onFailure { e ->
            val reason = (e as? BackgroundImportException)?.let { context.getString(it.messageRes) } ?: e.message
            Log.w(TAG, "debug background failed: $reason")
        }
    }
}
