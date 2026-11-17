package jr.brian.commandpannel.privileged

import java.io.File

/**
 * Pure path-confinement helpers for privileged media writes (replay clips, screenshots). Kept here so they are unit
 * testable on the JVM. Clean-room implementation of the confinement contract the replay tests assert.
 */
object PrivilegedHelperMain {

    internal val DEFAULT_STORAGE_ROOTS = listOf("/sdcard", "/storage/emulated/0")

    /**
     * True when [path] is a `[storageRoot]/[subDirs]/<leaf><requiredExtension>` file on internal shared storage with
     * no traversal, no nesting below the leaf, and no symlinked segment escaping the storage root.
     */
    internal fun isValidScopedMediaOutputPath(
        path: String,
        subDirs: List<String>,
        requiredExtension: String,
        storageRoots: List<String> = DEFAULT_STORAGE_ROOTS,
    ): Boolean {
        if (path.isBlank() || path.contains("..") || subDirs.isEmpty() || subDirs.any { it.isBlank() }) return false
        val storageRoot = storageRoots.firstOrNull { path.startsWith("$it/") } ?: return false
        val expectedPrefix = "$storageRoot/${subDirs.joinToString("/")}/"
        if (!path.startsWith(expectedPrefix)) return false
        val leafName = path.removePrefix(expectedPrefix)
        if (leafName.isEmpty() || leafName.contains('/')) return false
        if (!leafName.endsWith(requiredExtension) || leafName == requiredExtension) return false
        // Symlink safety: the real (symlink-resolved) path must still sit under the real storage root.
        return try {
            val realRoot = File(storageRoot).canonicalPath
            val realParent = File(path).parentFile?.canonicalPath ?: return false
            realParent == realRoot || realParent.startsWith("$realRoot/")
        } catch (_: Throwable) {
            false
        }
    }
}
