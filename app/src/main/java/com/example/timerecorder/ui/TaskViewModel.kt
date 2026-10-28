package com.example.timerecorder.ui

import android.app.Application
import com.example.timerecorder.data.TaskNameConflict
import com.example.timerecorder.data.TaskNameDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerecorder.R
import com.example.timerecorder.data.AdvancedAnalysisEngine
import com.example.timerecorder.data.AdvancedAnalysisResult
import com.example.timerecorder.data.AnalysisRequest
import com.example.timerecorder.data.DailyPlans
import com.example.timerecorder.data.SwitchResult
import com.example.timerecorder.data.TaskAnalysisEngine
import com.example.timerecorder.data.TaskAnalysisRequest
import com.example.timerecorder.data.TaskAnalysisResult
import com.example.timerecorder.data.TaskDeletionMode
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.WorkHistoryAction
import com.example.timerecorder.data.RestoreWorkResult
import com.example.timerecorder.data.WeeklyReportData
import com.example.timerecorder.data.WeeklyReportEngine
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private const val HISTORY_PAGE_SIZE = 200
private const val DEFAULT_MERGE_THRESHOLD_SECONDS: Long = 60

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data object TasksMerged : UiEvent
    data object DataRestored : UiEvent
}

data class AdvancedAnalysisUiState(
    val isLoading: Boolean = false,
    val result: AdvancedAnalysisResult? = null,
    val errorMessage: String? = null
)

data class TaskAnalysisUiState(
    val request: TaskAnalysisRequest? = null,
    val isLoading: Boolean = false,
    val result: TaskAnalysisResult? = null,
    val errorMessage: String? = null
)

