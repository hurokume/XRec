package com.example.timerecorder

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.TaskEventEntity
import com.example.timerecorder.ui.TaskViewModel
import com.example.timerecorder.ui.calendarDay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date

class StatisticsCustomRangeTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var db: AppDatabase
    private lateinit var vm: TaskViewModel
    private val store = ViewModelStore()
    private val locale get() = app.resources.configuration.locales[0]

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
    private fun date(month: Int, day: Int, hour: Int = 0) = Calendar.getInstance().apply {
        clear(); set(2026, month - 1, day, hour, 0)
    }.timeInMillis

    private fun openCustom() {
        compose.onNodeWithText(text(R.string.stats_custom)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.timeline_select_range)).assertIsDisplayed()
    }

    private fun selectDate(displayed: Long, selected: Long) {
        val from = Calendar.getInstance().apply { timeInMillis = displayed }
        val to = Calendar.getInstance().apply { timeInMillis = selected }
        if (from.get(Calendar.YEAR) != to.get(Calendar.YEAR)) {
            compose.onNodeWithText(from.get(Calendar.YEAR).toString()).performClick()
            compose.onNodeWithText(to.get(Calendar.YEAR).toString()).performClick()
        }
        if (from.get(Calendar.MONTH) != to.get(Calendar.MONTH)) {
            val names = DateFormatSymbols(locale).months
            compose.onNodeWithText(names[from.get(Calendar.MONTH)]).performClick()
            compose.onNodeWithText(names[to.get(Calendar.MONTH)]).performClick()
        }
        val label = DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date(selected))
        compose.onNodeWithContentDescription(label).performClick()
    }

    private fun rangeLabel(start: Long, end: Long): String {
        val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        return app.getString(R.string.date_range, formatter.format(Date(start)), formatter.format(Date(end)))
    }

    private fun assertTotal(minutes: Long) {
        val summary = app.getString(R.string.stats_selected_period_summary, text(R.string.stats_custom), minutes)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(summary).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(summary).assertIsDisplayed()
    }

    @Test fun customDatesApplyCancelRestoreAndSupportSingleDay(): Unit = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Custom range task"))
        val start = date(1, 31)
        val end = date(2, 2)
        val endExclusive = date(2, 3)
        for ((s, e) in listOf(
            start - 3_600_000L to start + 3_600_000L,
            date(2, 1, 10) to date(2, 1, 12),
            endExclusive - 1_800_000L to endExclusive + 1_800_000L,
            start - 7_200_000L to start,
            endExclusive to endExclusive + 7_200_000L
        )) {
            db.taskDao().insertEvent(TaskEventEntity(taskId = task, pressedAtEpochMs = s, durationMillis = e - s))
        }
        val initial = calendarDay(System.currentTimeMillis(), -7)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { StatsScreen(vm) } }
        openCustom()
        selectDate(initial, start)
        compose.onNodeWithText(text(R.string.action_apply)).assertIsNotEnabled()
        selectDate(start, end)
        compose.onNodeWithText(text(R.string.action_apply)).performClick()
        assertTotal(210)
        compose.onNodeWithText(rangeLabel(start, end)).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        assertTotal(210)
        compose.onNodeWithText(rangeLabel(start, end)).performClick()
        selectDate(start, start)
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        assertTotal(210)
        compose.onNodeWithText(rangeLabel(start, end)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.action_today)).performScrollTo().performClick()
        openCustom()
        val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        compose.onNodeWithText(app.getString(R.string.timeline_range_start, formatter.format(Date(start))))
            .assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.timeline_range_end, formatter.format(Date(end))))
            .assertIsDisplayed()
        selectDate(start, end)
        selectDate(end, end)
        compose.onNodeWithText(text(R.string.action_apply)).performClick()
        assertTotal(30)
        compose.onNodeWithText(rangeLabel(end, end)).assertIsDisplayed()
    }

    @Test fun customRangeCountsRunningTaskOnlyThroughNow(): Unit = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Running task"))
        val now = System.currentTimeMillis()
        TaskRepository(db, db.sessionDao(), db.taskDao()).startOrSwitch(task, now - 600_000L)
        val start = calendarDay(now, -1)
        val end = calendarDay(now, 1)
        compose.setContent { MaterialTheme { StatsScreen(vm) } }
        openCustom()
        selectDate(calendarDay(now, -7), start)
        selectDate(start, end)
        compose.onNodeWithText(text(R.string.action_apply)).performClick()
        assertTotal(10)

        // A wholly future period must not count any of the running record.
        openCustom()
        selectDate(start, end)
        selectDate(end, end)
        compose.onNodeWithText(text(R.string.action_apply)).performClick()
        assertTotal(0)
        compose.onNodeWithText(text(R.string.stats_no_data)).assertIsDisplayed()
    }
}
