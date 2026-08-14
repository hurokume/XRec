package com.example.timerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

internal object ActiveTaskNotificationContract {
    // 新しいIDを使い、既存端末でもサイレント設定を確実に適用する。
    const val CHANNEL_ID = "active_task_channel_v3_silent"
    const val NOTIFICATION_ID = 1001
    const val ACTION_NEXT = "com.example.timerecorder.action.NEXT"
    const val ACTION_SWITCH_TO = "com.example.timerecorder.action.SWITCH_TO"
    const val EXTRA_TASK_ID = "extra_task_id"
}

internal data class NotificationCandidate(
    val taskId: Long,
    val label: String
)

/** ActiveTaskService から Android 通知の構築詳細を分離する。 */
internal class ActiveTaskNotificationFactory(
    private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (notificationManager.getNotificationChannel(ActiveTaskNotificationContract.CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            ActiveTaskNotificationContract.CHANNEL_ID,
            "Active task",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "現在実行中のタスクと予測候補"
            setShowBadge(false)
            enableVibration(false)
            vibrationPattern = null
            setSound(null, null)
            enableLights(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun startingNotification(): Notification =
        baseBuilder()
            .setContentTitle("Time Recorder")
            .setContentText("起動中…")
            .addAction(0, "次へ", pendingNext())
            .build()

    fun activeNotification(
        title: String,
        startedAtMs: Long,
        candidates: List<NotificationCandidate>
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            pendingIntentFlags()
        )

        return baseBuilder()
            .setContentTitle(title)
            .setContentText("計測中")
            .setWhen(startedAtMs)
            .setUsesChronometer(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedViews(title, startedAtMs))
            .setCustomBigContentView(expandedViews(title, startedAtMs, candidates))
            .addAction(android.R.drawable.ic_media_next, "次へ", pendingNext())
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, ActiveTaskNotificationContract.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setVibrate(null)
            .setDefaults(0)

    private fun expandedViews(
        title: String,
        startedAtMs: Long,
        candidates: List<NotificationCandidate>
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_active_big).apply {
        setTextViewText(R.id.title, title)
        setChronometer(R.id.chrono, chronometerBase(startedAtMs), null, true)

        val candidateViewIds =
            listOf(R.id.btn_candidate1, R.id.btn_candidate2, R.id.btn_candidate3)
        candidateViewIds.forEachIndexed { index, viewId ->
            val candidate = candidates.getOrNull(index)
            if (candidate == null) {
                setViewVisibility(viewId, View.GONE)
            } else {
                setViewVisibility(viewId, View.VISIBLE)
                setTextViewText(viewId, candidate.label)
                setOnClickPendingIntent(
                    viewId,
                    pendingSwitch(candidate.taskId, requestCode = 200 + index)
                )
            }
        }
    }

    private fun collapsedViews(title: String, startedAtMs: Long): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_active_collapsed).apply {
            setTextViewText(R.id.title_small, title)
            setChronometer(R.id.chrono_small, chronometerBase(startedAtMs), null, true)
        }

    private fun pendingNext(): PendingIntent {
        val intent = Intent(context, ActiveTaskService::class.java)
            .setAction(ActiveTaskNotificationContract.ACTION_NEXT)
        return PendingIntent.getService(context, 1, intent, pendingIntentFlags())
    }

    private fun pendingSwitch(taskId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, ActiveTaskService::class.java).apply {
            action = ActiveTaskNotificationContract.ACTION_SWITCH_TO
            putExtra(ActiveTaskNotificationContract.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(context, requestCode, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0

    private fun chronometerBase(startedAtMs: Long): Long =
        SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAtMs)
}
