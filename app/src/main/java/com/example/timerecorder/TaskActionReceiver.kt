package com.example.timerecorder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.db.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Re-check settings when tapped, including for widgets created before notifications were disabled. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pressedAt = System.currentTimeMillis()
        val app = context.applicationContext
        if (TaskNotificationPreferences.read(app).showNotification) {
            ContextCompat.startForegroundService(app, Intent(intent).apply {
                setClass(app, ActiveTaskService::class.java)
                putExtra(ActiveTaskNotificationContract.EXTRA_PRESSED_AT, pressedAt)
            })
            return
        }

        // A timestamp in Room keeps tracking elapsed time without a persistent service.
        // goAsync keeps this short database operation alive without posting a notification.
        val pending = goAsync()
        scope.launch {
            try {
                mutex.withLock {
                    val db = AppDatabase.get(app, scope)
                    val repository = TaskRepository(db, db.sessionDao(), db.taskDao(), app::getString)
                    executeTaskAction(app, repository, intent, pressedAt)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("TaskActionReceiver", "Could not switch task", error)
                Toast.makeText(app, R.string.task_switch_failed, Toast.LENGTH_LONG).show()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mutex = Mutex()
        private val actions = setOf(
            ActiveTaskNotificationContract.ACTION_TEMPORARY,
            ActiveTaskNotificationContract.ACTION_NEXT,
            ActiveTaskNotificationContract.ACTION_SWITCH_TO
        )

        internal fun pendingIntent(context: Context, intent: Intent, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode, Intent(intent).setClass(context, TaskActionReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}

internal suspend fun executeTaskAction(
    context: Context,
    repository: TaskRepository,
    intent: Intent?,
    pressedAt: Long
) {
    when (intent?.action) {
        ActiveTaskNotificationContract.ACTION_TEMPORARY -> {
            repository.startTemporaryTask(pressedAt)
            Toast.makeText(context, R.string.temporary_task_started, Toast.LENGTH_SHORT).show()
        }
        ActiveTaskNotificationContract.ACTION_NEXT -> repository.switchToPredictedNext(pressedAt)
        ActiveTaskNotificationContract.ACTION_SWITCH_TO -> {
            val id = intent.getLongExtra(ActiveTaskNotificationContract.EXTRA_TASK_ID, -1L)
            if (id > 0L) repository.startOrSwitch(id, pressedAt)
        }
    }
}
