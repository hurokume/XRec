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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel,
    initialTaskId: Long? = null,
    initialStartMs: Long? = null,
    initialEndMs: Long? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val parents by viewModel.parents.collectAsStateWithLifecycle()

    var selectedParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSubtaskId by rememberSaveable { mutableStateOf<Long?>(null) }

    // initialTaskId が来たら初期選択に反映（子 or サブタスクを自動選択）
    LaunchedEffect(initialTaskId, parents) {
        val target = initialTaskId ?: return@LaunchedEffect

        // 子タスク一致
        parents.firstOrNull { g -> g.children.any { it.id == target } }?.let { g ->
            selectedParentId = g.parent.id
            selectedTaskId = target
            selectedSubtaskId = null
            return@LaunchedEffect
        }

        // サブタスク一致
        outer@ for (g in parents) {
            for (child in g.children) {
                if (viewModel.hasChildren(child.id)) {
                    val subs = viewModel.children(child.id).first()
                    if (subs.any { it.id == target }) {
                        selectedParentId = g.parent.id
                        selectedTaskId = child.id
                        selectedSubtaskId = target
                        break@outer
                    }
                }
            }
        }
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

    fun pickDateTime(
        initial: Long,
        onPicked: (Long) -> Unit
    ) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = initial }
        android.app.DatePickerDialog(
            ctx,
            { _, y, m, d ->
                cal.set(java.util.Calendar.YEAR, y)
                cal.set(java.util.Calendar.MONTH, m)
                cal.set(java.util.Calendar.DAY_OF_MONTH, d)
                android.app.TimePickerDialog(
                    ctx,
                    { _, hh, mm ->
                        cal.set(java.util.Calendar.HOUR_OF_DAY, hh)
                        cal.set(java.util.Calendar.MINUTE, mm)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        onPicked(cal.timeInMillis)
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    // 上書き確認ダイアログの状態
    var showOverwriteDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingStart by rememberSaveable { mutableLongStateOf(0L) }
    var pendingEnd by rememberSaveable { mutableLongStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タスク記録を追加") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
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
                "親タスク / タスク / サブタスク（任意）と、開始・終了の日時を指定して保存します。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 親タスク
            DropdownField(
                label = "親タスク",
                current = parents.firstOrNull { it.parent.id == selectedParentId }?.parent?.name
                    ?: "選択してください",
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
                label = "タスク",
                current = tasksInParent.firstOrNull { it.id == selectedTaskId }?.name
                    ?: if (selectedParentId == null) "（親タスク未選択）" else "選択してください",
                enabled = selectedParentId != null && tasksInParent.isNotEmpty(),
                items = tasksInParent.map { it.id to it.name },
                onSelect = { id ->
                    selectedTaskId = id
                    selectedSubtaskId = null
                }
            )

            // サブタスク（任意）
            DropdownField(
                label = "サブタスク（任意）",
                current = when {
                    selectedTaskId == null -> "（タスク未選択）"
                    subtasks.isEmpty() -> "（サブタスクなし）"
                    selectedSubtaskId == null -> "未選択"
                    else -> subtasks.firstOrNull { it.id == selectedSubtaskId }?.name ?: "未選択"
                },
                enabled = selectedTaskId != null && subtasks.isNotEmpty(),
                items = subtasks.map { it.id to it.name },
                onSelect = { id -> selectedSubtaskId = id }
            )

            // 開始日時
            TimeField(
                label = "開始",
                valueText = dfFull.format(java.util.Date(startMs)),
                onClick = {
                    pickDateTime(startMs) { picked ->
                        startMs = picked
                        if (endMs <= startMs) endMs = startMs + 5 * 60 * 1000L
                    }
                }
            )

            // 終了日時
            TimeField(
                label = "終了",
                valueText = dfFull.format(java.util.Date(endMs)),
                onClick = {
                    pickDateTime(endMs) { picked ->
                        endMs = picked
                        if (endMs <= startMs) startMs = endMs - 5 * 60 * 1000L
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("キャンセル") }
                FilledTonalButton(onClick = {
                    val baseTaskId = selectedTaskId
                    val finalTaskId = selectedSubtaskId ?: baseTaskId
                    when {
                        selectedParentId == null -> Toast.makeText(
                            ctx,
                            "親タスクを選択してください",
                            Toast.LENGTH_SHORT
                        ).show()

                        baseTaskId == null -> Toast.makeText(
                            ctx,
                            "タスクを選択してください",
                            Toast.LENGTH_SHORT
                        ).show()

                        endMs <= startMs -> Toast.makeText(
                            ctx,
                            "終了は開始より後にしてください",
                            Toast.LENGTH_SHORT
                        ).show()

                        else -> {
                            scope.launch {
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
                }) { Text("保存") }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    // 上書き警告ダイアログ
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("重複する記録を上書きします") },
            text = {
                Text(
                    "この期間には既存の記録が存在します。\n" +
                            "追加する記録を優先し、重複部分は削除または切り詰められます。続行しますか？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTaskId?.let { t ->
                        // 重複時は近傍吸収版で一度だけ登録・調整する
                        viewModel.upsertRecordAbsorbingNeighbors(t, pendingStart, pendingEnd)
                    }
                    showOverwriteDialog = false
                    onBack()
                }) { Text("上書きして追加") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = false }) { Text("キャンセル") }
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
private fun TimeField(
    label: String,
    valueText: String,
    onClick: () -> Unit
) {
    OutlinedTextField(
        value = valueText,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}
