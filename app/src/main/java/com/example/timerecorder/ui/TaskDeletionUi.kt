package com.example.timerecorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timerecorder.NameSort
import com.example.timerecorder.R
import com.example.timerecorder.data.TaskDeletionMode
import com.example.timerecorder.data.db.ParentWithChildren
import kotlinx.coroutines.launch

internal data class TaskDeletionUiState(
    val taskId: Long,
    val taskName: String,
    val isRootTask: Boolean,
    val mode: TaskDeletionMode = if (isRootTask) {
        TaskDeletionMode.KeepHistory
    } else {
        TaskDeletionMode.PromoteToParent
    },
    val replacementTaskId: Long? = null,
    val replacementTaskLabel: String? = null,
    val excludedTaskIds: Set<Long> = emptySet(),
    val isSelectingReplacement: Boolean = false
)

@Composable
internal fun TaskDeletionDialog(
    taskName: String,
    isRootTask: Boolean,
    mode: TaskDeletionMode,
    replacementTaskId: Long?,
    replacementTaskLabel: String?,
    onModeChange: (TaskDeletionMode) -> Unit,
    onSelectReplacement: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_task_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.delete_task_message, taskName))
                Spacer(Modifier.height(8.dp))

                if (!isRootTask) {
                    DeletionModeRow(
                        selected = mode == TaskDeletionMode.PromoteToParent,
                        title = stringResource(R.string.delete_task_promote_title),
                        description = stringResource(R.string.delete_task_promote_description),
                        onClick = { onModeChange(TaskDeletionMode.PromoteToParent) }
                    )
                }
                DeletionModeRow(
                    selected = mode == TaskDeletionMode.Replace,
                    title = stringResource(R.string.delete_task_replace_title),
                    description = stringResource(R.string.delete_task_replace_description),
                    onClick = { onModeChange(TaskDeletionMode.Replace) }
                )
                if (mode == TaskDeletionMode.Replace) {
                    OutlinedButton(
                        onClick = onSelectReplacement,
                        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                    ) {
                        Text(
                            replacementTaskLabel
                                ?: stringResource(R.string.delete_task_select_replacement)
                        )
                    }
                }
                DeletionModeRow(
                    selected = mode == TaskDeletionMode.KeepHistory,
                    title = stringResource(R.string.delete_task_keep_history_title),
                    description = stringResource(R.string.delete_task_keep_history_description),
                    onClick = { onModeChange(TaskDeletionMode.KeepHistory) }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = mode != TaskDeletionMode.Replace || replacementTaskId != null,
                onClick = onConfirm
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun DeletionModeRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private sealed interface ReplacementSelectionState {
    data object TaskList : ReplacementSelectionState
    data class SubtaskList(
        val projectName: String,
        val taskName: String,
        val taskId: Long,
        val color: Color
    ) : ReplacementSelectionState
}

/** main UI の Select a task と同じカード一覧・サブタスク遷移で置換先を選ぶ。 */
@Composable
internal fun ReplacementTaskSelectionScreen(
    viewModel: TaskViewModel,
    parents: List<ParentWithChildren>,
    excludedTaskIds: Set<Long>,
    nameSort: NameSort,
    onBack: () -> Unit,
    onPick: (Long, String) -> Unit
) {
    var selectionState by remember {
        mutableStateOf<ReplacementSelectionState>(ReplacementSelectionState.TaskList)
    }

    BackHandler {
        if (selectionState is ReplacementSelectionState.SubtaskList) {
            selectionState = ReplacementSelectionState.TaskList
        } else {
            onBack()
        }
    }

    val availableParents = remember(parents, excludedTaskIds, nameSort) {
        val filtered = parents
            .filterNot { it.parent.id in excludedTaskIds }
            .map { group ->
                group.copy(children = group.children.filterNot { it.id in excludedTaskIds })
            }
            .filter { it.children.isNotEmpty() }
        when (nameSort) {
            NameSort.None -> filtered
            NameSort.Asc -> filtered.sortedBy { it.parent.name.lowercase() }
            NameSort.Desc -> filtered.sortedByDescending { it.parent.name.lowercase() }
        }
    }

    when (val state = selectionState) {
        ReplacementSelectionState.TaskList -> ReplacementTaskList(
            viewModel = viewModel,
            parents = availableParents,
            excludedTaskIds = excludedTaskIds,
            nameSort = nameSort,
            onBack = onBack,
            onOpenSubtasks = { projectName, taskName, taskId, color ->
                selectionState = ReplacementSelectionState.SubtaskList(
                    projectName = projectName,
                    taskName = taskName,
                    taskId = taskId,
                    color = color
                )
            },
            onPick = onPick
        )

        is ReplacementSelectionState.SubtaskList -> ReplacementSubtaskList(
            viewModel = viewModel,
            state = state,
            excludedTaskIds = excludedTaskIds,
            nameSort = nameSort,
            onBack = { selectionState = ReplacementSelectionState.TaskList },
            onPick = onPick
        )
    }
}

@Composable
private fun ReplacementTaskList(
    viewModel: TaskViewModel,
    parents: List<ParentWithChildren>,
    excludedTaskIds: Set<Long>,
    nameSort: NameSort,
    onBack: () -> Unit,
    onOpenSubtasks: (String, String, Long, Color) -> Unit,
    onPick: (Long, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SelectionHeader(title = stringResource(R.string.label_select_task), onBack = onBack)

        when {
            excludedTaskIds.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            parents.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.replacement_no_tasks))
                }
            }

            else -> LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                items(parents, key = { it.parent.id }) { group ->
                    val projectColor = group.parent.colorInt?.let { Color(it) }
                        ?: MaterialTheme.colorScheme.primary
                    val children = when (nameSort) {
                        NameSort.None -> group.children
                        NameSort.Asc -> group.children.sortedBy { it.name.lowercase() }
                        NameSort.Desc -> group.children.sortedByDescending { it.name.lowercase() }
                    }

                    ProjectCard(group.parent.name) {
                        children.forEach { child ->
                            SelectionCard(
                                label = child.name,
                                color = projectColor,
                                cornerRadius = 2,
                                onClick = {
                                    scope.launch {
                                        if (viewModel.hasChildren(child.id)) {
                                            onOpenSubtasks(
                                                group.parent.name,
                                                child.name,
                                                child.id,
                                                projectColor
                                            )
                                        } else {
                                            onPick(
                                                child.id,
                                                "${group.parent.name} / ${child.name}"
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ReplacementSubtaskList(
    viewModel: TaskViewModel,
    state: ReplacementSelectionState.SubtaskList,
    excludedTaskIds: Set<Long>,
    nameSort: NameSort,
    onBack: () -> Unit,
    onPick: (Long, String) -> Unit
) {
    val subtasks by viewModel.children(state.taskId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val availableSubtasks = remember(subtasks, excludedTaskIds, nameSort) {
        val filtered = subtasks.filterNot { it.id in excludedTaskIds }
        when (nameSort) {
            NameSort.None -> filtered
            NameSort.Asc -> filtered.sortedBy { it.name.lowercase() }
            NameSort.Desc -> filtered.sortedByDescending { it.name.lowercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SelectionHeader(
            title = stringResource(R.string.replacement_subtask_title, state.taskName),
            onBack = onBack
        )

        if (availableSubtasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.replacement_no_subtasks))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(availableSubtasks, key = { it.id }) { subtask ->
                    SelectionCard(
                        label = subtask.name,
                        color = state.color,
                        cornerRadius = 8,
                        onClick = {
                            onPick(
                                subtask.id,
                                "${state.projectName} / ${state.taskName} / ${subtask.name}"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back_with_marker))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProjectCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun SelectionCard(
    label: String,
    color: Color,
    cornerRadius: Int,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}
