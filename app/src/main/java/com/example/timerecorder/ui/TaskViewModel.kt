package com.example.timerecorder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.db.ActiveWithTask
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.TaskEventEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: TaskRepository

    init {
        val db = AppDatabase.get(app.applicationContext, viewModelScope)
        repo = TaskRepository(db.taskDao())
    }

    val parents: StateFlow<List<ParentWithChildren>> =
        repo.observeTree()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val active: StateFlow<ActiveWithTask?> =
        repo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentEvents: StateFlow<List<TaskEventEntity>> =
        repo.observeRecentEvents()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onTaskPressed(taskId: Long) = viewModelScope.launch {
        repo.startOrSwitch(taskId, nowMs = System.currentTimeMillis())
    }

    fun onStopPressed() = viewModelScope.launch {
        repo.stopActive(nowMs = System.currentTimeMillis())
    }

    fun children(parentId: Long) = repo.observeChildren(parentId)
    suspend fun hasChildren(taskId: Long) = repo.hasChildren(taskId)
}
