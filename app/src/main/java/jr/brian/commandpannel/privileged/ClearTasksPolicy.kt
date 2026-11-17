package jr.brian.commandpannel.privileged

/**
 * Pure decision logic for the Clear All Tasks tile, shared by the `UID 0` helper
 * (the root helper (`removeTask`) and the shell-dump parsing below in
 * `PrivilegedBridge.clearAllRecentTasks`. No reflection or Android dependencies, so the
 * rules can be unit-tested directly.
 *
 * Dual-screen frontends (Cocoon, RetroHrai, Jarngreipr) run a companion activity as its own
 * task on the other display. `removeAllVisibleRecentTasks()` would remove it along with the
 * foreground game, so the clear list is always built here and removed task by task.
 */
object ClearTasksPolicy {
    const val ACTIVITY_TYPE_UNDEFINED = 0
    const val ACTIVITY_TYPE_STANDARD = 1
    const val ACTIVITY_TYPE_HOME = 2
    const val ACTIVITY_TYPE_RECENTS = 3
    const val ACTIVITY_TYPE_ASSISTANT = 4
    const val ACTIVITY_TYPE_DREAM = 5

    /** `android.os.Process.FIRST_APPLICATION_UID`; shared system uids are never used for matching. */
    private const val FIRST_APPLICATION_UID = 10000
    private const val PER_USER_RANGE = 100000

    /** Packages that resolve as the chooser instead of a real home app. */
    private val NON_HOME_PACKAGES = setOf("android", "com.android.internal.app")

    data class TaskSnapshot(
        val taskId: Int,
        val displayId: Int,
        val packageName: String,
        val uid: Int?,
        val activityType: Int,
        val visible: Boolean,
        val childTaskIds: List<Int> = emptyList(),
        /** Other packages seen in the task (base / real / original activity), besides [packageName]. */
        val otherPackages: Set<String> = emptySet()
    ) {
        val packages: Set<String>
            get() = (otherPackages + packageName).filterTo(LinkedHashSet()) { it.isNotEmpty() }
    }

    data class ClearPlan(
        val remove: List<Int>,
        val kept: List<TaskSnapshot>
    ) {
        /** Distinct kept package names in recents order, for the toast. */
        val keptPackages: List<String>
            get() = kept.map { it.packageName }.filter { it.isNotEmpty() }.distinct()
    }

    fun isClearableActivityType(activityType: Int): Boolean =
        activityType == ACTIVITY_TYPE_UNDEFINED || activityType == ACTIVITY_TYPE_STANDARD

    /** Drops chooser / framework packages returned when no default home is set. */
    fun filterHomePackages(candidates: Iterable<String?>): Set<String> =
        candidates.filterNotNullTo(LinkedHashSet())
            .filterTo(LinkedHashSet()) { it.isNotEmpty() && it !in NON_HOME_PACKAGES }

    /**
     * Decides which recent tasks to remove.
     *
     * Never removed: tasks from [excludedPackages] (this app, stock assistant, SystemUI) and
     * home / recents / assistant / dream activity types.
     *
     * With [keepOnScreen] also kept:
     * 1. every visible root task in [onScreenRootTasks] and its child tasks,
     * 2. every recent task sharing a package or app uid with a visible standard task
     *    (a frontend's companion screen, in either direction),
     * 3. every task of the default home app(s) in [homePackages], even when not on screen.
     */
    fun selectTasksToClear(
        recents: List<TaskSnapshot>,
        onScreenRootTasks: List<TaskSnapshot>,
        keepOnScreen: Boolean,
        excludedPackages: Set<String>,
        homePackages: Set<String> = emptySet()
    ): ClearPlan {
        val protectedTaskIds = HashSet<Int>()
        val keepPackages = HashSet<String>()
        val keepUids = HashSet<Int>()
        if (keepOnScreen) {
            for (root in onScreenRootTasks) {
                if (!root.visible) continue
                if (root.taskId > 0) protectedTaskIds.add(root.taskId)
                protectedTaskIds.addAll(root.childTaskIds.filter { it > 0 })
                if (!isClearableActivityType(root.activityType)) continue
                val pkgs = root.packages
                if (pkgs.any { it in excludedPackages }) continue
                keepPackages.addAll(pkgs)
                root.uid?.takeIf { isAppUid(it) }?.let { keepUids.add(it) }
            }
            keepPackages.addAll(filterHomePackages(homePackages))
        }

        val remove = ArrayList<Int>()
        val kept = ArrayList<TaskSnapshot>()
        val seen = HashSet<Int>()
        for (task in recents) {
            if (task.taskId <= 0 || !seen.add(task.taskId)) continue
            val pkgs = task.packages
            if (pkgs.any { it in excludedPackages }) continue
            if (!isClearableActivityType(task.activityType)) continue
            val keep = keepOnScreen && (
                task.taskId in protectedTaskIds ||
                    pkgs.any { it in keepPackages } ||
                    (task.uid != null && task.uid in keepUids)
                )
            if (keep) kept.add(task) else remove.add(task.taskId)
        }
        return ClearPlan(remove = remove, kept = kept)
    }

