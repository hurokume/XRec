package com.example.timerecorder

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun settingsPersistIndependentlyAndNotifyObservers() = runBlocking {
        val prefs = context.getSharedPreferences("notification-settings-test", Context.MODE_PRIVATE)
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
        prefs.edit().clear().commit()
        try {
            assertEquals(TaskNotificationSettings(), TaskNotificationPreferences.read(isolated))
            val observed = async {
                withTimeout(5_000) {
                    TaskNotificationPreferences.observe(isolated).first { !it.showNotification }
                }
            }
            TaskNotificationPreferences.setShowTemporaryOnLockScreen(isolated, false)
            TaskNotificationPreferences.setShowNotification(isolated, false)
            assertEquals(TaskNotificationSettings(false, false), observed.await())
            TaskNotificationPreferences.setShowNotification(isolated, true)
            assertEquals(
                TaskNotificationSettings(true, false),
                TaskNotificationPreferences.read(isolated)
            )
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun hidingTemporaryRemovesCollapsedButtonAndExpandedActionButKeepsNext() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val factory = ActiveTaskNotificationFactory(context)
            for (visible in listOf(false, true)) {
                val notification = factory.activeNotification("Task", 1_000L, emptyList(), visible)
                val view = notification.contentView.apply(context, FrameLayout(context))
                assertEquals(
                    if (visible) View.VISIBLE else View.GONE,
                    view.findViewById<View>(R.id.btn_temporary).visibility
                )
                val titles = notification.actions.map { it.title.toString() }
                assertEquals(visible, context.getString(R.string.notification_temporary) in titles)
                assertTrue(context.getString(R.string.notification_next) in titles)
                assertEquals(visible, factory.startingNotification(visible).actions.any {
                    it.title.toString() == context.getString(R.string.notification_temporary)
                })
            }
        }
    }
}
