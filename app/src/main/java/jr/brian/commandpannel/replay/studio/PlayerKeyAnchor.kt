package jr.brian.commandpannel.replay.studio

import android.app.Activity
import android.view.View
import android.view.ViewGroup

/**
 * Keeps the player's first D-pad press from being lost (#46).
 *
 * The player's window can start in touch mode (a window takes the display's current mode, so after any touch), and a
 * tap on the video puts it back there.
 * In touch mode, `ViewRootImpl` checks every navigation key (the D-pad) before the activity sees it: it leaves touch
 * mode, gives focus to the window's default focusable view (the Compose root) and, when that moved focus, eats the
 * key. So the first left / right after opening or after a tap never reached [Activity.dispatchKeyEvent]. A and the
 * shoulders aren't navigation keys, which is why they always worked.
 *
 * That check lets the key through when a plain (non-group) view already holds focus. This adds one: a 1 x 1 view that
 * can hold focus in touch mode, takes it at start and takes it back if anything else gets it. Its focus search
 * points at itself, so an unused arrow never moves focus away. It draws nothing and has no click behavior; keys still
 * go to the activity first, as before, in normal and trim mode alike.
 */
internal class PlayerKeyAnchor(activity: Activity) {

    private val view = View(activity).apply {
        id = View.generateViewId()
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        nextFocusUpId = id
        nextFocusDownId = id
        nextFocusLeftId = id
        nextFocusRightId = id
        nextFocusForwardId = id
        setOnFocusChangeListener { v, hasFocus -> if (!hasFocus) v.post { v.requestFocus() } }
    }

    init {
        activity.addContentView(view, ViewGroup.LayoutParams(1, 1))
    }

    /** Takes focus (call when the window gains focus). */
    fun claim() {
        if (!view.hasFocus()) view.requestFocus()
    }
}
