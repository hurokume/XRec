package com.example.timerecorder.data

import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.HistoryRowDb
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.TaskDao
import com.example.timerecorder.data.db.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

class TaskRepository(private val dao: TaskDao) {

    /* -------- タスクツリー（親 → 直下の子） -------- */
    fun observeTree(): Flow<List<ParentWithChildren>> =
        dao.observeParentsWithChildren()

    /* -------- サブタスク（任意の parentId の直下の子） -------- */
    suspend fun hasChildren(taskId: Long): Boolean =
        dao.countChildren(taskId) > 0

    fun observeChildren(parentId: Long): Flow<List<TaskEntity>> =
        dao.observeChildren(parentId)

    /* -------- アクティブタスク -------- */
    fun observeActive(): Flow<ActiveWithTask?> =
        dao.observeActiveWithTask()

    /**
     * タスクを開始 or 切替。
     * すでにアクティブがあれば、その期間を履歴として確定してから切り替える。
     */
    suspend fun startOrSwitch(taskId: Long, nowMs: Long) {
        val active = dao.getActiveOrNull()
        if (active != null) {
            val duration = max(0L, nowMs - active.startedAtEpochMs)
            dao.insertEvent(
                com.example.timerecorder.data.db.TaskEventEntity(
                    taskId = active.taskId,
                    pressedAtEpochMs = active.startedAtEpochMs,
                    durationMillis = duration
                )
            )
        }
        dao.upsertActive(
            com.example.timerecorder.data.db.ActiveTaskEntity(
                taskId = taskId,
                startedAtEpochMs = nowMs
            )
        )
    }

    /**
     * 現在のアクティブを停止（履歴に確定して active をクリア）
     */
    suspend fun stopActive(nowMs: Long) {
        val active = dao.getActiveOrNull() ?: return
        val duration = max(0L, nowMs - active.startedAtEpochMs)
        dao.insertEvent(
            com.example.timerecorder.data.db.TaskEventEntity(
                taskId = active.taskId,
                pressedAtEpochMs = active.startedAtEpochMs,
                durationMillis = duration
            )
        )
        dao.clearActive()
    }

    /* -------- 履歴（ページング：最新→過去、limit/offset） -------- */
    suspend fun loadHistoryPage(page: Int, pageSize: Int): List<HistoryRowDb> {
        val offset = page * pageSize
        return dao.getEventsPage(limit = pageSize, offset = offset)
    }

    suspend fun clearHistory() = dao.clearAllEvents()

    suspend fun addTask(name: String, parentId: Long?): Long {
        val next = dao.getMaxSortOrder(parentId) + 1
        return dao.insertTask(TaskEntity(name = name, parentId = parentId, sortOrder = next))
    }

    suspend fun deleteProject(parentId: Long) {
        dao.deleteChildren(parentId)
        dao.deleteTaskById(parentId)
    }

    suspend fun changeColor(taskId: Long, colorInt: Int) {
        dao.updateTaskColor(taskId, colorInt)
    }
}