    private fun isAppUid(uid: Int): Boolean = uid % PER_USER_RANGE >= FIRST_APPLICATION_UID

    // ---- Shell fallback parsing (`dumpsys activity recents` / `dumpsys activity activities`) ----

    private val taskHeaderRegex =
        Regex("""\*\s*(?:Recent #\d+:\s*)?Task\{[0-9a-f]+ #(\d+) type=(\w+)(?: (A|I)=([^ }]+))?""")
    private val activityRecordRegex =
        Regex("""ActivityRecord\{[0-9a-f]+ u\d+ ([A-Za-z0-9_.]+)/[^ }]+\}? t(\d+)""")
    private val componentRegex = Regex("""([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/""")
    private val displayHeaderRegex = Regex("""^\s*Display #(\d+)""")
    private val rootTaskIdRegex = Regex("""rootTaskId=(\d+)""")
    private val effectiveUidRegex = Regex("""effectiveUid=(?:u(\d+)a(\d+)|(\d+))""")

    fun activityTypeFromName(name: String): Int = when (name) {
        "standard" -> ACTIVITY_TYPE_STANDARD
        "home" -> ACTIVITY_TYPE_HOME
        "recents" -> ACTIVITY_TYPE_RECENTS
        "assistant" -> ACTIVITY_TYPE_ASSISTANT
        "dream" -> ACTIVITY_TYPE_DREAM
        else -> ACTIVITY_TYPE_UNDEFINED
    }

