package com.example.timerecorder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskNotificationSettingsTest {
    @Test fun defaultsPreserveBothExistingNotificationButtons() {
        val settings = TaskNotificationSettings()
        assertTrue(settings.showNotification)
        assertTrue(settings.showTemporaryButton(isLocked = false))
        assertTrue(settings.showTemporaryButton(isLocked = true))
    }

    @Test fun hidingLockScreenButtonKeepsItAvailableAfterUnlocking() {
        val settings = TaskNotificationSettings(showTemporaryOnLockScreen = false)
        assertFalse(settings.showTemporaryButton(isLocked = true))
        assertTrue(settings.showTemporaryButton(isLocked = false))
    }

    @Test fun disablingNotificationsOverridesBothLockScreenChoices() {
        for (showOnLock in listOf(false, true)) {
            val settings = TaskNotificationSettings(false, showOnLock)
            assertFalse(settings.showTemporaryButton(isLocked = false))
            assertFalse(settings.showTemporaryButton(isLocked = true))
        }
    }
}
