// ActiveTaskService.kt
package com.example.timerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
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
        private const val CHANNEL_ID = "active_task_channel_v2"
        private const val NOTIF_ID = 1001

        // actions
        private const val ACTION_NEXT = "com.example.timerecorder.action.NEXT"
        private const val ACTION_STOP = "com.example.timerecorder.action.STOP"
        private const val ACTION_SWITCH_TO = "com.example.timerecorder.action.SWITCH_TO"
        private const val EXTRA_TASK_ID = "extra_task_id"


        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ActiveTaskService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ActiveTaskService::class.java))
        }

        fun next(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, ActiveTaskService::class.java).setAction(ACTION_NEXT)
            )
        }

        fun stopFromAction(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, ActiveTaskService::class.java).setAction(ACTION_STOP)
            )
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

        // すぐ前面化（仮通知）
        val boot = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Time Recorder")
            .setContentText("起動中…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "次へ", pendingNext())
            .addAction(0, "停止", pendingStop())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, boot, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, boot)
        }

        // 実タスクを監視して通知更新
        lifecycleScope.launch {
            repo.observeActive().collectLatest { a ->
                if (a == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }

                // 予測 Top-3（現在タスクは除外）
                val now = System.currentTimeMillis()
                val preds = NextTaskPredictor.getInstance(taskDao)
                    .predictTopK(currentTaskId = a.task.id, nowMs = now, k = 4)
                    .map { it.taskId }
                    .filter { it != a.task.id }
                    .distinct()
                    .take(3)

                // ボタン表示名（短く）
                val actions = preds.mapIndexed { idx, tid ->
                    CandidateAction(taskId = tid, label = shortLabelFor(tid).take(14))
                }

                val title = buildTitleFor(a.task.id)
                val n = buildNotification(
                    title = title,
                    startedAtMs = a.active.startedAtEpochMs,
                    candidates = actions
                )

                startForeground(NOTIF_ID, n)
            }
        }
    }

    // 通知アクションの受け口
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SWITCH_TO -> {
                val targetId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (targetId > 0) {
                    lifecycleScope.launch {
                        repo.startOrSwitch(newTaskId = targetId, now = System.currentTimeMillis())
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun pendingNext(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0)
        val i = Intent(this, ActiveTaskService::class.java).setAction(ACTION_NEXT)
        return PendingIntent.getService(this, 1, i, flags)
    }

    private fun pendingStop(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0)
        val i = Intent(this, ActiveTaskService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(this, 2, i, flags)
    }

    private suspend fun buildTitleFor(taskId: Long): String {
        val t = taskDao.getTaskById(taskId) ?: return "Time Recorder"
        val p = t.parentId?.let { taskDao.getTaskById(it) }
        val g = p?.parentId?.let { taskDao.getTaskById(it) }
        val parts = buildList {
            if (g != null) add(g.name)
            if (p != null) add(p.name)
            add(t.name)
        }
        return parts.joinToString(" / ")
    }

    private fun buildNotification(title: String, startedAtMs: Long): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // 次へ
        val nextIntent = Intent(this, ActiveTaskService::class.java).apply { action = ACTION_NEXT }
        val piNext = PendingIntent.getService(
            this, 1, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // 停止
        val stopIntent = Intent(this, ActiveTaskService::class.java).apply { action = ACTION_STOP }
        val piStop = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("計測中")
            .setWhen(startedAtMs)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(piOpen)
            .addAction(android.R.drawable.ic_media_next, "次へ", piNext)
            .addAction(android.R.drawable.ic_media_pause, "停止", piStop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Active task",
                    NotificationManager.IMPORTANCE_DEFAULT // ← LOWから引き上げ
                ).apply { description = "現在実行中のタスクと予測候補" }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private data class CandidateAction(val taskId: Long, val label: String)

    private suspend fun shortLabelFor(taskId: Long): String {
        val t = taskDao.getTaskById(taskId) ?: return "Task $taskId"
        // サブタスクならその名前、子タスクもその名前（ボタンは短く）
        return t.name
    }

    private fun switchPendingIntent(targetTaskId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(this, ActiveTaskService::class.java).apply {
            action = ACTION_SWITCH_TO
            putExtra(EXTRA_TASK_ID, targetTaskId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getService(this, requestCode, intent, flags)
    }

    private fun buildNotification(
        title: String,
        startedAtMs: Long,
        candidates: List<CandidateAction>
    ): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPI = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val big = buildBigRemoteViews(title, startedAtMs, candidates)
        val small = buildCollapsedRemoteViews(title, startedAtMs)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("計測中")
            .setWhen(startedAtMs)
            .setUsesChronometer(true) // ヘッダ側（端末によって出る）
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 初回展開を“促す”
            .setContentIntent(openPI)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .build()
    }

    private fun buildBigRemoteViews(
        title: String,
        startedAtMs: Long,
        candidates: List<CandidateAction>
    ): RemoteViews {
        val rv = RemoteViews(packageName, R.layout.notification_active_big)

        rv.setTextViewText(R.id.title, title)

        // Chronometer base = elapsedRealtime基準に変換
        val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAtMs)
        rv.setChronometer(R.id.chrono, base, null, true)

        // 3本分を埋める（不足分は非表示）
        val ids = listOf(R.id.btn_candidate1, R.id.btn_candidate2, R.id.btn_candidate3)
        ids.forEachIndexed { index, viewId ->
            val c = candidates.getOrNull(index)
            if (c == null) {
                rv.setViewVisibility(viewId, View.GONE)
            } else {
                rv.setViewVisibility(viewId, View.VISIBLE)
                rv.setTextViewText(viewId, c.label)
                rv.setOnClickPendingIntent(viewId, switchPendingIntent(c.taskId, 200 + index))
            }
        }
        return rv
    }

    private fun buildCollapsedRemoteViews(
        title: String,
        startedAtMs: Long
    ): RemoteViews {
        val rv = RemoteViews(packageName, R.layout.notification_active_collapsed)
        rv.setTextViewText(R.id.title_small, title)
        val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAtMs)
        rv.setChronometer(R.id.chrono_small, base, null, true)
        return rv
    }
}
