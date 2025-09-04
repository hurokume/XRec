package com.example.timerecorder

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timerecorder.ui.TaskViewModel
import com.example.timerecorder.ui.theme.TimeRecorderTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private enum class AppScreen { Main, History }

class MainActivity : ComponentActivity() {
    private val taskViewModel: TaskViewModel by viewModels()

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeRecorderTheme {
                AppRoot(viewModel = taskViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppRoot(viewModel: TaskViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    var currentPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collectLatest { page -> currentPage = page }
    }

    var screen by remember { mutableStateOf(AppScreen.Main) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    DrawerSheet(
                        onItemSelected = { key ->
                            scope.launch { drawerState.close() }
                            when (key) {
                                "home" -> screen = AppScreen.Main
                                "history" -> {
                                    screen = AppScreen.History
                                    viewModel.ensureHistoryFirstPage()
                                }

                                "settings" -> { /* TODO */
                                }

                                "help" -> { /* TODO */
                                }
                            }
                        }
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    topBar = {
                        TopAppBar(
                            navigationIcon = {},
                            title = {
                                Text(
                                    text = when (screen) {
                                        AppScreen.Main -> stringResource(R.string.header_title)
                                        AppScreen.History -> "History"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            actions = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = stringResource(R.string.drawer_title)
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (screen == AppScreen.Main) {
                            AdaptiveBottomBar(
                                currentPage = currentPage,
                                onLeftAction = { },
                                onCheckAction = { },
                                onRightAction = { }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (screen) {
                        AppScreen.Main -> MainPager(
                            modifier = Modifier.padding(innerPadding),
                            pagerState = pagerState,
                            viewModel = viewModel
                        )

                        AppScreen.History -> HistoryScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerSheet(onItemSelected: (String) -> Unit) {
    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.drawer_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_home)) },
            selected = false,
            onClick = { onItemSelected("home") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_settings)) },
            selected = false,
            onClick = { onItemSelected("settings") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("History") },
            selected = false,
            onClick = { onItemSelected("history") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_help)) },
            selected = false,
            onClick = { onItemSelected("help") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPager(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    viewModel: TaskViewModel
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> BlankScreen(label = stringResource(R.string.left_page_label))
            1 -> DBTaskScreen(viewModel = viewModel)
            2 -> BlankScreen(label = stringResource(R.string.right_page_label))
        }
    }
}

/* ------- 中央ページ UI State ------- */
private sealed interface TaskCenterUiState {
    data object List : TaskCenterUiState
    data class SubtaskSelect(
        val parentTitle: String,
        val parentId: Long,
        val color: Color
    ) : TaskCenterUiState
}

@Composable
fun DBTaskScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var ui by remember { mutableStateOf<TaskCenterUiState>(TaskCenterUiState.List) }

    // ハイライト対象ID（子レベル）
    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    // MaterialTheme は Composable の文脈で取得しておく
    val themePrimary = MaterialTheme.colorScheme.primary

    // 親タスク追加（FAB）
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newTaskName by rememberSaveable { mutableStateOf("") }

    // 親行右端メニュー
    var menuForParentId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 子タスク追加ダイアログ
    var showAddChildDialogFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var newChildName by rememberSaveable { mutableStateOf("") }

    // 色変更ダイアログ
    var colorPickForParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    val presetColors = listOf(
        0xFFEF9A9A, 0xFFA5D6A7, 0xFF90CAF9, 0xFFFFF59D, 0xFFCE93D8
    ).map { Color(it) }

    // 実行中タスクの復元：孫タスクでもサブタスク画面には遷移せず、親の「子」をハイライト
    LaunchedEffect(active, parents) {
        val a = active
        if (a == null) {
            selectedTaskId = null
            ui = TaskCenterUiState.List
        } else {
            val activeId = a.task.id
            val parentIdOfActive = a.task.parentId
            val foundDirect =
                parents.firstOrNull { group -> group.children.any { it.id == activeId } }
            if (foundDirect != null) {
                selectedTaskId = activeId
            } else if (parentIdOfActive != null) {
                val container =
                    parents.firstOrNull { group -> group.children.any { it.id == parentIdOfActive } }
                selectedTaskId = container?.children?.firstOrNull { it.id == parentIdOfActive }?.id
            } else {
                selectedTaskId = null
            }
            ui = TaskCenterUiState.List
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (val s = ui) {
                TaskCenterUiState.List -> {
                    Text(
                        text = stringResource(R.string.label_select_task),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                        items(parents.size) { idx ->
                            val p = parents[idx]
                            val parentColor = p.parent.colorInt?.let { Color(it) } ?: themePrimary

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {

                                    // 親タスク行（右端メニュー付き）
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            p.parent.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { menuForParentId = p.parent.id }) {
                                            Icon(
                                                Icons.Filled.Menu,
                                                contentDescription = "Parent task menu"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuForParentId == p.parent.id,
                                            onDismissRequest = { menuForParentId = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("タスクを追加") },
                                                onClick = {
                                                    menuForParentId = null
                                                    newChildName = ""
                                                    showAddChildDialogFor = p.parent.id
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("親タスク（プロジェクト）を削除") },
                                                onClick = {
                                                    menuForParentId = null
                                                    viewModel.deleteParent(p.parent.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("色の変更") },
                                                onClick = {
                                                    menuForParentId = null
                                                    colorPickForParentId = p.parent.id
                                                }
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    p.children.forEach { child ->
                                        val isSelected = selectedTaskId == child.id
                                        val bg =
                                            if (isSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                                        val border =
                                            if (isSelected) Color.Red else MaterialTheme.colorScheme.outline
                                        val textColor =
                                            if (isSelected) Color.Red else MaterialTheme.colorScheme.onSurface

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .border(1.dp, border, RoundedCornerShape(8.dp)),
                                            onClick = {
                                                selectedTaskId = child.id
                                                scope.launch {
                                                    val has = viewModel.hasChildren(child.id)
                                                    if (has) {
                                                        ui = TaskCenterUiState.SubtaskSelect(
                                                            parentTitle = child.name,
                                                            parentId = child.id,
                                                            color = parentColor
                                                        )
                                                    } else {
                                                        viewModel.onTaskPressed(child.id)
                                                    }
                                                }
                                            },
                                            colors = CardDefaults.cardColors(containerColor = bg),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = child.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    modifier = Modifier.weight(1f),
                                                    color = textColor
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .background(
                                                            parentColor,
                                                            RoundedCornerShape(3.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            border,
                                                            RoundedCornerShape(3.dp)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                is TaskCenterUiState.SubtaskSelect -> {
                    SubtaskPicker(
                        title = "${s.parentTitle} のサブタスク",
                        parentId = s.parentId,
                        color = s.color,
                        onBack = { ui = TaskCenterUiState.List },
                        onPick = { pickedId ->
                            viewModel.onTaskPressed(pickedId)
                            ui = TaskCenterUiState.List
                        },
                        viewModel = viewModel
                    )
                }
            }
        }

        // 親タスク追加（常に parentId = null）
        FloatingActionButton(
            onClick = {
                newTaskName = ""
                showAddDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.AddCircle, contentDescription = "Add task")
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newTaskName.isNotBlank()) {
                                viewModel.addParentTask(newTaskName.trim())
                                showAddDialog = false
                            }
                        }
                    ) { Text("追加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("キャンセル") }
                },
                title = { Text("タスクを追加") },
                text = {
                    OutlinedTextField(
                        value = newTaskName,
                        onValueChange = { newTaskName = it },
                        singleLine = true,
                        label = { Text("タスク名") }
                    )
                }
            )
        }

        // 子タスク追加ダイアログ
        if (showAddChildDialogFor != null) {
            AlertDialog(
                onDismissRequest = { showAddChildDialogFor = null },
                title = { Text("子タスクを追加") },
                text = {
                    OutlinedTextField(
                        value = newChildName,
                        onValueChange = { newChildName = it },
                        singleLine = true,
                        label = { Text("タスク名") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val pid = showAddChildDialogFor
                        if (pid != null && newChildName.isNotBlank()) {
                            viewModel.addChildTask(pid, newChildName.trim())
                            showAddChildDialogFor = null
                        }
                    }) { Text("追加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddChildDialogFor = null }) { Text("キャンセル") }
                }
            )
        }

        // 色変更ダイアログ
        if (colorPickForParentId != null) {
            AlertDialog(
                onDismissRequest = { colorPickForParentId = null },
                title = { Text("色の変更") },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        presetColors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(c, RoundedCornerShape(6.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        val id = colorPickForParentId
                                        if (id != null) {
                                            viewModel.changeParentColor(id, c)
                                            colorPickForParentId = null
                                        }
                                    }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { colorPickForParentId = null }) { Text("閉じる") }
                }
            )
        }
    }
}


/* ------- 孫一覧（選択で開始 + 赤系ハイライト） ------- */
@Composable
private fun SubtaskPicker(
    title: String,
    parentId: Long,
    color: Color,
    onBack: () -> Unit,
    onPick: (Long) -> Unit,
    viewModel: TaskViewModel
) {
    val subtasks by viewModel.children(parentId).collectAsStateWithLifecycle(emptyList())
    var selectedSubId by rememberSaveable { mutableStateOf<Long?>(null) }

    val active by viewModel.active.collectAsStateWithLifecycle()
    LaunchedEffect(active, subtasks) {
        val aid = active?.task?.id
        if (aid != null && subtasks.any { it.id == aid }) {
            selectedSubId = aid
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("＜ 戻る") }
            Spacer(Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp)
        ) {
            items(subtasks.size) { i ->
                val t = subtasks[i]
                val isSelected = selectedSubId == t.id
                val bg = if (isSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                val border = if (isSelected) Color.Red else MaterialTheme.colorScheme.outline
                val textColor = if (isSelected) Color.Red else MaterialTheme.colorScheme.onSurface

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, border, RoundedCornerShape(8.dp)),
                    onClick = {
                        selectedSubId = t.id
                        onPick(t.id)
                    },
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            color = textColor
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(color, RoundedCornerShape(3.dp))
                                .border(1.dp, border, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

/* ------- History（100件ページング・末尾付近で追加ロード） ------- */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier, viewModel: TaskViewModel) {
    val items by viewModel.historyItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.historyLoading.collectAsStateWithLifecycle()
    val endReached by viewModel.historyEndReached.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (!isLoading && !endReached && last >= total - 5) {
            scope.launch { viewModel.loadNextHistoryPage() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
        ) {
            items(items.size) { i ->
                val row = items[i]
                HistoryRowCard(row)
                Spacer(Modifier.height(8.dp))
            }
            item {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    endReached -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("以上です") }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.reloadHistory() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reload history")
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun HistoryRowCard(row: com.example.timerecorder.ui.HistoryItemUi) {
    val dateStr = remember(row.pressedAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(row.pressedAt))
    }
    val minutes = max(0L, row.durationMillis) / 1000 / 60
    val seconds = (max(0L, row.durationMillis) / 1000) % 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.taskName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(row.color, RoundedCornerShape(3.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                String.format("%d:%02d", minutes, seconds),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/* ------- BottomBar / Blank ------- */
@Composable
fun AdaptiveBottomBar(
    currentPage: Int,
    onLeftAction: () -> Unit = {},
    onCheckAction: () -> Unit = {},
    onRightAction: () -> Unit = {}
) {
    when (currentPage) {
        0 -> {
            BottomAppBar {
                IconButton(onClick = onLeftAction) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "Left action")
                }
                Text("左ページ", modifier = Modifier.padding(start = 8.dp))
            }
        }

        1 -> {
            BottomAppBar(
                actions = {
                    IconButton(onClick = onCheckAction) {
                        Icon(Icons.Filled.Check, contentDescription = "Confirm")
                    }
                }
            )
        }

        2 -> {
            BottomAppBar {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRightAction) {
                    Icon(Icons.Filled.Settings, contentDescription = "Right action")
                }
            }
        }
    }
}

@Composable
fun BlankScreen(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
