package io.github.hurokume.xrec

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.TaskTemplateType
import io.github.hurokume.xrec.data.TemplateApplyMode
import io.github.hurokume.xrec.ui.TemplateScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TemplateScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun selectingOnlyPreviewsAndAddMustBeChosenExplicitly() {
        var applied: Pair<TaskTemplateType, TemplateApplyMode>? = null
        compose.setContent {
            MaterialTheme { TemplateScreen(false, { template, mode -> applied = template to mode }) }
        }
        compose.onNodeWithText(text(R.string.help_template_undergraduates)).performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText(text(R.string.template_job_hunting)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("• ${text(R.string.template_dates)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.help_template_add)).performClick()
        compose.runOnIdle { assertEquals(TaskTemplateType.Undergraduates to TemplateApplyMode.Add, applied) }
        compose.onNodeWithText(text(R.string.help_template_replace_warning_title)).assertDoesNotExist()
    }

    @Test
    fun replacementRequiresWarningConfirmationAndCancellationDoesNothing() {
        val applied = mutableListOf<Pair<TaskTemplateType, TemplateApplyMode>>()
        compose.setContent {
            MaterialTheme { TemplateScreen(false, { template, mode -> applied.add(template to mode) }) }
        }
        compose.onNodeWithText(text(R.string.help_template_secondary_students)).performClick()
        compose.onNodeWithText(text(R.string.help_template_replace)).performClick()
        compose.onNodeWithText(text(R.string.help_template_replace_warning_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(
            R.string.help_template_replace_warning,
            text(R.string.help_template_secondary_students)
        )).assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<Pair<TaskTemplateType, TemplateApplyMode>>(), applied) }
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertEquals(emptyList<Pair<TaskTemplateType, TemplateApplyMode>>(), applied) }
        compose.onNodeWithText(text(R.string.help_template_add)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.help_template_replace)).performClick()
        compose.onNodeWithText(text(R.string.help_template_replace_confirm)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(TaskTemplateType.SecondaryStudents to TemplateApplyMode.ReplaceAll), applied)
        }
    }

    @Test
    fun switchingFromUndergraduatesPreviewsAndAddsSecondaryStudentTasks() {
        var applied: Pair<TaskTemplateType, TemplateApplyMode>? = null
        compose.setContent {
            MaterialTheme { TemplateScreen(false, { template, mode -> applied = template to mode }) }
        }
        compose.onNodeWithText(text(R.string.help_template_undergraduates)).performClick()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.onNodeWithText(text(R.string.help_template_secondary_students)).performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText(context.getString(R.string.help_template_summary, 6, 24)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.template_school)).assertIsDisplayed()
        compose.onNodeWithText("• ${text(R.string.template_homework)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("• ${text(R.string.template_school_clubs)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.template_job_hunting)).assertDoesNotExist()
        compose.onNodeWithText("• ${text(R.string.template_school_commute)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.help_template_add)).performClick()
        compose.runOnIdle { assertEquals(TaskTemplateType.SecondaryStudents to TemplateApplyMode.Add, applied) }
    }

    @Test
    fun lawStudentTemplateCanBePreviewedAddedAndReplaced() {
        assertTemplateCanBePreviewedAddedAndReplaced(
            TaskTemplateType.LawStudents, 7, 24, R.string.template_bar_essays
        )
    }

    @Test
    fun workingAdultTemplateCanBePreviewedAddedAndReplaced() {
        assertTemplateCanBePreviewedAddedAndReplaced(
            TaskTemplateType.WorkingAdults, 6, 23, R.string.template_work_commute
        )
    }

    @Test
    fun homemakerTemplateCanBePreviewedAddedAndReplaced() {
        assertTemplateCanBePreviewedAddedAndReplaced(
            TaskTemplateType.Homemakers, 7, 23, R.string.template_caregiving
        )
    }

    private fun assertTemplateCanBePreviewedAddedAndReplaced(
        templateType: TaskTemplateType,
        projects: Int,
        tasks: Int,
        representativeTask: Int
    ) {
        val applied = mutableListOf<Pair<TaskTemplateType, TemplateApplyMode>>()
        compose.setContent {
            MaterialTheme { TemplateScreen(false, { template, mode -> applied.add(template to mode) }) }
        }
        compose.onNodeWithText(text(R.string.help_template_search)).assertIsNotEnabled()
        compose.onNodeWithText(text(templateType.titleRes)).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(context.getString(R.string.help_template_summary, projects, tasks)).assertIsDisplayed()
        compose.onNodeWithText("• ${text(representativeTask)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("• ${text(R.string.template_other_travel)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertEquals(emptyList<Pair<TaskTemplateType, TemplateApplyMode>>(), applied) }

        compose.onNodeWithText(text(templateType.titleRes)).performClick()
        compose.onNodeWithText(text(R.string.help_template_add)).performClick()
        compose.runOnIdle { assertEquals(listOf(templateType to TemplateApplyMode.Add), applied) }

        compose.onNodeWithText(text(templateType.titleRes)).performClick()
        compose.onNodeWithText(text(R.string.help_template_replace)).performClick()
        compose.onNodeWithText(context.getString(
            R.string.help_template_replace_warning, text(templateType.titleRes)
        )).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertEquals(listOf(templateType to TemplateApplyMode.Add), applied) }
        compose.onNodeWithText(text(R.string.help_template_replace)).performClick()
        compose.onNodeWithText(text(R.string.help_template_replace_confirm)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(templateType to TemplateApplyMode.Add, templateType to TemplateApplyMode.ReplaceAll), applied)
        }
    }

    @Test
    fun searchAndApplyingStateCannotStartAnotherApply() {
        compose.setContent {
            MaterialTheme { TemplateScreen(true, { _, _ -> error("Must stay disabled") }) }
        }
        compose.onNodeWithText(text(R.string.help_template_applying)).assertIsDisplayed()
        listOf(
            R.string.help_template_search,
            R.string.help_template_secondary_students,
            R.string.help_template_undergraduates,
            R.string.help_template_law_students,
            R.string.help_template_working_adults,
            R.string.help_template_homemakers
        ).forEach { compose.onNodeWithText(text(it)).assertIsNotEnabled() }
    }
}
