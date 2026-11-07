package io.github.hurokume.xrec

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.DailyPlans
import io.github.hurokume.xrec.data.WorkHistoryAction
import io.github.hurokume.xrec.data.db.WorkHistoryItem
import io.github.hurokume.xrec.ui.SettingsScreen
import io.github.hurokume.xrec.ui.WorkHistoryScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkHistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun settingsOffersWorkHistory() {
        var opened = false
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    true,
                    {},
                    DailyPlans(),
                    emptyList(),
                    {},
                    {},
                    onWorkHistoryClick = { opened = true })
            }
        }
        compose.onNodeWithText(text(R.string.work_history_title)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun restorationRequiresConfirmationAndCanBeCancelled() {
        var restored: Pair<Long, Long>? = null
        val entry = WorkHistoryItem(
            42,
            1_700_000_000_000,
            WorkHistoryAction.RenameTask.name,
            "Old → New",
            1,
            0
        )
        compose.setContent {
            MaterialTheme {
                WorkHistoryScreen(listOf(entry), false, { id, latest -> restored = id to latest })
            }
        }
        compose.onNodeWithText(text(R.string.work_history_details)).performScrollTo().performClick()
        compose.onNodeWithText(entry.details).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.work_history_restore_before)).performScrollTo()
            .performClick()
        compose.onNodeWithText(text(R.string.work_history_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.work_history_confirm_message, 1))
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertNull(restored) }
        compose.onNodeWithText(text(R.string.work_history_restore_before)).performScrollTo()
            .performClick()
        compose.onNodeWithText(text(R.string.work_history_restore_action)).performClick()
        compose.runOnIdle { assertEquals(42L to 42L, restored) }
    }

    @Test
    fun emptyHistoryExplainsThatNewChangesWillBeRecorded() {
        compose.setContent { MaterialTheme { WorkHistoryScreen(emptyList(), false, { _, _ -> }) } }
        compose.onNodeWithText(text(R.string.work_history_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.work_history_restore_before)).assertDoesNotExist()
    }
}
