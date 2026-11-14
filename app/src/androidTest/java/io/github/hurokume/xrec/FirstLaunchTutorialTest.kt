package io.github.hurokume.xrec

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.TaskTemplateType
import io.github.hurokume.xrec.data.db.AppDatabase
import io.github.hurokume.xrec.data.db.TaskEntity
import io.github.hurokume.xrec.ui.FirstLaunchTutorialHost
import io.github.hurokume.xrec.ui.TaskViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FirstLaunchTutorialTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val preferenceName = "first-launch-test-${UUID.randomUUID()}"
    private lateinit var preferences: SharedPreferences
    private lateinit var db: AppDatabase
    private lateinit var vm: TaskViewModel
    private val store = ViewModelStore()
    private val launch = mutableIntStateOf(0)

    @Before fun setup() = runBlocking {
        preferences = app.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        vm = TaskViewModel(app, db)
        store.put("test", vm)
        val project = db.taskDao().insertTask(TaskEntity(name = "Existing project"))
        db.taskDao().insertTask(TaskEntity(name = "Existing task", parentId = project))
        Unit
    }

    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close()
        app.deleteSharedPreferences(preferenceName)
    }

    @Composable private fun TestApp() {
        MaterialTheme {
            key(launch.intValue) {
                FirstLaunchTutorialHost(preferences) { initialTemplate ->
                    AppRoot(vm, {}, { _, _, _, _ -> }, initialTemplate)
                }
            }
        }
    }

    private fun text(id: Int) = app.getString(id)
    private fun next() = compose.onNodeWithText(text(R.string.action_next)).performClick()
    private fun freshLaunch() = compose.runOnIdle { launch.intValue++ }
    private fun assertHome() {
        compose.onNodeWithText(text(R.string.tutorial_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.label_select_task)).assertIsDisplayed()
    }

    @Test fun completingOpensSelectedPreviewWithoutChangingTasksAndOnlyRunsOnce() {
        val original = runBlocking { db.taskDao().getAllTasksOnce() }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TestApp() }
        compose.onNodeWithText(text(R.string.tutorial_record_title)).assertIsDisplayed()
        next()
        compose.onNodeWithText(text(R.string.tutorial_organize_title)).assertIsDisplayed()
        next()
        compose.onNodeWithText(text(R.string.tutorial_templates_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_preview_template)).assertIsNotEnabled()
        TaskTemplateType.entries.forEach {
            compose.onNodeWithText(text(it.titleRes)).performScrollTo().assertIsNotSelected()
        }
        compose.onNodeWithText(text(R.string.help_template_homemakers)).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.tutorial_templates_hint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_preview_template)).performClick()
        compose.onNodeWithText(text(R.string.template_home_chores)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.help_template_summary, 7, 23)).assertIsDisplayed()
        assertEquals(original, runBlocking { db.taskDao().getAllTasksOnce() })
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.template_home_chores)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.onNodeWithText(text(R.string.help_template_list)).assertIsDisplayed()
        TaskTemplateType.entries.forEach {
            compose.onNodeWithText(text(it.titleRes)).performScrollTo().assertIsDisplayed()
        }
        assertEquals(original, runBlocking { db.taskDao().getAllTasksOnce() })

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.help_template_list)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.help_template_add)).assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithText(text(R.string.help_privacy_policy_title)).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.help_privacy_policy_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.help_template_title)).performClick()
        compose.onNodeWithText(text(R.string.help_template_list)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.help_template_add)).assertDoesNotExist()
        freshLaunch()
        assertHome()
    }

    @Test fun selectedTemplateSurvivesRecreationAndBackAndAddsTheLatestSelection() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TestApp() }
        next()
        next()
        compose.onNodeWithText(text(R.string.help_template_law_students)).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.help_template_law_students)).assertIsSelected()
        compose.onNodeWithText(text(R.string.help_template_working_adults)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.help_template_law_students)).assertIsNotSelected()
        compose.onNodeWithText(text(R.string.action_back)).performClick()
        next()
        compose.onNodeWithText(text(R.string.help_template_working_adults)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(text(R.string.tutorial_preview_template)).performClick()
        compose.onNodeWithText(text(R.string.template_work)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.help_template_summary, 6, 23)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.help_template_add)).performClick()
        compose.waitUntil(10_000) {
            !vm.templateApplying.value && runBlocking { db.taskDao().getAllTasksOnce().size == 31 }
        }
        val roots = runBlocking { db.taskDao().getChildrenOnce(null) }
        assertEquals(
            listOf("Existing project") + TaskTemplateType.WorkingAdults.projects { text(it) }.map { it.name },
            roots.map { it.name }
        )
        val originalChildren = runBlocking { db.taskDao().getChildren(roots.first().id) }
        assertEquals(listOf("Existing task"), originalChildren.map { it.name })
    }

    @Test fun skippingFromMiddleOpensHomeAndPersistsAcrossLaunches() {
        compose.setContent { TestApp() }
        next()
        compose.onNodeWithText(text(R.string.tutorial_skip)).performClick()
        assertHome()
        freshLaunch()
        assertHome()
    }

    @Test fun unfinishedTutorialRestoresStepAndBackNavigationWithoutMarkingComplete() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TestApp() }
        next()
        next()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.tutorial_templates_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_organize_title)).assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText(text(R.string.tutorial_record_title)).assertIsDisplayed()
        next()
        freshLaunch()
        compose.onNodeWithText(text(R.string.tutorial_record_title)).assertIsDisplayed()
    }

    @Test fun systemBackFromFirstStepSkipsAndPersists() {
        compose.setContent { TestApp() }
        Espresso.pressBack()
        assertHome()
        freshLaunch()
        assertHome()
    }
}
