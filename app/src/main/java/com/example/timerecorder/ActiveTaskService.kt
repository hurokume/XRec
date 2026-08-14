package com.example.timerecorder

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.TaskDao
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ActiveTaskService : LifecycleService() {

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ActiveTaskService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ActiveTaskService::class.java))
        }
    }

    private lateinit var repository: TaskRepository
    private lateinit var taskDao: TaskDao
    private lateinit var notificationFactory: ActiveTaskNotificationFactory

    override fun onCreate() {
        super.onCreate()
        notificationFactory = ActiveTaskNotificationFactory(this).also { it.createChannel() }

        val database = AppDatabase.get(applicationContext, lifecycleScope)
        taskDao = database.taskDao()
        repository = TaskRepository(database, database.sessionDao(), taskDao)

        startAsForeground()
        observeActiveTask()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ActiveTaskNotificationContract.ACTION_NEXT -> lifecycleScope.launch {
                repository.switchToPredictedNext(now = System.currentTimeMillis())
            }

            ActiveTaskNotificationContract.ACTION_SWITCH_TO -> {
                val targetId = intent.getLongExtra(
                    ActiveTaskNotificationContract.EXTRA_TASK_ID,
                    -1L
                )
                if (targetId > 0L) {
                    lifecycleScope.launch {
                        repository.startOrSwitch(
                            newTaskId = targetId,
                            now = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = notificationFactory.startingNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                ActiveTaskNotificationContract.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ActiveTaskNotificationContract.NOTIFICATION_ID, notification)
        }
    }

    private fun observeActiveTask() {
        lifecycleScope.launch {
            repository.observeActive().collectLatest { active ->
                if (active == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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
    }

    private suspend fun taskPath(taskId: Long): String {
        val task = taskDao.getTaskById(taskId) ?: return "Time Recorder"
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
