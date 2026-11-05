package com.example.timerecorder

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews

/** The whole widget is one button; it uses exactly the same command as the notification. */
class TemporaryTaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_temporary_task).apply {
                setTextViewText(R.id.widget_temporary_title, context.getString(R.string.notification_temporary))
                setTextViewText(R.id.widget_temporary_hint, context.getString(R.string.temporary_widget_hint))
                setContentDescription(R.id.widget_temporary_button, context.getString(R.string.temporary_task_description))
                setOnClickPendingIntent(R.id.widget_temporary_button, ActiveTaskService.temporaryPendingIntent(context))
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        fun requestPin(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 26) return false
            val manager = AppWidgetManager.getInstance(context)
            return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(
                ComponentName(context, TemporaryTaskWidgetProvider::class.java), null, null
            )
        }
    }
}
