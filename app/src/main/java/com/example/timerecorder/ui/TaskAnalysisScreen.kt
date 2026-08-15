package com.example.timerecorder.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timerecorder.R
import com.example.timerecorder.NameSort
import com.example.timerecorder.data.TaskAnalysisDayGroup
import com.example.timerecorder.data.TaskAnalysisEngine
import com.example.timerecorder.data.TaskAnalysisPeriod
import com.example.timerecorder.data.TaskAnalysisRequest
import com.example.timerecorder.data.TaskAnalysisResult
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.ParentWithChildren
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private enum class TaskAnalysisChartType {
    Total,
    CumulativeStacked,
    HeatMap
}

private const val BATCH_ANALYSIS_TASK_ID = Long.MIN_VALUE

private data class AnalysisTaskChoice(
    val id: Long,
    val name: String,
    val pathLabel: String,
    val color: Color
)

/** 右端ページ：分析対象タスクの選択と、その期間別集計。 */
@Composable
fun TaskAnalysisScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val analysisState by viewModel.taskAnalysis.collectAsStateWithLifecycle()
    val drillPath = remember { mutableStateListOf<AnalysisTaskChoice>() }
    val batchColor = MaterialTheme.colorScheme.primary

    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var analysisTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var checkedTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var visibleTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedTaskName by rememberSaveable { mutableStateOf("") }
    var selectedPathLabel by rememberSaveable { mutableStateOf("") }
    var selectedColorValue by rememberSaveable { mutableLongStateOf(0L) }
    var period by rememberSaveable { mutableStateOf(TaskAnalysisPeriod.Month) }
    var anchorMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var customStartMs by rememberSaveable { mutableLongStateOf(defaultCustomStartMs()) }
    var customEndMs by rememberSaveable { mutableLongStateOf(defaultCustomEndMs()) }
    var chartType by rememberSaveable { mutableStateOf(TaskAnalysisChartType.Total) }

    LaunchedEffect(parents) {
        visibleTaskIds = viewModel.visibleTaskIds()
        checkedTaskIds = checkedTaskIds.intersect(visibleTaskIds)
    }

    fun toggleChecked(taskId: Long) {
        checkedTaskIds = if (taskId in checkedTaskIds) {
            checkedTaskIds - taskId
        } else {
            checkedTaskIds + taskId
        }
    }

    fun openAnalysis(choice: AnalysisTaskChoice) {
        analysisTaskIds = setOf(choice.id)
        selectedTaskId = choice.id
        selectedTaskName = choice.name
        selectedPathLabel = choice.pathLabel
        selectedColorValue = choice.color.value.toLong()
        anchorMs = System.currentTimeMillis()
    }

    fun openBatchAnalysis(taskIds: Set<Long>, label: String) {
        if (taskIds.isEmpty()) return
        analysisTaskIds = taskIds
        selectedTaskId = taskIds.singleOrNull() ?: BATCH_ANALYSIS_TASK_ID
        selectedTaskName = label
        selectedPathLabel = label
        selectedColorValue = batchColor.value.toLong()
        anchorMs = System.currentTimeMillis()
    }

    val taskId = selectedTaskId
    if (taskId != null) {
        val request = remember(
            taskId,
            analysisTaskIds,
            selectedTaskName,
            selectedPathLabel,
            period,
            anchorMs,
            customStartMs,
            customEndMs
        ) {
            TaskAnalysisRequest(
                taskId = taskId,
                taskName = selectedPathLabel.ifBlank { selectedTaskName },
                period = period,
                anchorMs = anchorMs,
                taskIds = analysisTaskIds.ifEmpty { setOf(taskId) },
                customStartMs = customStartMs,
                customEndMs = customEndMs
            )
        }
        LaunchedEffect(request) {
            viewModel.loadTaskAnalysis(request)
        }
        BackHandler { selectedTaskId = null }
        TaskAnalysisDetail(
            modifier = modifier,
            viewModel = viewModel,
            parents = parents,
            request = request,
            taskColor = Color(selectedColorValue.toULong()),
            state = analysisState,
            chartType = chartType,
            onChartTypeChange = { chartType = it },
            onPeriodChange = { period = it },
            onPrevious = { anchorMs = TaskAnalysisEngine.shiftAnchor(request, -1) },
            onNext = { anchorMs = TaskAnalysisEngine.shiftAnchor(request, 1) },
            onToday = { anchorMs = System.currentTimeMillis() },
            onCustomStartChange = { pickedStart ->
                customStartMs = pickedStart
                if (customEndMs <= pickedStart) {
                    customEndMs = nextCalendarDay(pickedStart)
                }
            },
            onCustomEndChange = { pickedEndInclusive ->
                val endExclusive = nextCalendarDay(pickedEndInclusive)
                customEndMs = if (endExclusive > customStartMs) {
                    endExclusive
                } else {
                    nextCalendarDay(customStartMs)
                }
            },
            onBack = { selectedTaskId = null }
        )
        return
    }

    val allRootTaskIds = parents.map { it.parent.id }.toSet()
    val allChecked = visibleTaskIds.isNotEmpty() && checkedTaskIds.containsAll(visibleTaskIds)
    if (drillPath.isNotEmpty()) {
        BackHandler { drillPath.removeAt(drillPath.lastIndex) }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AnalysisSelectionBottomBar(
                checkedCount = checkedTaskIds.size,
                allChecked = allChecked,
                enabled = visibleTaskIds.isNotEmpty(),
                onToggleAll = {
                    checkedTaskIds = if (allChecked) emptySet() else visibleTaskIds
                }
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        if (drillPath.isNotEmpty()) {
            AnalysisSubtaskPicker(
                modifier = contentModifier,
                viewModel = viewModel,
                current = drillPath.last(),
                checkedTaskIds = checkedTaskIds,
                canAnalyzeAll = allRootTaskIds.isNotEmpty(),
                onToggleChecked = ::toggleChecked,
                onAnalyzeAll = {
                    openBatchAnalysis(allRootTaskIds, "全項目")
                },
                onAnalyzeChecked = {
                    openBatchAnalysis(checkedTaskIds, "選択した${checkedTaskIds.size}項目")
                },
                onBack = { drillPath.removeAt(drillPath.lastIndex) },
                onOpenChild = { drillPath += it },
                onAnalyze = ::openAnalysis
            )
        } else {
            AnalysisTaskPicker(
                modifier = contentModifier,
                parents = parents,
                viewModel = viewModel,
                checkedTaskIds = checkedTaskIds,
                canAnalyzeAll = allRootTaskIds.isNotEmpty(),
                onToggleChecked = ::toggleChecked,
                onAnalyzeAll = {
                    openBatchAnalysis(allRootTaskIds, "全項目")
                },
                onAnalyzeChecked = {
                    openBatchAnalysis(checkedTaskIds, "選択した${checkedTaskIds.size}項目")
                },
                onOpenChild = { drillPath += it },
                onAnalyze = ::openAnalysis
            )
        }
    }
}

