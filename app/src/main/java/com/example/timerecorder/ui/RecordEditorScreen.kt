package com.example.timerecorder.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timerecorder.NameSort
import com.example.timerecorder.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel,
    initialEventId: Long? = null,
    initialTaskId: Long? = null,
    initialStartMs: Long? = null,
    initialEndMs: Long? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val isEditing = initialEventId != null
    val isRunning = initialEventId == -1L

    var selectedParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSubtaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editedTaskId by rememberSaveable { mutableStateOf(initialTaskId) }
    var editedTaskPath by rememberSaveable { mutableStateOf("") }
    var showTaskSelection by rememberSaveable { mutableStateOf(false) }

    // 編集対象の現在のタスク名は初回だけ解決し、選択画面から戻った値を上書きしない。
    LaunchedEffect(editedTaskId) {
        val target = editedTaskId ?: return@LaunchedEffect
        editedTaskPath = viewModel.taskPathLabel(target).orEmpty()
    }

    // 選択された親配下の子タスク
    val tasksInParent = remember(selectedParentId, parents) {
        parents.firstOrNull { it.parent.id == selectedParentId }?.children.orEmpty()
    }

    // 選択された子タスク配下のサブタスク
    val subtasks by remember(selectedTaskId) {
        if (selectedTaskId != null) {
            viewModel.children(selectedTaskId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // 開始・終了の初期値：編集遷移時（initialStart/End）があればそれを採用
    var startMs by rememberSaveable {
        mutableLongStateOf(initialStartMs ?: (System.currentTimeMillis() - 60 * 60 * 1000L))
    }
    var endMs by rememberSaveable {
        mutableLongStateOf(initialEndMs ?: System.currentTimeMillis())
    }

    val dfFull =
        remember { java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()) }

    // 上書き確認ダイアログの状態
    var showOverwriteDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingStart by rememberSaveable { mutableLongStateOf(0L) }
    var pendingEnd by rememberSaveable { mutableLongStateOf(0L) }

    if (showTaskSelection) {
        ReplacementTaskSelectionScreen(
            viewModel = viewModel,
            parents = parents,
            excludedTaskIds = setOf(Long.MIN_VALUE),
            nameSort = NameSort.None,
            onBack = { showTaskSelection = false },
            onPick = { taskId, path ->
                editedTaskId = taskId
                editedTaskPath = path
                showTaskSelection = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) R.string.record_edit_title
                            else R.string.record_add_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(
                    if (isEditing) R.string.record_edit_description
                    else R.string.record_add_description
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isEditing) {
                PickerField(
                    label = stringResource(R.string.record_task),
                    valueText = editedTaskPath.ifBlank {
                        stringResource(R.string.record_select_prompt)
                    },
                    onClick = { showTaskSelection = true }
                )
            } else {
                // 親タスク
                DropdownField(
                    label = stringResource(R.string.record_parent_task),
                    current = parents.firstOrNull { it.parent.id == selectedParentId }?.parent?.name
                        ?: stringResource(R.string.record_select_prompt),
                    enabled = parents.isNotEmpty(),
                    items = parents.map { it.parent.id to it.parent.name },
                    onSelect = { id ->
                        selectedParentId = id
                        selectedTaskId = null
                        selectedSubtaskId = null
                    }
                )

                // タスク（子）
                DropdownField(
                    label = stringResource(R.string.record_task),
                    current = tasksInParent.firstOrNull { it.id == selectedTaskId }?.name
                        ?: if (selectedParentId == null) {
                            stringResource(R.string.record_parent_not_selected)
                        } else {
                            stringResource(R.string.record_select_prompt)
                        },
                    enabled = selectedParentId != null && tasksInParent.isNotEmpty(),
                    items = tasksInParent.map { it.id to it.name },
                    onSelect = { id ->
                        selectedTaskId = id
                        selectedSubtaskId = null
                    }
                )

                // サブタスク（任意）
                DropdownField(
                    label = stringResource(R.string.record_optional_subtask),
                    current = when {
                        selectedTaskId == null -> stringResource(R.string.record_task_not_selected)
                        subtasks.isEmpty() -> stringResource(R.string.record_no_subtasks)
                        selectedSubtaskId == null -> stringResource(R.string.record_not_selected)
                        else -> subtasks.firstOrNull { it.id == selectedSubtaskId }?.name
                            ?: stringResource(R.string.record_not_selected)
                    },
                    enabled = selectedTaskId != null && subtasks.isNotEmpty(),
                    items = if (subtasks.isEmpty()) emptyList() else listOf(0L to stringResource(R.string.subtask_none)) + subtasks.map { it.id to it.name },
                    onSelect = { id -> selectedSubtaskId = id.takeUnless { it == 0L } }
                )
            }

            if (!isRunning) {
            TimeField(
                label = stringResource(R.string.label_start),
                valueText = dfFull.format(java.util.Date(startMs)),
                onClick = {
                    showRecordDateTimePicker(ctx, startMs) { picked ->
                        startMs = picked
                        if (endMs <= startMs) endMs = startMs + 5 * 60 * 1000L
                    }
                }
            )

            // 終了日時
            TimeField(
                label = stringResource(R.string.label_end),
                valueText = dfFull.format(java.util.Date(endMs)),
                onClick = {
                    showRecordDateTimePicker(ctx, endMs) { picked ->
                        endMs = picked
                        if (endMs <= startMs) startMs = endMs - 5 * 60 * 1000L
                    }
                }
            )

            } else {
                Text(stringResource(R.string.record_running_description))
                Text(dfFull.format(java.util.Date(startMs)))
            }
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.action_cancel))
                }
                FilledTonalButton(onClick = {
                    val baseTaskId = if (isEditing) editedTaskId else selectedTaskId
                    val finalTaskId = if (isEditing) {
                        editedTaskId
                    } else {
                        selectedSubtaskId ?: baseTaskId
                    }
                    when {
                        !isEditing && selectedParentId == null -> Toast.makeText(
                            ctx,
                            ctx.getString(R.string.record_select_parent_error),
                            Toast.LENGTH_SHORT
                        ).show()

                        baseTaskId == null -> Toast.makeText(
                            ctx,
                            ctx.getString(R.string.record_select_task_error),
                            Toast.LENGTH_SHORT
                        ).show()

                        endMs <= startMs -> Toast.makeText(
                            ctx,
                            ctx.getString(R.string.record_end_after_start_error),
                            Toast.LENGTH_SHORT
                        ).show()

                        else -> {
                            if (isRunning) {
                                scope.launch {
                                    if (viewModel.changeRunningTask(requireNotNull(initialTaskId),
                                            requireNotNull(initialStartMs), requireNotNull(finalTaskId))) onBack()
                                    else Toast.makeText(ctx, ctx.getString(R.string.record_running_changed), Toast.LENGTH_LONG).show()
                                }
                            } else if (isEditing) {
                                viewModel.editEventAbsorbNeighbors(
                                    eventId = requireNotNull(initialEventId),
                                    newTaskId = requireNotNull(finalTaskId),
                                    newStartMs = startMs,
                                    newEndMs = endMs
                                )
                                onBack()
                            } else scope.launch {
                                // 既存と重なる場合は上書き確認
                                val overlap = viewModel.hasOverlap(startMs, endMs)
                                if (overlap) {
                                    pendingTaskId = finalTaskId!!
                                    pendingStart = startMs
                                    pendingEnd = endMs
                                    showOverwriteDialog = true
                                } else {
                                    viewModel.addManualRecord(finalTaskId!!, startMs, endMs)
                                    onBack()
                                }
                            }
                        }
                    }
                }) { Text(stringResource(R.string.action_save)) }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    // 上書き警告ダイアログ
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text(stringResource(R.string.record_overwrite_title)) },
            text = {
                Text(stringResource(R.string.record_overwrite_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTaskId?.let { t ->
                        // 重複時は近傍吸収版で一度だけ登録・調整する
                        viewModel.upsertRecordAbsorbingNeighbors(t, pendingStart, pendingEnd)
                    }
                    showOverwriteDialog = false
                    onBack()
                }) { Text(stringResource(R.string.record_overwrite_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    current: String,
    enabled: Boolean = true,
    items: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // アンカー用コンテナ
    Box(modifier = Modifier.fillMaxWidth()) {
        // 表示用の読み取り専用 TextField
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
        )

        // フィールド全体をクリックでメニュー展開
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { expanded = true }
        )

        // 通常の DropdownMenu を使う
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    valueText: String,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    valueText: String,
    onClick: () -> Unit
) {
    PickerField(label = label, valueText = valueText, onClick = onClick)
}
