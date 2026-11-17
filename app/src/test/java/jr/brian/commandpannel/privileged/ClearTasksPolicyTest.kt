package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.privileged.ClearTasksPolicy.ACTIVITY_TYPE_ASSISTANT
import jr.brian.commandpannel.privileged.ClearTasksPolicy.ACTIVITY_TYPE_DREAM
import jr.brian.commandpannel.privileged.ClearTasksPolicy.ACTIVITY_TYPE_HOME
import jr.brian.commandpannel.privileged.ClearTasksPolicy.ACTIVITY_TYPE_RECENTS
import jr.brian.commandpannel.privileged.ClearTasksPolicy.ACTIVITY_TYPE_STANDARD
import jr.brian.commandpannel.privileged.ClearTasksPolicy.ACTIVITY_TYPE_UNDEFINED
import jr.brian.commandpannel.privileged.ClearTasksPolicy.TaskSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearTasksPolicyTest {

    private val excluded = setOf(
        "jr.brian.commandpannel",
        "com.odin.dualscreen.assistant",
        "com.android.systemui"
    )

    private fun task(
        id: Int,
        pkg: String,
        display: Int = 0,
        uid: Int? = null,
        type: Int = ACTIVITY_TYPE_STANDARD,
        visible: Boolean = false,
        children: List<Int> = emptyList()
    ) = TaskSnapshot(id, display, pkg, uid, type, visible, children)

    private fun plan(
        recents: List<TaskSnapshot>,
        onScreen: List<TaskSnapshot>,
        keepOnScreen: Boolean = true,
        home: Set<String> = emptySet()
    ) = ClearTasksPolicy.selectTasksToClear(recents, onScreen, keepOnScreen, excluded, home)

    @Test
    fun `frontend on top and companion on bottom are kept, background apps removed`() {
        val frontend = task(10, "org.cocoon", display = 0, visible = true)
        val companion = task(11, "org.cocoon", display = 4, visible = true)
        val result = plan(
            recents = listOf(frontend, companion, task(12, "org.mozilla.firefox"), task(13, "com.spotify")),
            onScreen = listOf(frontend, companion)
        )
        assertEquals(listOf(12, 13), result.remove)
        assertEquals(listOf(10, 11), result.kept.map { it.taskId })
        assertEquals(listOf("org.cocoon"), result.keptPackages)
    }

    @Test
    fun `swapped layout with frontend on bottom and companion on top is kept`() {
        val companion = task(11, "org.cocoon", display = 0, visible = true)
        val frontend = task(10, "org.cocoon", display = 4, visible = true)
        val result = plan(
            recents = listOf(frontend, companion, task(12, "org.mozilla.firefox")),
            onScreen = listOf(companion, frontend)
        )
        assertEquals(listOf(12), result.remove)
    }

    @Test
    fun `companion with the same uid but a different activity package is kept`() {
        val frontend = task(10, "com.retrohrai", display = 0, uid = 10301, visible = true)
        val companion = task(11, "com.retrohrai.companion", display = 4, uid = 10301)
        val result = plan(
            recents = listOf(frontend, companion, task(12, "com.other", uid = 10400)),
            onScreen = listOf(frontend)
        )
        assertEquals(listOf(12), result.remove)
        assertEquals(listOf(10, 11), result.kept.map { it.taskId })
    }

    @Test
    fun `shared system uids do not protect unrelated tasks`() {
        val settings = task(10, "com.android.settings", uid = 1000, visible = true)
        val result = plan(
            recents = listOf(settings, task(11, "com.android.other", uid = 1000)),
            onScreen = listOf(settings)
        )
        assertEquals(listOf(11), result.remove)
    }

    @Test
    fun `companion reported as not visible is still kept by package`() {
        val frontend = task(10, "org.cocoon", display = 0, visible = true)
        val hiddenCompanion = task(11, "org.cocoon", display = 4, visible = false)
        val result = plan(
            recents = listOf(frontend, hiddenCompanion, task(12, "com.other")),
            onScreen = listOf(frontend, hiddenCompanion)
        )
        assertEquals(listOf(12), result.remove)
    }

    @Test
    fun `app visible through a child task id is kept`() {
        val root = task(5, "", type = ACTIVITY_TYPE_UNDEFINED, visible = true, children = listOf(20))
        val result = plan(
            recents = listOf(task(20, "com.example.split"), task(21, "com.other")),
            onScreen = listOf(root)
        )
        assertEquals(listOf(21), result.remove)
        assertEquals(listOf(20), result.kept.map { it.taskId })
    }

    @Test
    fun `keepOnScreen off removes everything except exclusions`() {
        val frontend = task(10, "org.cocoon", visible = true)
        val result = plan(
            recents = listOf(
                frontend,
                task(11, "org.cocoon", display = 4, visible = true),
                task(12, "com.other"),
                task(13, "com.android.systemui"),
                task(14, "com.launcher", type = ACTIVITY_TYPE_HOME)
            ),
            onScreen = listOf(frontend),
            keepOnScreen = false,
            home = setOf("org.cocoon")
        )
        assertEquals(listOf(10, 11, 12), result.remove)
        assertTrue(result.kept.isEmpty())
    }

    @Test
    fun `excluded packages and non-standard activity types are never removed`() {
        for (keepOnScreen in listOf(true, false)) {
            val result = plan(
                recents = listOf(
                    task(1, "com.launcher", type = ACTIVITY_TYPE_HOME),
                    task(2, "com.android.launcher3", type = ACTIVITY_TYPE_RECENTS),
                    task(3, "com.google.assistant", type = ACTIVITY_TYPE_ASSISTANT),
                    task(4, "com.dream", type = ACTIVITY_TYPE_DREAM),
                    task(5, "jr.brian.commandpannel"),
                    task(6, "com.odin.dualscreen.assistant"),
                    task(7, "com.android.systemui"),
                    task(8, "com.removable")
                ),
                onScreen = emptyList(),
                keepOnScreen = keepOnScreen
            )
            assertEquals(listOf(8), result.remove)
            assertTrue(result.kept.isEmpty())
        }
    }

    @Test
    fun `jarngreipr layout keeps the game and both launcher tasks`() {
        // HomeRouterActivity is the default home; MainActivity (bottom) and FrontEndActivity (top)
        // are standard tasks it launched explicitly. A game is in front on the top screen.
        val game = task(355, "com.retroarch", display = 0, uid = 10207, visible = true)
        val launcherBottom = task(333, "jr.brian.home", display = 4, uid = 10215, visible = true)
        val frontEndTop = task(340, "jr.brian.home", display = 0, uid = 10215, visible = false)
        val result = plan(
            recents = listOf(
                game,
                launcherBottom,
                frontEndTop,
                task(353, "io.github.azaharplus.android", uid = 10208),
                task(351, "org.mozilla.firefox", uid = 10148)
            ),
            onScreen = listOf(game, frontEndTop, launcherBottom),
            home = setOf("jr.brian.home")
        )
        assertEquals(listOf(353, 351), result.remove)
        assertEquals(listOf(355, 333, 340), result.kept.map { it.taskId })
        assertEquals(listOf("com.retroarch", "jr.brian.home"), result.keptPackages)
    }

    @Test
    fun `default home tasks are kept even when nothing of them is on screen`() {
        val game = task(355, "com.retroarch", visible = true)
        val result = plan(
            recents = listOf(game, task(340, "jr.brian.home", display = 0), task(351, "org.mozilla.firefox")),
            onScreen = listOf(game),
            home = setOf("jr.brian.home")
        )
        assertEquals(listOf(351), result.remove)
    }

    @Test
    fun `chooser packages are ignored as home packages`() {
        assertEquals(setOf("jr.brian.home"), ClearTasksPolicy.filterHomePackages(listOf("jr.brian.home", "android", null, "")))
    }

    @Test
    fun `empty recents produce an empty plan`() {
        val result = plan(recents = emptyList(), onScreen = listOf(task(1, "com.game", visible = true)))
        assertTrue(result.remove.isEmpty())
        assertTrue(result.kept.isEmpty())
    }

    @Test
    fun `duplicate and invalid task ids are ignored`() {
        val result = plan(
            recents = listOf(task(0, "com.a"), task(-1, "com.b"), task(7, "com.c"), task(7, "com.c")),
            onScreen = emptyList()
        )
        assertEquals(listOf(7), result.remove)
    }

    @Test
    fun `parseRecentsDump reads ids, types, packages and uids and skips autoRemoveRecents`() {
        val dump = """
            ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)
            mHiddenTasks=[Task{1813c0b #332 type=standard A=10215:jr.brian.home}]
              Recent tasks:
              * Recent #0: Task{8dd6c93 #355 type=standard A=10207:com.retroarch}
                userId=0 effectiveUid=u0a207 mCallingUid=u0a215
                mActivityComponent=com.retroarch/.browser.retroactivity.RetroActivityFuture
                autoRemoveRecents=false isPersistable=true activityType=1
              * Recent #1: Task{aaaaaaa #360 type=standard A=10300:custom.affinity}
                userId=0 effectiveUid=u0a300
                intent={act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] cmp=com.real.pkg/.Main}
              * Recent #2: Task{bbbbbbb #361 type=standard A=10301:com.temp}
                autoRemoveRecents=true
              * Recent #3: Task{3f671a5 #349 type=recents I=com.android.launcher3/com.android.quickstep.RecentsActivity}
                userId=0 effectiveUid=u0a83

              Visible recent tasks (most recent first):
              * RecentTaskInfo #0:
                id=355 userId=0 hasTask=true
                realActivity={com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture}
        """.trimIndent()
        val tasks = ClearTasksPolicy.parseRecentsDump(dump)!!
        assertEquals(listOf(355, 360, 349), tasks.map { it.taskId })
        assertEquals(listOf("com.retroarch", "com.real.pkg", "com.android.launcher3"), tasks.map { it.packageName })
        assertEquals(listOf(10207, 10300, 10083), tasks.map { it.uid })
        assertEquals(
            listOf(ACTIVITY_TYPE_STANDARD, ACTIVITY_TYPE_STANDARD, ACTIVITY_TYPE_RECENTS),
            tasks.map { it.activityType }
        )
        assertNull(ClearTasksPolicy.parseRecentsDump("nothing here"))
    }

    @Test
    fun `parseActivitiesDump reads displays, visibility, child tasks and record packages`() {
        val dump = """
            Display #0 (activities from top to bottom):
              * Task{8dd6c93 #355 type=standard A=10207:com.retroarch U=0 visible=true visibleRequested=true mode=fullscreen sz=1}
                topResumedActivity=ActivityRecord{5040cc9 u0 com.retroarch/.browser.retroactivity.RetroActivityFuture} t355}
                * Hist  #0: ActivityRecord{5040cc9 u0 com.retroarch/.browser.retroactivity.RetroActivityFuture} t355}
              * Task{930a3b6 #3 type=undefined U=0 visible=true visibleRequested=true mode=fullscreen sz=2}
                * Task{e1b4542 #5 type=undefined U=0 rootTaskId=3 visible=true visibleRequested=true mode=multi-window sz=1}
                  * Hist  #0: ActivityRecord{1111111 u0 com.split.app/.Main} t5}
              * Task{6593dec #351 type=standard A=10148:org.mozilla.firefox U=0 visible=false visibleRequested=false sz=1}
            Display #4 (activities from top to bottom):
              * Task{d2d5f00 #333 type=standard A=10215:jr.brian.home U=0 visible=true visibleRequested=true sz=1}
                * Hist  #0: ActivityRecord{49e0d7e u0 jr.brian.home/.MainActivity} t333}
        """.trimIndent()
        val tasks = ClearTasksPolicy.parseActivitiesDump(dump)!!.associateBy { it.taskId }
        assertEquals(setOf(355, 3, 5, 351, 333), tasks.keys)
        assertEquals("com.retroarch", tasks.getValue(355).packageName)
        assertEquals(10207, tasks.getValue(355).uid)
        assertEquals(0, tasks.getValue(355).displayId)
        assertEquals(listOf(5), tasks.getValue(3).childTaskIds)
        assertEquals("com.split.app", tasks.getValue(5).packageName)
        assertEquals(false, tasks.getValue(351).visible)
        assertEquals(4, tasks.getValue(333).displayId)
        assertEquals("jr.brian.home", tasks.getValue(333).packageName)
        assertNull(ClearTasksPolicy.parseActivitiesDump("garbage"))
    }
}