@Composable
private fun AnalysisTaskPicker(
    parents: List<com.example.timerecorder.data.db.ParentWithChildren>,
    viewModel: TaskViewModel,
    checkedTaskIds: Set<Long>,
    canAnalyzeAll: Boolean,
    onToggleChecked: (Long) -> Unit,
    onAnalyzeAll: () -> Unit,
    onAnalyzeChecked: () -> Unit,
    onOpenChild: (AnalysisTaskChoice) -> Unit,
    onAnalyze: (AnalysisTaskChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_select_task),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            AnalysisBatchActions(
                canAnalyzeAll = canAnalyzeAll,
                checkedCount = checkedTaskIds.size,
                onAnalyzeAll = onAnalyzeAll,
                onAnalyzeChecked = onAnalyzeChecked
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(parents, key = { it.parent.id }) { group ->
                val rootColor = group.parent.colorInt?.let(::Color)
                    ?: MaterialTheme.colorScheme.primary
                val rootChoice = AnalysisTaskChoice(
                    id = group.parent.id,
                    name = group.parent.name,
                    pathLabel = group.parent.name,
                    color = rootColor
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        AnalysisTaskRow(
                            name = group.parent.name,
                            color = rootColor,
                            checked = group.parent.id in checkedTaskIds,
                            onCheckedChange = { onToggleChecked(group.parent.id) },
                            titleStyle = true,
                            hasChildren = group.children.isNotEmpty(),
                            onClick = null,
                            onAnalyze = { onAnalyze(rootChoice) }
                        )
                        Spacer(Modifier.height(8.dp))
                        group.children.forEach { child ->
                            val hasChildren by produceState(false, child.id) {
                                value = viewModel.hasChildren(child.id)
                            }
                            val choice = AnalysisTaskChoice(
                                id = child.id,
                                name = child.name,
                                pathLabel = "${group.parent.name} / ${child.name}",
                                color = child.colorInt?.let(::Color) ?: rootColor
                            )
                            AnalysisTaskRow(
                                name = child.name,
                                color = choice.color,
                                checked = child.id in checkedTaskIds,
                                onCheckedChange = { onToggleChecked(child.id) },
                                hasChildren = hasChildren,
                                onClick = {
                                    if (hasChildren) onOpenChild(choice) else onAnalyze(choice)
                                },
                                onAnalyze = { onAnalyze(choice) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AnalysisSubtaskPicker(
    viewModel: TaskViewModel,
    current: AnalysisTaskChoice,
    checkedTaskIds: Set<Long>,
    canAnalyzeAll: Boolean,
    onToggleChecked: (Long) -> Unit,
    onAnalyzeAll: () -> Unit,
    onAnalyzeChecked: () -> Unit,
    onBack: () -> Unit,
    onOpenChild: (AnalysisTaskChoice) -> Unit,
    onAnalyze: (AnalysisTaskChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val children by viewModel.children(current.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
            Checkbox(
                checked = current.id in checkedTaskIds,
                onCheckedChange = { onToggleChecked(current.id) }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = current.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "サブタスクを選択",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnalysisBatchActions(
                canAnalyzeAll = canAnalyzeAll,
                checkedCount = checkedTaskIds.size,
                onAnalyzeAll = onAnalyzeAll,
                onAnalyzeChecked = onAnalyzeChecked
            )
        }
        Spacer(Modifier.height(12.dp))
        if (children.isEmpty()) {
            Text(
                "サブタスクはありません",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(children, key = TaskEntity::id) { child ->
                    val hasChildren by produceState(false, child.id) {
                        value = viewModel.hasChildren(child.id)
                    }
                    val choice = AnalysisTaskChoice(
                        id = child.id,
                        name = child.name,
                        pathLabel = "${current.pathLabel} / ${child.name}",
                        color = child.colorInt?.let(::Color) ?: current.color
                    )
                    AnalysisTaskRow(
                        name = child.name,
                        color = choice.color,
                        checked = child.id in checkedTaskIds,
                        onCheckedChange = { onToggleChecked(child.id) },
                        hasChildren = hasChildren,
                        onClick = {
                            if (hasChildren) onOpenChild(choice) else onAnalyze(choice)
                        },
                        onAnalyze = { onAnalyze(choice) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisBatchActions(
    canAnalyzeAll: Boolean,
    checkedCount: Int,
    onAnalyzeAll: () -> Unit,
    onAnalyzeChecked: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FilledTonalButton(
            onClick = onAnalyzeAll,
            enabled = canAnalyzeAll,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("全項目", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = onAnalyzeChecked,
            enabled = checkedCount > 0
        ) {
            Box(Modifier.size(24.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = "チェックした項目を解析",
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopStart)
                )
                Icon(
                    Icons.Filled.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
private fun AnalysisSelectionBottomBar(
    checkedCount: Int,
    allChecked: Boolean,
    enabled: Boolean,
    onToggleAll: () -> Unit
) {
    BottomAppBar {
        IconButton(onClick = onToggleAll, enabled = enabled) {
            Icon(
                imageVector = when {
                    allChecked -> Icons.Filled.CheckBox
                    checkedCount > 0 -> Icons.Filled.IndeterminateCheckBox
                    else -> Icons.Filled.CheckBoxOutlineBlank
                },
                contentDescription = if (allChecked) "すべてチェック解除" else "すべてチェック"
            )
        }
        Text(
            text = if (allChecked) "すべてチェック解除" else "すべてチェック",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${checkedCount}件選択",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

@Composable
private fun AnalysisTaskRow(
    name: String,
    color: Color,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    hasChildren: Boolean,
    onClick: (() -> Unit)?,
    onAnalyze: () -> Unit,
    titleStyle: Boolean = false
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .then(
            if (titleStyle) Modifier else Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable(enabled = onClick != null) { onClick?.invoke() }
        )
        .padding(horizontal = if (titleStyle) 0.dp else 12.dp)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
        Text(
            text = name,
            style = if (titleStyle) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        if (hasChildren && !titleStyle) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "サブタスクを表示",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onAnalyze) {
            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "$name を分析")
        }
    }
}

@Composable
private fun TaskAnalysisDetail(
    viewModel: TaskViewModel,
    parents: List<ParentWithChildren>,
    request: TaskAnalysisRequest,
    taskColor: Color,
    state: TaskAnalysisUiState,
    chartType: TaskAnalysisChartType,
    onChartTypeChange: (TaskAnalysisChartType) -> Unit,
    onPeriodChange: (TaskAnalysisPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onCustomStartChange: (Long) -> Unit,
    onCustomEndChange: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stateMatchesRequest = state.request == request
    val result = state.result.takeIf { stateMatchesRequest }
    var editMode by rememberSaveable(request.taskIds) { mutableStateOf(false) }
    var selectingDestination by rememberSaveable(request.taskIds) { mutableStateOf(false) }
    var selectedEventIds by remember(request.taskIds) { mutableStateOf<Set<Long>>(emptySet()) }
    val taskHasChildren = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(result) {
        result?.dayGroups
            ?.flatMap { it.entries }
            ?.map { it.taskId }
            ?.distinct()
            ?.forEach { taskId ->
                taskHasChildren[taskId] = viewModel.hasChildren(taskId)
            }
    }

    BackHandler(enabled = selectingDestination) { selectingDestination = false }
    BackHandler(enabled = editMode && !selectingDestination) {
        editMode = false
        selectedEventIds = emptySet()
    }

    if (selectingDestination) {
        ReplacementTaskSelectionScreen(
            viewModel = viewModel,
            parents = parents,
            excludedTaskIds = setOf(BATCH_ANALYSIS_TASK_ID),
            nameSort = NameSort.None,
            onBack = { selectingDestination = false },
            onPick = { taskId, _ ->
                viewModel.reassignEvents(selectedEventIds, taskId)
                selectingDestination = false
                editMode = false
                selectedEventIds = emptySet()
            }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            TaskAnalysisEditBottomBar(
                editMode = editMode,
                selectedCount = selectedEventIds.size,
                onStartEditing = { editMode = true },
                onCancelEditing = {
                    editMode = false
                    selectedEventIds = emptySet()
                },
                onApplyEditing = { selectingDestination = true }
            )
        }
    ) { innerPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                request.taskName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                result?.let(::formatAnalysisRange) ?: "集計中…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            result?.totalMs?.let(::formatDuration) ?: "--:--:--",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskAnalysisPeriod.entries.forEach { item ->
                            AnalysisChoiceChip(
                                text = periodLabel(item),
                                selected = request.period == item,
                                onClick = { onPeriodChange(item) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (request.period == TaskAnalysisPeriod.Custom) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AnalysisChoiceChip(
                                text = "開始 ${formatDate(request.customStartMs ?: request.anchorMs, request.timeZoneId, "yyyy/M/d")}",
                                selected = false,
                                onClick = {
                                    showAnalysisDatePicker(
                                        context = context,
                                        initialMs = request.customStartMs ?: request.anchorMs,
                                        onPicked = onCustomStartChange
                                    )
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            AnalysisChoiceChip(
                                text = "終了 ${formatDate((request.customEndMs ?: request.anchorMs) - 1L, request.timeZoneId, "yyyy/M/d")}",
                                selected = false,
                                onClick = {
                                    showAnalysisDatePicker(
                                        context = context,
                                        initialMs = (request.customEndMs ?: request.anchorMs) - 1L,
                                        onPicked = onCustomEndChange
                                    )
                                }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(onClick = onPrevious) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "前の期間")
                            }
                            AnalysisChoiceChip("今日", selected = false, onClick = onToday)
                            IconButton(onClick = onNext) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "次の期間")
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskAnalysisChartType.entries.forEach { item ->
                    AnalysisChoiceChip(
                        text = chartTypeLabel(item),
                        selected = chartType == item,
                        onClick = { onChartTypeChange(item) }
                    )
                }
            }
        }

        when {
            !stateMatchesRequest || state.isLoading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null -> item {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            result != null -> {
                item {
                    when (chartType) {
                        TaskAnalysisChartType.Total -> TotalBarChart(result, taskColor)
                        TaskAnalysisChartType.CumulativeStacked -> CumulativeStackedChart(result)
                        TaskAnalysisChartType.HeatMap -> ProbabilityHeatMap(result)
                    }
                }
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = averageLabel(request.period),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatDuration(result.averagePerBucketMs),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
                if (result.dayGroups.isEmpty()) {
                    item {
                        Text(
                            "この期間の記録はありません",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    result.dayGroups.forEach { group ->
                        item(key = "day-${group.dayStartMs}") {
                            AnalysisDayHeader(group, request.timeZoneId)
                        }
                        items(
                            items = group.entries,
                            key = { "${group.dayStartMs}-${it.eventId}-${it.startMs}" }
                        ) { entry ->
                            val editable = entry.eventId > 0L &&
                                taskHasChildren[entry.taskId] == false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (editMode) {
                                    Checkbox(
                                        checked = entry.eventId in selectedEventIds,
                                        enabled = editable,
                                        onCheckedChange = {
                                            if (editable) {
                                                selectedEventIds =
                                                    if (entry.eventId in selectedEventIds) {
                                                        selectedEventIds - entry.eventId
                                                    } else {
                                                        selectedEventIds + entry.eventId
                                                    }
                                            }
                                        }
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.taskLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "開始 ${formatClock(entry.startMs, request.timeZoneId)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "終了 ${formatClock(entry.endMs, request.timeZoneId)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    formatDuration(entry.durationMs),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun TaskAnalysisEditBottomBar(
    editMode: Boolean,
    selectedCount: Int,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onApplyEditing: () -> Unit
) {
    BottomAppBar {
        if (editMode) {
            IconButton(onClick = onCancelEditing) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "編集モードを終了")
            }
            IconButton(onClick = onApplyEditing, enabled = selectedCount > 0) {
                Icon(Icons.Filled.Edit, contentDescription = "選択した記録のタスクを変更")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${selectedCount}件選択",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 16.dp)
            )
        } else {
            IconButton(onClick = onStartEditing) {
                Icon(Icons.Filled.Edit, contentDescription = "記録を編集")
            }
        }
    }
}

@Composable
private fun AnalysisChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun AnalysisDayHeader(group: TaskAnalysisDayGroup, timeZoneId: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                formatDate(group.dayStartMs, timeZoneId, "yyyy年M月d日（E）"),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(formatDuration(group.totalMs), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun periodLabel(period: TaskAnalysisPeriod): String = when (period) {
    TaskAnalysisPeriod.Day -> "日"
    TaskAnalysisPeriod.Week -> "週"
    TaskAnalysisPeriod.Month -> "月"
    TaskAnalysisPeriod.Year -> "年"
    TaskAnalysisPeriod.Custom -> "カスタム"
}

private fun chartTypeLabel(type: TaskAnalysisChartType): String = when (type) {
    TaskAnalysisChartType.Total -> "期間別"
    TaskAnalysisChartType.CumulativeStacked -> "累積積み上げ"
    TaskAnalysisChartType.HeatMap -> "24時間ヒートマップ"
}

private fun averageLabel(period: TaskAnalysisPeriod): String = when (period) {
    TaskAnalysisPeriod.Day -> "1時間平均"
    TaskAnalysisPeriod.Week,
    TaskAnalysisPeriod.Month,
    TaskAnalysisPeriod.Custom -> "1日平均"

    TaskAnalysisPeriod.Year -> "1か月平均"
}

private fun formatAnalysisRange(result: TaskAnalysisResult): String {
    val zone = result.request.timeZoneId
    return when (result.request.period) {
        TaskAnalysisPeriod.Day -> formatDate(result.range.startMs, zone, "yyyy年M月d日（E）")
        TaskAnalysisPeriod.Week -> {
            val inclusiveEnd = result.range.endMs - 1L
            "${formatDate(result.range.startMs, zone, "yyyy年M月d日")} ～ " +
                formatDate(inclusiveEnd, zone, "M月d日")
        }

        TaskAnalysisPeriod.Month -> formatDate(result.range.startMs, zone, "yyyy年M月")
        TaskAnalysisPeriod.Year -> formatDate(result.range.startMs, zone, "yyyy年")
        TaskAnalysisPeriod.Custom -> {
            "${formatDate(result.range.startMs, zone, "yyyy年M月d日")} ～ " +
                formatDate(result.range.endMs - 1L, zone, "yyyy年M月d日")
        }
    }
}

private fun showAnalysisDatePicker(
    context: android.content.Context,
    initialMs: Long,
    onPicked: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialMs }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onPicked(
                Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                }.timeInMillis
            )
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun nextCalendarDay(valueMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = valueMs
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun defaultCustomEndMs(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun defaultCustomStartMs(): Long = Calendar.getInstance().apply {
    timeInMillis = defaultCustomEndMs()
    add(Calendar.DAY_OF_MONTH, -30)
}.timeInMillis

private fun formatClock(valueMs: Long, timeZoneId: String): String =
    formatDate(valueMs, timeZoneId, "HH:mm:ss")

private fun formatDate(valueMs: Long, timeZoneId: String, pattern: String): String =
    SimpleDateFormat(pattern, Locale.JAPAN).apply {
        timeZone = TimeZone.getTimeZone(timeZoneId)
    }.format(Date(valueMs))

private fun formatDuration(valueMs: Long): String {
    val totalSeconds = (valueMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.JAPAN, "%d:%02d:%02d", hours, minutes, seconds)
}
