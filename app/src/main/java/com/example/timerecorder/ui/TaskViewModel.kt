package com.example.timerecorder.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.ParentWithChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItemUi(
    val id: Long,
    val taskId: Long,
    val taskName: String,
    val color: Color,
    val pressedAt: Long,
    val durationMillis: Long
)

/** 進行中タスクを UI で使いやすい形に正規化したモデル */
data class ActiveUi(
    val taskId: Long,
    val taskName: String,
    val color: Color,
    /** 進行開始のエポック（ms） */
    val startEpochMs: Long
)

private const val HISTORY_PAGE_SIZE = 100

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: TaskRepository

    init {
        val db = AppDatabase.get(app.applicationContext, viewModelScope)
        repo = TaskRepository(db.taskDao())
    }

    // 親＋直下の子
    val parents: StateFlow<List<ParentWithChildren>> =
        repo.observeTree()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // アクティブ（DBの未終了セッションを監視する想定）
    val active: StateFlow<ActiveWithTask?> =
        repo.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** UI 向けに正規化された進行中タスク（タイムライン・集計で使用） */
    val activeUi: StateFlow<ActiveUi?> =
        repo.observeActive()
            .map { a: ActiveWithTask? ->
                if (a == null) return@map null
                val start = a.active.startedAtEpochMs
                ActiveUi(
                    taskId = a.task.id,
                    taskName = a.task.name,
                    color = Color(a.task.colorInt ?: 0xFF9E9E9E.toInt()),
                    startEpochMs = start
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- 操作 ---
    fun onTaskPressed(taskId: Long) = viewModelScope.launch {
        // 既存が走っていれば停止してから切替（Repository側で制御）
        repo.startOrSwitch(taskId, nowMs = System.currentTimeMillis())
    }

    fun onStopPressed() = viewModelScope.launch {
        repo.stopActive(nowMs = System.currentTimeMillis())
    }

    // --- サブタスク（孫）関連 ---
    fun children(parentId: Long) = repo.observeChildren(parentId)
    suspend fun hasChildren(taskId: Long) = repo.hasChildren(taskId)

    // --- History ページング ---
    private val _historyItems = MutableStateFlow<List<HistoryItemUi>>(emptyList())
    val historyItems: StateFlow<List<HistoryItemUi>> = _historyItems

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading

    private val _historyEndReached = MutableStateFlow(false)
    val historyEndReached: StateFlow<Boolean> = _historyEndReached

    private var historyPage: Int = 0
    private var historyInitialized = false

    fun ensureHistoryFirstPage() {
        if (historyInitialized) return
        historyInitialized = true
        loadHistoryPageInternal(reset = true)
    }

    // これを TaskViewModel.kt に置く（既存の同名拡張を置き換え）
    private fun com.example.timerecorder.data.db.HistoryRowDb.toUi(): HistoryItemUi =
        HistoryItemUi(
            id = eventId,
            taskId = taskId,
            taskName = taskName,
            color = Color(colorInt ?: 0xFF9E9E9E.toInt()),
            pressedAt = pressedAtEpochMs,
            durationMillis = durationMillis
        )

    fun loadNextHistoryPage(pageSize: Int = HISTORY_PAGE_SIZE) {
        if (_historyLoading.value || _historyEndReached.value) return
        viewModelScope.launch {
            _historyLoading.value = true
            val rows = repo.loadHistoryPage(historyPage, pageSize)   // List<HistoryRowDb>
            val mapped: List<HistoryItemUi> = rows.map { it.toUi() }
            _historyItems.value = _historyItems.value + mapped

            if (rows.size < pageSize) {
                _historyEndReached.value = true
            } else {
                historyPage += 1
            }
            _historyLoading.value = false
        }
    }

    private fun loadHistoryPageInternal(reset: Boolean) {
        viewModelScope.launch {
            _historyLoading.value = true
            if (reset) {
                historyPage = 0
                _historyEndReached.value = false
                _historyItems.value = emptyList()
            }
            val rows = repo.loadHistoryPage(historyPage, HISTORY_PAGE_SIZE)
            val mapped = rows.map {
                HistoryItemUi(
                    id = it.eventId,
                    taskId = it.taskId,
                    taskName = it.taskName,
                    color = Color(it.colorInt ?: 0xFF9E9E9E.toInt()),
                    pressedAt = it.pressedAtEpochMs,
                    durationMillis = it.durationMillis
                )
            }
            _historyItems.value = _historyItems.value + mapped
            if (rows.size < HISTORY_PAGE_SIZE) {
                _historyEndReached.value = true
            } else {
                historyPage += 1
            }
            _historyLoading.value = false
        }
    }

    fun reloadHistory(pageSize: Int = HISTORY_PAGE_SIZE) {
        viewModelScope.launch {
            _historyItems.value = emptyList()
            _historyEndReached.value = false
            historyPage = 0
            historyInitialized = false
            loadNextHistoryPage(pageSize)
        }
    }

    fun addParentTask(name: String) = viewModelScope.launch {
        repo.addTask(name = name, parentId = null)
    }

    fun addChildTask(parentId: Long, name: String) = viewModelScope.launch {
        repo.addTask(name = name, parentId = parentId)
    }

    fun deleteParent(parentId: Long) = viewModelScope.launch {
        repo.deleteProject(parentId)
    }

    fun changeParentColor(parentId: Long, color: Color) = viewModelScope.launch {
        repo.changeColor(parentId, color.toArgb())
    }

    /** タスク名を変更（1〜128文字に制限） */
    fun renameTask(taskId: Long, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim().take(128)
        if (trimmed.isNotEmpty()) {
            repo.renameTask(taskId, trimmed)
        }
    }

    /** 単一タスクを削除（子孫の扱いはリポジトリ側の実装に依存） */
    fun deleteTask(taskId: Long) = viewModelScope.launch {
        repo.deleteTaskById(taskId)
    }

    fun reorderChildren(parentId: Long, idsInOrder: List<Long>) = viewModelScope.launch {
        repo.updateSortOrdersForParent(parentId, idsInOrder)
    }

    /** 比較用に名前を正規化（trim + lowerCase） */
    private fun normalizeName(name: String) = name.trim().lowercase()

    /** 親（プロジェクト）直下に、同名の子タスクが存在するか */
    fun hasDuplicateChildName(parentId: Long, name: String): Boolean {
        if (name.isBlank()) return false
        val target = parents.value.firstOrNull { it.parent.id == parentId } ?: return false
        val n = normalizeName(name)
        return target.children.any { normalizeName(it.name) == n }
    }

    /** 親（プロジェクト）に、同名の親タスクが存在するか */
    fun hasDuplicateParentName(name: String): Boolean {
        if (name.isBlank()) return false
        val n = normalizeName(name)
        return parents.value.any { normalizeName(it.parent.name) == n }
    }

    // --- タイムライン用 ---
    private val _timelineItems = MutableStateFlow<List<HistoryItemUi>>(emptyList())
    val timelineItems: StateFlow<List<HistoryItemUi>> = _timelineItems

    /** 指定区間 [startMs, endMs) に重なる履歴をロードして StateFlow に反映 */
    fun loadTimelineRange(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            val rows = repo.loadHistoryRange(startMs, endMs)
            _timelineItems.value = rows.map {
                HistoryItemUi(
                    id = it.eventId,
                    taskId = it.taskId,
                    taskName = it.taskName,
                    color = Color(it.colorInt ?: 0xFF9E9E9E.toInt()),
                    pressedAt = it.pressedAtEpochMs,
                    durationMillis = it.durationMillis.coerceAtLeast(0L)
                )
            }
        }
    }

    // =======================================
    // Export / Import（タスク名＋色＋直下の子）  👇
    // =======================================

    /**
     * 現在の親タスク＋直下の子タスクと色を JSON 文字列でエクスポート。
     *
     * フォーマット：
     * [
     *   {
     *     "name": "Parent",
     *     "color": 16753928,       // null の場合は省略可
     *     "children": [ {"name":"Child1"}, {"name":"Child2"} ]
     *   },
     *   ...
     * ]
     */
    suspend fun exportTasksJson(): String = withContext(Dispatchers.Default) {
        val arr = JSONArray()
        parents.value.forEach { group ->
            val obj = JSONObject().apply {
                put("name", group.parent.name)
                group.parent.colorInt?.let { put("color", it) }
                val kids = JSONArray()
                group.children.forEach { c ->
                    kids.put(JSONObject().apply { put("name", c.name) })
                }
                put("children", kids)
            }
            arr.put(obj)
        }
        arr.toString(2) // pretty print
    }

    /**
     * JSONから Import。replaceAll=true の場合は既存の親を全削除してから取り込み。
     * 既存に同名の親がある場合はマージ（子は同名重複を避けて追加）。
     */
    fun importTasksFromJson(json: String, replaceAll: Boolean) = viewModelScope.launch {
        val nowParents = parents.value

        // 置換なら全部消す（進行中は止めない/履歴は保持）
        if (replaceAll) {
            // 既存親を順に削除
            nowParents.forEach { repo.deleteProject(it.parent.id) }
        }

        // 現在の親名 → id / 色 の索引（マージ用）
        suspend fun findParentIdByName(name: String): Long? =
            parents.value.firstOrNull { it.parent.name == name }?.parent?.id

        // JSON を解析
        val root = JSONArray(json)
        for (i in 0 until root.length()) {
            val p = root.optJSONObject(i) ?: continue
            val parentName = p.optString("name", "").trim().take(128)
            if (parentName.isEmpty()) continue

            val colorInt = if (p.has("color")) p.optInt("color") else null
            val parentId: Long = when (val existing = findParentIdByName(parentName)) {
                null -> repo.addTask(parentName, null)
                else -> existing
            }
            // 色設定（新規時は null のままも可）
            if (colorInt != null) repo.changeColor(parentId, colorInt)

            // 子を取り込み
            val children = p.optJSONArray("children") ?: JSONArray()
            // 既存の子名集合（重複追加を防止）
            val existingChildNames =
                parents.value.firstOrNull { it.parent.id == parentId }?.children?.map { it.name }
                    ?.toMutableSet() ?: mutableSetOf()

            for (j in 0 until children.length()) {
                val c = children.optJSONObject(j) ?: continue
                val childName = c.optString("name", "").trim().take(128)
                if (childName.isEmpty()) continue
                if (existingChildNames.contains(childName)) continue
                repo.addTask(childName, parentId)
                existingChildNames.add(childName)
            }
        }
    }
}
