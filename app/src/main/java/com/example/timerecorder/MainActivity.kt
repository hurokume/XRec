package com.example.timerecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

// ---------------------------
// MainActivity
// ---------------------------
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

// ---------------------------
// ルート（Drawer + Scaffold + Pager）
// ---------------------------
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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    DrawerSheet(onItemSelected = { scope.launch { drawerState.close() } })
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
                                    text = stringResource(R.string.header_title),
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
                        AdaptiveBottomBar(
                            currentPage = currentPage,
                            onLeftAction = { /* 左ページアクション */ },
                            onCheckAction = { /* 中央ページアクション */ },
                            onRightAction = { /* 右ページアクション */ }
                        )
                    }
                ) { innerPadding ->
                    MainPager(
                        modifier = Modifier.padding(innerPadding),
                        pagerState = pagerState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

// ---------------------------
// Drawer
// ---------------------------
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
            label = { Text(stringResource(R.string.drawer_help)) },
            selected = false,
            onClick = { onItemSelected("help") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------
// Pager
// ---------------------------
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

// ---------------------------
// UI state for center page
// ---------------------------
private sealed interface TaskCenterUiState {
    data object List : TaskCenterUiState
    data class SubtaskSelect(
        val parentTitle: String,      // タップした“子”の名前（＝サブタスク親）
        val parentId: Long,
        val color: Color
    ) : TaskCenterUiState
}

// ---------------------------
// DBTaskScreen : 親→子の一覧。子をタップすると、
//  1) その子に孫が1件以上あれば SubtaskSelect へ
//  2) 無ければその子を即アクティブ化
// ---------------------------
@Composable
fun DBTaskScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var ui by remember { mutableStateOf<TaskCenterUiState>(TaskCenterUiState.List) }

    Column(
        modifier = modifier
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
                        val parentColor = p.parent.colorInt?.let { Color(it) }
                            ?: MaterialTheme.colorScheme.primary

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
                                Text(p.parent.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))

                                p.children.forEach { child ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(8.dp)
                                            ),
                                        onClick = {
                                            // 子をタップ → 孫が1件以上あるかチェックして遷移 or 即開始
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
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                                modifier = Modifier.weight(1f)
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
                                                        MaterialTheme.colorScheme.outline,
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

                // 現在アクティブ表示 & 停止
                active?.let { a ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "実行中：${a.task.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onStopPressed() }) {
                        Text("停止")
                    }
                }
            }

            is TaskCenterUiState.SubtaskSelect -> {
                SubtaskPicker(
                    title = "${s.parentTitle} のサブタスク",
                    parentId = s.parentId,
                    color = s.color,
                    onBack = { ui = TaskCenterUiState.List },
                    onPick = { pickedTaskId ->
                        viewModel.onTaskPressed(pickedTaskId)
                        ui = TaskCenterUiState.List
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}

// ---------------------------
// SubtaskPicker : 指定親（＝タップした“子”）の直下の子（孫）を一覧
// ---------------------------
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

    Column(Modifier.fillMaxSize()) {
        // 簡易な戻る行
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    onClick = { onPick(t.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(color, RoundedCornerShape(3.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------
// BottomBar / Blank
// ---------------------------
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
