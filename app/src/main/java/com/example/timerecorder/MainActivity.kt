package com.example.timerecorder

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.timerecorder.ui.HistoryItemUi
import com.example.timerecorder.ui.TaskViewModel
import com.example.timerecorder.ui.theme.TimeRecorderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** 画面モード（メイン/履歴） */
private enum class AppScreen { Main, History }

/** Navigation のルート定義 */
private object Routes {
    const val Root = "root"
    const val AddTask = "addTask"
}

/**
 * エントリポイント Activity。
 * - Compose をセットアップし、NavHost で画面遷移を定義する。
 * - ここでは「Root」（既存 UI）と「AddTask」（タスク記録追加）に遷移可能。
 */
class MainActivity : ComponentActivity() {
    private val taskViewModel: TaskViewModel by viewModels()

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeRecorderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // NavController を用意し、アプリ内の画面遷移を管理
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Routes.Root
                    ) {
                        // 既存UI（ドロワー＋ページャー等）は Root に載せる
                        composable(Routes.Root) {
                            AppRoot(
                                viewModel = taskViewModel,
                                onNavigateAddTask = { navController.navigate(Routes.AddTask) }
                            )
                        }
                        // タスク記録追加（プレースホルダ）
                        composable(Routes.AddTask) {
                            AddTaskScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * アプリ全体の土台（ドロワー、TopBar、BottomBar、Pager、各種ダイアログ）。
 * @param onNavigateAddTask タイムテーブルの＋ボタンで呼ばれる遷移ハンドラ
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppRoot(
    viewModel: TaskViewModel,
    onNavigateAddTask: () -> Unit
) {
    // ドロワーの開閉状態
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 中央ページ（タスク一覧）を起点に、左右へページング
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Import ダイアログの状態
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var replaceAll by rememberSaveable { mutableStateOf(false) }

    // 現在表示中のページインデックス（BottomBar の表示切替に利用）
    var currentPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState) {
        // ページが変わったら currentPage を追従
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collectLatest { page -> currentPage = page }
    }

    // 画面モード（メイン / 履歴）
    var screen by remember { mutableStateOf(AppScreen.Main) }

    // 並び替えモード（タスクの上下入れ替え）
    var sortMode by rememberSaveable { mutableStateOf(false) }

    // 右からドロワーを出したいので、RTL/LTR を切り替えて実現
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    DrawerSheet(
                        onItemSelected = { key ->
                            // ドロワー操作後は閉じる
                            scope.launch { drawerState.close() }
                            when (key) {
                                "home" -> screen = AppScreen.Main
                                "history" -> {
                                    screen = AppScreen.History
                                    // 履歴はページングされるため、先頭ページを確保
                                    viewModel.ensureHistoryFirstPage()
                                }

                                "export" -> {
                                    // タスク定義を JSON で共有
                                    scope.launch {
                                        try {
                                            val text = viewModel.exportTasksJson()
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                                putExtra(
                                                    Intent.EXTRA_SUBJECT,
                                                    "TimeRecorder Tasks Export"
                                                )
                                            }
                                            ctx.startActivity(
                                                Intent.createChooser(
                                                    send,
                                                    "Export tasks as JSON"
                                                )
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                ctx,
                                                "Export 失敗: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }

                                "import" -> {
                                    // JSON テキスト貼り付けで Import するためのダイアログ
                                    showImportDialog = true
                                }

                                "settings" -> { /* 設定画面（今後実装） */
                                }

                                "help" -> { /* ヘルプ（今後実装） */
                                }
                            }
                        }
                    )
                }
            }
        ) {
            // 以降は通常の LTR で UI を組む
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    snackbarHost = { SnackbarHost(hostState = snackbar) },
                    topBar = {
                        TopAppBar(
                            navigationIcon = {},
                            title = {
                                Text(
                                    text = when (screen) {
                                        AppScreen.Main ->
                                            if (sortMode) "並び替え" else stringResource(R.string.header_title)

                                        AppScreen.History -> "History"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            actions = {
                                // 並び替え中は「×」で終了、それ以外はメニューボタン
                                if (screen == AppScreen.Main && sortMode) {
                                    IconButton(onClick = { sortMode = false }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Exit sort mode"
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = stringResource(R.string.drawer_title)
                                        )
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        // Main 画面のみ BottomBar を表示
                        if (screen == AppScreen.Main) {
                            AdaptiveBottomBar(
                                currentPage = currentPage,
                                onLeftAction = { /* 予備アクション */ },
                                onSortAction = { sortMode = true },
                                onRightAction = { /* 予備アクション */ }
                            )
                        }
                    }
                ) { innerPadding ->
                    // ページ本体
                    when (screen) {
                        AppScreen.Main -> MainPager(
                            modifier = Modifier.padding(innerPadding),
                            pagerState = pagerState,
                            viewModel = viewModel,
                            sortMode = sortMode,
                            onNavigateAddTask = onNavigateAddTask
                        )

                        AppScreen.History -> HistoryScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            viewModel = viewModel
                        )
                    }

                    // ===== Import ダイアログ =====
                    if (showImportDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportDialog = false },
                            title = { Text("Import (JSON)") },
                            text = {
                                Column {
                                    Text(
                                        "エクスポートした JSON をここに貼り付けてください。",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    // JSON 入力欄
                                    OutlinedTextField(
                                        value = importText,
                                        onValueChange = { importText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp),
                                        placeholder = {
                                            Text(
                                                "[\n  {\"name\":\"Project\",\"color\":16753928,\"children\":[{\"name\":\"Task\"}]}\n]"
                                            )
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    // 既存を全削除して置換するかどうか
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = replaceAll,
                                            onCheckedChange = { replaceAll = it }
                                        )
                                        Text("既存を全て削除して置き換える")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    // Import 実行
                                    scope.launch {
                                        try {
                                            viewModel.importTasksFromJson(
                                                importText,
                                                replaceAll
                                            )
                                            // 後片付け
                                            importText = ""
                                            replaceAll = false
                                            showImportDialog = false
                                            snackbar.showSnackbar("Import 完了")
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                ctx,
                                                "Import 失敗: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }) { Text("実行") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showImportDialog = false }) {
                                    Text("キャンセル")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * サイドドロワーの項目群。
 * @param onItemSelected クリックされたキー文字列をハンドリングするコールバック
 */
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
        // Export / Import
        NavigationDrawerItem(
            label = { Text("Export (JSON)") },
            selected = false,
            onClick = { onItemSelected("export") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Import (JSON)") },
            selected = false,
            onClick = { onItemSelected("import") },
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

/**
 * 3ページ（左：タイムライン / 中央：タスク / 右：集計）を横スワイプで切替。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPager(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    viewModel: TaskViewModel,
    sortMode: Boolean,
    onNavigateAddTask: () -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> TimelineScreen(
                viewModel = viewModel,
                onAddRecord = onNavigateAddTask
            ) // 左ページ：マルチ日タイムライン
            1 -> DBTaskScreen(viewModel = viewModel, sortMode = sortMode) // 中央：タスク
            2 -> StatsScreen(viewModel = viewModel) // 右ページ：集計グラフ
        }
    }
}

/* ------- 中央ページ UI State ------- */
private sealed interface TaskCenterUiState {
    /** 通常の一覧表示 */
    data object List : TaskCenterUiState

    /** サブタスク選択中（親タスクの情報付き） */
    data class SubtaskSelect(
        val parentTitle: String,
        val parentId: Long,
        val color: Color
    ) : TaskCenterUiState
}

/**
 * 中央ページ：タスク一覧画面。
 * - クリックで開始/サブタスク選択
 * - 長押しドラッグで並び替え
 * - FAB で親/子タスクの追加
 */
@Composable
fun DBTaskScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier,
    sortMode: Boolean
) {
    // タスク構成（親→子のグループ）
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    // 実行中タスクの UI 状態
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    // コルーチンスコープ（DB 更新など）
    val scope = rememberCoroutineScope()

    // 現在の UI サブ状態（通常/サブタスク選択）
    var ui by remember { mutableStateOf<TaskCenterUiState>(TaskCenterUiState.List) }

    // ハイライト対象（実行中 or クリックしたタスク）
    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 追加／ダイアログ用 state（親）
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newTaskName by rememberSaveable { mutableStateOf("") }
    var addParentError by rememberSaveable { mutableStateOf<String?>(null) }

    // 親行のメニュー＆子追加ダイアログ
    var menuForParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddChildDialogFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var newChildName by rememberSaveable { mutableStateOf("") }
    var addChildError by rememberSaveable { mutableStateOf<String?>(null) }

    // 子タスク行メニュー（リネーム/削除）
    var menuForChildTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTaskCurrentName by rememberSaveable { mutableStateOf("") }
    var renameTaskText by rememberSaveable { mutableStateOf("") }
    var confirmDeleteTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteTaskName by rememberSaveable { mutableStateOf("") }

    // 親の色変更／削除
    var colorPickForParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteParentName by rememberSaveable { mutableStateOf("") }

    // プリセット色
    val presetColors =
        listOf(0xFFEF9A9A, 0xFFA5D6A7, 0xFF90CAF9, 0xFFFFF59D, 0xFFCE93D8).map { Color(it) }

    // FAB の自動フェードアウト制御
    var fabVisible by rememberSaveable { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastInteractionTime) {
        fabVisible = true
        delay(5000)
        fabVisible = false
    }

    // スクロール監視（操作があればタイマー延長）
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { lastInteractionTime = System.currentTimeMillis() }
    }

// 実行中タスクに合わせてハイライトを更新（サブ状態はリセット）
// 小タスク（サブタスク）が選択・実行中なら、その直上の子タスク行をハイライトする
    LaunchedEffect(activeUi, parents) {
        // 初期化
        selectedTaskId = null
        ui = TaskCenterUiState.List

        val a = activeUi ?: return@LaunchedEffect

        // 1) 直接この画面に並んでいる子タスクなら、その行をハイライト
        val directGroup = parents.firstOrNull { group -> group.children.any { it.id == a.taskId } }
        if (directGroup != null) {
            selectedTaskId = a.taskId
            return@LaunchedEffect
        }

        // 2) 直接の子でない場合（= 小タスク）:
        //    親の子タスクそれぞれのサブタスクをスナップショットで確認して、
        //    該当する直上の子タスクIDをハイライト対象にする
        var highlightParentChildId: Long? = null
        outer@ for (group in parents) {
            for (child in group.children) {
                if (viewModel.hasChildren(child.id)) {
                    // Flow を一度だけ取得（LaunchedEffect内なので suspend OK）
                    val subs = viewModel.children(child.id).first()
                    if (subs.any { it.id == a.taskId }) {
                        highlightParentChildId = child.id
                        break@outer
                    }
                }
            }
        }
        selectedTaskId = highlightParentChildId
    }

    // 並び替えモード時に使用：親ごとの並び順を保持
    val reorderLists = remember(sortMode, parents) {
        mutableStateMapOf<Long, MutableList<Long>>().apply {
            parents.forEach { group ->
                put(group.parent.id, group.children.map { it.id }.toMutableList())
            }
        }
    }

    // ドラッグ中の情報
    var draggingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draggingDy by remember { mutableFloatStateOf(0f) }

    /** 並び替え結果を ViewModel に反映 */
    fun commitOrder(parentId: Long) {
        reorderLists[parentId]?.let { ordered ->
            viewModel.reorderChildren(parentId, ordered)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (val s = ui) {
                // ------- 通常一覧 -------
                TaskCenterUiState.List -> {
                    Text(
                        text = if (sortMode) "長押しして上下にドラッグすると並び替えできます"
                        else stringResource(R.string.label_select_task),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = if (sortMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = true),
                        state = listState
                    ) {
                        items(parents.size) { idx ->
                            val p = parents[idx]
                            val parentColor = p.parent.colorInt?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.primary

                            // 子タスクを id → エンティティに引けるように保持
                            val childById =
                                remember(p.children) { p.children.associateBy { it.id } }
                            // この親の現在の並び順
                            val order: MutableList<Long> =
                                reorderLists[p.parent.id] ?: p.children.map { it.id }
                                    .toMutableList()
                            reorderLists[p.parent.id] = order

                            // 子タスク行の中心座標（ドラッグ判定に利用）
                            val centers = remember(p.parent.id) { mutableStateMapOf<Long, Float>() }

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

                                    // 親行（タイトル＋メニュー）
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            p.parent.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = {
                                            menuForParentId = p.parent.id
                                            lastInteractionTime = System.currentTimeMillis()
                                        }) {
                                            Icon(
                                                Icons.Filled.Menu,
                                                contentDescription = "Parent task menu"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuForParentId == p.parent.id,
                                            onDismissRequest = {
                                                menuForParentId = null
                                                lastInteractionTime = System.currentTimeMillis()
                                            }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("タスクを追加") },
                                                onClick = {
                                                    menuForParentId = null
                                                    newChildName = ""
                                                    addChildError = null
                                                    showAddChildDialogFor = p.parent.id
                                                    lastInteractionTime = System.currentTimeMillis()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("親タスク（プロジェクト）を削除") },
                                                onClick = {
                                                    menuForParentId = null
                                                    confirmDeleteParentId = p.parent.id
                                                    confirmDeleteParentName = p.parent.name
                                                    lastInteractionTime = System.currentTimeMillis()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("色の変更") },
                                                onClick = {
                                                    menuForParentId = null
                                                    colorPickForParentId = p.parent.id
                                                    lastInteractionTime = System.currentTimeMillis()
                                                }
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // 子要素一覧
                                    order.forEach { childId ->
                                        val child = childById[childId] ?: return@forEach

                                        // ハイライト（選択中 or 実行中）
                                        val isSelected = selectedTaskId == child.id && !sortMode
                                        val bg =
                                            if (isSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                                        val border =
                                            if (isSelected) Color.Red else MaterialTheme.colorScheme.outline
                                        val textColor =
                                            if (isSelected) Color.Red else MaterialTheme.colorScheme.onSurface

                                        // ドラッグ中は zIndex と移動量を変更
                                        val isDragging = draggingTaskId == child.id
                                        val z = if (isDragging) 1f else 0f

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .border(1.dp, border, RoundedCornerShape(8.dp))
                                                .graphicsLayer {
                                                    translationY =
                                                        if (isDragging) draggingDy else 0f
                                                }
                                                .zIndex(z)
                                                .then(
                                                    if (sortMode)
                                                    // 並び替えモード：座標を記録し、長押しドラッグで上下入れ替え
                                                        Modifier
                                                            .onGloballyPositioned { coords ->
                                                                val c =
                                                                    coords.boundsInParent().center.y
                                                                centers[child.id] = c
                                                            }
                                                            .pointerInput(sortMode, order) {
                                                                detectDragGesturesAfterLongPress(
                                                                    onDragStart = {
                                                                        draggingTaskId = child.id
                                                                        draggingDy = 0f
                                                                    },
                                                                    onDrag = { change, drag ->
                                                                        change.consume()
                                                                        if (draggingTaskId != child.id) return@detectDragGesturesAfterLongPress
                                                                        draggingDy += drag.y
                                                                        val idx =
                                                                            order.indexOf(child.id)
                                                                        if (idx == -1) return@detectDragGesturesAfterLongPress
                                                                        val currCenter =
                                                                            centers[child.id]
                                                                                ?: return@detectDragGesturesAfterLongPress
                                                                        // 下へ移動
                                                                        if (draggingDy > 0 && idx < order.lastIndex) {
                                                                            val nextId =
                                                                                order[idx + 1]
                                                                            val nextCenter =
                                                                                centers[nextId]
                                                                                    ?: Float.MAX_VALUE
                                                                            if (currCenter + draggingDy > nextCenter) {
                                                                                order.removeAt(idx)
                                                                                order.add(
                                                                                    idx + 1,
                                                                                    child.id
                                                                                )
                                                                                draggingDy -= (nextCenter - currCenter)
                                                                            }
                                                                        }
                                                                        // 上へ移動
                                                                        if (draggingDy < 0 && idx > 0) {
                                                                            val prevId =
                                                                                order[idx - 1]
                                                                            val prevCenter =
                                                                                centers[prevId]
                                                                                    ?: Float.MIN_VALUE
                                                                            if (currCenter + draggingDy < prevCenter) {
                                                                                order.removeAt(idx)
                                                                                order.add(
                                                                                    idx - 1,
                                                                                    child.id
                                                                                )
                                                                                draggingDy += (currCenter - prevCenter)
                                                                            }
                                                                        }
                                                                    },
                                                                    onDragEnd = {
                                                                        draggingTaskId = null
                                                                        draggingDy = 0f
                                                                        commitOrder(p.parent.id)
                                                                    },
                                                                    onDragCancel = {
                                                                        draggingTaskId = null
                                                                        draggingDy = 0f
                                                                    }
                                                                )
                                                            }
                                                    else
                                                    // 通常モード：クリックで選択→子ありならサブタスク選択へ
                                                        Modifier.clickable {
                                                            selectedTaskId = child.id
                                                            lastInteractionTime =
                                                                System.currentTimeMillis()
                                                            scope.launch {
                                                                val has =
                                                                    viewModel.hasChildren(child.id)
                                                                if (has) {
                                                                    ui =
                                                                        TaskCenterUiState.SubtaskSelect(
                                                                            parentTitle = child.name,
                                                                            parentId = child.id,
                                                                            color = parentColor
                                                                        )
                                                                } else {
                                                                    viewModel.onTaskPressed(child.id)
                                                                }
                                                            }
                                                        }
                                                ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else bg
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // タスク名
                                                Text(
                                                    text = child.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    modifier = Modifier.weight(1f),
                                                    color = textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                // 親色のインジケーター
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
                                                Spacer(Modifier.width(8.dp))
                                                // 子タスクのメニュー（名前変更/削除）
                                                if (!sortMode) {
                                                    IconButton(onClick = {
                                                        menuForChildTaskId = child.id
                                                        renameTaskCurrentName = child.name
                                                        renameTaskText = child.name
                                                        lastInteractionTime =
                                                            System.currentTimeMillis()
                                                    }) {
                                                        Icon(
                                                            Icons.Filled.Menu,
                                                            contentDescription = "Task menu"
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = menuForChildTaskId == child.id,
                                                        onDismissRequest = {
                                                            menuForChildTaskId = null
                                                            lastInteractionTime =
                                                                System.currentTimeMillis()
                                                        }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("名前を変更") },
                                                            onClick = {
                                                                menuForChildTaskId = null
                                                                renameTaskId = child.id
                                                                renameTaskCurrentName = child.name
                                                                renameTaskText = child.name
                                                                lastInteractionTime =
                                                                    System.currentTimeMillis()
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("タスクを削除") },
                                                            onClick = {
                                                                menuForChildTaskId = null
                                                                confirmDeleteTaskId = child.id
                                                                confirmDeleteTaskName = child.name
                                                                lastInteractionTime =
                                                                    System.currentTimeMillis()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                // ------- サブタスク選択 -------
                is TaskCenterUiState.SubtaskSelect -> {
                    SubtaskPicker(
                        title = "${s.parentTitle} のサブタスク",
                        parentId = s.parentId,
                        color = s.color,
                        onBack = {
                            ui = TaskCenterUiState.List
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onPick = { pickedId ->
                            // サブタスクを開始
                            viewModel.onTaskPressed(pickedId)
                            ui = TaskCenterUiState.List
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onRenameRequest = { taskId, currentName ->
                            renameTaskId = taskId
                            renameTaskCurrentName = currentName
                            renameTaskText = currentName
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onDeleteRequest = { taskId, name ->
                            confirmDeleteTaskId = taskId
                            confirmDeleteTaskName = name
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        viewModel = viewModel
                    )
                }
            }
        }

        // ====== 各種ダイアログ ======

        // 親タスク追加（重複禁止）
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("タスクを追加") },
                text = {
                    OutlinedTextField(
                        value = newTaskName,
                        onValueChange = {
                            if (it.length <= 128) {
                                newTaskName = it
                                val name = it.trim()
                                addParentError = when {
                                    name.isEmpty() -> null
                                    viewModel.hasDuplicateParentName(name) -> "同名の親タスクが既に存在します"
                                    else -> null
                                }
                            }
                        },
                        singleLine = true,
                        isError = addParentError != null,
                        label = { Text("タスク名（1〜128文字）") },
                        supportingText = { if (addParentError != null) Text(addParentError!!) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = newTaskName.trim().take(128)
                        when {
                            name.isEmpty() -> addParentError = "1文字以上で入力してください"
                            viewModel.hasDuplicateParentName(name) -> addParentError =
                                "同名の親タスクが既に存在します"

                            else -> {
                                viewModel.addParentTask(name)
                                showAddDialog = false
                                addParentError = null
                            }
                        }
                    }) { Text("追加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("キャンセル") }
                }
            )
        }

        // 子タスク追加（同一プロジェクト内で重複禁止）
        if (showAddChildDialogFor != null) {
            AlertDialog(
                onDismissRequest = { showAddChildDialogFor = null },
                title = { Text("子タスクを追加") },
                text = {
                    OutlinedTextField(
                        value = newChildName,
                        onValueChange = {
                            if (it.length <= 128) {
                                newChildName = it
                                val pid = showAddChildDialogFor
                                val name = it.trim()
                                addChildError = when {
                                    name.isEmpty() -> null
                                    pid != null && viewModel.hasDuplicateChildName(pid, name) ->
                                        "同名のタスクがこのプロジェクトに存在します"

                                    else -> null
                                }
                            }
                        },
                        singleLine = true,
                        isError = addChildError != null,
                        label = { Text("タスク名（1〜128文字）") },
                        supportingText = { if (addChildError != null) Text(addChildError!!) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val pid = showAddChildDialogFor
                        val name = newChildName.trim().take(128)
                        when {
                            pid == null -> {}
                            name.isEmpty() -> addChildError = "1文字以上で入力してください"
                            viewModel.hasDuplicateChildName(pid, name) ->
                                addChildError = "同名のタスクがこのプロジェクトに存在します"

                            else -> {
                                viewModel.addChildTask(pid, name)
                                showAddChildDialogFor = null
                                addChildError = null
                            }
                        }
                    }) { Text("追加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddChildDialogFor = null }) { Text("キャンセル") }
                }
            )
        }

        // 親色の変更（プリセットから選択）
        if (colorPickForParentId != null) {
            AlertDialog(
                onDismissRequest = { colorPickForParentId = null },
                title = { Text("色の変更") },
                text = {
                    Row {
                        presetColors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
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

        // 親削除確認
        if (confirmDeleteParentId != null) {
            AlertDialog(
                onDismissRequest = { confirmDeleteParentId = null },
                title = { Text("削除の確認") },
                text = {
                    Text("親タスク（プロジェクト）「$confirmDeleteParentName」を削除します。子タスクも削除されます。よろしいですか？")
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = confirmDeleteParentId
                        if (id != null) viewModel.deleteParent(id)
                        confirmDeleteParentId = null
                    }) { Text("削除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteParentId = null }) { Text("キャンセル") }
                }
            )
        }

        // タスク名変更
        if (renameTaskId != null) {
            AlertDialog(
                onDismissRequest = { renameTaskId = null },
                title = { Text("名前を変更") },
                text = {
                    Column {
                        Text(
                            "現在: $renameTaskCurrentName",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameTaskText,
                            onValueChange = { if (it.length <= 128) renameTaskText = it },
                            singleLine = true,
                            label = { Text("新しい名前（1〜128文字）") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = renameTaskId
                        val name = renameTaskText.trim().take(128)
                        if (id != null && name.isNotEmpty()) {
                            viewModel.renameTask(id, name)
                        }
                        renameTaskId = null
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { renameTaskId = null }) { Text("キャンセル") }
                }
            )
        }

        // タスク削除確認
        if (confirmDeleteTaskId != null) {
            AlertDialog(
                onDismissRequest = { confirmDeleteTaskId = null },
                title = { Text("削除の確認") },
                text = { Text("タスク「$confirmDeleteTaskName」を削除します。よろしいですか？") },
                confirmButton = {
                    TextButton(onClick = {
                        val id = confirmDeleteTaskId
                        if (id != null) viewModel.deleteTask(id)
                        confirmDeleteTaskId = null
                    }) { Text("削除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteTaskId = null }) { Text("キャンセル") }
                }
            )
        }

        // 右下 FAB（親 or 子の追加）
        if (fabVisible) {
            FloatingActionButton(
                onClick = {
                    when (val s = ui) {
                        // サブタスク選択中なら子タスク追加
                        is TaskCenterUiState.SubtaskSelect -> {
                            newChildName = ""
                            addChildError = null
                            showAddChildDialogFor = s.parentId
                        }
                        // 通常は親タスク追加
                        else -> {
                            newTaskName = ""
                            addParentError = null
                            showAddDialog = true
                        }
                    }
                    lastInteractionTime = System.currentTimeMillis()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = "Add task")
            }
        }
    }
}

/**
 * サブタスク選択画面。
 * - 親の配色を右端の色チップで表示
 * - クリックで onPick に選択通知
 */
@Composable
private fun SubtaskPicker(
    title: String,
    parentId: Long,
    color: Color,
    onBack: () -> Unit,
    onPick: (Long) -> Unit,
    onRenameRequest: (Long, String) -> Unit,
    onDeleteRequest: (Long, String) -> Unit,
    viewModel: TaskViewModel
) {
    // Flow<List<...>> なので初期値を渡すオーバーロードを使用
    val subtasks by viewModel.children(parentId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSubId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 実行中変更に追従してハイライト
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    LaunchedEffect(activeUi, subtasks) {
        val aid = activeUi?.taskId
        if (aid != null && subtasks.any { it.id == aid }) {
            selectedSubId = aid
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ヘッダ（戻る）
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

        // サブタスク一覧
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
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 親色（インジケータ）
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(color, RoundedCornerShape(3.dp))
                                .border(1.dp, border, RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(8.dp))

                        // 個別メニュー
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Subtask menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("名前を変更") },
                                onClick = {
                                    menuExpanded = false
                                    onRenameRequest(t.id, t.name)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("削除") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteRequest(t.id, t.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/* =========================
   左ページ：マルチ日タイムライン（Googleカレンダー週表示風）
   ========================= */

/**
 * タイムテーブル。履歴＋進行中のブロックを日別に描画。
 * @param onAddRecord 右下 FAB（過去記録の追加）押下で呼ばれる
 */
@Composable
private fun TimelineScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier,
    onAddRecord: () -> Unit
) {
    // 初回で履歴の先頭ページを確保
    LaunchedEffect(Unit) { viewModel.ensureHistoryFirstPage() }

    // 履歴・実行中・タスク色参照
    val history by viewModel.historyItems.collectAsStateWithLifecycle()
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    val parents by viewModel.parents.collectAsStateWithLifecycle()

    // 表示日数と基準日のスクロール位置（ページ送り）
    var dayCount by rememberSaveable { mutableIntStateOf(3) }     // 3/5/7 切替
    var anchorOffset by rememberSaveable { mutableIntStateOf(0) } // 0=今日起点、±dayCountでページ送り
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 表示期間（startMs〜endMs）
    val startOfToday = remember(nowMs) { midnightOf(nowMs) }
    val startMs = remember(startOfToday, anchorOffset) {
        startOfToday + anchorOffset * 24L * 60L * 60L * 1000L
    }
    val endMs = remember(startMs, dayCount) { startMs + dayCount * 24L * 60L * 60L * 1000L }

    // 期間ヘッダの表示
    val f = remember { SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault()) }
    val headerLabel = remember(startMs, endMs, dayCount) {
        val first = f.format(Date(startMs))
        val last = f.format(Date(endMs - 1))
        if (dayCount == 1) first else "$first 〜 $last"
    }

    // テーマ色（タスク色が未設定の時に使用）
    val defaultTaskColor = MaterialTheme.colorScheme.onSurface

    // 子タスク ID から親色を引く簡易関数
    fun colorForTask(taskId: Long, defaultColor: Color): Color {
        val group = parents.firstOrNull { g -> g.children.any { it.id == taskId } }
        return group?.parent?.colorInt?.let { Color(it) } ?: defaultColor
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ヘッダ（← →、今日へ、日数切替）
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { anchorOffset -= dayCount }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "prev")
                }
                Text(
                    headerLabel,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (anchorOffset != 0) {
                    TextButton(onClick = { anchorOffset = 0 }) { Text("今日へ") }
                }
                IconButton(onClick = { anchorOffset += dayCount }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "next")
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegButton("3日", active = dayCount == 3) { dayCount = 3 }
                SegButton("5日", active = dayCount == 5) { dayCount = 5 }
                SegButton("7日", active = dayCount == 7) { dayCount = 7 }
                Spacer(Modifier.weight(1f))
                // 「更新」：現在時刻を更新し、履歴をリロード
                OutlinedButton(onClick = {
                    nowMs = System.currentTimeMillis()
                    viewModel.reloadHistory()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "refresh")
                    Spacer(Modifier.width(8.dp)); Text("更新")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 履歴 + 進行中を各日に分割（はみ出しは日単位にクランプ）
            val blocksByDay: List<List<TimelineBlock>> =
                remember(history, activeUi, parents, startMs, endMs, dayCount, nowMs) {
                    val days = List(dayCount) { mutableListOf<TimelineBlock>() }

                    // 指定区間を日区切りで分割して追加
                    fun addClampedBlock(s: Long, e: Long, name: String, color: Color) {
                        if (e <= startMs || s >= endMs) return
                        repeat(dayCount) { i ->
                            val dStart = startMs + i * 24L * 60L * 60L * 1000L
                            val dEnd = dStart + 24L * 60L * 60L * 1000L
                            val cs = maxOf(s, dStart)
                            val ce = minOf(e, dEnd)
                            if (ce > cs) days[i].add(TimelineBlock(cs, ce, name, color))
                        }
                    }

                    // 履歴を集計
                    history.forEach { h ->
                        val itemStart = h.pressedAt
                        val itemEnd = h.pressedAt + max(0L, h.durationMillis)
                        addClampedBlock(itemStart, itemEnd, h.taskName, h.color)
                    }

                    // 進行中（開始〜今）を仮ブロックとして描画
                    activeUi?.let { a ->
                        val start = a.startEpochMs
                        if (start > 0L) {
                            val color = colorForTask(a.taskId, defaultTaskColor)
                            val end = nowMs
                            addClampedBlock(start, end, a.taskName, color)
                        }
                    }

                    // 各日で開始時刻順に並べる
                    days.map { it.sortedBy { b -> b.startMs } }
                }

            // タイムライン描画
            MultiDayTimeline(
                startMs = startMs,
                dayCount = dayCount,
                blocksByDay = blocksByDay
            )
        }

        // ✅ タイムテーブル画面用の「＋」ボタン（タスク記録追加画面へ遷移）
        FloatingActionButton(
            onClick = onAddRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.AddCircle, contentDescription = "Add record")
        }
    }
}

/** タイムライン上の 1 ブロック（開始・終了・表示名・色） */
private data class TimelineBlock(
    val startMs: Long,
    val endMs: Long,
    val name: String,
    val color: Color
)

/**
 * 日数分のカラムを並べたタイムライン本体（時間グリッド＋ブロック描画）
 */
@Composable
private fun MultiDayTimeline(
    startMs: Long,
    dayCount: Int,
    blocksByDay: List<List<TimelineBlock>>,
    modifier: Modifier = Modifier
) {
    val HOUR_HEIGHT = 48.dp
    val GUTTER = 40.dp
    val totalHeight = HOUR_HEIGHT * 24
    val dateFmt = remember { SimpleDateFormat("M/d (E)", Locale.getDefault()) }

    // 縦スクロール（全時間分の高さ）
    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .height(totalHeight + 0.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        // 左：時間ガター（00〜24）
        Column(
            modifier = Modifier
                .width(GUTTER)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            repeat(25) { h ->
                Box(
                    modifier = Modifier
                        .height(HOUR_HEIGHT)
                        .width(GUTTER),
                    contentAlignment = Alignment.TopEnd
                ) {
                    if (h in 0..23) {
                        Text(
                            text = String.format("%02d", h),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        }

        // 右：日カラム群
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            repeat(dayCount) { i ->
                val dayStart = startMs + i * 24L * 60L * 60L * 1000L
                val label = dateFmt.format(Date(dayStart))
                DayColumn(
                    label = label,
                    hourHeight = HOUR_HEIGHT,
                    blocks = blocksByDay.getOrNull(i).orEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }
    }
}

/**
 * 1 日分のカラム（背景グリッド＋ブロック群）
 */
@Composable
private fun DayColumn(
    label: String,
    hourHeight: Dp,
    blocks: List<TimelineBlock>,
    modifier: Modifier = Modifier
) {
    val hourHeightPx = with(LocalDensity.current) { hourHeight.toPx() }

    // 使用色を先に抜き出しておく（Recompose 範囲の最小化）
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val outline40 = outline.copy(alpha = 0.4f)
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier) {
        // 背景グリッド（時間線）
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val step = h / 24f
            // 日付ヘッダ帯
            drawRect(
                color = surfaceVariant,
                size = Size(w, hourHeightPx * 0.75f)
            )
            // 時間線（1h 毎、6h 毎は太線）
            repeat(25) { i ->
                val y = step * i
                drawLine(
                    color = outline40,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (i % 6 == 0) 2f else 1f
                )
            }
        }

        // 日付ラベル
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hourHeight * 0.75f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }

        // ブロック群（開始分→オフセット、長さ→高さ）
        blocks.forEach { b ->
            val startMin = minutesOfDay(b.startMs)
            val endMin = minutesOfDay(b.endMs)
            val top = hourHeight * (startMin / 60f)
            val height = hourHeight * ((endMin - startMin).coerceAtLeast(1) / 60f)

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .offset(y = top)
                    .fillMaxWidth()
                    .height(height)
                    .background(b.color.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    .border(1.dp, outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    b.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onSurface
                )
            }
        }
    }
}

/** 与えられた時刻（ms）を 0:00 に切り下げたミリ秒にする */
private fun midnightOf(timeMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** 1 日の中での分位置（例: 13:30 -> 810）を返す */
private fun minutesOfDay(ms: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

/* =========================
   右ページ：期間集計グラフ
   ========================= */

/**
 * 指定期間（7/30/90 日）でタスク時間を集計し、棒グラフ＋リストで表示。
 */
@Composable
private fun StatsScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    // 初回で履歴の先頭ページを確保
    LaunchedEffect(Unit) { viewModel.ensureHistoryFirstPage() }

    // 履歴・実行中・タスク色参照
    val history by viewModel.historyItems.collectAsStateWithLifecycle()
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    val parents by viewModel.parents.collectAsStateWithLifecycle()

    // 期間選択と「今」の更新
    var days by rememberSaveable { mutableIntStateOf(7) } // 7/30/90
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 再読み込み押下で現在時刻と履歴を更新
    val onRefresh = {
        nowMs = System.currentTimeMillis()
        viewModel.reloadHistory()
    }

    // 集計の下限時刻（この時刻以降のものを合計）
    val cutoff = remember(nowMs, days) { nowMs - days * 24L * 60L * 60L * 1000L }

    // テーマ色（タスク色が未設定の時に使用）
    val defaultTaskColor = MaterialTheme.colorScheme.primary

    // 子タスク ID から親色を引く簡易関数
    fun colorForTask(taskId: Long, defaultColor: Color): Color {
        val group = parents.firstOrNull { g -> g.children.any { it.id == taskId } }
        return group?.parent?.colorInt?.let { Color(it) } ?: defaultColor
    }

    // 単純な集計データの入れ物
    data class Agg(val taskId: Long, val name: String, val color: Color, val totalMs: Long)

    // 履歴と進行中を合算して集計
    val aggregated: List<Agg> = remember(history, activeUi, parents, cutoff, nowMs) {
        val map = LinkedHashMap<Long, Agg>()

        fun addMs(taskId: Long, name: String, color: Color, ms: Long) {
            if (ms <= 0L) return
            val cur = map[taskId]
            if (cur == null) map[taskId] = Agg(taskId, name, color, ms)
            else map[taskId] = cur.copy(totalMs = cur.totalMs + ms)
        }

        // 履歴
        history.filter { it.pressedAt >= cutoff }.forEach { row ->
            addMs(row.taskId, row.taskName, row.color, max(0L, row.durationMillis))
        }

        // 進行中（cutoff 以降）を加算
        activeUi?.let { a ->
            val s = maxOf(a.startEpochMs, cutoff)
            val e = nowMs
            addMs(a.taskId, a.taskName, colorForTask(a.taskId, defaultTaskColor), max(0L, e - s))
        }

        map.values.sortedByDescending { it.totalMs }.toList()
    }

    // 合計（分母 0 回避のため 1 に丸め）
    val totalAll = aggregated.sumOf { it.totalMs }.coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("集計", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        // 期間切替と更新ボタン
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegButton("7日", active = days == 7) { days = 7 }
            SegButton("30日", active = days == 30) { days = 30 }
            SegButton("90日", active = days == 90) { days = 90 }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "refresh")
                Spacer(Modifier.width(8.dp))
                Text("更新")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "期間: 過去${days}日 / 合計 ${(totalAll / 1000 / 60)} 分",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // 上位8件をグラフに
        if (aggregated.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("データがありません。記録するとここに表示されます。")
                }
            }
        } else {
            val top = aggregated.take(8)
            BarChart(
                entries = top.map { it.name to it.totalMs.toFloat() },
                colors = top.map { it.color },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
            )

            Spacer(Modifier.height(12.dp))

            // 明細
            Column {
                top.forEach { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(a.color, RoundedCornerShape(3.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            a.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val minutes = (a.totalMs / 1000 / 60).toInt()
                        val ratio = (a.totalMs.toDouble() / totalAll.toDouble() * 100.0)
                        Text(
                            "${minutes}分  (${ratio.roundToInt()}%)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** セグメント風ボタン（選択時はトーナル、非選択はアウトライン） */
@Composable
private fun SegButton(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(999.dp)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(999.dp)) {
            Text(label)
        }
    }
}

/**
 * シンプルな横向き棒グラフ（Compose Canvas）
 * @param entries （ラベル, 値）リスト
 * @param colors 棒の色リスト（entries と同順）
 */
@Composable
private fun BarChart(
    entries: List<Pair<String, Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    barCornerRadius: Float = 16f
) {
    val maxVal = (entries.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
    LocalDensity.current // 現在の Density を触っておく（Lint 抑制）

    Column(modifier = modifier) {
        entries.forEachIndexed { index, (label, value) ->
            val ratio = value / maxVal
            val barColor = colors.getOrNull(index) ?: MaterialTheme.colorScheme.primary

            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                // 背景トラック
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(999.dp)
                        )
                ) {
                    // 実バー
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(22.dp)
                    ) {
                        drawRoundRect(
                            color = barColor,
                            cornerRadius = CornerRadius(barCornerRadius, barCornerRadius)
                        )
                    }
                }
            }
        }
    }
}

/* ------- History（100件ページング・末尾付近で追加ロード） ------- */

/**
 * 履歴一覧。末尾付近までスクロールしたら次ページを読み込む。
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier, viewModel: TaskViewModel) {
    val items by viewModel.historyItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.historyLoading.collectAsStateWithLifecycle()
    val endReached by viewModel.historyEndReached.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 画面に表示された最後の index を見て、末尾近くなら追加ロード
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

        // 右下に「再読み込み」ボタン
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

/** 履歴 1 行分のカード表示（開始日時・色・所要時間） */
@SuppressLint("DefaultLocale")
@Composable
private fun HistoryRowCard(row: HistoryItemUi) {
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
            // 色インジケータ
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

/**
 * ページごとに内容が変わる BottomBar。
 * - 左ページ：左アクションのみ
 * - 中央ページ：並び替えボタン
 * - 右ページ：右アクションのみ
 */
@Composable
fun AdaptiveBottomBar(
    currentPage: Int,
    onLeftAction: () -> Unit = {},
    onSortAction: () -> Unit = {},
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
                    IconButton(onClick = onSortAction) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
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

/** 何も表示するものがない時のプレースホルダ */
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

/* =========================
   タスク追加画面（プレースホルダ）
   ========================= */

/**
 * タスク記録を追加するための画面（現状はプレースホルダ）。
 * - 戻る（←）で直前の画面へ復帰。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タスク記録を追加") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 既存アイコンセット内で流用
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("ここに過去記録を追加するフォームを実装します。")
        }
    }
}
