package jr.brian.commandpannel.system.update

import java.io.File
import java.security.MessageDigest

/** The SHA-256 of a downloaded APK, compared with `sha256sum` of the copy root stages before installing it. */
internal object ApkDigest {
    /** Lowercase hex, as `sha256sum` prints it. */
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
