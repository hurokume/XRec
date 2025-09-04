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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

}
