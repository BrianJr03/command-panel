package jr.brian.commandpannel.privileged.helper

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Process

/**
 * A Context for framework APIs that insist on one (AudioPolicy, AudioRecord) in a process with no app: the system
 * context of a bare `ActivityThread`, reporting [packageName] as its package and the process uid as its attribution.
 * Same idea as scrcpy's `FakeContext` / `Workarounds`. Built on the helper's main looper thread.
 */
internal class FakeContext private constructor(base: Context, private val pkg: String) : ContextWrapper(base) {
    override fun getPackageName(): String = pkg
    override fun getOpPackageName(): String = pkg
    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(Process.myUid()).setPackageName(pkg).build()
    override fun getApplicationContext(): Context = this

    companion object {
        private var system: Context? = null
        private val byPackage = mutableMapOf<String, FakeContext>()

        /** A context reporting [pkg]; it must be a package the framework accepts for this uid. */
        @Synchronized
        fun get(pkg: String): FakeContext = byPackage.getOrPut(pkg) {
            FakeContext(system ?: systemContext().also { system = it }, pkg)
        }

        /** Package names to try for this uid, most likely first: root's ops package, then the shell's. */
        fun candidatePackages(): List<String> =
            if (Process.myUid() == 0) listOf("root", "com.android.shell") else listOf("com.android.shell")

        private fun systemContext(): Context {
            val atClass = Class.forName("android.app.ActivityThread")
            val ctor = atClass.getDeclaredConstructor().apply { isAccessible = true }
            val thread = ctor.newInstance()
            atClass.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, thread)
            return atClass.getMethod("getSystemContext").invoke(thread) as Context
        }
    }
}
