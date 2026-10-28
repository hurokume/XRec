package com.example.timerecorder

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timerecorder.data.TaskAnalysisPeriod
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.TaskEventEntity
import com.example.timerecorder.ui.TaskAnalysisScreen
import com.example.timerecorder.ui.TaskViewModel
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskAnalysisSummaryTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as Application
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

    private fun choose(label: Int) {
        compose.onNodeWithText(app.getString(label)).performScrollTo().performClick()
    }

    private fun assertSummary(label: Int, durationMs: Long) {
        val seconds = durationMs / 1000
        val duration = String.format(
            Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60
        )
        compose.onNodeWithTag("task-analysis-statistic").performScrollTo()
            .assert(hasAnyDescendant(hasText(app.getString(label))))
            .assert(hasAnyDescendant(hasText(duration)))
    }

    @Test fun summarySwitchesBetweenCalendarAveragesAndCumulativeTotal(): Unit = runBlocking {
        val firstTask = db.taskDao().insertTask(TaskEntity(name = "Work"))
        val secondTask = db.taskDao().insertTask(TaskEntity(name = "Study"))
        val previousYearMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = previousYearMonth.timeInMillis
        val hour = 3_600_000L
        for ((taskId, offset, duration) in listOf(
            Triple(firstTask, hour, hour),
            Triple(secondTask, 3 * hour, 3 * hour),
            Triple(firstTask, 49 * hour, 2 * hour)
        )) {
            db.taskDao().insertEvent(
                TaskEventEntity(taskId = taskId, pressedAtEpochMs = start + offset, durationMillis = duration)
            )
        }
        compose.setContent { MaterialTheme { TaskAnalysisScreen(vm) } }
        compose.waitUntil(10_000) { vm.parents.value.size == 2 }
        compose.onNodeWithContentDescription(app.getString(R.string.analysis_analyze_all)).performClick()
        choose(R.string.analysis_period_year)
        compose.onNodeWithContentDescription(app.getString(R.string.analysis_previous_period))
            .performClick()
        compose.waitUntil(10_000) {
            val result = vm.taskAnalysis.value.result
            result?.request?.period == TaskAnalysisPeriod.Year && result.totalMs == 6 * hour
        }

        assertSummary(R.string.analysis_average_month, hour / 2)
        choose(R.string.analysis_chart_cumulative_stacked)
        assertSummary(R.string.analysis_cumulative_total, 6 * hour)
        choose(R.string.analysis_chart_period_total)
        assertSummary(R.string.analysis_average_month, hour / 2)

        choose(R.string.analysis_period_month)
        compose.waitUntil(10_000) {
            val result = vm.taskAnalysis.value.result
            result?.request?.period == TaskAnalysisPeriod.Month && result.totalMs == 6 * hour
        }
        val dailyAverage = 6 * hour / previousYearMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        assertSummary(R.string.analysis_average_day, dailyAverage)
        choose(R.string.analysis_chart_cumulative_stacked)
        assertSummary(R.string.analysis_cumulative_total, 6 * hour)
        choose(R.string.analysis_chart_heatmap)
        assertSummary(R.string.analysis_average_day, dailyAverage)
    }
}
