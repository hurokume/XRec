package io.github.hurokume.xrec

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class TaskNotificationSettings(
    val showNotification: Boolean = true,
    val showTemporaryOnLockScreen: Boolean = true
) {
    fun showTemporaryButton(isLocked: Boolean): Boolean =
        showNotification && (!isLocked || showTemporaryOnLockScreen)
}

internal object TaskNotificationPreferences {
    private const val SHOW_NOTIFICATION = "show_task_notification"
    private const val SHOW_TEMPORARY_ON_LOCK_SCREEN = "show_temporary_on_lock_screen"

    private fun preferences(context: Context) =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun read(context: Context): TaskNotificationSettings = read(preferences(context))

    private fun read(prefs: SharedPreferences) = TaskNotificationSettings(
        showNotification = prefs.getBoolean(SHOW_NOTIFICATION, true),
        showTemporaryOnLockScreen = prefs.getBoolean(SHOW_TEMPORARY_ON_LOCK_SCREEN, true)
    )

    fun setShowNotification(context: Context, show: Boolean) {
        preferences(context).edit().putBoolean(SHOW_NOTIFICATION, show).apply()
    }

    fun setShowTemporaryOnLockScreen(context: Context, show: Boolean) {
        preferences(context).edit().putBoolean(SHOW_TEMPORARY_ON_LOCK_SCREEN, show).apply()
    }

    fun observe(context: Context) = callbackFlow {
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SHOW_NOTIFICATION || key == SHOW_TEMPORARY_ON_LOCK_SCREEN) {
                trySend(read(prefs))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(prefs))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
