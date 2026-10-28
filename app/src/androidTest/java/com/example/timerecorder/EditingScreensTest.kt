package com.example.timerecorder

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timerecorder.data.DailyPlans
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*

class EditingScreensTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var db: AppDatabase
    private lateinit var vm: TaskViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        vm = TaskViewModel(app, db)
        store.put("test", vm)
    }
    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close()
    }
    private fun text(id: Int) = app.getString(id)

    @Test fun statisticsOffersNewPeriodsAndUpdatesSummary() {
        compose.setContent { MaterialTheme { AppRoot(vm, {}, { _, _, _, _ -> }) } }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.stats_title)).fetchSemanticsNodes().isNotEmpty()
        }
        for (label in listOf(R.string.action_today, R.string.stats_yesterday,
            R.string.stats_one_day, R.string.stats_one_year)) {
            compose.onNodeWithText(text(label)).performScrollTo().performClick()
            compose.onNodeWithText(app.getString(R.string.stats_selected_period_summary, text(label), 0L))
                .assertIsDisplayed()
        }
    }

    @Test fun settingsOpensMenuThenSeparateDisplayPage() {
        compose.setContent { MaterialTheme {
            SettingsScreen(true, {}, DailyPlans(), emptyList(), {}, {})
        } }
        compose.onNodeWithText(text(R.string.settings_display_title)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.settings_stop_button_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.daily_plan_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.timeline_custom)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_timeline_default)).performClick()
        compose.onNodeWithText(text(R.string.timeline_custom)).assertIsDisplayed()
        compose.onNodeWithText("Monthly").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.daily_plan_title)).assertIsDisplayed()
    }

    @Test fun runningEditorDoesNotOfferEndTime() {
        compose.setContent { MaterialTheme {
            AddTaskScreen({}, vm, initialEventId = -1L, initialTaskId = 1L,
                initialStartMs = 1_700_000_000_000L, initialEndMs = 1_700_001_000_000L)
        } }
        compose.onNodeWithText(text(R.string.record_running_description)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.label_end)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_save)).assertIsDisplayed()
    }

    @Test fun customTimelineSelectsAndAppliesBothDates() {
        fun date(day: Int) = java.util.Calendar.getInstance().apply {
            clear(); set(2026, java.util.Calendar.SEPTEMBER, day)
        }.timeInMillis
        val selected = java.util.concurrent.atomic.AtomicReference<Pair<Long, Long>?>(null)
        val range = TimelineRange(TimelinePeriod.ThreeDays, date(1), date(4), {}, {}, { s, e -> selected.set(s to e) })
        compose.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column { TimelineRangeControls(range) }
        } }
        compose.onNodeWithContentDescription(text(R.string.timeline_previous)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_previous)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.timeline_custom)).performClick()
        compose.onNodeWithText(text(R.string.timeline_select_range)).assertIsDisplayed()
        val dateLabel = java.text.DateFormat.getDateInstance(
            java.text.DateFormat.FULL, app.resources.configuration.locales[0]
        )
        compose.onNodeWithContentDescription(dateLabel.format(java.util.Date(date(10)))).performClick()
        compose.onNodeWithText(text(R.string.action_apply)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(dateLabel.format(java.util.Date(date(12)))).performClick()
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
        val start = java.util.Calendar.getInstance().apply { clear(); set(2025, 11, 31) }.timeInMillis
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

    @Test fun selectingNoneRecordsTheTaskWithSubtasks() = runBlocking {
        val parent = db.taskDao().insertTask(TaskEntity(name = "UI Project"))
        val task = db.taskDao().insertTask(TaskEntity(name = "UI Task", parentId = parent))
        db.taskDao().insertTask(TaskEntity(name = "UI Subtask", parentId = task))
        compose.setContent { MaterialTheme { DBTaskScreen(vm, sortMode = false, nameSort = NameSort.None) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("UI Task").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("UI Task").performClick()
        compose.onNodeWithText(text(R.string.subtask_none)).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.activeUi.value?.taskId == task }
        Assert.assertEquals(task, db.taskDao().getActiveOrNull()!!.taskId)
    }

    @Test fun longPressInAnalysisOffersRenameWithoutStartingTask() = runBlocking {
        val parent = db.taskDao().insertTask(TaskEntity(name = "UI Project"))
        db.taskDao().insertTask(TaskEntity(name = "UI Task", parentId = parent))
        compose.setContent { MaterialTheme { TaskAnalysisScreen(vm); TaskEditDialogs(vm) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("UI Task").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("UI Task").performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.task_rename)).assertIsDisplayed()
        Assert.assertNull(db.taskDao().getActiveOrNull())
    }

    @Test fun timelineEditPassesOriginalCrossMidnightBounds() = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Night record"))
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val start = midnight - 25 * 3_600_000L
        val end = midnight + 7_200_000L
        db.taskDao().insertEvent(com.example.timerecorder.data.db.TaskEventEntity(
            taskId = task, pressedAtEpochMs = start, durationMillis = end - start))
        val opened = java.util.concurrent.atomic.AtomicReference<Pair<Long, Long>?>(null)
        compose.setContent { MaterialTheme {
            AppRoot(vm, {}, { _, _, s, e -> opened.set(s to e) })
        } }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text(R.string.action_today)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.action_today)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Night record").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Night record").performClick()
        // Today's visible fragment is two hours, but the selected record spans 27 hours.
        compose.onNodeWithText("(27:00)", substring = true).assertIsDisplayed()
        val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        compose.onNodeWithText("${clock.format(java.util.Date(start))} - ${clock.format(java.util.Date(end))}")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.bottom_edit_record)).performClick()
        compose.waitUntil(10_000) { opened.get() != null }
        Assert.assertEquals(start to end, opened.get())
    }
}
