package com.example.timerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
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
        private const val CHANNEL_ID = "active_task_channel"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, ActiveTaskService::class.java)
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ActiveTaskService::class.java))
        }
    }

    private lateinit var repo: TaskRepository
    private lateinit var taskDao: TaskDao
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val db = AppDatabase.get(applicationContext, lifecycleScope)
        taskDao = db.taskDao()
        repo = TaskRepository(db, db.sessionDao(), taskDao)

        // 1) すぐ前面化（仮の内容）
        val boot = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Time Recorder")
            .setContentText("起動中…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, boot, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, boot)
        }

        // 2) Flowで実タスクを監視し、通知を更新
        lifecycleScope.launch {
            repo.observeActive().collectLatest { a ->
                if (a == null) {
                    // 実行中が無くなったら終了
                    stopForeground(STOP_FOREGROUND_REMOVE) // API<24なら stopForeground(true)
                    stopSelf()
                    return@collectLatest
                }
                val title = buildTitleFor(a.task.id)
                val n = buildNotification(title, a.active.startedAtEpochMs)
                
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIF_ID, n)
                }
            }
        }
    }

    private suspend fun buildTitleFor(taskId: Long): String {
        val t = taskDao.getTaskById(taskId) ?: return "Time Recorder"

        val p = t.parentId?.let { taskDao.getTaskById(it) }
        val g = p?.parentId?.let { taskDao.getTaskById(it) }

        // Build "Project / Task / SubTask" safely
        val parts = buildList {
            if (g != null) add(g.name)        // project
            if (p != null) add(p.name)        // task (parent of subtask or child)
            add(t.name)                       // leaf (child or subtask)
        }
        return parts.joinToString(" / ")
    }

    private fun buildNotification(title: String, startedAtMs: Long): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 0, intent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // use an existing icon
            .setContentTitle(title)              // "Project / Task / SubTask"
            .setContentText("計測中")
            .setWhen(startedAtMs)
            .setUsesChronometer(true)            // HH:MM:SS auto
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Active task",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "現在実行中のタスクを表示します" }
            nm.createNotificationChannel(ch)
        }
    }
}
