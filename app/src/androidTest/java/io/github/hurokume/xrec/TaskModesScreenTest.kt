package io.github.hurokume.xrec

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.db.AppDatabase
import io.github.hurokume.xrec.data.DailyPlans
import io.github.hurokume.xrec.data.db.TaskEntity
import io.github.hurokume.xrec.ui.TaskViewModel
import io.github.hurokume.xrec.ui.SettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskModesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var db: AppDatabase
    private lateinit var vm: TaskViewModel
    private val store = ViewModelStore()

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        vm = TaskViewModel(app, db)
        store.put("test", vm)
        val project = db.taskDao().insertTask(TaskEntity(name = "Project"))
        val task = db.taskDao().insertTask(TaskEntity(name = "Task", parentId = project))
        db.taskDao().insertTask(TaskEntity(name = "Subtask", parentId = task))
        Unit
    }

    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close()
    }

    private fun text(id: Int) = app.getString(id)
    private fun named(id: Int, name: String) = app.getString(id, name)
    private fun waitForExpand() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(named(R.string.task_expand_named, "Task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun expandTask() {
        waitForExpand()
        compose.onNodeWithContentDescription(named(R.string.task_expand_named, "Task")).performClick()
        compose.onNodeWithText("Subtask", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun sortModePreservesRowGeometryAndHidesActionIconsAtAllLevels(): Unit = runBlocking {
        val sorting = mutableStateOf(false)
        val nameSort = mutableStateOf(NameSort.None)
        compose.setContent {
            MaterialTheme {
                DBTaskScreen(vm, sortMode = sorting.value, nameSort = nameSort.value,
                    taskMenuSettings = TaskMenuSettings(showButtons = true))
            }
        }
        expandTask()
        fun label(name: String) = compose.onNodeWithText(name, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val normalTask = label("Task")
        val normalSubtask = label("Subtask")
        val normalMenu = compose.onNodeWithContentDescription(text(R.string.task_menu))
            .fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { sorting.value = true }
        for (name in listOf("Project", "Task", "Subtask")) {
            compose.onNodeWithContentDescription(named(R.string.task_delete_named, name)).assertDoesNotExist()
            compose.onNodeWithContentDescription(named(R.string.task_start_named, name)).assertDoesNotExist()
            compose.onNodeWithContentDescription(named(R.string.task_stop_named, name)).assertDoesNotExist()
        }
        compose.onAllNodesWithContentDescription(text(R.string.task_add)).fetchSemanticsNodes()
            .also { assertTrue(it.isEmpty()) }
        compose.onAllNodesWithContentDescription(text(R.string.task_add_subtask)).fetchSemanticsNodes()
            .also { assertTrue(it.isEmpty()) }
        val sortedTask = label("Task")
        val sortedSubtask = label("Subtask")
        val sortedMenu = compose.onNodeWithContentDescription(text(R.string.task_menu))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(normalTask.left, sortedTask.left, 0.5f)
        assertEquals(normalTask.width, sortedTask.width, 0.5f)
        assertEquals(normalSubtask.top - normalTask.top, sortedSubtask.top - sortedTask.top, 0.5f)
        assertEquals(normalTask.center.y - normalMenu.center.y, sortedTask.center.y - sortedMenu.center.y, 0.5f)
        compose.onNodeWithText("Task", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Subtask", useUnmergedTree = true).performClick()
        assertNull(db.taskDao().getActiveOrNull())
        compose.runOnIdle { nameSort.value = NameSort.Asc }
        compose.onNodeWithText("Task", useUnmergedTree = true).performClick()
        assertNull(db.taskDao().getActiveOrNull())
        compose.runOnIdle { sorting.value = false }
        compose.onNodeWithContentDescription(named(R.string.task_delete_named, "Task")).assertIsDisplayed()
    }

    @Test fun editModeOverridesHiddenActionsAndRenamesWithoutStartingTasks() = runBlocking {
        val editing = mutableStateOf(false)
        val expansion = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                DBTaskScreen(
                    vm, sortMode = false, nameSort = NameSort.None, editMode = editing.value,
                    showTaskAddButtons = false, showTaskDeleteButtons = false,
                    showTaskPlaybackButtons = true,
                    showTaskExpandButtons = expansion.value,
                    taskMenuSettings = TaskMenuSettings(showButtons = false)
                )
            }
        }
        expandTask()
        compose.onNodeWithContentDescription(named(R.string.task_delete_named, "Task")).assertDoesNotExist()
        compose.onNodeWithContentDescription(named(R.string.task_start_named, "Task")).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_start_named, "Subtask")).assertIsDisplayed()
        compose.runOnIdle { editing.value = true }
        for (name in listOf("Project", "Task", "Subtask")) {
            compose.onNodeWithContentDescription(named(R.string.task_start_named, name)).assertDoesNotExist()
            compose.onNodeWithContentDescription(named(R.string.task_stop_named, name)).assertDoesNotExist()
            compose.onNodeWithContentDescription(named(R.string.task_delete_named, name)).assertIsDisplayed()
            compose.onNodeWithText(name, useUnmergedTree = true).performTouchInput { longClick() }
            compose.onNodeWithText(named(R.string.task_current_name, name)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        }
        assertEquals(2, compose.onAllNodesWithContentDescription(text(R.string.task_add_subtask))
            .fetchSemanticsNodes().size)
        compose.onAllNodesWithContentDescription(text(R.string.task_add_subtask))[0].performClick()
        compose.onNodeWithText(text(R.string.task_add_child)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.onNodeWithContentDescription(named(R.string.task_delete_named, "Subtask")).performClick()
        compose.onNodeWithText(named(R.string.delete_task_message, "Subtask")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        assertNull(db.taskDao().getActiveOrNull())
        compose.runOnIdle { editing.value = false }
        compose.onNodeWithContentDescription(named(R.string.task_start_named, "Task")).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_start_named, "Subtask")).assertIsDisplayed()
        for (name in listOf("Project", "Task", "Subtask")) {
            compose.onNodeWithContentDescription(named(R.string.task_delete_named, name)).assertDoesNotExist()
        }
        assertTrue(compose.onAllNodesWithContentDescription(text(R.string.task_add_subtask))
            .fetchSemanticsNodes().isEmpty())
        compose.runOnIdle { editing.value = true; expansion.value = false }
        compose.onNodeWithText("Task", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Subtask", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_delete_named, "Subtask")).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_start_named, "Subtask")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.subtask_none)).assertDoesNotExist()
        assertNull(db.taskDao().getActiveOrNull())
    }

    @Test fun editingNameTapsDoNotStartOrSwitchRecording() = runBlocking {
        val project = db.taskDao().getChildrenOnce(null).single().id
        val leaf = db.taskDao().insertTask(TaskEntity(name = "Leaf", parentId = project))
        compose.setContent { MaterialTheme { AppRoot(vm, {}, { _, _, _, _ -> }) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Leaf", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(text(R.string.bottom_edit_tasks)).performClick()
        compose.onNodeWithText(text(R.string.label_select_task)).assertDoesNotExist()
        fun tapNameAndCancelRename(name: String) {
            compose.onNodeWithText(name, useUnmergedTree = true).performClick()
            compose.onNodeWithText(named(R.string.task_current_name, name)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        }
        tapNameAndCancelRename("Project")
        tapNameAndCancelRename("Leaf")
        expandTask()
        tapNameAndCancelRename("Subtask")
        assertNull(db.taskDao().getActiveOrNull())

        compose.onNodeWithText("Task", useUnmergedTree = true).performClick()
        tapNameAndCancelRename("Subtask")
        compose.onNodeWithText(text(R.string.subtask_none)).assertDoesNotExist()
        assertNull(db.taskDao().getActiveOrNull())
        compose.onNodeWithText(text(R.string.action_back_with_marker)).performClick()
        compose.onNodeWithContentDescription(text(R.string.main_exit_task_edit_mode)).performClick()
        compose.onNodeWithText("Leaf", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { runBlocking { db.taskDao().getActiveOrNull()?.taskId == leaf } }
        val active = db.taskDao().getActiveOrNull()
        compose.onNodeWithContentDescription(text(R.string.main_stop_active_task)).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_stop_named, "Leaf")).assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.bottom_edit_tasks)).performClick()
        compose.onNodeWithContentDescription(text(R.string.main_stop_active_task)).assertDoesNotExist()
        for (name in listOf("Task", "Leaf", "Subtask")) {
            compose.onNodeWithContentDescription(named(R.string.task_start_named, name)).assertDoesNotExist()
            compose.onNodeWithContentDescription(named(R.string.task_stop_named, name)).assertDoesNotExist()
        }
        tapNameAndCancelRename("Leaf")
        tapNameAndCancelRename("Subtask")
        assertEquals(active, db.taskDao().getActiveOrNull())
        compose.onNodeWithContentDescription(text(R.string.main_exit_task_edit_mode)).performClick()
        compose.onNodeWithContentDescription(text(R.string.main_stop_active_task)).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_stop_named, "Leaf")).assertIsDisplayed()
        Unit
    }

    @Test fun expandButtonsRevealChildrenInlineAndRestoreAcrossRecreation() = runBlocking {
        val showExpand = mutableStateOf(true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                DBTaskScreen(
                    vm, sortMode = false, nameSort = NameSort.None,
                    showTaskExpandButtons = showExpand.value,
                    taskMenuSettings = TaskMenuSettings(showButtons = true)
                )
            }
        }
        waitForExpand()
        compose.onNodeWithText("Subtask").assertDoesNotExist()
        val menu = compose.onNodeWithContentDescription(text(R.string.task_menu)).fetchSemanticsNode().boundsInRoot
        val expand = compose.onNodeWithContentDescription(named(R.string.task_expand_named, "Task"))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(expand.left >= menu.right)
        expandTask()
        val taskLabel = compose.onNodeWithText("Task", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val subtaskLabel = compose.onNodeWithText("Subtask", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(subtaskLabel.top > taskLabel.bottom)
        assertTrue(subtaskLabel.left > taskLabel.left)
        assertNull(db.taskDao().getActiveOrNull())
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Subtask", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(named(R.string.task_collapse_named, "Task")).performClick()
        compose.onNodeWithText("Subtask").assertDoesNotExist()
        compose.onNodeWithContentDescription(named(R.string.task_collapse_named, "Project")).performClick()
        compose.onNodeWithText("Task").assertDoesNotExist()
        compose.runOnIdle { showExpand.value = false }
        compose.onNodeWithText("Task", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_expand_named, "Project")).assertDoesNotExist()
        compose.onNodeWithContentDescription(named(R.string.task_expand_named, "Task")).assertDoesNotExist()
        compose.onNodeWithText("Task", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Subtask", useUnmergedTree = true).assertIsDisplayed()
        assertNull(db.taskDao().getActiveOrNull())
    }

    @Test fun iconSpacingSettingPersistsAndAppliesToEveryLevel() {
        val prefs = app.getSharedPreferences("task-icon-spacing-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val showSettings = mutableStateOf(true)
        val sorting = mutableStateOf(false)
        val width = mutableStateOf(TaskIconSpacing.read(prefs))
        try {
            assertEquals(TaskIconSpacing.Default, width.value)
            compose.setContent {
                MaterialTheme {
                    if (showSettings.value) {
                        SettingsScreen(
                            true, {}, DailyPlans(), emptyList(), {}, {},
                            taskIconWidthDp = width.value,
                            onTaskIconWidthChange = {
                                width.value = it
                                TaskIconSpacing.write(prefs, it)
                            }
                        )
                    } else {
                        DBTaskScreen(
                            vm, sortMode = sorting.value, nameSort = NameSort.None,
                            taskMenuSettings = TaskMenuSettings(showButtons = true),
                            taskIconWidthDp = width.value
                        )
                    }
                }
            }
            fun setSpacing(value: Int) {
                compose.onNodeWithText(text(R.string.settings_display_title)).performClick()
                compose.onNodeWithContentDescription(text(R.string.settings_task_icon_spacing))
                    .performScrollTo()
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
                assertEquals(value, TaskIconSpacing.read(prefs))
                compose.runOnIdle {
                    width.value = TaskIconSpacing.read(prefs)
                    showSettings.value = false
                }
                expandTask()
            }
            fun iconDistance(name: String): Float {
                val delete = compose.onNodeWithContentDescription(named(R.string.task_delete_named, name))
                    .fetchSemanticsNode().boundsInRoot
                val addLabel = if (name == "Project") R.string.task_add else R.string.task_add_subtask
                val add = compose.onAllNodesWithContentDescription(text(addLabel))
                    .fetchSemanticsNodes().single { kotlin.math.abs(it.boundsInRoot.center.y - delete.center.y) < 1f }
                return delete.center.x - add.boundsInRoot.center.x
            }
            setSpacing(TaskIconSpacing.Min)
            val names = listOf("Project", "Task", "Subtask")
            val narrowDistances = names.associateWith { iconDistance(it) }
            compose.runOnIdle { showSettings.value = true }
            setSpacing(TaskIconSpacing.Max)
            names.forEach { assertTrue(iconDistance(it) > narrowDistances.getValue(it)) }
            val menus = listOf(R.string.task_parent_menu, R.string.task_menu, R.string.task_subtask_menu)
            val normalPositions = menus.map {
                compose.onNodeWithContentDescription(text(it)).fetchSemanticsNode().boundsInRoot
            }
            compose.runOnIdle { sorting.value = true }
            menus.forEachIndexed { index, menu ->
                val sorted = compose.onNodeWithContentDescription(text(menu)).fetchSemanticsNode().boundsInRoot
                assertEquals(normalPositions[index].left, sorted.left, 0.5f)
                assertEquals(normalPositions[index].width, sorted.width, 0.5f)
            }
            compose.runOnIdle { sorting.value = false }
            compose.onNodeWithText("Task", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Subtask", useUnmergedTree = true).assertIsDisplayed()
            assertTrue(iconDistance("Subtask") > narrowDistances.getValue("Subtask"))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun bottomBarSwitchesBetweenSortAndEditAndAllowsExiting() = runBlocking {
        compose.setContent { MaterialTheme { AppRoot(vm, {}, { _, _, _, _ -> }) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Task", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val sort = compose.onNodeWithContentDescription(text(R.string.bottom_sort)).fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithContentDescription(text(R.string.bottom_edit_tasks)).fetchSemanticsNode().boundsInRoot
        assertTrue(edit.left >= sort.right)
        compose.onNodeWithContentDescription(text(R.string.bottom_edit_tasks)).performClick()
        compose.onNodeWithText(text(R.string.main_task_edit_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.main_exit_task_edit_mode)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.bottom_sort)).performClick()
        compose.onNodeWithContentDescription(text(R.string.main_exit_task_edit_mode)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.main_exit_sort_mode)).assertIsDisplayed()
        compose.onNodeWithContentDescription(named(R.string.task_delete_named, "Task")).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.bottom_edit_tasks)).performClick()
        compose.onNodeWithContentDescription(text(R.string.main_exit_sort_mode)).assertDoesNotExist()
        compose.onNodeWithContentDescription(named(R.string.task_delete_named, "Task")).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.main_exit_task_edit_mode)).performClick()
        compose.onNodeWithText(text(R.string.label_select_task)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.bottom_edit_tasks)).performClick()
        Espresso.pressBack()
        compose.onNodeWithText(text(R.string.label_select_task)).assertIsDisplayed()
        assertNull(db.taskDao().getActiveOrNull())
    }
}
