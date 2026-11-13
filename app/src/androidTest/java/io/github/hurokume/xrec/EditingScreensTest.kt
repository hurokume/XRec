package io.github.hurokume.xrec

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.DailyPlans
import io.github.hurokume.xrec.data.db.AppDatabase
import io.github.hurokume.xrec.data.db.TaskEntity
import io.github.hurokume.xrec.ui.AddTaskScreen
import io.github.hurokume.xrec.ui.SettingsScreen
import io.github.hurokume.xrec.ui.TaskAnalysisScreen
import io.github.hurokume.xrec.ui.TaskEditDialogs
import io.github.hurokume.xrec.ui.TaskViewModel
import io.github.hurokume.xrec.ui.TimelinePeriod
import io.github.hurokume.xrec.ui.TimelineRange
import io.github.hurokume.xrec.ui.TimelineRangeControls
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditingScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var db: AppDatabase
    private lateinit var vm: TaskViewModel
    private val store = ViewModelStore()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        vm = TaskViewModel(app, db)
        store.put("test", vm)
    }

    @After
    fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close()
    }

    private fun text(id: Int) = app.getString(id)

    @Test
    fun statisticsOffersNewPeriodsAndUpdatesSummary() {
        compose.setContent { MaterialTheme { AppRoot(vm, {}, { _, _, _, _ -> }) } }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.stats_title)).fetchSemanticsNodes()
                .isNotEmpty()
        }
        for (label in listOf(
            R.string.action_today, R.string.stats_yesterday,
            R.string.stats_one_day, R.string.stats_one_year
        )) {
            compose.onNodeWithText(text(label)).performScrollTo().performClick()
            compose.onNodeWithText(
                app.getString(
                    R.string.stats_selected_period_summary,
                    text(label),
                    0L
                )
            )
                .assertIsDisplayed()
        }
    }

    @Test
    fun settingsOpensMenuThenSeparateDisplayPage() {
        compose.setContent {
            MaterialTheme {
                SettingsScreen(true, {}, DailyPlans(), emptyList(), {}, {})
            }
        }
        compose.onNodeWithText(text(R.string.settings_display_title)).assertIsDisplayed()
            .performClick()
        compose.onNodeWithText(text(R.string.settings_stop_button_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.daily_plan_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.timeline_custom)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_timeline_default)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.timeline_custom)).assertIsDisplayed()
        compose.onNodeWithText("Monthly").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText(text(R.string.action_back)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.daily_plan_title)).assertIsDisplayed()
    }

    @Test
    fun taskButtonSettingsFollowIconOrderAndToggleDeleteVisibility() {
        val showDelete = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    true, {}, DailyPlans(), emptyList(), {}, {},
                    showTaskDeleteButtons = showDelete.value,
                    onShowTaskDeleteButtonsChange = { showDelete.value = it }
                )
            }
        }
        compose.onNodeWithText(text(R.string.settings_display_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_show_task_menu_buttons)).performScrollTo()
        val settingPositions = listOf(
            R.string.settings_show_task_playback_buttons,
            R.string.settings_show_task_add_buttons,
            R.string.settings_show_task_delete_buttons,
            R.string.settings_show_task_menu_buttons
        ).map { compose.onNodeWithText(text(it)).fetchSemanticsNode().boundsInRoot.top }
        Assert.assertTrue(settingPositions.zipWithNext().all { (above, below) -> above < below })
        compose.onNodeWithText(text(R.string.settings_show_task_delete_buttons))
            .performScrollTo().assertIsOn().performClick().assertIsOff()
        Assert.assertFalse(showDelete.value)
        compose.onNodeWithText(text(R.string.settings_show_task_delete_buttons))
            .performClick().assertIsOn()
        Assert.assertTrue(showDelete.value)
    }

    @Test
    fun deleteButtonsFollowRowOrderCanBeHiddenAndConfirmDeletionAtEveryLevel() = runBlocking {
        val project = db.taskDao().insertTask(TaskEntity(name = "Trash project"))
        val task = db.taskDao().insertTask(TaskEntity(name = "Trash task", parentId = project))
        val subtask = db.taskDao().insertTask(TaskEntity(name = "Trash subtask", parentId = task))
        val showDelete = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                DBTaskScreen(
                    vm, sortMode = false, nameSort = NameSort.None,
                    showTaskDeleteButtons = showDelete.value
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Trash task").fetchSemanticsNodes().isNotEmpty()
        }
        fun deleteLabel(name: String) = app.getString(R.string.task_delete_named, name)
        fun checkButtonOrder(name: String, addLabel: Int, menuLabel: Int) {
            val delete = compose.onNodeWithContentDescription(deleteLabel(name))
                .fetchSemanticsNode().boundsInRoot
            val add = compose.onAllNodesWithContentDescription(text(addLabel))
                .fetchSemanticsNodes().single { it.boundsInRoot.center.y == delete.center.y }
                .boundsInRoot
            val menu = compose.onNodeWithContentDescription(text(menuLabel))
                .fetchSemanticsNode().boundsInRoot
            Assert.assertTrue(add.center.x < delete.center.x && delete.center.x < menu.center.x)
        }
        fun openAndCancel(name: String, isRoot: Boolean) {
            compose.onNodeWithContentDescription(deleteLabel(name)).performClick()
            compose.onNodeWithText(app.getString(R.string.delete_task_message, name))
                .assertIsDisplayed()
            if (isRoot) {
                compose.onNodeWithText(text(R.string.delete_task_promote_title)).assertDoesNotExist()
            } else {
                compose.onNodeWithText(text(R.string.delete_task_promote_title)).assertIsDisplayed()
            }
            compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        }

        checkButtonOrder("Trash project", R.string.task_add, R.string.task_parent_menu)
        checkButtonOrder("Trash task", R.string.task_add_subtask, R.string.task_menu)
        openAndCancel("Trash project", isRoot = true)
        openAndCancel("Trash task", isRoot = false)
        Assert.assertFalse(db.taskDao().getTaskById(project)!!.isArchived)
        Assert.assertNotNull(db.taskDao().getTaskById(task))

        compose.runOnIdle { showDelete.value = false }
        compose.onNodeWithContentDescription(deleteLabel("Trash project")).assertDoesNotExist()
        compose.onNodeWithContentDescription(deleteLabel("Trash task")).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.task_menu)).assertIsDisplayed()
        compose.runOnIdle { showDelete.value = true }
        compose.onNodeWithText("Trash task", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Trash subtask").assertIsDisplayed()
        compose.runOnIdle { showDelete.value = false }
        compose.onNodeWithContentDescription(deleteLabel("Trash subtask")).assertDoesNotExist()
        compose.runOnIdle { showDelete.value = true }
        checkButtonOrder("Trash subtask", R.string.task_add_subtask, R.string.task_subtask_menu)
        openAndCancel("Trash subtask", isRoot = false)
        Assert.assertNotNull(db.taskDao().getTaskById(subtask))

        compose.onNodeWithContentDescription(deleteLabel("Trash subtask")).performClick()
        compose.onNodeWithText(text(R.string.action_delete)).performClick()
        compose.waitUntil(10_000) { runBlocking { db.taskDao().getTaskById(subtask) == null } }
        Assert.assertNotNull(db.taskDao().getTaskById(task))
        compose.onNodeWithText(text(R.string.action_back_with_marker)).performClick()
        compose.onNodeWithContentDescription(deleteLabel("Trash task")).performClick()
        compose.onNodeWithText(text(R.string.action_delete)).performClick()
        compose.waitUntil(10_000) { runBlocking { db.taskDao().getTaskById(task) == null } }
        Assert.assertFalse(db.taskDao().getTaskById(project)!!.isArchived)
        compose.onNodeWithContentDescription(deleteLabel("Trash project")).performClick()
        compose.onNodeWithText(text(R.string.action_delete)).performClick()
        compose.waitUntil(10_000) {
            runBlocking { db.taskDao().getTaskById(project)?.isArchived == true }
        }
        Assert.assertNull(db.taskDao().getActiveOrNull())
    }

    @Test
    fun taskMenuCustomizationRequiresVisibleButtonsAndKeepsSavedChoices() {
        val settings = mutableStateOf(TaskMenuSettings())
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    true, {}, DailyPlans(), emptyList(), {}, {},
                    taskMenuSettings = settings.value,
                    onTaskMenuSettingsChange = { settings.value = it }
                )
            }
        }
        compose.onNodeWithText(text(R.string.settings_display_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_show_task_menu_buttons))
            .performScrollTo().assertIsOn()
        compose.onNodeWithText(text(R.string.settings_task_menu_customize))
            .performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.task_rename)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        Assert.assertTrue(TaskMenuAction.Rename in settings.value.actions)

        compose.onNodeWithText(text(R.string.settings_task_menu_customize)).performClick()
        TaskMenuAction.entries.filter { it != TaskMenuAction.Rename }.forEach {
            compose.onNodeWithText(text(it.label)).performScrollTo().assertIsOn().performClick()
        }
        compose.onNodeWithText(text(R.string.action_save)).performClick()
        Assert.assertEquals(setOf(TaskMenuAction.Rename), settings.value.actions)

        compose.onNodeWithText(text(R.string.settings_show_task_menu_buttons))
            .performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText(text(R.string.settings_task_menu_customize))
            .performScrollTo().assertIsNotEnabled()
        Assert.assertEquals(setOf(TaskMenuAction.Rename), settings.value.actions)
        compose.onNodeWithText(text(R.string.settings_show_task_menu_buttons))
            .performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.settings_task_menu_customize))
            .performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.task_rename)).performScrollTo().assertIsOn()
        compose.onNodeWithText(text(R.string.action_delete)).performScrollTo().assertIsOff()
    }

    @Test
    fun taskMenuChoicesApplyToAllLevelsAndDisableMenusWithoutApplicableItems() = runBlocking {
        val project = db.taskDao().insertTask(TaskEntity(name = "Menu project"))
        val task = db.taskDao().insertTask(TaskEntity(name = "Menu task", parentId = project))
        db.taskDao().insertTask(TaskEntity(name = "Menu subtask", parentId = task))
        val settings = mutableStateOf(TaskMenuSettings(actions = setOf(TaskMenuAction.Rename)))
        compose.setContent {
            MaterialTheme {
                DBTaskScreen(
                    vm, sortMode = false, nameSort = NameSort.None,
                    showTaskAddButtons = false,
                    showTaskPlaybackButtons = false,
                    taskMenuSettings = settings.value
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Menu task").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(text(R.string.task_parent_menu)).performClick()
        compose.onNodeWithText(text(R.string.task_rename)).assertIsDisplayed()
        for (label in listOf(R.string.task_add, R.string.task_delete_parent, R.string.task_change_color)) {
            compose.onNodeWithText(text(label)).assertDoesNotExist()
        }
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription(text(R.string.task_menu)).performClick()
        compose.onNodeWithText(text(R.string.task_rename)).assertIsDisplayed()
        for (label in listOf(R.string.task_edit_subtasks, R.string.task_add_subtask, R.string.task_delete)) {
            compose.onNodeWithText(text(label)).assertDoesNotExist()
        }
        androidx.test.espresso.Espresso.pressBack()

        compose.runOnIdle { settings.value = settings.value.copy(showButtons = false) }
        compose.onNodeWithContentDescription(text(R.string.task_parent_menu)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.task_menu)).assertDoesNotExist()
        compose.onNodeWithText("Menu task").performClick()
        compose.onNodeWithText("Menu subtask").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.task_subtask_menu)).assertDoesNotExist()
        compose.runOnIdle { settings.value = settings.value.copy(showButtons = true) }
        compose.onNodeWithContentDescription(text(R.string.task_subtask_menu)).performClick()
        compose.onNodeWithText(text(R.string.action_delete)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.task_rename)).performClick()
        compose.onNodeWithText(app.getString(R.string.task_current_name, "Menu subtask"))
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()

        compose.runOnIdle { settings.value = settings.value.copy(actions = emptySet()) }
        compose.onNodeWithContentDescription(text(R.string.task_subtask_menu)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.action_back_with_marker)).performClick()
        compose.onNodeWithContentDescription(text(R.string.task_parent_menu)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.task_menu)).assertIsNotEnabled()
        compose.runOnIdle {
            settings.value = settings.value.copy(actions = setOf(TaskMenuAction.ChangeColor))
        }
        compose.onNodeWithContentDescription(text(R.string.task_menu)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.task_parent_menu))
            .assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.task_change_color)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.task_rename)).assertDoesNotExist()
        Assert.assertNull(db.taskDao().getActiveOrNull())
    }

    @Test
    fun taskMenuPreferencesPreserveHiddenAndEmptySelections() {
        val prefs = app.getSharedPreferences("task-menu-settings-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            Assert.assertEquals(TaskMenuSettings(), TaskMenuPreferences.read(prefs))
            val customized = TaskMenuSettings(actions = setOf(TaskMenuAction.Rename))
            TaskMenuPreferences.write(prefs, customized.copy(showButtons = false))
            Assert.assertEquals(
                customized.copy(showButtons = false), TaskMenuPreferences.read(prefs)
            )
            TaskMenuPreferences.write(prefs, TaskMenuPreferences.read(prefs).copy(showButtons = true))
            Assert.assertEquals(customized, TaskMenuPreferences.read(prefs))
            TaskMenuPreferences.write(prefs, customized.copy(actions = emptySet()))
            Assert.assertEquals(emptySet<TaskMenuAction>(), TaskMenuPreferences.read(prefs).actions)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun notificationSwitchDisablesDependentLockControlWithoutChangingItsChoice() {
        val showNotification = mutableStateOf(true)
        val showOnLock = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    true, {}, DailyPlans(), emptyList(), {}, {},
                    showNotification = showNotification.value,
                    onShowNotificationChange = { showNotification.value = it },
                    showTemporaryOnLockScreen = showOnLock.value,
                    onShowTemporaryOnLockScreenChange = { showOnLock.value = it })
            }
        }
        compose.onNodeWithText(text(R.string.settings_display_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_show_temporary_on_lock_screen)).performClick()
        compose.onNodeWithText(text(R.string.settings_show_temporary_on_lock_screen)).assertIsOff()
        compose.onNodeWithText(text(R.string.settings_show_notification)).performClick()
        compose.onNodeWithText(text(R.string.settings_show_temporary_on_lock_screen))
            .assertIsNotEnabled().assertIsOff()
        compose.onNodeWithText(text(R.string.settings_show_notification)).performClick()
        compose.onNodeWithText(text(R.string.settings_show_temporary_on_lock_screen))
            .assertIsEnabled().assertIsOff()
    }

    @Test
    fun runningEditorDoesNotOfferEndTime() {
        compose.setContent {
            MaterialTheme {
                AddTaskScreen(
                    {}, vm, initialEventId = -1L, initialTaskId = 1L,
                    initialStartMs = 1_700_000_000_000L, initialEndMs = 1_700_001_000_000L
                )
            }
        }
        compose.onNodeWithText(text(R.string.record_running_description)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.label_end)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_save)).assertIsDisplayed()
    }

    @Test
    fun customTimelineSelectsAndAppliesBothDates() {
        fun date(day: Int) = java.util.Calendar.getInstance().apply {
            clear(); set(2026, java.util.Calendar.SEPTEMBER, day)
        }.timeInMillis

        val selected = java.util.concurrent.atomic.AtomicReference<Pair<Long, Long>?>(null)
        val range = TimelineRange(
            TimelinePeriod.ThreeDays,
            date(1),
            date(4),
            {},
            {},
            { s, e -> selected.set(s to e) })
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column { TimelineRangeControls(range) }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.timeline_previous)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_previous)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.timeline_custom)).performClick()
        compose.onNodeWithText(text(R.string.timeline_select_range)).assertIsDisplayed()
        val dateLabel = java.text.DateFormat.getDateInstance(
            java.text.DateFormat.FULL, app.resources.configuration.locales[0]
        )
        compose.onNodeWithContentDescription(dateLabel.format(java.util.Date(date(10))))
            .performClick()
        compose.onNodeWithText(text(R.string.action_apply)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(dateLabel.format(java.util.Date(date(12))))
            .performClick()
        Assert.assertNull(selected.get())
        compose.onNodeWithText(text(R.string.action_apply)).performClick()
        Assert.assertEquals(date(10) to date(12), selected.get())

        // Jump across years and months without typing, then select a cross-month range.
        compose.onNodeWithText(text(R.string.timeline_custom)).performClick()
        compose.onNodeWithText("2026").performClick()
        compose.onNodeWithText(text(R.string.timeline_select_year)).assertIsDisplayed()
        compose.onNodeWithText("2025").performClick()
        val months = java.text.DateFormatSymbols(app.resources.configuration.locales[0]).months
        compose.onNodeWithText(months[8]).performClick()
        compose.onNodeWithText(text(R.string.timeline_select_month)).assertIsDisplayed()
        compose.onNodeWithText(months[11]).performClick()
        val start =
            java.util.Calendar.getInstance().apply { clear(); set(2025, 11, 31) }.timeInMillis
        val end = java.util.Calendar.getInstance().apply { clear(); set(2026, 0, 2) }.timeInMillis
        compose.onNodeWithContentDescription(dateLabel.format(java.util.Date(start))).performClick()
        compose.onNodeWithText("2025").performClick()
        compose.onNodeWithText("2026").performClick()
        compose.onNodeWithText(months[11]).performClick()
        compose.onNodeWithText(months[0]).performClick()
        compose.onNodeWithContentDescription(dateLabel.format(java.util.Date(end))).performClick()
        compose.onNodeWithText(text(R.string.action_apply)).performClick()
        Assert.assertEquals(start to end, selected.get())
    }

    @Test
    fun playbackButtonsRecordTasksAndSubtasksAndCanBeHidden() = runBlocking {
        val project = db.taskDao().insertTask(TaskEntity(name = "Playback project"))
        val task = db.taskDao().insertTask(TaskEntity(name = "Playback task", parentId = project))
        val subtask = db.taskDao().insertTask(TaskEntity(name = "Playback subtask", parentId = task))
        val showPlayback = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                DBTaskScreen(
                    vm,
                    sortMode = false,
                    nameSort = NameSort.None,
                    showTaskPlaybackButtons = showPlayback.value
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Playback task").fetchSemanticsNodes().isNotEmpty()
        }
        fun playbackLabel(running: Boolean, name: String) = app.getString(
            if (running) R.string.task_stop_named else R.string.task_start_named, name
        )

        // The icon starts this task directly, even when the row opens a subtask picker.
        compose.onNodeWithContentDescription(playbackLabel(false, "Playback task")).performClick()
        compose.waitUntil(10_000) { vm.activeUi.value?.taskId == task }
        Assert.assertEquals(task, db.taskDao().getActiveOrNull()!!.taskId)
        compose.onNodeWithContentDescription(playbackLabel(true, "Playback task")).performClick()
        compose.waitUntil(10_000) { vm.activeUi.value == null }
        Assert.assertNull(db.taskDao().getActiveOrNull())

        // Hiding the controls leaves task browsing available at both levels.
        compose.runOnIdle { showPlayback.value = false }
        compose.onNodeWithContentDescription(playbackLabel(false, "Playback task"))
            .assertDoesNotExist()
        // Tap the actual label: the merged row's center can overlap an action button.
        compose.onNodeWithText("Playback task", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Playback subtask").assertIsDisplayed()
        compose.onNodeWithContentDescription(playbackLabel(false, "Playback subtask"))
            .assertDoesNotExist()
        compose.runOnIdle { showPlayback.value = true }
        compose.onNodeWithContentDescription(playbackLabel(false, "Playback subtask")).performClick()
        compose.waitUntil(10_000) { vm.activeUi.value?.taskId == subtask }

        // A running subtask can also be stopped from its parent task's row.
        compose.onNodeWithContentDescription(playbackLabel(true, "Playback task")).performClick()
        compose.waitUntil(10_000) { vm.activeUi.value == null }
        Assert.assertNull(db.taskDao().getActiveOrNull())

        compose.onNodeWithText("Playback task", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription(playbackLabel(false, "Playback subtask")).performClick()
        compose.waitUntil(10_000) { vm.activeUi.value?.taskId == subtask }
        compose.onNodeWithText("Playback task", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription(playbackLabel(true, "Playback subtask")).performClick()
        compose.waitUntil(10_000) { vm.activeUi.value == null }
        Assert.assertNull(db.taskDao().getActiveOrNull())
        val recordedTasks = db.taskDao().getAllEventsForCsv().map { it.taskId }
        Assert.assertEquals(1, recordedTasks.count { it == task })
        Assert.assertEquals(2, recordedTasks.count { it == subtask })
    }

    @Test
    fun selectingNoneRecordsTheTaskWithSubtasks() = runBlocking {
        val parent = db.taskDao().insertTask(TaskEntity(name = "UI Project"))
        val task = db.taskDao().insertTask(TaskEntity(name = "UI Task", parentId = parent))
        db.taskDao().insertTask(TaskEntity(name = "UI Subtask", parentId = task))
        compose.setContent {
            MaterialTheme {
                DBTaskScreen(
                    vm,
                    sortMode = false,
                    nameSort = NameSort.None
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("UI Task").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("UI Task", useUnmergedTree = true).performClick()
        compose.onNodeWithText(text(R.string.subtask_none)).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.activeUi.value?.taskId == task }
        Assert.assertEquals(task, db.taskDao().getActiveOrNull()!!.taskId)
    }

    @Test
    fun longPressInAnalysisOffersRenameWithoutStartingTask() = runBlocking {
        val parent = db.taskDao().insertTask(TaskEntity(name = "UI Project"))
        db.taskDao().insertTask(TaskEntity(name = "UI Task", parentId = parent))
        compose.setContent { MaterialTheme { TaskAnalysisScreen(vm); TaskEditDialogs(vm) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("UI Task").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("UI Task").performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.task_rename)).assertIsDisplayed()
        Assert.assertNull(db.taskDao().getActiveOrNull())
    }

    @Test
    fun timelineEditPassesOriginalCrossMidnightBounds() = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Night record"))
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val start = midnight - 25 * 3_600_000L
        val end = midnight + 7_200_000L
        db.taskDao().insertEvent(
            io.github.hurokume.xrec.data.db.TaskEventEntity(
                taskId = task, pressedAtEpochMs = start, durationMillis = end - start
            )
        )
        val opened = java.util.concurrent.atomic.AtomicReference<Pair<Long, Long>?>(null)
        compose.setContent {
            MaterialTheme {
                AppRoot(vm, {}, { _, _, s, e -> opened.set(s to e) })
            }
        }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.action_today)).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.action_today)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Night record").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Night record").performClick()
        // Today's visible fragment is two hours, but the selected record spans 27 hours.
        compose.onNodeWithText("(27:00)", substring = true).assertIsDisplayed()
        val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        compose.onNodeWithText(
            "${clock.format(java.util.Date(start))} - ${
                clock.format(
                    java.util.Date(
                        end
                    )
                )
            }"
        )
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.bottom_edit_record)).performClick()
        compose.waitUntil(10_000) { opened.get() != null }
        Assert.assertEquals(start to end, opened.get())
    }
}
