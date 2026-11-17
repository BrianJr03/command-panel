package android.window;

/**
 * Copy of the framework's hidden callback (frameworks/base core/java/android/window/ITaskFpsCallback.aidl, API 33),
 * so the root helper can build a stub for IWindowManager.registerTaskFpsCallback. Package, name and method must stay
 * identical: at runtime the framework's own class is loaded in its place.
 */
oneway interface ITaskFpsCallback {
    void onFpsReported(in float fps);
}
