package com.example.timerecorder.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
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
        scope.launch {
            val visible = visibleTaskIds.ifEmpty { viewModel.visibleTaskIds() }
            visibleTaskIds = visible
            val subtree = viewModel.taskTreeIds(taskId).intersect(visible)
            checkedTaskIds = toggleTaskTreeSelection(checkedTaskIds, subtree)
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
    val allItemsLabel = stringResource(R.string.analysis_all_items)
    val selectedItemsLabel = stringResource(
        R.string.analysis_selected_items,
        checkedTaskIds.size
    )
    if (drillPath.isNotEmpty()) {
        BackHandler { drillPath.removeAt(drillPath.lastIndex) }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            HierarchicalTaskSelectionBottomBar(
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
                    openBatchAnalysis(allRootTaskIds, allItemsLabel)
                },
                onAnalyzeChecked = {
                    openBatchAnalysis(checkedTaskIds, selectedItemsLabel)
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
                    openBatchAnalysis(allRootTaskIds, allItemsLabel)
                },
                onAnalyzeChecked = {
                    openBatchAnalysis(checkedTaskIds, selectedItemsLabel)
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
                text = stringResource(R.string.comprehensive_analysis_title),
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
                            onLongClick = { viewModel.requestTaskEdit(group.parent.id, group.parent.name) },
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
                                onLongClick = { viewModel.requestTaskEdit(child.id, child.name) },
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
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
                    text = stringResource(R.string.analysis_select_subtask),
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
                stringResource(R.string.analysis_no_subtasks),
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
                        onLongClick = { viewModel.requestTaskEdit(child.id, child.name) },
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
        IconButton(
            onClick = onAnalyzeAll,
            enabled = canAnalyzeAll
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = stringResource(R.string.analysis_analyze_all)
            )
        }
        IconButton(
            onClick = onAnalyzeChecked,
            enabled = checkedCount > 0
        ) {
            Box(Modifier.size(24.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = stringResource(R.string.analysis_analyze_checked),
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
private fun AnalysisTaskRow(
    name: String,
    color: Color,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    hasChildren: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit = {},
    onAnalyze: () -> Unit,
    titleStyle: Boolean = false
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .then(
            if (titleStyle) Modifier else Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))

        )
        .combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
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
                contentDescription = stringResource(R.string.analysis_show_subtasks),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onAnalyze) {
            Icon(
                Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = stringResource(R.string.analysis_analyze_task, name)
            )
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
    val selectableEventIds = remember(result) {
        result?.dayGroups
            ?.flatMap { it.entries }
            ?.mapNotNull { entry -> entry.eventId.takeIf { it > 0L } }
            ?.toSet()
            .orEmpty()
    }
    val allEventsSelected = selectableEventIds.isNotEmpty() &&
        selectedEventIds.containsAll(selectableEventIds)

    LaunchedEffect(selectableEventIds) {
        selectedEventIds = selectedEventIds.intersect(selectableEventIds)
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
                allSelected = allEventsSelected,
                selectionEnabled = selectableEventIds.isNotEmpty(),
                onStartEditing = { editMode = true },
                onCancelEditing = {
                    editMode = false
                    selectedEventIds = emptySet()
                },
                onToggleAll = {
                    selectedEventIds = if (allEventsSelected) {
                        emptySet()
                    } else {
                        selectableEventIds
                    }
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                request.taskName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (result != null) {
                                    formatAnalysisRange(result)
                                } else {
                                    stringResource(R.string.analysis_loading)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            result?.totalMs?.let(::formatDuration)
                                ?: stringResource(R.string.label_empty_duration),
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
                                text = stringResource(
                                    R.string.analysis_custom_start,
                                    formatDate(
                                        request.customStartMs ?: request.anchorMs,
                                        request.timeZoneId,
                                        "yyyy/M/d"
                                    )
                                ),
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
                                text = stringResource(
                                    R.string.analysis_custom_end,
                                    formatDate(
                                        (request.customEndMs ?: request.anchorMs) - 1L,
                                        request.timeZoneId,
                                        "yyyy/M/d"
                                    )
                                ),
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
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = stringResource(
                                        R.string.analysis_previous_period
                                    )
                                )
                            }
                            AnalysisChoiceChip(
                                stringResource(R.string.action_today),
                                selected = false,
                                onClick = onToday
                            )
                            IconButton(onClick = onNext) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = stringResource(R.string.analysis_next_period)
                                )
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
                            stringResource(R.string.analysis_no_records),
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
                            val editable = entry.eventId > 0L
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
                                        stringResource(
                                            R.string.analysis_record_start,
                                            formatClock(entry.startMs, request.timeZoneId)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(
                                            R.string.analysis_record_end,
                                            formatClock(entry.endMs, request.timeZoneId)
                                        ),
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
    allSelected: Boolean,
    selectionEnabled: Boolean,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onToggleAll: () -> Unit,
    onApplyEditing: () -> Unit
) {
    BottomAppBar {
        if (editMode) {
            IconButton(onClick = onCancelEditing) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.analysis_exit_edit_mode)
                )
            }
            IconButton(onClick = onToggleAll, enabled = selectionEnabled) {
                Icon(
                    imageVector = when {
                        allSelected -> Icons.Filled.CheckBox
                        selectedCount > 0 -> Icons.Filled.IndeterminateCheckBox
                        else -> Icons.Filled.CheckBoxOutlineBlank
                    },
                    contentDescription = stringResource(
                        if (allSelected) {
                            R.string.analysis_uncheck_all
                        } else {
                            R.string.analysis_check_all
                        }
                    )
                )
            }
            if (selectedCount > 0) {
                IconButton(onClick = onApplyEditing) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(
                            R.string.analysis_change_selected_records_task
                        )
                    )
                }
            }
        } else {
            IconButton(onClick = onStartEditing) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.analysis_edit_records)
                )
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
                formatDate(
                    group.dayStartMs,
                    timeZoneId,
                    stringResource(R.string.date_pattern_day_full)
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(formatDuration(group.totalMs), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun periodLabel(period: TaskAnalysisPeriod): String = when (period) {
    TaskAnalysisPeriod.Day -> stringResource(R.string.analysis_period_day)
    TaskAnalysisPeriod.Week -> stringResource(R.string.analysis_period_week)
    TaskAnalysisPeriod.Month -> stringResource(R.string.analysis_period_month)
    TaskAnalysisPeriod.Year -> stringResource(R.string.analysis_period_year)
    TaskAnalysisPeriod.Custom -> stringResource(R.string.analysis_period_custom)
}

@Composable
private fun chartTypeLabel(type: TaskAnalysisChartType): String = when (type) {
    TaskAnalysisChartType.Total -> stringResource(R.string.analysis_chart_period_total)
    TaskAnalysisChartType.CumulativeStacked ->
        stringResource(R.string.analysis_chart_cumulative_stacked)
    TaskAnalysisChartType.HeatMap -> stringResource(R.string.analysis_chart_heatmap)
}

@Composable
private fun averageLabel(period: TaskAnalysisPeriod): String = when (period) {
    TaskAnalysisPeriod.Day -> stringResource(R.string.analysis_average_hour)
    TaskAnalysisPeriod.Week,
    TaskAnalysisPeriod.Month,
    TaskAnalysisPeriod.Custom -> stringResource(R.string.analysis_average_day)

    TaskAnalysisPeriod.Year -> stringResource(R.string.analysis_average_month)
}

@Composable
private fun formatAnalysisRange(result: TaskAnalysisResult): String {
    val zone = result.request.timeZoneId
    return when (result.request.period) {
        TaskAnalysisPeriod.Day -> formatDate(
            result.range.startMs,
            zone,
            stringResource(R.string.date_pattern_day_full)
        )
        TaskAnalysisPeriod.Week -> {
            val inclusiveEnd = result.range.endMs - 1L
            stringResource(
                R.string.date_range,
                formatDate(
                    result.range.startMs,
                    zone,
                    stringResource(R.string.date_pattern_day)
                ),
                formatDate(
                    inclusiveEnd,
                    zone,
                    stringResource(R.string.date_pattern_month_day)
                )
            )
        }

        TaskAnalysisPeriod.Month -> formatDate(
            result.range.startMs,
            zone,
            stringResource(R.string.date_pattern_month)
        )
        TaskAnalysisPeriod.Year -> formatDate(
            result.range.startMs,
            zone,
            stringResource(R.string.date_pattern_year)
        )
        TaskAnalysisPeriod.Custom -> {
            stringResource(
                R.string.date_range,
                formatDate(
                    result.range.startMs,
                    zone,
                    stringResource(R.string.date_pattern_day)
                ),
                formatDate(
                    result.range.endMs - 1L,
                    zone,
                    stringResource(R.string.date_pattern_day)
                )
            )
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
    SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(timeZoneId)
    }.format(Date(valueMs))

private fun formatDuration(valueMs: Long): String {
    val totalSeconds = (valueMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
}
