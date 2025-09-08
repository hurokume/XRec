package com.example.timerecorder.data

import androidx.room.withTransaction
import com.example.timerecorder.data.db.ActiveTaskEntity
import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.HistoryRowDb
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.SessionDao
import com.example.timerecorder.data.db.TaskDao
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.TaskEventEntity
import com.example.timerecorder.data.db.TaskSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class TaskRepository(
    private val db: AppDatabase,
    private val sessionDao: SessionDao,
    private val taskDao: TaskDao
) {
    // ---------------- 観測系 ----------------
    fun observeTree(): Flow<List<ParentWithChildren>> = taskDao.observeParentsWithChildren()
    fun observeChildren(parentId: Long): Flow<List<TaskEntity>> = taskDao.observeChildren(parentId)
    suspend fun hasChildren(taskId: Long): Boolean = taskDao.countChildren(taskId) > 0
    fun observeActive(): Flow<ActiveWithTask?> = taskDao.observeActiveWithTask()

    // ---------------- 操作系（新規：短時間統合スイッチ） ----------------
    /**
     * 現在のタスクから newTaskId へ切替。
     * 直前セッションが mergeThresholdSec（秒）以下なら短時間とみなし、新タスクへ「吸収」して前のセッションは削除。
     * それ以外は通常終了→イベント記録→新規開始。
     * どちらの場合も active_task_singleton は最新状態に更新。
     */
    suspend fun startOrSwitch(
        newTaskId: Long,
        now: Long,
        mergeThresholdSec: Long = 60
    ) = db.withTransaction {
        val active = sessionDao.getActive()

        // すでに同じタスクなら何もしない（開始時刻も維持）
        if (active?.taskId == newTaskId) return@withTransaction

        if (active == null) {
            // 新規開始
            val start = now
            sessionDao.insert(
                TaskSessionEntity(
                    taskId = newTaskId,
                    startedAt = start,
                    endedAt = null
                )
            )
            taskDao.upsertActive(ActiveTaskEntity(taskId = newTaskId, startedAtEpochMs = start))
            return@withTransaction
        }

        val durationMs = now - active.startedAt
        val thresholdMs = mergeThresholdSec * 1000L

        if (durationMs <= thresholdMs) {
            // 短時間：前セッションを破棄し、新タスクを「前の開始時刻」で開始＝吸収
            val mergedStart = active.startedAt
            sessionDao.deleteById(active.id)
            sessionDao.insert(
                TaskSessionEntity(
                    taskId = newTaskId,
                    startedAt = mergedStart,
                    endedAt = null
                )
            )
            taskDao.upsertActive(
                ActiveTaskEntity(
                    taskId = newTaskId,
                    startedAtEpochMs = mergedStart
                )
            )
            // ※イベントは作らない（前の短時間は履歴に残さない設計）
        } else {
            // 通常切替：前セッションを終了してイベント化 → 新タスク開始
            sessionDao.updateEnd(active.id, now)
            taskDao.insertEvent(
                TaskEventEntity(
                    taskId = active.taskId,
                    pressedAtEpochMs = active.startedAt,
                    durationMillis = (now - active.startedAt).coerceAtLeast(0L)
                )
            )
            sessionDao.insert(
                TaskSessionEntity(
                    taskId = newTaskId,
                    startedAt = now,
                    endedAt = null
                )
            )
            taskDao.upsertActive(ActiveTaskEntity(taskId = newTaskId, startedAtEpochMs = now))
        }
    }

    /** 停止：実行中セッションを終了して履歴に記録、アクティブをクリア */
    suspend fun stopActive(nowMs: Long) = db.withTransaction {
        val active = sessionDao.getActive() ?: return@withTransaction
        sessionDao.updateEnd(active.id, nowMs)
        taskDao.insertEvent(
            TaskEventEntity(
                taskId = active.taskId,
                pressedAtEpochMs = active.startedAt,
                durationMillis = (nowMs - active.startedAt).coerceAtLeast(0L)
            )
        )
        taskDao.clearActive()
    }

    // ---------------- タスク CRUD / 並び順 / 色 ----------------
    suspend fun addTask(name: String, parentId: Long?): Long {
        val next = taskDao.getMaxSortOrder(parentId) + 1
        return taskDao.insertTask(TaskEntity(name = name, parentId = parentId, sortOrder = next))
    }

    suspend fun deleteProject(parentId: Long) {
        taskDao.deleteChildren(parentId)
        taskDao.deleteTaskById(parentId)
    }

    suspend fun changeColor(id: Long, colorInt: Int) = taskDao.updateTaskColor(id, colorInt)
    suspend fun renameTask(taskId: Long, newName: String) = taskDao.updateTaskName(taskId, newName)
    suspend fun deleteTaskById(taskId: Long) = taskDao.deleteTaskById(taskId)
    suspend fun updateSortOrdersForParent(parentId: Long, idsInOrder: List<Long>) =
        taskDao.updateSortOrdersForParent(parentId, idsInOrder)

    // ---------------- 履歴（task_events） ----------------
    suspend fun loadHistoryPage(page: Int, pageSize: Int): List<HistoryRowDb> {
        val offset = page * pageSize
        return taskDao.getEventsPage(limit = pageSize, offset = offset)
    }

    suspend fun loadHistoryRange(startMs: Long, endMs: Long): List<HistoryRowDb> =
        taskDao.getEventsInRange(startMs, endMs)

    // ---------------- Export / Import ----------------
    suspend fun exportTasksJson(): String {
        // 親＋直下の子を一括取得（1回だけ）
        val groups = observeTree().first()
        val arr = JSONArray()
        groups.forEach { g ->
            val obj = JSONObject().apply {
                put("name", g.parent.name)
                g.parent.colorInt?.let { put("color", it) }
                val kids = JSONArray()
                g.children.forEach { c -> kids.put(JSONObject().put("name", c.name)) }
                put("children", kids)
            }
            arr.put(obj)
        }
        return arr.toString(2)
    }

    suspend fun importTasksJson(json: String, replaceAll: Boolean) = db.withTransaction {
        val current = observeTree().first()

        if (replaceAll) {
            current.forEach { p ->
                taskDao.deleteChildren(p.parent.id)
                taskDao.deleteTaskById(p.parent.id)
            }
        }

        // 名前 → 既存親ID の索引（マージ用）
        fun findParentIdByName(name: String): Long? =
            current.firstOrNull { it.parent.name == name }?.parent?.id

        val root = JSONArray(json)
        for (i in 0 until root.length()) {
            val p = root.optJSONObject(i) ?: continue
            val parentName = p.optString("name", "").trim().take(128)
            if (parentName.isEmpty()) continue

            val colorInt = if (p.has("color")) p.optInt("color") else null

            val parentId: Long = findParentIdByName(parentName)
                ?: addTask(parentName, null)

            if (colorInt != null) changeColor(parentId, colorInt)

            val children = p.optJSONArray("children") ?: JSONArray()
            for (j in 0 until children.length()) {
                val c = children.optJSONObject(j) ?: continue
                val childName = c.optString("name", "").trim().take(128)
                if (childName.isEmpty()) continue
                val dup = taskDao.countByNameInParent(parentId, childName) > 0
                if (!dup) addTask(childName, parentId)
            }
        }
    }
}
