package com.example.timerecorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.example.timerecorder.R
import com.example.timerecorder.data.db.ParentWithChildren
import com.example.timerecorder.data.db.TaskEntity
import kotlinx.coroutines.launch

private data class StatsFilterTaskChoice(
    val id: Long,
    val name: String,
    val color: Color
)

@Composable
fun StatsFilterSelectionScreen(
    viewModel: TaskViewModel,
    selectedTaskIds: Set<Long>?,
    onSelectionChange: (Set<Long>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val drillPath = remember { mutableStateListOf<StatsFilterTaskChoice>() }
    val scope = rememberCoroutineScope()
    var visibleTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(parents) {
        visibleTaskIds = viewModel.visibleTaskIds()
    }

    val selected = selectedTaskIds?.intersect(visibleTaskIds) ?: visibleTaskIds
    val allChecked = visibleTaskIds.isNotEmpty() && selected.containsAll(visibleTaskIds)

    fun toggleTask(taskId: Long) {
        scope.launch {
            val visible = visibleTaskIds.ifEmpty { viewModel.visibleTaskIds() }
            visibleTaskIds = visible
            val subtree = viewModel.taskTreeIds(taskId).intersect(visible)
            onSelectionChange(toggleTaskTreeSelection(selected, subtree))
        }
    }

    BackHandler {
        if (drillPath.isNotEmpty()) {
            drillPath.removeAt(drillPath.lastIndex)
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            HierarchicalTaskSelectionBottomBar(
                checkedCount = selected.size,
                allChecked = allChecked,
                enabled = visibleTaskIds.isNotEmpty(),
                onToggleAll = {
                    onSelectionChange(if (allChecked) emptySet() else visibleTaskIds)
                }
            )
        }
    ) { innerPadding ->
        if (drillPath.isEmpty()) {
            StatsFilterRootList(
                parents = parents,
                viewModel = viewModel,
                selectedTaskIds = selected,
                onToggleTask = ::toggleTask,
                onOpenChild = { drillPath += it },
                onBack = onBack,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            StatsFilterSubtaskList(
                viewModel = viewModel,
                current = drillPath.last(),
                selectedTaskIds = selected,
                onToggleTask = ::toggleTask,
                onOpenChild = { drillPath += it },
                onBack = { drillPath.removeAt(drillPath.lastIndex) },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun StatsFilterRootList(
    parents: List<ParentWithChildren>,
    viewModel: TaskViewModel,
    selectedTaskIds: Set<Long>,
    onToggleTask: (Long) -> Unit,
    onOpenChild: (StatsFilterTaskChoice) -> Unit,
    onBack: () -> Unit,
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
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
            Text(
                text = stringResource(R.string.label_select_task),
                style = MaterialTheme.typography.titleMedium
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(parents, key = { it.parent.id }) { group ->
                val rootColor = group.parent.colorInt?.let(::Color)
                    ?: MaterialTheme.colorScheme.primary
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
                        StatsFilterTaskRow(
                            task = StatsFilterTaskChoice(
                                id = group.parent.id,
                                name = group.parent.name,
                                color = rootColor
                            ),
                            checked = group.parent.id in selectedTaskIds,
                            hasChildren = group.children.isNotEmpty(),
                            titleStyle = true,
                            onToggle = { onToggleTask(group.parent.id) }
                        )
                        Spacer(Modifier.height(8.dp))
                        group.children.forEach { child ->
                            val hasChildren by produceState(false, child.id) {
                                value = viewModel.hasChildren(child.id)
                            }
                            val choice = StatsFilterTaskChoice(
                                id = child.id,
                                name = child.name,
                                color = child.colorInt?.let(::Color) ?: rootColor
                            )
                            StatsFilterTaskRow(
                                task = choice,
                                checked = child.id in selectedTaskIds,
                                hasChildren = hasChildren,
                                onToggle = { onToggleTask(child.id) },
                                onOpenChild = if (hasChildren) {
                                    { onOpenChild(choice) }
                                } else {
                                    null
                                }
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
private fun StatsFilterSubtaskList(
    viewModel: TaskViewModel,
    current: StatsFilterTaskChoice,
    selectedTaskIds: Set<Long>,
    onToggleTask: (Long) -> Unit,
    onOpenChild: (StatsFilterTaskChoice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val children by viewModel.children(current.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

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
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
            Checkbox(
                checked = current.id in selectedTaskIds,
                onCheckedChange = { onToggleTask(current.id) }
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
        }

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
                    val choice = StatsFilterTaskChoice(
                        id = child.id,
                        name = child.name,
                        color = child.colorInt?.let(::Color) ?: current.color
                    )
                    StatsFilterTaskRow(
                        task = choice,
                        checked = child.id in selectedTaskIds,
                        hasChildren = hasChildren,
                        onToggle = { onToggleTask(child.id) },
                        onOpenChild = if (hasChildren) {
                            { onOpenChild(choice) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsFilterTaskRow(
    task: StatsFilterTaskChoice,
    checked: Boolean,
    hasChildren: Boolean,
    onToggle: () -> Unit,
    onOpenChild: (() -> Unit)? = null,
    titleStyle: Boolean = false
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .then(
            if (titleStyle) {
                Modifier
            } else {
                Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { onOpenChild?.invoke() ?: onToggle() }
            }
        )
        .padding(horizontal = if (titleStyle) 0.dp else 12.dp)

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text = task.name,
            style = if (titleStyle) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(task.color, RoundedCornerShape(3.dp))
        )
        if (hasChildren && !titleStyle) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.analysis_show_subtasks),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun HierarchicalTaskSelectionBottomBar(
    checkedCount: Int,
    allChecked: Boolean,
    enabled: Boolean,
    onToggleAll: () -> Unit
) {
    val toggleAllLabel = if (allChecked) {
        stringResource(R.string.analysis_uncheck_all)
    } else {
        stringResource(R.string.analysis_check_all)
    }
    BottomAppBar {
        IconButton(onClick = onToggleAll, enabled = enabled) {
            Icon(
                imageVector = when {
                    allChecked -> Icons.Filled.CheckBox
                    checkedCount > 0 -> Icons.Filled.IndeterminateCheckBox
                    else -> Icons.Filled.CheckBoxOutlineBlank
                },
                contentDescription = toggleAllLabel
            )
        }
    }
}
