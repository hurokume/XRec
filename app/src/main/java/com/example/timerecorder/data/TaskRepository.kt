package com.example.timerecorder.data

import com.example.timerecorder.data.db.ActiveTaskEntity
import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.TaskDao
import com.example.timerecorder.data.db.TaskEventEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

class TaskRepository(private val dao: TaskDao) {

    // 階層ツリー（親＋直下の子）を監視
    fun observeTree(): Flow<List<ParentWithChildren>> = dao.observeParentsWithChildren()

    // アクティブ状態を監視
    fun observeActive(): Flow<ActiveWithTask?> = dao.observeActiveWithTask()

    // 履歴（直近）
    fun observeRecentEvents(limit: Int = 100): Flow<List<TaskEventEntity>> =
        dao.observeRecentEvents(limit)

    /**
     * タスク開始 or 切替：
     * すでにアクティブがあれば履歴に duration を確定してから切替
     */
    suspend fun startOrSwitch(taskId: Long, nowMs: Long) {
        val active = dao.getActiveOrNull()
        if (active != null) {
            val duration = max(0L, nowMs - active.startedAtEpochMs)
            dao.insertEvent(
                TaskEventEntity(
                    taskId = active.taskId,
                    pressedAtEpochMs = active.startedAtEpochMs,
                    durationMillis = duration
                )
            )
        }
        dao.upsertActive(ActiveTaskEntity(taskId = taskId, startedAtEpochMs = nowMs))
    }

    /** 現在アクティブを停止（履歴に追記して active をクリア） */
    suspend fun stopActive(nowMs: Long) {
        val active = dao.getActiveOrNull() ?: return
        val duration = max(0L, nowMs - active.startedAtEpochMs)
        dao.insertEvent(
            TaskEventEntity(
                taskId = active.taskId,
                pressedAtEpochMs = active.startedAtEpochMs,
                durationMillis = duration
            )
        )
        dao.clearActive()
    }

    suspend fun hasChildren(taskId: Long): Boolean = dao.countChildren(taskId) > 0
    fun observeChildren(taskId: Long) = dao.observeChildren(taskId)
}
