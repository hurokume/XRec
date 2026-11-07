package io.github.hurokume.xrec

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.hurokume.xrec.data.TaskRepository
import io.github.hurokume.xrec.data.db.ActiveWithTask
import io.github.hurokume.xrec.data.db.AppDatabase
import io.github.hurokume.xrec.data.db.TaskDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

open class ActiveTaskService : LifecycleService() {

    companion object {
        fun start(context: Context) {
            if (!TaskNotificationPreferences.read(context).showNotification) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, ActiveTaskService::class.java)
            )
        }

        fun temporaryPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ActiveTaskService::class.java)
                .setAction(ActiveTaskNotificationContract.ACTION_TEMPORARY)
            return TaskActionReceiver.pendingIntent(context, intent, 2)
        }
    }

    private lateinit var repository: TaskRepository
    private lateinit var taskDao: TaskDao
    private lateinit var notificationFactory: ActiveTaskNotificationFactory

    private data class Command(val intent: Intent?, val pressedAt: Long)

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val pendingCommands = MutableStateFlow(0)
    private var observation: Job? = null
    private lateinit var keyguard: KeyguardManager
    private val screenLocked = MutableStateFlow(true)
    private var lockRefresh: Job? = null
    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lockRefresh?.cancel()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenLocked.value = true
                Intent.ACTION_USER_PRESENT -> screenLocked.value = false
                Intent.ACTION_SCREEN_ON -> {
                    // Keep the button hidden until the lock screen finishes becoming visible.
                    lockRefresh = lifecycleScope.launch {
                        delay(250)
                        screenLocked.value = keyguard.isKeyguardLocked
                    }
                }
            }
        }
    }

    private data class NotificationState(
        val active: ActiveWithTask?,
        val pending: Int,
        val settings: TaskNotificationSettings,
        val isLocked: Boolean
    )

    override fun onCreate() {
        super.onCreate()
        notificationFactory = ActiveTaskNotificationFactory(this).also { it.createChannel() }

        val database = AppDatabase.get(applicationContext, lifecycleScope)
        taskDao = database.taskDao()
        repository = TaskRepository(database, database.sessionDao(), taskDao, ::getString)
        keyguard = getSystemService(KeyguardManager::class.java)
        screenLocked.value = keyguard.isKeyguardLocked
        // USER_PRESENT may come from System UI rather than the system UID. All three
        // actions are protected broadcasts, so other apps cannot spoof these events.
        ContextCompat.registerReceiver(this, lockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_EXPORTED)

        startAsForeground()
        lifecycleScope.launch {
            for (command in commands) {
                try {
                    executeTaskAction(
                        this@ActiveTaskService,
                        repository,
                        command.intent,
                        command.pressedAt
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("ActiveTaskService", "Could not switch task", error)
                    Toast.makeText(
                        this@ActiveTaskService,
                        R.string.task_switch_failed,
                        Toast.LENGTH_LONG
                    ).show()
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
        val pressedAt = intent?.getLongExtra(
            ActiveTaskNotificationContract.EXTRA_PRESSED_AT, System.currentTimeMillis()
        ) ?: System.currentTimeMillis()
        commands.trySend(Command(intent, pressedAt))
        if (observation == null) observation = observeActiveTask()
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = notificationFactory.startingNotification(
            TaskNotificationPreferences.read(this).showTemporaryButton(screenLocked.value)
        )
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
            val state = combine(
                repository.observeActive(), pendingCommands,
                TaskNotificationPreferences.observe(this@ActiveTaskService), screenLocked
            ) { active, pending, settings, locked ->
                NotificationState(
                    active,
                    pending,
                    settings,
                    locked
                )
            }
            state.collectLatest { (active, pending, settings, locked) ->
                if (!settings.showNotification) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (pending == 0 && pendingCommands.value == 0 &&
                        !TaskNotificationPreferences.read(this@ActiveTaskService).showNotification
                    ) {
                        stopSelf()
                    }
                    return@collectLatest
                }
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
                val showTemporaryButton = settings.showTemporaryButton(locked)
                val candidateIds = NextTaskPredictor.getInstance(taskDao)
                    .predictTopK(currentTaskId = active.task.id, nowMs = now, k = 4)
                    .map { it.taskId }
                    .filter { it != active.task.id }
                    .filter { showTemporaryButton || taskDao.getTaskById(it)?.isTemporary != true }
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
                    candidates = candidates,
                    showTemporaryButton = showTemporaryButton
                )
                startForeground(ActiveTaskNotificationContract.NOTIFICATION_ID, notification)
            }
        }

    override fun onDestroy() {
        unregisterReceiver(lockReceiver)
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