    /** Parses `A=<uid>:<affinity>` / `I=<pkg>/<cls>` from a task header into (uid, package). */
    private fun parseHeaderIdentity(kind: String?, value: String?): Pair<Int?, String?> {
        if (kind == null || value == null) return null to null
        return if (kind == "A") {
            val uid = value.substringBefore(':', "").toIntOrNull()
            uid to value.substringAfter(':', "").takeIf { it.isNotEmpty() }
        } else {
            null to value.substringBefore('/').takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Parses `dumpsys activity recents` into snapshots, skipping `autoRemoveRecents=true` tasks.
     * Returns `null` when no recent entries could be recognised.
     */
    fun parseRecentsDump(dump: String): List<TaskSnapshot>? {
        val result = ArrayList<TaskSnapshot>()
        var sawRecentHeader = false
        var current: MutableSnapshot? = null
        fun flush() {
            current?.takeIf { !it.autoRemove }?.toSnapshot()?.let { result.add(it) }
            current = null
        }
        for (line in dump.lineSequence()) {
            if (line.contains("* Recent #")) {
                flush()
                val m = taskHeaderRegex.find(line) ?: continue
                sawRecentHeader = true
                val (uid, headerPkg) = parseHeaderIdentity(m.groupValues[3].ifEmpty { null }, m.groupValues[4].ifEmpty { null })
                current = MutableSnapshot(
                    taskId = m.groupValues[1].toInt(),
                    activityType = activityTypeFromName(m.groupValues[2]),
                    uid = uid,
                    headerPackage = headerPkg,
                    affinityHeader = m.groupValues[3] == "A"
                )
                continue
            }
            val cur = current ?: continue
            if (line.isBlank() || !line.startsWith("    ")) {
                // Recent records are indented; anything else ends the section.
                flush()
                continue
            }
            when {
                line.contains("autoRemoveRecents=true") -> cur.autoRemove = true
                line.contains("effectiveUid=") -> effectiveUidRegex.find(line)?.let { m ->
                    cur.uid = if (m.groupValues[3].isNotEmpty()) {
                        m.groupValues[3].toInt()
                    } else {
                        m.groupValues[1].toInt() * PER_USER_RANGE + FIRST_APPLICATION_UID + m.groupValues[2].toInt()
                    }
                }
                line.trimStart().startsWith("mActivityComponent=") ||
                    line.trimStart().startsWith("realActivity=") ||
                    line.trimStart().startsWith("intent={") ->
                    componentRegex.find(line.substringAfter('='))?.let { cur.packages.add(it.groupValues[1]) }
            }
        }
        flush()
        return if (sawRecentHeader) result else null
    }

    /**
     * Parses `dumpsys activity activities` into root and child tasks per display, with the
     * package of each task taken from its activity records. Returns `null` when no display
     * section could be recognised.
     */
    fun parseActivitiesDump(dump: String): List<TaskSnapshot>? {
        data class Header(val taskId: Int, val displayId: Int, val type: Int, val uid: Int?, val visible: Boolean, val rootTaskId: Int?)
        val headers = LinkedHashMap<Int, Header>()
        val pkgsByTask = HashMap<Int, LinkedHashSet<String>>()
        var displayId: Int? = null
        for (line in dump.lineSequence()) {
            displayHeaderRegex.find(line)?.let { displayId = it.groupValues[1].toInt() }
            val disp = displayId ?: continue
            val header = taskHeaderRegex.find(line)
            if (header != null && line.trimStart().startsWith("* Task{")) {
                val taskId = header.groupValues[1].toInt()
                if (taskId !in headers) {
                    val (uid, _) = parseHeaderIdentity(header.groupValues[3].ifEmpty { null }, header.groupValues[4].ifEmpty { null })
                    headers[taskId] = Header(
                        taskId = taskId,
                        displayId = disp,
                        type = activityTypeFromName(header.groupValues[2]),
                        uid = uid,
                        visible = line.contains(" visible=true"),
                        rootTaskId = rootTaskIdRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
                    )
                }
                continue
            }
            activityRecordRegex.findAll(line).forEach { m ->
                pkgsByTask.getOrPut(m.groupValues[2].toInt()) { LinkedHashSet() }.add(m.groupValues[1])
            }
        }
        if (headers.isEmpty()) return null
        val childrenByRoot = headers.values.filter { it.rootTaskId != null && it.rootTaskId != it.taskId }
            .groupBy({ it.rootTaskId!! }, { it.taskId })
        return headers.values.map { h ->
            val pkgs = pkgsByTask[h.taskId].orEmpty()
            TaskSnapshot(
                taskId = h.taskId,
                displayId = h.displayId,
                packageName = pkgs.firstOrNull().orEmpty(),
                uid = h.uid,
                activityType = h.type,
                visible = h.visible,
                childTaskIds = childrenByRoot[h.taskId].orEmpty(),
                otherPackages = pkgs.drop(1).toSet()
            )
        }
    }

    private class MutableSnapshot(
        val taskId: Int,
        val activityType: Int,
        var uid: Int?,
        val headerPackage: String?,
        val affinityHeader: Boolean
    ) {
        val packages = LinkedHashSet<String>()
        var autoRemove = false

        fun toSnapshot(): TaskSnapshot {
            // An `A=` header carries the task affinity, which only matches the package by default;
            // prefer real component packages and fall back to it.
            val primary = packages.firstOrNull() ?: headerPackage.orEmpty()
            val others = LinkedHashSet(packages)
            if (!affinityHeader && headerPackage != null) others.add(headerPackage)
            others.remove(primary)
            return TaskSnapshot(
                taskId = taskId,
                displayId = -1,
                packageName = primary,
                uid = uid,
                activityType = activityType,
                visible = false,
                otherPackages = others
            )
        }
    }
}

/** Result of a Clear All Tasks run, as returned to the app side. */
data class ClearRecentTasksResult(
    val clearedCount: Int,
    val keptPackages: List<String> = emptyList()
) {
    val failed: Boolean get() = clearedCount < 0

    companion object {
        val FAILED = ClearRecentTasksResult(-1)
    }
}
