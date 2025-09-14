package com.example.timerecorder.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerecorder.data.SwitchResult
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.TaskDao
import com.example.timerecorder.data.db.TaskEventEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryItemUi(
    val id: Long,
    val taskId: Long,
    val taskName: String,
    val color: Color,
    val pressedAt: Long,
    val durationMillis: Long
)

data class ActiveUi(
    val taskId: Long,
    val taskName: String,
    val color: Color,
    val startEpochMs: Long
)

private const val HISTORY_PAGE_SIZE = 100
private const val DEFAULT_MERGE_THRESHOLD_SECONDS: Long = 60

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
}

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app.applicationContext, viewModelScope)
    private val taskDao: TaskDao = db.taskDao()
    private val repo: TaskRepository = TaskRepository(
        db = db,
        sessionDao = db.sessionDao(),
        taskDao = taskDao
    )

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val parents: StateFlow<List<ParentWithChildren>> =
        repo.observeTree()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val active: StateFlow<ActiveWithTask?> =
        repo.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeUi: StateFlow<ActiveUi?> =
        repo.observeActive()
            .map { a: ActiveWithTask? ->
                if (a == null) return@map null
                ActiveUi(
                    taskId = a.task.id,
                    taskName = a.task.name,
                    color = Color(a.task.colorInt ?: 0xFF9E9E9E.toInt()),
                    startEpochMs = a.active.startedAtEpochMs
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onTaskPressed(taskId: Long) = viewModelScope.launch {
        val result = repo.startOrSwitch(
            newTaskId = taskId,
            now = System.currentTimeMillis(),
            mergeThresholdSec = DEFAULT_MERGE_THRESHOLD_SECONDS
        )

        if (result is SwitchResult.MergedShort) {
            val prevSec = (result.prevDurationMs / 1000).coerceAtLeast(0)
            val thresholdSec = (result.thresholdMs / 1000).coerceAtLeast(0)
            _events.tryEmit(
                UiEvent.ShowMessage("直前のタスクが ${prevSec}秒（しきい値 ${thresholdSec}秒）未満だったため統合しました")
            )
        }

        reloadHistory()
        ensureHistoryFirstPage()
    }

    fun onStopPressed() = viewModelScope.launch {
        repo.stopActive(nowMs = System.currentTimeMillis())
    }

    fun children(parentId: Long) = repo.observeChildren(parentId)
    suspend fun hasChildren(taskId: Long) = repo.hasChildren(taskId)

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

    private fun com.example.timerecorder.data.db.HistoryRowDb.toUi(): HistoryItemUi =
        HistoryItemUi(
            id = eventId,
            taskId = taskId,
            taskName = taskName,
            color = Color(colorInt ?: 0xFF9E9E9E.toInt()),
            pressedAt = pressedAtEpochMs,
            durationMillis = durationMillis
        )

    fun loadNextHistoryPage(pageSize: Int = HISTORY_PAGE_SIZE) = viewModelScope.launch {
        if (_historyEndReached.value) return@launch
        val rows = repo.loadHistoryPage(historyPage, pageSize)
        if (rows.isEmpty()) {
            _historyEndReached.value = true
            return@launch
        }
        _historyItems.value = _historyItems.value + rows.map { it.toUi() }
        historyPage++
        _historyEndReached.value = rows.size < pageSize
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
            _historyItems.value = _historyItems.value + rows.map { it.toUi() }
            if (rows.size < HISTORY_PAGE_SIZE) _historyEndReached.value = true else historyPage += 1
            _historyLoading.value = false
        }
    }

    fun reloadHistory(pageSize: Int = HISTORY_PAGE_SIZE) = viewModelScope.launch {
        historyPage = 0
        _historyEndReached.value = false

        val first = repo.loadHistoryPage(0, pageSize)
        _historyItems.value = first.map { it.toUi() }
        _historyEndReached.value = first.size < pageSize
        historyInitialized = true
    }

    fun addParentTask(name: String) = viewModelScope.launch { repo.addTask(name, null) }
    fun addChildTask(parentId: Long, name: String) =
        viewModelScope.launch { repo.addTask(name, parentId) }

    fun deleteParent(parentId: Long) = viewModelScope.launch { repo.deleteProject(parentId) }
    fun changeParentColor(parentId: Long, color: Color) =
        viewModelScope.launch { repo.changeColor(parentId, color.toArgb()) }

    fun renameTask(taskId: Long, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim().take(128)
        if (trimmed.isNotEmpty()) repo.renameTask(taskId, trimmed)
    }

    fun deleteTask(taskId: Long) = viewModelScope.launch { repo.deleteTaskById(taskId) }
    fun reorderChildren(parentId: Long, idsInOrder: List<Long>) =
        viewModelScope.launch { repo.updateSortOrdersForParent(parentId, idsInOrder) }

    private fun normalizeName(name: String) = name.trim().lowercase()
    fun hasDuplicateChildName(parentId: Long, name: String): Boolean {
        if (name.isBlank()) return false
        val target = parents.value.firstOrNull { it.parent.id == parentId } ?: return false
        val n = normalizeName(name)
        return target.children.any { normalizeName(it.name) == n }
    }

    fun hasDuplicateParentName(name: String): Boolean {
        if (name.isBlank()) return false
        val n = normalizeName(name)
        return parents.value.any { normalizeName(it.parent.name) == n }
    }

    private val _timelineItems = MutableStateFlow<List<HistoryItemUi>>(emptyList())
    val timelineItems: StateFlow<List<HistoryItemUi>> = _timelineItems

    fun loadTimelineRange(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            val rows = repo.loadHistoryRange(startMs, endMs)
            _timelineItems.value =
                rows.map {
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

    suspend fun exportTasksJson(): String = repo.exportTasksJson()
    fun importTasksFromJson(json: String, replaceAll: Boolean) = viewModelScope.launch {
        repo.importTasksJson(json, replaceAll)
    }

    suspend fun hasOverlap(startMs: Long, endMs: Long): Boolean = repo.hasOverlap(startMs, endMs)

    fun addManualRecord(taskId: Long, startedAt: Long, endedAt: Long) {
        viewModelScope.launch {
            if (endedAt <= startedAt) {
                _events.emit(UiEvent.ShowMessage("終了は開始より後にしてください"))
                return@launch
            }
            try {
                repo.addManualRecord(taskId, startedAt, endedAt)
                reloadHistory()
                _events.emit(UiEvent.ShowMessage("記録を追加しました"))
            } catch (t: Throwable) {
                _events.emit(UiEvent.ShowMessage("追加に失敗: ${t.message}"))
            }
        }
    }

    fun editEventAbsorbNeighbors(
        eventId: Long,
        newTaskId: Long,
        newStartMs: Long,
        newEndMs: Long
    ) = viewModelScope.launch {
        if (newEndMs <= newStartMs) return@launch

        val overlaps = taskDao.getEventsInRange(newStartMs, newEndMs)

        overlaps.forEach { row ->
            if (row.eventId == eventId) return@forEach

            val s = row.pressedAtEpochMs
            val e = row.pressedAtEpochMs + kotlin.math.max(0L, row.durationMillis)

            when {
                s >= newStartMs && e <= newEndMs -> {
                    taskDao.deleteEventById(row.eventId)
                }

                s < newStartMs && e > newStartMs && e <= newEndMs -> {
                    val newDur = newStartMs - s
                    if (newDur > 0L) {
                        taskDao.updateEventTime(row.eventId, s, newDur)
                    } else {
                        taskDao.deleteEventById(row.eventId)
                    }
                }

                s >= newStartMs && s < newEndMs && e > newEndMs -> {
                    val newStart = newEndMs
                    val newDur = e - newEndMs
                    if (newDur > 0L) {
                        taskDao.updateEventTime(row.eventId, newStart, newDur)
                    } else {
                        taskDao.deleteEventById(row.eventId)
                    }
                }
            }
        }

        taskDao.updateEventTask(eventId, newTaskId)
        taskDao.updateEventTime(eventId, newStartMs, newEndMs - newStartMs)

        reloadHistory()
    }

    fun upsertRecordAbsorbingNeighbors(taskId: Long, startMs: Long, endMs: Long) {
        viewModelScope.launch {
            if (endMs <= startMs) {
                _events.emit(UiEvent.ShowMessage("終了は開始より後にしてください"))
                return@launch
            }

            val overlaps = taskDao.getEventsInRange(startMs, endMs)

            overlaps.forEach { row ->
                val s = row.pressedAtEpochMs
                val e = row.pressedAtEpochMs + kotlin.math.max(0L, row.durationMillis)

                when {
                    s >= startMs && e <= endMs -> {
                        taskDao.deleteEventById(row.eventId)
                    }

                    s < startMs && e > startMs && e <= endMs -> {
                        val newDur = startMs - s
                        if (newDur > 0L) {
                            taskDao.updateEventTime(row.eventId, s, newDur)
                        } else {
                            taskDao.deleteEventById(row.eventId)
                        }
                    }

                    s >= startMs && s < endMs && e > endMs -> {
                        val newStart = endMs
                        val newDur = e - endMs
                        if (newDur > 0L) {
                            taskDao.updateEventTime(row.eventId, newStart, newDur)
                        } else {
                            taskDao.deleteEventById(row.eventId)
                        }
                    }

                    s < startMs && e > endMs -> {
                        val leftDur = startMs - s
                        val rightStart = endMs
                        val rightDur = e - endMs

                        if (leftDur > 0L) {
                            taskDao.updateEventTime(row.eventId, s, leftDur)
                        } else {
                            taskDao.deleteEventById(row.eventId)
                        }

                        if (rightDur > 0L) {
                            taskDao.insertEvent(
                                com.example.timerecorder.data.db.TaskEventEntity(
                                    taskId = row.taskId,
                                    pressedAtEpochMs = rightStart,
                                    durationMillis = rightDur
                                )
                            )
                        }
                    }
                }
            }

            taskDao.insertEvent(
                com.example.timerecorder.data.db.TaskEventEntity(
                    taskId = taskId,
                    pressedAtEpochMs = startMs,
                    durationMillis = endMs - startMs
                )
            )

            reloadHistory()
            _events.emit(UiEvent.ShowMessage("保存しました"))
        }
    }

    /** はさみ機能：イベントをsplitAtMsで2つに分割 */
    fun splitEventAt(eventId: Long, splitAtMs: Long) = viewModelScope.launch {
        val ev = taskDao.getEventById(eventId)
        if (ev == null) {
            _events.emit(UiEvent.ShowMessage("対象の記録が見つかりません"))
            return@launch
        }

        val s = ev.pressedAtEpochMs
        val e = ev.pressedAtEpochMs + kotlin.math.max(0L, ev.durationMillis)

        if (splitAtMs <= s || splitAtMs >= e) {
            _events.emit(UiEvent.ShowMessage("分割時刻は記録の範囲内で指定してください"))
            return@launch
        }

        val leftDur = splitAtMs - s
        val rightDur = e - splitAtMs

        // 左側を元イベントとして時間を更新
        taskDao.updateEventTime(eventId, s, leftDur)

        // 右側を新規イベントとして挿入（同じtaskIdのまま）
        taskDao.insertEvent(
            TaskEventEntity(
                taskId = ev.taskId,
                pressedAtEpochMs = splitAtMs,
                durationMillis = rightDur
            )
        )

        reloadHistory()
        _events.emit(UiEvent.ShowMessage("分割しました"))
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            try {
                repo.deleteEventById(eventId)

                // 履歴更新（UI反映）
                reloadHistory()

                // 任意：スナックバー用イベント
                _events.emit(UiEvent.ShowMessage("記録を削除しました"))
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowMessage("削除に失敗しました: ${e.message}"))
            }
        }
    }

    // 追加：CSV エクスポート
    suspend fun exportEventsCsv(): String = repo.exportEventsCsv()

    // 追加：CSV インポート
    fun importEventsFromCsv(csv: String, replaceAll: Boolean) {
        viewModelScope.launch {
            try {
                repo.importEventsFromCsv(csv, replaceAll)
                reloadHistory()
                _events.emit(UiEvent.ShowMessage("CSVインポート完了"))
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowMessage("CSVインポート失敗: ${e.message}"))
            }
        }
    }

    // ▼ TaskViewModel に追加
    suspend fun exportEventsTsv(): String = repo.exportEventsTsv()

    suspend fun importEventsTsv(tsv: String, clearExisting: Boolean, createMissing: Boolean): Int =
        repo.importEventsTsv(tsv, clearExisting, createMissing)
}
