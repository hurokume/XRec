package com.example.timerecorder.data

import com.example.timerecorder.data.db.ActiveTaskEntity
import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.HistoryRowDb
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.TaskDao
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.TaskEventEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

class TaskRepository(private val dao: TaskDao) {

    fun observeTree(): Flow<List<ParentWithChildren>> =
        dao.observeParentsWithChildren()

    suspend fun hasChildren(taskId: Long): Boolean =
        dao.countChildren(taskId) > 0

    fun observeChildren(parentId: Long): Flow<List<TaskEntity>> =
        dao.observeChildren(parentId)

    fun observeActive(): Flow<ActiveWithTask?> =
        dao.observeActiveWithTask()

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
        dao.upsertActive(
            ActiveTaskEntity(
                taskId = taskId,
                startedAtEpochMs = nowMs
            )
        )
    }

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

    suspend fun renameTask(taskId: Long, newName: String) {
        dao.updateTaskName(taskId, newName)
    }

    suspend fun deleteTaskById(taskId: Long) {
        dao.deleteTaskById(taskId)
    }

    suspend fun updateSortOrdersForParent(parentId: Long, idsInOrder: List<Long>) {
        dao.updateSortOrdersForParent(parentId, idsInOrder)
    }

    suspend fun loadHistoryRange(startMs: Long, endMs: Long): List<HistoryRowDb> {
        return dao.getEventsInRange(startMs, endMs)
    }
}
