package jr.brian.commandpannel.privileged.helper;

import android.os.Bundle;

/**
 * Interface of the root helper process (see RootHelperMain). Only the Command Panel app (and root) may call it.
 * Never renumber: append new methods at the end and bump HelperProtocol.VERSION.
 */
interface IRootHelper {
    int protocolVersion();

    /** "uid", "pid" (ints) and "context" (SELinux label) of the helper process. */
    Bundle identity();

    /** Runs a shell command as root. Result keys: "exit" (int, -1 on timeout), "out", "err". */
    Bundle exec(String command, int timeoutMs);

    /** Linear brightness 0..1 of a logical display. */
    float getDisplayBrightness(int displayId);

    /** temporary = true while a slider is being dragged; false commits the value. */
    void setDisplayBrightness(int displayId, float brightness, boolean temporary);

    /** Ties the helper's lifetime to the app: the helper exits as soon as this token's process dies. */
    void attachClient(IBinder token);

    oneway void shutdown();
}
