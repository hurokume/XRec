package com.example.timerecorder.data

import androidx.room.withTransaction
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
    private val taskDao: TaskDao
) {
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
    fun observeActive(): Flow<ActiveWithTask?> = taskDao.observeActiveWithTask()

    // ---------------- 操作系（短時間統合スイッチ） ----------------
    suspend fun startOrSwitch(
        newTaskId: Long,
        now: Long,
        mergeThresholdSec: Long = 60
    ): SwitchResult = db.withTransaction {
        val newTask = requireNotNull(taskDao.getTaskById(newTaskId)) {
            "開始するタスクが見つかりません"
        }
        require(!newTask.isArchived) { "非表示のタスクは開始できません" }
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

    suspend fun changeColor(id: Long, colorInt: Int) = taskDao.updateTaskColor(id, colorInt)
    suspend fun renameTask(taskId: Long, newName: String) = taskDao.updateTaskName(taskId, newName)
    suspend fun updateSortOrdersForParent(parentId: Long, idsInOrder: List<Long>) =
        taskDao.updateSortOrdersForParent(parentId, idsInOrder)

    // 親（ルート＝プロジェクト）の並び替え
    suspend fun reorderParents(idsInOrder: List<Long>) =
        taskDao.updateSortOrdersForRoot(idsInOrder)

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
    ) = db.withTransaction {
        val root = requireNotNull(taskDao.getTaskById(rootId)) { "削除対象が見つかりません" }
        check(!root.isArchived) { "削除対象は既に非表示です" }
        val ids = getTaskTreeIds(rootId)
        if (ids.isEmpty()) return@withTransaction

        when (mode) {
            TaskDeletionMode.PromoteToParent -> {
                val destinationId = requireNotNull(root.parentId) {
                    "最上位の項目は1階層上へ変更できません"
                }
                replaceTaskReferences(ids, destinationId)
                deleteTaskTreeInCurrentTransaction(rootId)
            }

            TaskDeletionMode.Replace -> {
                val destinationId = requireNotNull(replacementTaskId) {
                    "置換先タスクを選択してください"
                }
                require(destinationId !in ids) { "削除対象の配下は置換先にできません" }
                val destination = requireNotNull(taskDao.getTaskById(destinationId)) {
                    "置換先タスクが見つかりません"
                }
                require(!destination.isArchived) { "非表示のタスクは置換先にできません" }
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

    suspend fun importTasksJson(json: String, replaceAll: Boolean) = db.withTransaction {
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

            val taskId = taskDao.findTaskIdByNameInParent(parentId, name)
                ?: addTask(name, parentId)

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
    suspend fun addManualRecord(taskId: Long, startedAt: Long, endedAt: Long) = db.withTransaction {
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

    suspend fun deleteEventById(eventId: Long) = db.withTransaction {
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
    suspend fun importEventsFromCsv(csv: String, replaceAll: Boolean) = db.withTransaction {
        if (replaceAll) {
            taskDao.clearAllEvents()
        }
        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return@withTransaction

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
        createMissingTasks: Boolean = true
    ): Int = db.withTransaction {
        if (clearExistingEvents) {
            taskDao.clearAllEvents()
        }

        var imported = 0

        fun parseLongOrNull(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

        // 名前→ID（なければ作成）
        suspend fun ensureTaskIds(
            eventName: String,
            taskName: String?,
            subtaskName: String?
        ): Long {
            // 親（プロジェクト）
            var projectId = taskDao.findTaskIdByNameInParent(null, eventName)
            if (projectId == null) {
                check(createMissingTasks) { "project '$eventName' not found" }
                projectId = addTask(eventName, null)
            }

            // 子（タスク）
            val taskBaseName = taskName ?: subtaskName ?: error("task/subtask name missing")
            var childId = taskDao.findTaskIdByNameInParent(projectId, taskBaseName)
            if (childId == null) {
                check(createMissingTasks) { "task '$taskBaseName' under '$eventName' not found" }
                childId = addTask(taskBaseName, projectId)
            }

            // サブタスク
            if (!subtaskName.isNullOrBlank()) {
                var subId = taskDao.findTaskIdByNameInParent(childId, subtaskName)
                if (subId == null) {
                    check(createMissingTasks) { "subtask '$subtaskName' under '$taskBaseName' not found" }
                    subId = addTask(subtaskName, childId)
                }
                return subId
            }
            return childId
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
