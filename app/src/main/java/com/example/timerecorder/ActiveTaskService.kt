package com.example.timerecorder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.TaskDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ActiveTaskService : LifecycleService() {

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ActiveTaskService::class.java)
            )
        }

        fun temporaryPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ActiveTaskService::class.java)
                .setAction(ActiveTaskNotificationContract.ACTION_TEMPORARY)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= 26) {
                PendingIntent.getForegroundService(context, 2, intent, flags)
            } else {
                PendingIntent.getService(context, 2, intent, flags)
            }
        }
    }

    private lateinit var repository: TaskRepository
    private lateinit var taskDao: TaskDao
    private lateinit var notificationFactory: ActiveTaskNotificationFactory
    private data class Command(val intent: Intent?, val pressedAt: Long)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val pendingCommands = MutableStateFlow(0)
    private var observation: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationFactory = ActiveTaskNotificationFactory(this).also { it.createChannel() }

        val database = AppDatabase.get(applicationContext, lifecycleScope)
        taskDao = database.taskDao()
        repository = TaskRepository(database, database.sessionDao(), taskDao, ::getString)

        startAsForeground()
        lifecycleScope.launch {
            for (command in commands) {
                try {
                    execute(command)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("ActiveTaskService", "Could not switch task", error)
                    Toast.makeText(this@ActiveTaskService, R.string.task_switch_failed, Toast.LENGTH_LONG).show()
                } finally {
                    pendingCommands.value -= 1
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Capture each press before any database work and process every command in order.
        pendingCommands.value += 1
        commands.trySend(Command(intent, System.currentTimeMillis()))
        if (observation == null) observation = observeActiveTask()
        return START_STICKY
    }

    private suspend fun execute(command: Command) {
        val intent = command.intent
        when (intent?.action) {
            ActiveTaskNotificationContract.ACTION_TEMPORARY -> {
                repository.startTemporaryTask(command.pressedAt)
                Toast.makeText(this, R.string.temporary_task_started, Toast.LENGTH_SHORT).show()
            }
            ActiveTaskNotificationContract.ACTION_NEXT -> {
                repository.switchToPredictedNext(now = command.pressedAt)
            }

            ActiveTaskNotificationContract.ACTION_SWITCH_TO -> {
                val targetId = intent.getLongExtra(
                    ActiveTaskNotificationContract.EXTRA_TASK_ID,
                    -1L
                )
                if (targetId > 0L) {
                    repository.startOrSwitch(newTaskId = targetId, now = command.pressedAt)
                }
            }
        }
    }

    private fun startAsForeground() {
        val notification = notificationFactory.startingNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                ActiveTaskNotificationContract.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ActiveTaskNotificationContract.NOTIFICATION_ID, notification)
        }
    }

    private fun observeActiveTask(): Job =
        lifecycleScope.launch {
            val state = combine(repository.observeActive(), pendingCommands) { active, pending -> active to pending }
            state.collectLatest { (active, pending) ->
                if (active == null) {
                    // A cold widget start must finish creating its first session before stopping.
                    // Room invalidations can arrive after pendingCommands reaches zero.
                    // Re-read before stopping so an old null emission cannot cancel a widget start.
                    if (pending == 0 && taskDao.getActiveOrNull() == null && pendingCommands.value == 0) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    return@collectLatest
                }

                val now = System.currentTimeMillis()
                val candidateIds = NextTaskPredictor.getInstance(taskDao)
                    .predictTopK(currentTaskId = active.task.id, nowMs = now, k = 4)
                    .map { it.taskId }
                    .filter { it != active.task.id }
                    .distinct()
                    .take(3)
                val candidates = candidateIds.map { taskId ->
                    NotificationCandidate(
                        taskId = taskId,
                        label = shortLabelFor(taskId).take(14)
                    )
                }

                val notification = notificationFactory.activeNotification(
                    title = taskPath(active.task.id),
                    startedAtMs = active.active.startedAtEpochMs,
                    candidates = candidates
                )
                startForeground(ActiveTaskNotificationContract.NOTIFICATION_ID, notification)
            }
        }

    override fun onDestroy() {
        commands.close()
        super.onDestroy()
    }

    private suspend fun taskPath(taskId: Long): String {
        val task = taskDao.getTaskById(taskId) ?: return getString(R.string.app_name)
        val parent = task.parentId?.let { taskDao.getTaskById(it) }
        val grandparent = parent?.parentId?.let { taskDao.getTaskById(it) }
        return buildList {
            grandparent?.let { add(it.name) }
            parent?.let { add(it.name) }
            add(task.name)
        }.joinToString(" / ")
    }

    private suspend fun shortLabelFor(taskId: Long): String =
        taskDao.getTaskById(taskId)?.name ?: "Task $taskId"
}
