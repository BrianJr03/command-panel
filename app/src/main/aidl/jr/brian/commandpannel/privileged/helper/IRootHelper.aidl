package jr.brian.commandpannel.privileged.helper;

import android.os.Bundle;
import android.view.Surface;

/**
 * Interface of the root helper process (see RootHelperMain). Only the Command Panel app (and root) may call it.
 * Never renumber: append new methods at the end and bump HelperProtocol.VERSION.
 */
interface IRootHelper {
    int protocolVersion();

    /** "uid", "pid" (ints), "context" (SELinux label) and "apk" (the APK it runs from, v5) of the helper process. */
    Bundle identity();

    /** Runs a shell command as root. Result keys: "exit" (int, -1 on timeout), "out", "err". */
    Bundle exec(String command, int timeoutMs);

    /** Linear brightness 0..1 of a logical display. */
    float getDisplayBrightness(int displayId);

    /**
     * temporary = true while a slider is being dragged; false commits the value. Returns whether the call went
     * through (v8; void before).
     */
    boolean setDisplayBrightness(int displayId, float brightness, boolean temporary);

    /**
     * Ties the helper's lifetime to the app. When this token's process dies the helper waits HelperProtocol.GRACE_MS
     * for a new client, then (or at once if the app was uninstalled) restores stock if armed and exits (v5).
     */
    void attachClient(IBinder token);

    oneway void shutdown();

    /** Removes a recent task by id via IActivityTaskManager.removeTask (v2). Returns true if removed. */
    boolean removeTask(int taskId);

    /**
     * Mirrors the physical display with layer stack [layerStack] onto [surface] via a root SurfaceControl display,
     * scaling [srcW]x[srcH] into [dstW]x[dstH] (v3). Returns true on success. Replaces any existing mirror.
     */
    boolean startDisplayMirror(in Surface surface, int layerStack, int srcW, int srcH, int dstW, int dstH);

    /** Tears down the display mirror started by startDisplayMirror (v3). */
    void stopDisplayMirror();

    /**
     * Per-task FPS of the game task via IWindowManager.registerTaskFpsCallback (v4). The task is the focused root
     * task if it is a visible standard one, else the top one on [displayId], else the top one on any other display.
     * Keys: "fps" (float, -1 if none), "taskId" (int), "pkg" (String), "displayId" (int), "ageMs" (long, -1 before
     * the first report).
     */
    Bundle taskFps(int displayId);

    /** Unregisters the task FPS callback started by taskFps (v4). */
    void stopTaskFps();

    /** Whether the helper restores the stock assistant once the app is gone for good (v5). */
    void setStockRecoveryArmed(boolean armed);

    /** SurfaceFlinger's global composite counter (txn 1013) over binder, no shell (v5). -1 on failure. */
    long surfaceFlingerFrameCount();

    /**
     * Like startDisplayMirror, but runs alongside the mirrors already started instead of replacing them, so two
     * displays can be captured at once (v6). stopDisplayMirror tears all of them down.
     */
    boolean addDisplayMirror(in Surface surface, int layerStack, int srcW, int srcH, int dstW, int dstH);

    /**
     * Streams 48 kHz 16-bit stereo PCM into [sink] (v7): game audio (what apps play, still heard on the speakers)
     * and/or the mic, mixed. False if no requested source opened. Only the app's uid (or root) may call it.
     */
    boolean startAudioCapture(in ParcelFileDescriptor sink, boolean game, boolean mic);

    /** Stops startAudioCapture and closes its stream (v7). */
    void stopAudioCapture();
}
