package com.example.timerecorder

import android.app.Application
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.TaskEventEntity
import com.example.timerecorder.ui.AddTaskScreen
import com.example.timerecorder.ui.TaskViewModel
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CrossDayRecordEditingTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var db: AppDatabase
    private lateinit var vm: TaskViewModel
    private val store = ViewModelStore()
    private var taskId = 0L
    private val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        vm = TaskViewModel(app, db)
        store.put("test", vm)
        taskId = db.taskDao().insertTask(TaskEntity(name = "Night record"))
    }

    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close()
    }

    private fun date(dayOffset: Int, hour: Int, minute: Int = 0): Long =
        (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis

    private fun addRecord(start: Long, end: Long): Long = runBlocking {
        db.taskDao().insertEvent(TaskEventEntity(
            taskId = taskId, pressedAtEpochMs = start, durationMillis = end - start
        ))
    }

    private fun openTimelineSplit() {
        compose.setContent { MaterialTheme { AppRoot(vm, {}, { _, _, _, _ -> }) } }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(app.getString(R.string.action_today)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(app.getString(R.string.action_today)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Night record").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Night record").performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.bottom_split_record)).performClick()
        compose.waitForIdle()
    }

    private fun <T : View> updatePicker(type: Class<T>, update: (T) -> Unit) {
        onView(isAssignableFrom(type)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(type)
            override fun getDescription() = "Update ${type.simpleName}"
            override fun perform(uiController: UiController, view: View) {
                update(type.cast(view)!!)
                uiController.loopMainThreadUntilIdle()
            }
        })
    }

    private fun pickDateTime(value: Long, expectedInitial: Long? = null) {
        val picked = Calendar.getInstance().apply { timeInMillis = value }
        val initial = expectedInitial?.let { Calendar.getInstance().apply { timeInMillis = it } }
        updatePicker(DatePicker::class.java) { picker ->
            initial?.let {
                assertEquals(it.get(Calendar.YEAR), picker.year)
                assertEquals(it.get(Calendar.MONTH), picker.month)
                assertEquals(it.get(Calendar.DAY_OF_MONTH), picker.dayOfMonth)
            }
            picker.updateDate(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH))
        }
        onView(withId(android.R.id.button1)).perform(click())
        updatePicker(TimePicker::class.java) { picker ->
            initial?.let {
                assertEquals(it.get(Calendar.HOUR_OF_DAY), picker.hour)
                assertEquals(it.get(Calendar.MINUTE), picker.minute)
            }
            picker.hour = picked.get(Calendar.HOUR_OF_DAY)
            picker.minute = picked.get(Calendar.MINUTE)
        }
        onView(withId(android.R.id.button1)).perform(click())
    }

    private fun assertSplit(start: Long, split: Long, end: Long, originalId: Long) {
        compose.waitUntil(10_000) { runBlocking { db.taskDao().getAllEventsForCsv().size == 2 } }
        val records = runBlocking { db.taskDao().getAllEventsForCsv() }
        assertEquals(originalId, records[0].eventId)
        assertEquals(listOf(taskId, taskId), records.map { it.taskId })
        assertEquals(listOf(start, split), records.map { it.pressedAtEpochMs })
        assertEquals(listOf(split - start, end - split), records.map { it.durationMillis })
        assertEquals(end - start, records.sumOf { it.durationMillis })
    }

    @Test fun overnightRecordSplitsOnFollowingDate() {
        val start = date(-1, 23); val end = date(0, 2); val split = date(0, 1)
        val id = addRecord(start, end)
        openTimelineSplit()
        pickDateTime(split, start + (end - start) / 2)
        assertSplit(start, split, end, id)
    }

    @Test fun multiDayRecordCanSplitOutsideTheVisibleDay() {
        val start = date(-2, 23); val end = date(0, 2); val split = date(-1, 1, 30)
        val id = addRecord(start, end)
        openTimelineSplit()
        pickDateTime(split, start + (end - start) / 2)
        assertSplit(start, split, end, id)
    }

    @Test fun midnightIsAValidSplitInsideTheRecord() {
        val start = date(-1, 23); val end = date(0, 2); val split = date(0, 0)
        val id = addRecord(start, end)
        openTimelineSplit()
        pickDateTime(split)
        assertSplit(start, split, end, id)
    }

    @Test fun sameDayRecordUsesTheSameSplitFlow() {
        val start = date(0, 1); val end = date(0, 3); val split = date(0, 2)
        val id = addRecord(start, end)
        openTimelineSplit()
        pickDateTime(split, split)
        assertSplit(start, split, end, id)
    }

    @Test fun splitBeforeRecordIsRejectedWithoutChangingIt() {
        val start = date(-1, 23); val end = date(0, 2)
        val id = addRecord(start, end)
        openTimelineSplit()
        pickDateTime(date(-1, 1))
        compose.onNodeWithText(app.getString(R.string.error_split_time_out_of_range)).assertIsDisplayed()
        val record = runBlocking { db.taskDao().getAllEventsForCsv().single() }
        assertEquals(id, record.eventId)
        assertEquals(start, record.pressedAtEpochMs)
        assertEquals(end - start, record.durationMillis)
        compose.onNodeWithContentDescription(app.getString(R.string.bottom_split_record)).assertIsDisplayed()
    }

    @Test fun editingMultiDayRecordUpdatesBothBoundsAndOverlappingNeighbors() {
        val start = date(-2, 23); val end = date(0, 2)
        val newStart = date(-2, 22, 30); val newEnd = date(0, 3, 30)
        val before = addRecord(date(-2, 21), start)
        val id = addRecord(start, end)
        val after = addRecord(end, date(0, 4))
        val closed = AtomicBoolean(false)
        compose.setContent { MaterialTheme {
            AddTaskScreen({ closed.set(true) }, vm, id, taskId, start, end)
        } }
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        compose.onNodeWithText(format.format(Date(start))).performTouchInput { click() }
        pickDateTime(newStart, start)
        compose.onNodeWithText(format.format(Date(end))).performTouchInput { click() }
        pickDateTime(newEnd, end)
        compose.onNodeWithText(format.format(Date(newStart))).assertIsDisplayed()
        compose.onNodeWithText(format.format(Date(newEnd))).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.action_save)).performClick()
        compose.waitUntil(10_000) {
            closed.get() && runBlocking { db.taskDao().getEventById(id)?.durationMillis == newEnd - newStart }
        }
        val records = runBlocking { db.taskDao().getAllEventsForCsv() }
        assertEquals(listOf(before, id, after), records.map { it.eventId })
        assertEquals(listOf(date(-2, 21), newStart, newEnd), records.map { it.pressedAtEpochMs })
        assertEquals(listOf(newStart, newEnd, date(0, 4)), records.map { it.pressedAtEpochMs + it.durationMillis })
    }
}
