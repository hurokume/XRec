package com.example.timerecorder.data

import androidx.room.withTransaction
import com.example.timerecorder.R
import com.example.timerecorder.NextTaskPredictor
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
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** タスク切替えの結果（UI でメッセージ表示に利用） */
sealed class SwitchResult {
    /** すでに同じタスクを実行中（何もしない） */
    data object NoChange : SwitchResult()

    /** 実行中なし → 新規開始 */
    data class StartedFresh(val startedAt: Long) : SwitchResult()

    /** 通常切替（前タスクを終了し、履歴に記録） */
    data class SwitchedNormal(val prevDurationMs: Long) : SwitchResult()

    /** 短時間だったため「統合」した（前タスクは履歴に残さない） */
    data class MergedShort(
        val prevDurationMs: Long,
        val thresholdMs: Long
    ) : SwitchResult()
}

/** タスクを一覧から削除するとき、既存の記録をどう扱うか。 */
enum class TaskDeletionMode {
    /** 削除対象の記録を1階層上のタスクへ付け替える。 */
    PromoteToParent,

    /** 削除対象の記録をユーザーが選んだ別タスクへ付け替える。 */
    Replace,

    /** 記録とタスクを保持し、タスクだけを選択 UI から隠す。 */
    KeepHistory
}

class TaskRepository(
    private val db: AppDatabase,
    private val sessionDao: SessionDao,
    private val taskDao: TaskDao,
    private val stringResolver: (Int) -> String = { it.toString() }
) {
    private val workHistory = WorkHistory(db, stringResolver)
    fun observeWorkHistory() = workHistory.observe()
    suspend fun restoreWorkHistory(id: Long, expectedLatestId: Long) =
        workHistory.restoreBefore(id, expectedLatestId)

    suspend fun <T> recordChange(action: WorkHistoryAction, block: suspend () -> T): T =
        workHistory.record(action, block)

    // ---------------- 観測系 ----------------
    fun observeTree(): Flow<List<ParentWithChildren>> =
        taskDao.observeParentsWithChildren()
            .map { groups ->
                groups.map { g ->
                    g.copy(
                        children = g.children
                            .filterNot { it.isArchived }
                            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                    )
                }
            }

    fun observeChildren(parentId: Long): Flow<List<TaskEntity>> = taskDao.observeChildren(parentId)
    suspend fun hasChildren(taskId: Long): Boolean = taskDao.countChildren(taskId) > 0
    suspend fun getVisibleTaskIds(): Set<Long> = taskDao.getVisibleTaskIds().toSet()
    suspend fun getTaskPathLabel(taskId: Long): String? {
        val names = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var current = taskDao.getTaskById(taskId)
        while (current != null && visited.add(current.id)) {
            names += current.name
            current = current.parentId?.let { taskDao.getTaskById(it) }
        }
        return names.takeIf { it.isNotEmpty() }
            ?.asReversed()
            ?.joinToString(" / ")
    }
    fun observeActive(): Flow<ActiveWithTask?> = taskDao.observeActiveWithTask()

    // ---------------- 操作系（短時間統合スイッチ） ----------------
    suspend fun startOrSwitch(
        newTaskId: Long,
        now: Long,
        mergeThresholdSec: Long = 60
    ): SwitchResult = workHistory.record(WorkHistoryAction.StartOrSwitch) {
        val newTask = requireNotNull(taskDao.getTaskById(newTaskId)) {
            stringResolver(R.string.error_task_missing)
        }
        require(!newTask.isArchived) { stringResolver(R.string.error_task_archived) }
        val active = sessionDao.getActive()
        val thresholdMs = mergeThresholdSec * 1000L
        val decision = TaskSwitchPolicy.decide(
            currentTaskId = active?.taskId,
            currentStartedAt = active?.startedAt,
            newTaskId = newTaskId,
            nowMs = now,
            mergeThresholdMs = thresholdMs
        )

        when (decision.action) {
            TaskSwitchAction.NoChange -> SwitchResult.NoChange

            TaskSwitchAction.StartFresh -> {
                sessionDao.insert(
                    TaskSessionEntity(
                        taskId = newTaskId,
                        startedAt = now,
                        endedAt = null
                    )
                )
                taskDao.upsertActive(
                    ActiveTaskEntity(taskId = newTaskId, startedAtEpochMs = now)
                )
                SwitchResult.StartedFresh(startedAt = now)
            }

            TaskSwitchAction.MergeShort -> {
                val current = checkNotNull(active)
                val mergedStart = current.startedAt
                sessionDao.deleteById(current.id)
                sessionDao.insert(
                    TaskSessionEntity(
                        taskId = newTaskId,
                        startedAt = mergedStart,
                        endedAt = null
                    )
                )
                taskDao.upsertActive(
                    ActiveTaskEntity(taskId = newTaskId, startedAtEpochMs = mergedStart)
                )
                SwitchResult.MergedShort(
                    prevDurationMs = decision.previousDurationMs,
                    thresholdMs = thresholdMs
                )
            }

            TaskSwitchAction.SwitchNormal -> {
                val current = checkNotNull(active)
                sessionDao.updateEnd(current.id, now)
                taskDao.insertEvent(
                    TaskEventEntity(
                        taskId = current.taskId,
                        pressedAtEpochMs = current.startedAt,
                        durationMillis = decision.previousDurationMs
                    )
                )
                sessionDao.insert(
                    TaskSessionEntity(
                        taskId = newTaskId,
                        startedAt = now,
                        endedAt = null
                    )
                )
                taskDao.upsertActive(
                    ActiveTaskEntity(taskId = newTaskId, startedAtEpochMs = now)
                )
                SwitchResult.SwitchedNormal(prevDurationMs = decision.previousDurationMs)
            }
        }
    }

    /** 停止：実行中セッションを終了して履歴に記録、アクティブをクリア */
    suspend fun stopActive(nowMs: Long) = workHistory.record(WorkHistoryAction.Stop) {
        val active = sessionDao.getActive() ?: return@record
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
    suspend fun addTask(name: String, parentId: Long?): Long = workHistory.record(WorkHistoryAction.AddTask) {
        val next = taskDao.getMaxSortOrder(parentId) + 1
        taskDao.insertTask(TaskEntity(name = name, parentId = parentId, sortOrder = next))
    }

    suspend fun changeColor(id: Long, colorInt: Int) = workHistory.record(WorkHistoryAction.ChangeColor) {
        taskDao.updateTaskColor(id, colorInt)
    }
    suspend fun changeRunningTask(expectedTaskId: Long, expectedStart: Long, newTaskId: Long): Boolean = workHistory.record(WorkHistoryAction.ChangeRunningTask) {
        val active = sessionDao.getActive() ?: return@record false
        if (active.taskId != expectedTaskId || active.startedAt != expectedStart) return@record false
        val target = taskDao.getTaskById(newTaskId) ?: return@record false
        if (target.isArchived) return@record false
        sessionDao.changeActiveTask(active.id, newTaskId)
        taskDao.upsertActive(ActiveTaskEntity(taskId = newTaskId, startedAtEpochMs = active.startedAt))
        true
    }

    private suspend fun resolveTaskName(parentId: Long?, name: String, sourceId: Long?, decisions: Map<String, TaskNameDecision>): Pair<Long?, String> {
        val siblings = taskDao.getChildrenOnce(parentId).filter { it.id != sourceId }
        val duplicate = siblings.firstOrNull { sameTaskName(it.name, name) } ?: return null to name
        val key = "$parentId/$sourceId/$name"
        val choice = decisions[key] ?: decisions["*"]
            ?: throw TaskNameConflict(key, name, parentId?.let { getTaskPathLabel(it) }.orEmpty())
        if (choice.merge) return duplicate.id to name
        val renamed = choice.newName.trim().take(128).takeIf { it.isNotEmpty() && siblings.none { t -> sameTaskName(t.name, it) } }
            ?: uniqueTaskName(name, siblings.map { it.name })
        return null to renamed
    }

    suspend fun addTaskResolving(name: String, parentId: Long?, decisions: Map<String, TaskNameDecision>): Long = workHistory.record(WorkHistoryAction.AddTask) {
        val (existing, resolved) = resolveTaskName(parentId, name, null, decisions)
        existing ?: addTask(resolved, parentId)
    }

    suspend fun renameTask(taskId: Long, newName: String, decisions: Map<String, TaskNameDecision>): Map<Long, Long> = workHistory.record(WorkHistoryAction.RenameTask) {
        val remapped = mutableMapOf<Long, Long>()
        suspend fun merge(source: Long, target: Long) {
            taskDao.getChildrenIncludingArchived(source).forEach { child ->
                val (existing, resolved) = resolveTaskName(target, child.name, child.id, decisions)
                if (existing != null) merge(child.id, existing)
                else taskDao.updateTask(child.copy(parentId = target, name = resolved))
            }
            taskDao.replaceEventTask(source, target)
            sessionDao.replaceTask(source, target)
            taskDao.replaceActiveTask(source, target)
            taskDao.deleteTaskById(source)
            remapped[source] = target
        }
        val source = taskDao.getTaskById(taskId) ?: return@record emptyMap()
        val (target, resolved) = resolveTaskName(source.parentId, newName, taskId, decisions)
        if (target == null) taskDao.updateTaskName(taskId, resolved) else merge(taskId, target)
        remapped
    }
    suspend fun updateSortOrdersForParent(parentId: Long, idsInOrder: List<Long>) = workHistory.record(WorkHistoryAction.ReorderTasks) {
        taskDao.updateSortOrdersForParent(parentId, idsInOrder)
    }

    // 親（ルート＝プロジェクト）の並び替え
    suspend fun reorderParents(idsInOrder: List<Long>) = workHistory.record(WorkHistoryAction.ReorderTasks) {
        taskDao.updateSortOrdersForRoot(idsInOrder)
    }

    // ---------------- 履歴（task_events） ----------------
    suspend fun loadHistoryPage(page: Int, pageSize: Int): List<HistoryRowDb> {
        val offset = page * pageSize
        return taskDao.getEventsPage(limit = pageSize, offset = offset)
    }

    suspend fun loadHistoryRange(startMs: Long, endMs: Long): List<HistoryRowDb> =
        taskDao.getEventsInRange(startMs, endMs)

    suspend fun loadAnalysisEvents(
        startMs: Long,
        endMs: Long,
        nowMs: Long
    ): List<AnalysisEvent> {
        val tasks = taskDao.getAllTasksOnce().associateBy { it.id }

        fun pathFor(taskId: Long): List<AnalysisCategoryNode> {
            val reversed = mutableListOf<AnalysisCategoryNode>()
            val visited = mutableSetOf<Long>()
            var current = tasks[taskId]
            while (current != null && visited.add(current.id)) {
                reversed += AnalysisCategoryNode(current.id, current.name, current.colorInt)
                current = current.parentId?.let(tasks::get)
            }
            return reversed.asReversed()
        }

        val result = taskDao.getEventsInRange(startMs, endMs).mapNotNull { row ->
            val path = pathFor(row.taskId)
            if (path.isEmpty()) return@mapNotNull null
            AnalysisEvent(
                eventId = row.eventId,
                taskId = row.taskId,
                path = path,
                startMs = row.pressedAtEpochMs,
                endMs = row.pressedAtEpochMs + row.durationMillis.coerceAtLeast(0L)
            )
        }.toMutableList()

        taskDao.getActiveOrNull()?.let { active ->
            val activeEnd = minOf(nowMs, endMs)
            val path = pathFor(active.taskId)
            if (path.isNotEmpty() && activeEnd > active.startedAtEpochMs && activeEnd > startMs) {
                result += AnalysisEvent(
                    eventId = Long.MIN_VALUE,
                    taskId = active.taskId,
                    path = path,
                    startMs = active.startedAtEpochMs,
                    endMs = activeEnd
                )
            }
        }
        return result
    }

    suspend fun reassignEvents(eventIds: Set<Long>, newTaskId: Long): Int {
        val persistedIds = eventIds.filter { it > 0L }.distinct()
        if (persistedIds.isEmpty()) return 0
        return workHistory.record(WorkHistoryAction.ReassignRecords) {
            val destination = requireNotNull(taskDao.getTaskById(newTaskId)) {
                stringResolver(R.string.error_destination_missing)
            }
            require(!destination.isArchived) { stringResolver(R.string.error_destination_archived) }
            taskDao.updateEventTasks(persistedIds, newTaskId)
        }
    }

    // ---------------- Export / Import ----------------
    suspend fun getTaskTreeIds(rootId: Long): List<Long> {
        val result = mutableListOf<Long>()
        val visited = mutableSetOf<Long>()

        suspend fun visit(taskId: Long) {
            if (!visited.add(taskId)) return
            result += taskId
            taskDao.getChildrenIncludingArchived(taskId).forEach { child -> visit(child.id) }
        }

        visit(rootId)
        return result
    }

    /**
     * タスクと配下のタスクを一覧から削除し、既存記録を指定された方針で処理する。
     * 記録には task_events に加え、予測等に使う task_sessions と実行中状態も含める。
     */
    suspend fun deleteTask(
        rootId: Long,
        mode: TaskDeletionMode,
        replacementTaskId: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ) = workHistory.record(WorkHistoryAction.DeleteTask) {
        val root = requireNotNull(taskDao.getTaskById(rootId)) { stringResolver(R.string.error_delete_missing) }
        check(!root.isArchived) { stringResolver(R.string.error_delete_archived) }
        val ids = getTaskTreeIds(rootId)
        if (ids.isEmpty()) return@record

        when (mode) {
            TaskDeletionMode.PromoteToParent -> {
                val destinationId = requireNotNull(root.parentId) {
                    stringResolver(R.string.error_promote_root)
                }
                replaceTaskReferences(ids, destinationId)
                deleteTaskTreeInCurrentTransaction(rootId)
            }

            TaskDeletionMode.Replace -> {
                val destinationId = requireNotNull(replacementTaskId) {
                    stringResolver(R.string.error_replacement_required)
                }
                require(destinationId !in ids) { stringResolver(R.string.error_replacement_descendant) }
                val destination = requireNotNull(taskDao.getTaskById(destinationId)) {
                    stringResolver(R.string.error_replacement_missing)
                }
                require(!destination.isArchived) { stringResolver(R.string.error_replacement_archived) }
                replaceTaskReferences(ids, destinationId)
                deleteTaskTreeInCurrentTransaction(rootId)
            }

            TaskDeletionMode.KeepHistory -> {
                // 実行中なら削除時点までを履歴として確定し、その記録も元タスクのまま残す。
                sessionDao.getActive()?.takeIf { it.taskId in ids }?.let { active ->
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
                val activeTaskId = taskDao.getActiveOrNull()?.taskId
                if (activeTaskId != null && activeTaskId in ids) taskDao.clearActive()
                ids.forEach { taskDao.archiveTask(it) }
            }
        }
    }

    private suspend fun replaceTaskReferences(oldTaskIds: List<Long>, newTaskId: Long) {
        oldTaskIds.forEach { oldTaskId ->
            taskDao.replaceEventTask(oldTaskId, newTaskId)
            sessionDao.replaceTask(oldTaskId, newTaskId)
            taskDao.replaceActiveTask(oldTaskId, newTaskId)
        }
    }

    private suspend fun deleteTaskTreeInCurrentTransaction(rootId: Long) {
        val ids = getTaskTreeIds(rootId)
        if (ids.isEmpty()) return

        val activeTaskId = taskDao.getActiveOrNull()?.taskId
        if (activeTaskId != null && activeTaskId in ids) {
            taskDao.clearActive()
        }

        // 子孫から削除することで、自己参照の外部キーを追加した場合にも安全に動作する。
        ids.asReversed().forEach { taskDao.deleteTaskById(it) }
    }

    private suspend fun deleteTaskTree(rootId: Long) = db.withTransaction {
        deleteTaskTreeInCurrentTransaction(rootId)
    }

    suspend fun exportTasksJson(): String = db.withTransaction {
        val groups = observeTree().first()

        suspend fun encodeTask(task: TaskEntity): JSONObject {
            val children = taskDao.getChildren(task.id)
            return JSONObject().apply {
                put("name", task.name)
                task.colorInt?.let { put("color", it) }
                put(
                    "children",
                    JSONArray().also { array ->
                        children.forEach { child -> array.put(encodeTask(child)) }
                    }
                )
            }
        }

        JSONArray().also { array ->
            groups.forEach { group -> array.put(encodeTask(group.parent)) }
        }.toString(2)
    }

    suspend fun importTasksJson(json: String, replaceAll: Boolean, decisions: Map<String, TaskNameDecision> = emptyMap()) = workHistory.record(WorkHistoryAction.ImportTasks) {
        val current = observeTree().first()

        if (replaceAll) {
            current.forEach { p ->
                deleteTaskTreeInCurrentTransaction(p.parent.id)
            }
        }

        val root = JSONArray(json)

        suspend fun importNode(node: JSONObject, parentId: Long?, depth: Int) {
            require(depth <= 32) { "task hierarchy is too deep" }

            val name = node.optString("name", "").trim().take(128)
            if (name.isEmpty()) return

            val (existing, resolved) = resolveTaskName(parentId, name, null, decisions)
            val taskId = existing ?: addTask(resolved, parentId)

            if (node.has("color") && !node.isNull("color")) {
                changeColor(taskId, node.getInt("color"))
            }

            val children = node.optJSONArray("children") ?: JSONArray()
            for (index in 0 until children.length()) {
                val child = children.optJSONObject(index) ?: continue
                importNode(child, taskId, depth + 1)
            }
        }

        for (index in 0 until root.length()) {
            val node = root.optJSONObject(index) ?: continue
            importNode(node, parentId = null, depth = 0)
        }
    }

    /**
     * 手動追加した「過去の記録」を保存する。
     * 履歴画面は task_events を参照しているため、events も必ず追加する。
     * セッション表（task_sessions）にも“終了済み”として残す。
     */
    suspend fun addManualRecord(taskId: Long, startedAt: Long, endedAt: Long) = workHistory.record(WorkHistoryAction.AddRecord) {
        require(endedAt > startedAt) { "end must be after start" }

        val start = startedAt
        val end = endedAt

        // 1) 重複している既存イベントを取得
        val overlaps = taskDao.getEventsInRange(start, end)

        // 2) 既存イベントを「追加イベントを優先」で上書き調整
        overlaps.forEach { e ->
            val es = e.pressedAtEpochMs
            val ee = e.pressedAtEpochMs + kotlin.math.max(0L, e.durationMillis)

            when {
                // 追加区間が完全に覆う：削除（Case1 の B/C）
                es >= start && ee <= end -> {
                    taskDao.deleteEventById(e.eventId)
                }
                // 右側が食い込む：右端を切り詰め（...～start）
                es < start && ee <= end -> {
                    val newDur = start - es
                    if (newDur > 0) taskDao.updateEventTime(e.eventId, es, newDur)
                    else taskDao.deleteEventById(e.eventId)
                }
                // 左側が食い込む：左端を切り詰め（end～...）（Case2 の C）
                es >= start && ee > end -> {
                    val newStart = end
                    val newDur = ee - end
                    if (newDur > 0) taskDao.updateEventTime(e.eventId, newStart, newDur)
                    else taskDao.deleteEventById(e.eventId)
                }
                // 追加区間が中央に刺さる：左右で分割（...～start）と（end～...）
                es < start && ee > end -> {
                    val leftDur = start - es
                    val rightDur = ee - end

                    if (leftDur > 0) {
                        // 既存イベント本体は左側に縮める
                        taskDao.updateEventTime(e.eventId, es, leftDur)
                    } else {
                        // 左が0なら消して右を別イベントで作る
                        taskDao.deleteEventById(e.eventId)
                    }
                    if (rightDur > 0) {
                        taskDao.insertEvent(
                            TaskEventEntity(
                                taskId = e.taskId,
                                pressedAtEpochMs = end,
                                durationMillis = rightDur
                            )
                        )
                    }
                }
            }
        }

        // 3) 追加イベントを最後に挿入（最優先）
        taskDao.insertEvent(
            TaskEventEntity(
                taskId = taskId,
                pressedAtEpochMs = start,
                durationMillis = end - start
            )
        )
    }

    suspend fun hasOverlap(startedAt: Long, endedAt: Long): Boolean {
        if (endedAt <= startedAt) return false
        return taskDao.getEventsInRange(startedAt, endedAt).isNotEmpty()
    }

    suspend fun deleteEventById(eventId: Long) = workHistory.record(WorkHistoryAction.DeleteRecord) {
        taskDao.deleteEventById(eventId)
    }

    // === CSV Export ===
    suspend fun exportEventsCsv(): String {
        val rows = taskDao.getAllEventsForCsv()

        val fmt =
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())

        fun csvEscape(s: String): String {
            // カンマ・改行・ダブルクォートを含む場合は "..." で囲み、内部の " は "" にエスケープ
            val needQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            val body = s.replace("\"", "\"\"")
            return if (needQuote) "\"$body\"" else body
        }

        val sb = StringBuilder()
        sb.appendLine("event_id,task_id,task_name,start_epoch_ms,end_epoch_ms,duration_ms,start_iso,end_iso")

        rows.forEach { r ->
            val start = r.pressedAtEpochMs
            val dur = kotlin.math.max(0L, r.durationMillis)
            val end = start + dur

            val taskNameCsv = csvEscape(r.taskName) // ★ ここでタスク名を出力

            val startIso = fmt.format(java.util.Date(start))
            val endIso = fmt.format(java.util.Date(end))

            sb.append(r.eventId).append(',')
                .append(r.taskId).append(',')
                .append(taskNameCsv).append(',')
                .append(start).append(',')
                .append(end).append(',')
                .append(dur).append(',')
                .append(startIso).append(',')
                .append(endIso)
                .append('\n')
        }
        return sb.toString()
    }

    // === CSV Import ===
    suspend fun importEventsFromCsv(csv: String, replaceAll: Boolean) = workHistory.record(WorkHistoryAction.ImportRecords) {
        if (replaceAll) {
            taskDao.clearAllEvents()
        }
        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return@record

        // 先頭行がヘッダーならスキップ
        var idx = 0
        val head = lines.first().lowercase()
        if (head.contains("start_epoch_ms") && head.contains("end_epoch_ms")) idx = 1

        for (i in idx until lines.size) {
            val cols = parseCsvLine(lines[i])
            // 想定: event_id, task_id, task_name, start_epoch_ms, end_epoch_ms, duration_ms, start_iso, end_iso
            if (cols.size < 6) continue

            val taskId = cols.getOrNull(1)?.toLongOrNull() ?: continue
            val start = cols.getOrNull(3)?.toLongOrNull() ?: continue
            val end = cols.getOrNull(4)?.toLongOrNull()
                ?: (start + (cols.getOrNull(5)?.toLongOrNull() ?: 0L))
            if (end <= start) continue

            // 既存重複の調整は既存ロジックに委譲（新規を優先して調整）
            addManualRecord(taskId, start, end)
        }
    }

    // --- 行内カンマ＆ダブルクォート対応の簡易CSVパーサ ---
    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }

                c == ',' && !inQuotes -> {
                    out.add(sb.toString())
                    sb.setLength(0)
                }

                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    // ▼ TaskRepository.kt に追加（import等は適宜）
    suspend fun exportEventsTsv(startMs: Long, endMs: Long): String {
        val rows = taskDao.getEventsForTsvInRange(startMs, endMs)
        val fmt =
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())

        fun clean(s: String?): String =
            (s ?: "").replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

        val sb = StringBuilder()
        sb.append("event_name\ttask_name\tsubtask_name\tstart_epoch_ms\tend_epoch_ms\tduration_ms\tstart_iso\tend_iso\n")

        rows.forEach { r ->
            val rawStart = r.pressedAtEpochMs
            val rawDur = kotlin.math.max(0L, r.durationMillis)
            val rawEnd = rawStart + rawDur
            val start = maxOf(startMs, rawStart)
            val end = minOf(endMs, rawEnd)
            val dur = (end - start).coerceAtLeast(0L)
            if (dur <= 0L) return@forEach

            val startIso = fmt.format(java.util.Date(start))
            val endIso = fmt.format(java.util.Date(end))

            sb.append(clean(r.eventName)).append('\t')
                .append(clean(r.taskName)).append('\t')
                .append(clean(r.subtaskName)).append('\t')
                .append(start).append('\t')
                .append(end).append('\t')
                .append(dur).append('\t')
                .append(startIso).append('\t')
                .append(endIso).append('\n')
        }
        return sb.toString()
    }

    /** TSVインポート（イベント）。既存イベントを全部消すかは引数で制御 */
    suspend fun importEventsTsv(
        tsv: String,
        clearExistingEvents: Boolean = false,
        createMissingTasks: Boolean = true,
        decisions: Map<String, TaskNameDecision> = emptyMap()
    ): Int = workHistory.record(WorkHistoryAction.ImportRecords) {
        if (clearExistingEvents) {
            taskDao.clearAllEvents()
        }

        var imported = 0
        // Repeated TSV rows for the same path reuse the decision and task ID.
        val importedNames = mutableMapOf<Pair<Long?, String>, Long>()
        suspend fun resolveImported(parent: Long?, name: String): Long {
            val key = parent to name.trim().lowercase(java.util.Locale.ROOT)
            importedNames[key]?.let { return it }
            val (existing, resolved) = resolveTaskName(parent, name.trim().take(128), null, decisions)
            val id = existing ?: run {
                check(createMissingTasks) { "Task '$name' not found" }
                addTask(resolved, parent)
            }
            importedNames[key] = id
            return id
        }

        fun parseLongOrNull(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

        // 名前→ID（なければ作成）
        suspend fun ensureTaskIds(
            eventName: String,
            taskName: String?,
            subtaskName: String?
        ): Long {
            val projectId = resolveImported(null, eventName)
            if (taskName.isNullOrBlank() && subtaskName.isNullOrBlank()) return projectId
            val childId = resolveImported(projectId, taskName ?: requireNotNull(subtaskName))
            return if (!subtaskName.isNullOrBlank()) resolveImported(childId, subtaskName) else childId
        }

        // 1行ずつ
        tsv.lineSequence().forEachIndexed { idx, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed
            if (idx == 0 && trimmed.lowercase()
                    .startsWith("event_name")
            ) return@forEachIndexed // header

            val cols = trimmed.split('\t')
            if (cols.size < 6) return@forEachIndexed  // 必須列不足

            val eventName = cols[0].ifEmpty { return@forEachIndexed }
            val taskName = cols.getOrNull(1).orEmpty().ifBlank { null }
            val subtaskName = cols.getOrNull(2).orEmpty().ifBlank { null }
            val startMs = parseLongOrNull(cols.getOrNull(3)) ?: return@forEachIndexed
            val endMs = parseLongOrNull(cols.getOrNull(4))
                ?: (parseLongOrNull(cols.getOrNull(3))?.let { s ->
                    val d = parseLongOrNull(cols.getOrNull(5)) ?: return@forEachIndexed
                    s + d
                }) ?: return@forEachIndexed

            if (endMs <= startMs) return@forEachIndexed

            // 名前解決 → taskId 決定（必要なら作成）
            val taskId = ensureTaskIds(eventName, taskName, subtaskName)

            // 既存調整込みで登録（重複範囲を優先上書き）
            addManualRecord(taskId, startMs, endMs)
            imported++
        }
        imported
    }

    // ▼ 便利ラッパ（任意）
    suspend fun predictNextTaskId(now: Long = System.currentTimeMillis()): Long? {
        val cur = sessionDao.getActive()?.taskId
        return NextTaskPredictor.getInstance(taskDao).predictNext(cur, now)
    }

    suspend fun switchToPredictedNext(now: Long = System.currentTimeMillis()) {
        val next = predictNextTaskId(now) ?: return
        startOrSwitch(newTaskId = next, now = now)
    }


    suspend fun getDaysWithData(startMs: Long, endMs: Long): Set<Long> {
        val rows = taskDao.getEventsInRange(startMs, endMs)
        val cal = java.util.Calendar.getInstance()

        fun startOfDay(ms: Long): Long {
            cal.timeInMillis = ms
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        val out = linkedSetOf<Long>()
        rows.forEach { row ->
            val start = row.pressedAtEpochMs
            val endExclusive = row.pressedAtEpochMs + kotlin.math.max(0L, row.durationMillis)
            var day = startOfDay(start)
            val last = startOfDay((endExclusive - 1L).coerceAtLeast(start))
            while (day <= last) {
                out += day
                cal.timeInMillis = day
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                day = cal.timeInMillis
            }
        }
        return out
    }

}