class TaskViewModel @JvmOverloads constructor(app: Application, database: AppDatabase? = null) : AndroidViewModel(app) {
    private val conflictMutex = Mutex()
    val nameConflict = MutableStateFlow<TaskNameConflict?>(null)
    val editingTask = MutableStateFlow<Pair<Long, String>?>(null)
    private var conflictAnswer: CompletableDeferred<TaskNameDecision>? = null
    fun requestTaskEdit(id: Long, name: String) { editingTask.value = id to name }
    fun answerNameConflict(answer: TaskNameDecision?) {
        if (answer == null) conflictAnswer?.cancel() else conflictAnswer?.complete(answer)
    }
    private suspend fun <T> resolveNames(action: suspend (Map<String, TaskNameDecision>) -> T): T = conflictMutex.withLock {
        val decisions = mutableMapOf<String, TaskNameDecision>()
        while (true) {
            try { return@withLock action(decisions) }
            catch (conflict: TaskNameConflict) {
                // Transaction rolled back before the prompt; recording remains available.
                val answer = CompletableDeferred<TaskNameDecision>()
                conflictAnswer = answer
                nameConflict.value = conflict
                try {
                    val choice = answer.await()
                    decisions[conflict.key] = choice
                    if (choice.applyAll) decisions["*"] = choice.copy(newName = "")
                } finally { nameConflict.value = null; conflictAnswer = null }
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private fun text(@StringRes resourceId: Int, vararg formatArgs: Any): String =
        getApplication<Application>().getString(resourceId, *formatArgs)

    private val db = database ?: AppDatabase.get(app.applicationContext, viewModelScope)
    private val taskDao: TaskDao = db.taskDao()
    private val repo: TaskRepository = TaskRepository(
        db = db,
        sessionDao = db.sessionDao(),
        taskDao = taskDao,
        stringResolver = { text(it) }
    )

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val workHistory = repo.observeWorkHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _workHistoryRestoring = MutableStateFlow(false)
    val workHistoryRestoring: StateFlow<Boolean> = _workHistoryRestoring

    fun restoreWorkHistory(id: Long, expectedLatestId: Long) = viewModelScope.launch {
        if (_workHistoryRestoring.value) return@launch
        _workHistoryRestoring.value = true
        try {
            when (repo.restoreWorkHistory(id, expectedLatestId)) {
                RestoreWorkResult.Restored -> {
                    reloadHistory().join()
                    advancedAnalysisJob?.cancel()
                    taskAnalysisJob?.cancel()
                    _advancedAnalysis.value = AdvancedAnalysisUiState()
                    _taskAnalysis.value = TaskAnalysisUiState()
                    _timelineItems.value = emptyList()
                    _statsItems.value = emptyList()
                    _events.emit(UiEvent.DataRestored)
                    _events.emit(UiEvent.ShowMessage(text(R.string.work_history_restored)))
                }
                RestoreWorkResult.HistoryChanged, RestoreWorkResult.Missing ->
                    _events.emit(UiEvent.ShowMessage(text(R.string.work_history_changed)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _events.emit(UiEvent.ShowMessage(text(R.string.work_history_restore_failed)))
        } finally {
            _workHistoryRestoring.value = false
        }
    }

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
                UiEvent.ShowMessage(
                    text(R.string.message_short_task_merged, prevSec, thresholdSec)
                )
            )
        }

        reloadHistory()
    }

    fun onStopPressed() = viewModelScope.launch {
        repo.stopActive(nowMs = System.currentTimeMillis())
        reloadHistory()
    }

    fun children(parentId: Long) = repo.observeChildren(parentId)
    suspend fun recordForEdit(eventId: Long) = taskDao.getEventById(eventId)

    suspend fun changeRunningTask(expectedTaskId: Long, expectedStart: Long, newTaskId: Long): Boolean =
        repo.changeRunningTask(expectedTaskId, expectedStart, newTaskId)
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
        if (_historyLoading.value || _historyEndReached.value) return@launch
        _historyLoading.value = true
        try {
            val rows = repo.loadHistoryPage(historyPage, pageSize)
            if (rows.isEmpty()) {
                _historyEndReached.value = true
                return@launch
            }
            _historyItems.value = _historyItems.value + rows.map { it.toUi() }
            historyPage++
            _historyEndReached.value = rows.size < pageSize
        } finally {
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
            _historyItems.value = _historyItems.value + rows.map { it.toUi() }
            if (rows.size < HISTORY_PAGE_SIZE) _historyEndReached.value = true else historyPage += 1
            _historyLoading.value = false
        }
    }

    fun reloadHistory(pageSize: Int = HISTORY_PAGE_SIZE) = viewModelScope.launch {
        _historyLoading.value = true
        historyPage = 0
        _historyEndReached.value = false
        try {
            val first = repo.loadHistoryPage(0, pageSize)
            _historyItems.value = first.map { it.toUi() }
            _historyEndReached.value = first.size < pageSize
            historyPage = if (_historyEndReached.value) 0 else 1
            historyInitialized = true
        } finally {
            _historyLoading.value = false
        }
    }

    fun addParentTask(name: String) = viewModelScope.launch { resolveNames { repo.addTaskResolving(name.trim().take(128), null, it) } }
    fun addChildTask(parentId: Long, name: String) =
        viewModelScope.launch { resolveNames { repo.addTaskResolving(name.trim().take(128), parentId, it) } }

    fun changeParentColor(parentId: Long, color: Color) =
        viewModelScope.launch { repo.changeColor(parentId, color.toArgb()) }

    fun renameTask(taskId: Long, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim().take(128)
        if (trimmed.isNotEmpty()) {
            val mapping = resolveNames { repo.renameTask(taskId, trimmed, it) }
            if (mapping.isNotEmpty()) {
                val prefs = getApplication<Application>().getSharedPreferences("app_settings", 0)
                val plans = com.example.timerecorder.data.DailyPlansCodec.decode(prefs.getString("daily_plans", null))
                val updated = plans.copy(
                    weekdays = plans.weekdays.map { it.copy(targetTaskId = mapping[it.targetTaskId] ?: it.targetTaskId) },
                    holidays = plans.holidays.map { it.copy(targetTaskId = mapping[it.targetTaskId] ?: it.targetTaskId) })
                prefs.edit().putString("daily_plans", com.example.timerecorder.data.DailyPlansCodec.encode(updated)).apply()
                val stats = getApplication<Application>().getSharedPreferences("stats_prefs", 0)
                stats.getString("stats_included_leaf_ids", null)?.let { csv ->
                    stats.edit().putString("stats_included_leaf_ids", csv.split(',').mapNotNull { it.toLongOrNull() }
                        .map { mapping[it] ?: it }.distinct().joinToString(",")).apply()
                }
                _events.emit(UiEvent.TasksMerged)
            }
            reloadHistory()
        }
    }

    suspend fun taskTreeIds(taskId: Long): Set<Long> = repo.getTaskTreeIds(taskId).toSet()
    suspend fun taskPathLabel(taskId: Long): String? = repo.getTaskPathLabel(taskId)
    suspend fun visibleTaskIds(): Set<Long> = repo.getVisibleTaskIds()

    fun deleteTask(
        taskId: Long,
        mode: TaskDeletionMode,
        replacementTaskId: Long? = null
    ) = viewModelScope.launch {
        try {
            repo.deleteTask(taskId, mode, replacementTaskId)
            reloadHistory()
            _events.emit(UiEvent.ShowMessage(text(R.string.message_task_deleted)))
        } catch (e: IllegalArgumentException) {
            _events.emit(
                UiEvent.ShowMessage(e.message ?: text(R.string.error_task_delete_failed))
            )
        } catch (e: IllegalStateException) {
            _events.emit(
                UiEvent.ShowMessage(e.message ?: text(R.string.error_task_delete_failed))
            )
        }
    }
    fun reorderChildren(parentId: Long, idsInOrder: List<Long>) =
        viewModelScope.launch { repo.updateSortOrdersForParent(parentId, idsInOrder) }

    // 親（プロジェクト＝ルート）の並び替え
    fun reorderParents(idsInOrder: List<Long>) =
        viewModelScope.launch { repo.reorderParents(idsInOrder) }

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

    private val _statsItems = MutableStateFlow<List<HistoryItemUi>>(emptyList())
    val statsItems: StateFlow<List<HistoryItemUi>> = _statsItems

    private val _advancedAnalysis = MutableStateFlow(AdvancedAnalysisUiState())
    val advancedAnalysis: StateFlow<AdvancedAnalysisUiState> = _advancedAnalysis
    private var advancedAnalysisJob: Job? = null

    private val _taskAnalysis = MutableStateFlow(TaskAnalysisUiState())
    val taskAnalysis: StateFlow<TaskAnalysisUiState> = _taskAnalysis
    private var taskAnalysisJob: Job? = null

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

    fun loadStatsRange(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            val rows = repo.loadHistoryRange(startMs, endMs)
            _statsItems.value = rows.map { it.toUi() }
        }
    }

    fun runAdvancedAnalysis(request: AnalysisRequest, dailyPlans: DailyPlans) {
        advancedAnalysisJob?.cancel()
        advancedAnalysisJob = viewModelScope.launch {
            _advancedAnalysis.value = _advancedAnalysis.value.copy(
                isLoading = true,
                errorMessage = null
            )
            try {
                val range = AdvancedAnalysisEngine.rangeFor(request)
                val records = repo.loadAnalysisEvents(
                    startMs = range.startMs,
                    endMs = range.endMs,
                    nowMs = request.nowMs
                )
                val result = withContext(Dispatchers.Default) {
                    AdvancedAnalysisEngine.analyze(records, request, dailyPlans, unspecifiedLabel = text(R.string.label_unspecified))
                }
                _advancedAnalysis.value = AdvancedAnalysisUiState(result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _advancedAnalysis.value = AdvancedAnalysisUiState(
                    errorMessage = error.message
                        ?: text(R.string.error_advanced_analysis_failed)
                )
            }
        }
    }

    fun loadTaskAnalysis(request: TaskAnalysisRequest) {
        taskAnalysisJob?.cancel()
        _taskAnalysis.value = TaskAnalysisUiState(request = request, isLoading = true)
        taskAnalysisJob = viewModelScope.launch {
            try {
                val range = TaskAnalysisEngine.rangeFor(request)
                val records = repo.loadAnalysisEvents(
                    startMs = TaskAnalysisEngine.movingAverageLookbackStart(request),
                    endMs = range.endMs,
                    nowMs = minOf(request.nowMs, range.endMs)
                )
                val result = withContext(Dispatchers.Default) {
                    TaskAnalysisEngine.analyze(records, request)
                }
                _taskAnalysis.value = TaskAnalysisUiState(request = request, result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _taskAnalysis.value = TaskAnalysisUiState(
                    request = request,
                    errorMessage = error.message ?: text(R.string.error_task_analysis_failed)
                )
            }
        }
    }

    fun reassignEvents(eventIds: Set<Long>, newTaskId: Long) = viewModelScope.launch {
        try {
            val updated = repo.reassignEvents(eventIds, newTaskId)
            reloadHistory()
            _taskAnalysis.value.request?.let(::loadTaskAnalysis)
            _events.emit(
                UiEvent.ShowMessage(text(R.string.message_records_reassigned, updated))
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _events.emit(
                UiEvent.ShowMessage(
                    error.message ?: text(R.string.error_records_reassign_failed)
                )
            )
        }
    }

    suspend fun buildWeeklyReport(
        request: AnalysisRequest,
        timelineStartMinute: Int,
        timelineEndMinute: Int,
        dailyPlans: DailyPlans
    ): WeeklyReportData {
        val range = AdvancedAnalysisEngine.rangeFor(request)
        val events = withContext(Dispatchers.IO) {
            repo.loadAnalysisEvents(
                startMs = range.startMs,
                endMs = range.endMs,
                nowMs = minOf(System.currentTimeMillis(), range.endMs)
            )
        }
        return withContext(Dispatchers.Default) {
            WeeklyReportData(
                timeline = WeeklyReportEngine.buildTimeline(
                    events = events,
                    range = range,
                    displayStartMinute = timelineStartMinute,
                    displayEndMinute = timelineEndMinute,
                    timeZoneId = request.timeZoneId
                ),
                analysis = AdvancedAnalysisEngine.analyze(
                    events = events,
                    request = request,
                    dailyPlans = dailyPlans,
                    completedThroughMs = minOf(System.currentTimeMillis(), range.endMs),
                    unspecifiedLabel = text(R.string.label_unspecified)
                )
            )
        }
    }

    suspend fun exportTasksJson(): String = repo.exportTasksJson()
    suspend fun importTasksFromJson(json: String, replaceAll: Boolean) {
        resolveNames { repo.importTasksJson(json, replaceAll, it) }
    }

    suspend fun hasOverlap(startMs: Long, endMs: Long): Boolean = repo.hasOverlap(startMs, endMs)

    fun addManualRecord(taskId: Long, startedAt: Long, endedAt: Long) {
        viewModelScope.launch {
            if (endedAt <= startedAt) {
                _events.emit(
                    UiEvent.ShowMessage(text(R.string.record_end_after_start_error))
                )
                return@launch
            }
            try {
                repo.addManualRecord(taskId, startedAt, endedAt)
                reloadHistory()
                _events.emit(UiEvent.ShowMessage(text(R.string.message_record_added)))
            } catch (t: Throwable) {
                _events.emit(
                    UiEvent.ShowMessage(
                        text(R.string.error_record_add_failed, t.message.orEmpty())
                    )
                )
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

        repo.recordChange(WorkHistoryAction.EditRecord) {
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
        }

        reloadHistory()
    }

    fun upsertRecordAbsorbingNeighbors(taskId: Long, startMs: Long, endMs: Long) {
        viewModelScope.launch {
            if (endMs <= startMs) {
                _events.emit(
                    UiEvent.ShowMessage(text(R.string.record_end_after_start_error))
                )
                return@launch
            }

            repo.recordChange(WorkHistoryAction.AddRecord) {
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
            }

            reloadHistory()
            _events.emit(UiEvent.ShowMessage(text(R.string.message_saved)))
        }
    }

    /** はさみ機能：イベントをsplitAtMsで2つに分割 */
    fun splitEventAt(eventId: Long, splitAtMs: Long) = viewModelScope.launch {
        val split = repo.recordChange(WorkHistoryAction.SplitRecord) {
            val ev = taskDao.getEventById(eventId)
            if (ev == null) {
                _events.emit(UiEvent.ShowMessage(text(R.string.error_record_not_found)))
                return@recordChange false
            }

            val s = ev.pressedAtEpochMs
            val e = ev.pressedAtEpochMs + kotlin.math.max(0L, ev.durationMillis)

            if (splitAtMs <= s || splitAtMs >= e) {
                _events.emit(
                    UiEvent.ShowMessage(text(R.string.error_split_time_out_of_range))
                )
                return@recordChange false
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
            true
        }

        if (!split) return@launch
        reloadHistory()
        _events.emit(UiEvent.ShowMessage(text(R.string.message_record_split)))
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            try {
                repo.deleteEventById(eventId)

                // 履歴更新（UI反映）
                reloadHistory()

                // 任意：スナックバー用イベント
                _events.emit(UiEvent.ShowMessage(text(R.string.message_record_deleted)))
            } catch (e: Exception) {
                _events.emit(
                    UiEvent.ShowMessage(
                        text(R.string.error_record_delete_failed, e.message.orEmpty())
                    )
                )
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
                _events.emit(UiEvent.ShowMessage(text(R.string.message_csv_import_complete)))
            } catch (e: Exception) {
                _events.emit(
                    UiEvent.ShowMessage(
                        text(R.string.error_csv_import_failed, e.message.orEmpty())
                    )
                )
            }
        }
    }

    suspend fun exportEventsTsv(startMs: Long, endMs: Long): String =
        repo.exportEventsTsv(startMs, endMs)

    suspend fun getDaysWithData(startMs: Long, endMs: Long): Set<Long> =
        repo.getDaysWithData(startMs, endMs)

    suspend fun importEventsTsv(tsv: String, clearExisting: Boolean, createMissing: Boolean): Int =
        resolveNames { repo.importEventsTsv(tsv, clearExisting, createMissing, it) }
}
