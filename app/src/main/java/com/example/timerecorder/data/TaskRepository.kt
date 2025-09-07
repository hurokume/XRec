package com.example.timerecorder.data

import com.example.timerecorder.data.db.ActiveTaskEntity
import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.HistoryRowDb
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.TaskDao
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.TaskEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

class TaskRepository(private val dao: TaskDao) {

    // ------- Observable DB streams -------
    fun observeTree(): Flow<List<ParentWithChildren>> =
        dao.observeParentsWithChildren()

    suspend fun hasChildren(taskId: Long): Boolean =
        dao.countChildren(taskId) > 0

    fun observeChildren(parentId: Long): Flow<List<TaskEntity>> =
        dao.observeChildren(parentId)

    fun observeActive(): Flow<ActiveWithTask?> =
        dao.observeActiveWithTask()

    // ------- Start / Stop active task -------
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

    // ------- History -------
    suspend fun loadHistoryPage(page: Int, pageSize: Int): List<HistoryRowDb> {
        val offset = page * pageSize
        return dao.getEventsPage(limit = pageSize, offset = offset)
    }

    suspend fun clearHistory() = dao.clearAllEvents()

    // ------- Tasks CRUD -------
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

    // =========================================================================
    // Import / Export（親 → 子 → 孫 … 全階層を JSON 化）
    // 追加の DAO メソッドなしで動くように、Flow の最初の値を同期的に取得して再帰します。
    // =========================================================================

    /** Export ルート JSON オブジェクト */
    @Serializable
    data class TaskExportRoot(
        val version: Int = 1,
        /** 親（ルート）タスク群。各ノードの children に子 → 孫 … を含む。 */
        val projects: List<TaskExportNode>
    )

    /** 階層ノード（親/子/孫 すべて同じ型で表す） */
    @Serializable
    data class TaskExportNode(
        val id: Long? = null,
        val name: String,
        val colorInt: Int? = null,
        val children: List<TaskExportNode> = emptyList()
    )

    /** 直下の “子” を 1 回だけ同期取得（Flow の first() を使用） */
    private suspend fun loadChildrenOnce(parentId: Long): List<TaskEntity> =
        observeChildren(parentId).first()

    /** ルート（親 = parentId が null）の一覧を 1 回だけ同期取得 */
    private suspend fun loadRootParentsOnce(): List<TaskEntity> =
        observeTree().first().map { it.parent }

    /** （再帰）TaskEntity から輸出用ノードを構築（子→孫…を全収集） */
    private suspend fun buildExportNode(task: TaskEntity): TaskExportNode {
        val childEntities = loadChildrenOnce(task.id)
        val childNodes = mutableListOf<TaskExportNode>()
        for (child in childEntities) {
            childNodes += buildExportNode(child) // ← 孫以降もここで必ず取り込む
        }
        return TaskExportNode(
            id = task.id,
            name = task.name,
            colorInt = task.colorInt,
            children = childNodes
        )
    }

    /**
     * 親タスク / 子タスク / サブタスク（孫）をすべて含む JSON をエクスポート。
     * @param pretty prettyPrint を有効にするか
     */
    suspend fun exportTasksJson(pretty: Boolean = true): String {
        val parents = loadRootParentsOnce()
        val nodes = mutableListOf<TaskExportNode>()
        for (p in parents) {
            nodes += buildExportNode(p)
        }
        val root = TaskExportRoot(version = 1, projects = nodes)
        val json = if (pretty) Json { prettyPrint = true; encodeDefaults = true } else Json
        return json.encodeToString(root)
    }

    /**
     * JSON から階層ツリーを取り込み（同名があれば再利用。必要なら色も同期）
     * @param jsonString exportTasksJson の出力
     * @param updateColorOnMatch true で同名再利用時に色も更新
     */
    suspend fun importTasksJson(
        jsonString: String,
        updateColorOnMatch: Boolean = true
    ): ImportResult {
        val root = Json.decodeFromString<TaskExportRoot>(jsonString)
        var created = 0
        var reused = 0

        fun normalize(name: String) = name.trim().lowercase()

        suspend fun findExistingChild(parentId: Long?, name: String): TaskEntity? {
            val list = if (parentId == null) {
                loadRootParentsOnce()
            } else {
                loadChildrenOnce(parentId)
            }
            val n = normalize(name)
            return list.firstOrNull { normalize(it.name) == n }
        }

        // （再帰）ノードを作成 or 再利用し、子（孫以降も含む）を取り込む
        suspend fun importNode(node: TaskExportNode, parentId: Long?): Long {
            val existing = findExistingChild(parentId, node.name)
            val taskId = if (existing != null) {
                if (updateColorOnMatch && node.colorInt != null && node.colorInt != existing.colorInt) {
                    dao.updateTaskColor(existing.id, node.colorInt)
                }
                reused += 1
                existing.id
            } else {
                val id = addTask(node.name, parentId)
                if (node.colorInt != null) {
                    dao.updateTaskColor(id, node.colorInt)
                }
                created += 1
                id
            }

            for (child in node.children) {
                importNode(child, taskId)
            }
            return taskId
        }

        for (project in root.projects) {
            importNode(project, parentId = null)
        }
        return ImportResult(created = created, reused = reused)
    }

    data class ImportResult(
        val created: Int,
        val reused: Int
    )
}
