package com.example.timerecorder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timerecorder.data.DailyPlanEntry
import com.example.timerecorder.data.DailyPlans
import com.example.timerecorder.data.DailyPlanType
import java.util.Locale

data class DailyPlanTaskOption(
    val id: Long,
    val label: String,
    val colorInt: Int? = null
)

@Composable
fun SettingsScreen(
    showStopButton: Boolean,
    onShowStopButtonChange: (Boolean) -> Unit,
    dailyPlans: DailyPlans,
    taskOptions: List<DailyPlanTaskOption>,
    onDailyPlansChange: (DailyPlans) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("表示設定", style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("タスク停止ボタン", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "タスク実行中に上部バーへ停止ボタンを表示します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showStopButton,
                    onCheckedChange = onShowStopButtonChange
                )
            }
        }

        DailyPlansEditor(
            dailyPlans = dailyPlans,
            taskOptions = taskOptions,
            onDailyPlansChange = onDailyPlansChange
        )
    }
}

@Composable
private fun DailyPlansEditor(
    dailyPlans: DailyPlans,
    taskOptions: List<DailyPlanTaskOption>,
    onDailyPlansChange: (DailyPlans) -> Unit
) {
    var selectedType by rememberSaveable { mutableStateOf(DailyPlanType.Weekday) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingEntry by remember { mutableStateOf<DailyPlanEntry?>(null) }
    val entries = dailyPlans.entriesFor(selectedType)
    val optionById = taskOptions.associateBy { it.id }

    fun replaceEntries(updated: List<DailyPlanEntry>) {
        val sorted = updated.sortedWith(
            compareBy<DailyPlanEntry> { it.startMinute }.thenBy { it.endMinute }
        )
        onDailyPlansChange(
            when (selectedType) {
                DailyPlanType.Weekday -> dailyPlans.copy(weekdays = sorted)
                DailyPlanType.Holiday -> dailyPlans.copy(holidays = sorted)
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Daily plans", style = MaterialTheme.typography.titleMedium)
        Text(
            "平日と休日（土日）の基本パターンを時間帯ごとに定義します。予定タスクまたはその配下タスクの記録時間から、日ごとの達成度を算出します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedType == DailyPlanType.Weekday,
                onClick = { selectedType = DailyPlanType.Weekday },
                label = { Text("平日") }
            )
            FilterChip(
                selected = selectedType == DailyPlanType.Holiday,
                onClick = { selectedType = DailyPlanType.Holiday },
                label = { Text("休日") }
            )
        }

        DailyPlanTimeline(
            entries = entries,
            optionById = optionById,
            onEntryClick = { index ->
                editingIndex = index
                editingEntry = entries[index]
            }
        )

        if (entries.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "この日の行動パターンは未設定です。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            entries.forEachIndexed { index, entry ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${formatPlanTime(entry.startMinute)} - ${formatPlanTime(entry.endMinute)}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                optionById[entry.targetTaskId]?.label ?: "削除されたタスク",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                editingIndex = index
                                editingEntry = entry
                            }
                        ) {
                            Text("編集")
                        }
                        IconButton(onClick = { replaceEntries(entries.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "予定を削除")
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = taskOptions.isNotEmpty(),
            onClick = {
                editingIndex = null
                editingEntry = DailyPlanEntry(
                    targetTaskId = taskOptions.first().id,
                    startMinute = 9 * 60,
                    endMinute = 10 * 60
                )
            }
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" 行動を追加")
        }
        if (taskOptions.isEmpty()) {
            Text(
                "Daily planを作成するには、先にタスクを追加してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    editingEntry?.let { initial ->
        PlanEntryDialog(
            initialEntry = initial,
            taskOptions = taskOptions,
            existingEntries = entries,
            editingIndex = editingIndex,
            onDismiss = { editingEntry = null },
            onSave = { saved ->
                val index = editingIndex
                replaceEntries(
                    if (index == null) entries + saved else entries.mapIndexed { i, entry ->
                        if (i == index) saved else entry
                    }
                )
                editingEntry = null
            }
        )
    }
}

@Composable
private fun DailyPlanTimeline(
    entries: List<DailyPlanEntry>,
    optionById: Map<Long, DailyPlanTaskOption>,
    onEntryClick: (Int) -> Unit
) {
    val timelineHeight = 28.dp * 24
    val axisWidth = 46.dp
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val strongGridColor = MaterialTheme.colorScheme.outline
    val fallbackColor = MaterialTheme.colorScheme.primary

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Text("Timeline", style = MaterialTheme.typography.titleSmall)
                Text(
                    "予定ブロックをタップすると編集できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineHeight)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val axisX = axisWidth.toPx()
                    for (hour in 0..24) {
                        val y = size.height * hour / 24f
                        drawLine(
                            color = if (hour % 6 == 0) strongGridColor else gridColor,
                            start = Offset(axisX, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (hour % 6 == 0) 1.5f else 1f
                        )
                    }
                    drawLine(
                        color = strongGridColor,
                        start = Offset(axisX, 0f),
                        end = Offset(axisX, size.height),
                        strokeWidth = 1.5f
                    )
                }

                for (hour in 0..24 step 2) {
                    val y = (timelineHeight.value * hour / 24f - 7f)
                        .coerceIn(0f, timelineHeight.value - 16f)
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:00", hour),
                        modifier = Modifier
                            .width(axisWidth)
                            .offset(y = y.dp)
                            .padding(end = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = axisWidth, end = 8.dp)
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            text = "予定を追加すると、ここにTimelineが表示されます。",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp, start = 12.dp, end = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entries.forEachIndexed { index, entry ->
                        val option = optionById[entry.targetTaskId]
                        val blockColor = option?.colorInt?.let(::Color) ?: fallbackColor
                        val top = timelineHeight.value * entry.startMinute / (24f * 60f)
                        val height = timelineHeight.value *
                                (entry.endMinute - entry.startMinute) / (24f * 60f)
                        val shape = RoundedCornerShape(4.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = top.dp)
                                .height(height.dp)
                                .background(blockColor.copy(alpha = 0.24f), shape)
                                .border(1.dp, blockColor, shape)
                                .clickable { onEntryClick(index) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (height >= 22f) {
                                Text(
                                    text = "${formatPlanTime(entry.startMinute)}  " +
                                            (option?.label ?: "削除されたタスク"),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanEntryDialog(
    initialEntry: DailyPlanEntry,
    taskOptions: List<DailyPlanTaskOption>,
    existingEntries: List<DailyPlanEntry>,
    editingIndex: Int?,
    onDismiss: () -> Unit,
    onSave: (DailyPlanEntry) -> Unit
) {
    var taskId by remember(initialEntry) { mutableLongStateOf(initialEntry.targetTaskId) }
    var startMinute by remember(initialEntry) { mutableIntStateOf(initialEntry.startMinute) }
    var endMinute by remember(initialEntry) { mutableIntStateOf(initialEntry.endMinute) }
    val overlaps = existingEntries.withIndex().any { (index, entry) ->
        index != editingIndex && startMinute < entry.endMinute && endMinute > entry.startMinute
    }
    val isValid = endMinute > startMinute && !overlaps && taskOptions.any { it.id == taskId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingIndex == null) "行動を追加" else "行動を編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanDropdown(
                    label = "タスク",
                    valueLabel = taskOptions.firstOrNull { it.id == taskId }?.label ?: "選択してください",
                    choices = taskOptions.map { it.id to it.label },
                    onSelect = { taskId = it }
                )
                PlanDropdown(
                    label = "開始",
                    valueLabel = formatPlanTime(startMinute),
                    choices = (0 until 48).map { slot ->
                        val minute = slot * 30
                        minute.toLong() to formatPlanTime(minute)
                    },
                    onSelect = { startMinute = it.toInt() }
                )
                PlanDropdown(
                    label = "終了",
                    valueLabel = formatPlanTime(endMinute),
                    choices = (1..48).map { slot ->
                        val minute = slot * 30
                        minute.toLong() to formatPlanTime(minute)
                    },
                    onSelect = { endMinute = it.toInt() }
                )
                if (endMinute <= startMinute) {
                    Text("終了は開始より後にしてください。", color = MaterialTheme.colorScheme.error)
                } else if (overlaps) {
                    Text("同じ日の別の予定と時間帯が重複しています。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onSave(DailyPlanEntry(taskId, startMinute, endMinute)) }
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun PlanDropdown(
    label: String,
    valueLabel: String,
    choices: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Text(valueLabel)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                choices.forEach { (value, choiceLabel) ->
                    DropdownMenuItem(
                        text = { Text(choiceLabel) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatPlanTime(minutes: Int): String = if (minutes == 24 * 60) {
    "24:00"
} else {
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)
}
