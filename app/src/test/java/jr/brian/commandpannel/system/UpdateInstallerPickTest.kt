package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.update.UpdateInstaller
import jr.brian.commandpannel.system.update.UpdateInstaller.InstallerCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers choosing Android's installer for APK handoffs, so the "Open with" chooser never shows (#33). */
class UpdateInstallerPickTest {

    private val aosp = InstallerCandidate("com.android.packageinstaller", "com.android.packageinstaller.InstallStart", system = true)
    private val google = InstallerCandidate("com.google.android.packageinstaller", "com.android.packageinstaller.InstallStart", system = true)
    private val superFile = InstallerCandidate("com.example.superfile", "com.example.superfile.Open", system = false)
    private val apkPure = InstallerCandidate("com.apkpure.aegon", "com.apkpure.Install", system = false)
    private val termux = InstallerCandidate("com.termux", "com.termux.Open", system = false)
    private val systemFiles = InstallerCandidate("com.android.documentsui", "com.android.documentsui.Open", system = true)

    @Test
    fun `Android's installer wins over APK-handling apps`() {
        assertEquals(aosp, UpdateInstaller.pickInstaller(listOf(superFile, apkPure, aosp, termux)))
    }

    @Test
    fun `the package installer wins over another system handler, in any order`() {
        assertEquals(aosp, UpdateInstaller.pickInstaller(listOf(systemFiles, aosp)))
        assertEquals(aosp, UpdateInstaller.pickInstaller(listOf(aosp, systemFiles)))
    }

    @Test
    fun `Google's package installer counts`() {
        assertEquals(google, UpdateInstaller.pickInstaller(listOf(apkPure, google)))
    }

    @Test
    fun `another system handler is used when there's no package installer`() {
        assertEquals(systemFiles, UpdateInstaller.pickInstaller(listOf(superFile, systemFiles)))
    }

    @Test
    fun `apps outside the system are never picked`() {
        assertNull(UpdateInstaller.pickInstaller(listOf(superFile, apkPure, termux)))
        assertNull(UpdateInstaller.pickInstaller(listOf(InstallerCandidate("com.evil.packageinstaller", "X", system = false))))
        assertNull(UpdateInstaller.pickInstaller(emptyList()))
    }
}
