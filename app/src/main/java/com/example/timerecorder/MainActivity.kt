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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timerecorder.ui.HistoryItemUi
import com.example.timerecorder.ui.TaskViewModel
import com.example.timerecorder.ui.UiEvent
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
                        composable(Routes.Root) {
                            AppRoot(
                                viewModel = taskViewModel,
                                onNavigateAddTask = { navController.navigate(Routes.AddTask) },
                                onNavigateEditTask = { id, s, e ->
                                    navController.navigate("${Routes.AddTask}?initialTaskId=$id&initialStartMs=$s&initialEndMs=$e")
                                }
                            )
                        }

                        composable(
                            route = "${Routes.AddTask}?initialTaskId={initialTaskId}&initialStartMs={initialStartMs}&initialEndMs={initialEndMs}",
                            arguments = listOf(
                                navArgument("initialTaskId") {
                                    type = NavType.LongType
                                    defaultValue = -1L
                                },
                                navArgument("initialStartMs") {
                                    type = NavType.LongType
                                    defaultValue = -1L
                                },
                                navArgument("initialEndMs") {
                                    type = NavType.LongType
                                    defaultValue = -1L
                                }
                            )
                        ) { backStackEntry ->
                            val initTask = backStackEntry.arguments?.getLong("initialTaskId")
                                ?.takeIf { it >= 0L }
                            val initStart = backStackEntry.arguments?.getLong("initialStartMs")
                                ?.takeIf { it >= 0L }
                            val initEnd = backStackEntry.arguments?.getLong("initialEndMs")
                                ?.takeIf { it >= 0L }
                            AddTaskScreen(
                                viewModel = taskViewModel,
                                onBack = { navController.popBackStack() },
                                initialTaskId = initTask,
                                initialStartMs = initStart,
                                initialEndMs = initEnd
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
    onNavigateAddTask: () -> Unit,
    onNavigateEditTask: (Long, Long, Long) -> Unit
) {
    // ドロワーの開閉状態
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 中央ページ（タスク一覧）を起点に、左右へページング
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // タイムラインで選択中の taskId を保持（null で未選択）
    var selectedTimelineTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTimelineStartMs by rememberSaveable { mutableLongStateOf(-1L) }
    var selectedTimelineEndMs by rememberSaveable { mutableLongStateOf(-1L) }
    var selectedTimelineEventId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showDeleteEventDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteEventId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showImportTsvDialog by rememberSaveable { mutableStateOf(false) }
    var importTsvText by rememberSaveable { mutableStateOf("") }
    var clearExistingEvents by rememberSaveable { mutableStateOf(false) }
    var createMissing by rememberSaveable { mutableStateOf(true) }

    // ViewModel のワンショット UI イベントを収集して Snackbar を表示
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { ev ->
            when (ev) {
                is UiEvent.ShowMessage -> snackbar.showSnackbar(ev.message)
            }
        }
    }

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

    var showEdit by rememberSaveable { mutableStateOf(false) }

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

                                "export_tsv" -> {
                                    scope.launch {
                                        try {
                                            val text = viewModel.exportEventsTsv()
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/tab-separated-values"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                                putExtra(
                                                    Intent.EXTRA_SUBJECT,
                                                    "TimeRecorder Events Export (TSV)"
                                                )
                                            }
                                            ctx.startActivity(
                                                Intent.createChooser(
                                                    send,
                                                    "Export records as TSV"
                                                )
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                ctx,
                                                "Export TSV 失敗: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }

                                "import_tsv" -> {
                                    showImportTsvDialog = true
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
                        if (screen == AppScreen.Main) {
                            // 左ページでは選択時のみ、他ページは常に表示
                            val showBar = when (currentPage) {
                                0 -> selectedTimelineTaskId != null
                                else -> true
                            }
                            if (showBar) {
                                AdaptiveBottomBar(
                                    currentPage = currentPage,
                                    timelineSelected = (selectedTimelineTaskId != null),
                                    onActionA = {
                                        val eid = selectedTimelineEventId
                                        val s = selectedTimelineStartMs
                                        val e = selectedTimelineEndMs
                                        if (eid == null || eid <= 0L || s < 0L || e <= s) {
                                            // active中など eventIdが無い/不正なら何もしない
                                            scope.launch { snackbar.showSnackbar("分割できない記録です") }
                                            return@AdaptiveBottomBar
                                        }

                                        // 初期値: ブロック中央
                                        val mid = (s + e) / 2
                                        val calInit =
                                            Calendar.getInstance().apply { timeInMillis = mid }
                                        val initHour = calInit.get(Calendar.HOUR_OF_DAY)
                                        val initMin = calInit.get(Calendar.MINUTE)

                                        android.app.TimePickerDialog(
                                            ctx,
                                            { _, hh, mm ->
                                                // 分割日は選択ブロックの開始日（ブロックは1日内にクランプ済み）
                                                val base = Calendar.getInstance().apply {
                                                    timeInMillis = s
                                                    set(Calendar.HOUR_OF_DAY, hh)
                                                    set(Calendar.MINUTE, mm)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }.timeInMillis

                                                if (base <= s || base >= e) {
                                                    scope.launch { snackbar.showSnackbar("分割時刻は選択中の区間内で指定してください") }
                                                } else {
                                                    viewModel.splitEventAt(eid, base)
                                                    // 選択は一旦クリア
                                                    selectedTimelineEventId = null
                                                    selectedTimelineTaskId = null
                                                }
                                            },
                                            initHour, initMin, true
                                        ).show()
                                    },
                                    onActionB = {
                                        val id = selectedTimelineTaskId
                                        if (id != null && selectedTimelineStartMs >= 0 && selectedTimelineEndMs >= 0) {
                                            onNavigateEditTask(
                                                id,
                                                selectedTimelineStartMs,
                                                selectedTimelineEndMs
                                            )
                                        }
                                    },
                                    onLeftAction = { /* 予備 */ },
                                    onSortAction = { sortMode = true },
                                    onRightAction = { /* 予備 */ },
                                    onDeleteAction = {
                                        val eid = selectedTimelineEventId
                                        val s = selectedTimelineStartMs
                                        val e = selectedTimelineEndMs
                                        if (eid == null || eid <= 0L || s < 0L || e <= s) {
                                            scope.launch { snackbar.showSnackbar("削除できない記録です") }
                                            return@AdaptiveBottomBar
                                        }
                                        pendingDeleteEventId = eid
                                        showDeleteEventDialog = true
                                    }

                                )
                            }
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
                            onNavigateAddTask = onNavigateAddTask,
                            selectedTimelineTaskId = selectedTimelineTaskId,
                            selectedTimelineEventId = selectedTimelineEventId,
                            onTimelineTaskSelected = { tid ->
                                selectedTimelineTaskId = tid
                                if (tid == null) {
                                    selectedTimelineEventId = null
                                    selectedTimelineStartMs = -1L
                                    selectedTimelineEndMs = -1L
                                }
                            },
                            onTimelineEventSelected = { eid ->                                  // ★ 追加
                                selectedTimelineEventId = eid
                                if (eid == null) {
                                    selectedTimelineTaskId = null
                                    selectedTimelineStartMs = -1L
                                    selectedTimelineEndMs = -1L
                                }
                            },
                            onTimelineBlockSelected = { eid, tid, s, e ->
                                selectedTimelineEventId = eid
                                selectedTimelineTaskId = tid
                                selectedTimelineStartMs = s
                                selectedTimelineEndMs = e
                            }
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
                    if (showImportTsvDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportTsvDialog = false },
                            title = { Text("Import (TSV)") },
                            text = {
                                Column {
                                    Text(
                                        "次の形式のTSVを貼り付けてください。\n" +
                                                "event_name\t task_name\t subtask_name\t start_epoch_ms\t end_epoch_ms\t duration_ms\t start_iso\t end_iso",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = importTsvText,
                                        onValueChange = { importTsvText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp),
                                        placeholder = {
                                            Text(
                                                "WASTE\ttest\tda\t1757689200000\t1757692800000\t3600000\t2025-09-13T00:00:00+09:00\t2025-09-13T01:00:00+09:00"
                                            )
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = clearExistingEvents,
                                            onCheckedChange = { clearExistingEvents = it })
                                        Text("既存の記録を全て削除してから取り込み")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = createMissing,
                                            onCheckedChange = { createMissing = it })
                                        Text("存在しないプロジェクト/タスク/サブタスクを自動作成")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            val count = viewModel.importEventsTsv(
                                                tsv = importTsvText,
                                                clearExisting = clearExistingEvents,
                                                createMissing = createMissing
                                            )
                                            showImportTsvDialog = false
                                            importTsvText = ""
                                            snackbar.showSnackbar("TSV Import 完了（$count 件）")
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
                                TextButton(onClick = { showImportTsvDialog = false }) {
                                    Text("キャンセル")
                                }
                            }
                        )
                    }
                    if (showDeleteEventDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                pendingDeleteEventId = null
                                showDeleteEventDialog = false
                            },
                            title = { Text("記録を削除") },
                            text = { Text("選択中の記録を削除します。よろしいですか？") },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingDeleteEventId?.let { eid ->
                                        // ViewModel 側の削除 API（名称はプロジェクトに合わせて）
                                        viewModel.deleteEvent(eid)
                                    }
                                    // 選択解除
                                    selectedTimelineEventId = null
                                    selectedTimelineTaskId = null
                                    selectedTimelineStartMs = -1L
                                    selectedTimelineEndMs = -1L
                                    pendingDeleteEventId = null
                                    showDeleteEventDialog = false
                                }) { Text("削除") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    pendingDeleteEventId = null
                                    showDeleteEventDialog = false
                                }) { Text("キャンセル") }
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
        Spacer(Modifier.height(8.dp))
        Text(
            "Manage Data",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        NavigationDrawerItem(
            label = { Text("Export (TSV)") },
            selected = false,
            onClick = { onItemSelected("export_tsv") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Import (TSV)") },
            selected = false,
            onClick = { onItemSelected("import_tsv") },
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
@Composable
fun MainPager(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    viewModel: TaskViewModel,
    sortMode: Boolean,
    onNavigateAddTask: () -> Unit,
    selectedTimelineTaskId: Long?,
    selectedTimelineEventId: Long?,
    onTimelineTaskSelected: (Long?) -> Unit,
    onTimelineEventSelected: (Long?) -> Unit,
    onTimelineBlockSelected: (Long, Long, Long, Long) -> Unit
) {
    HorizontalPager(state = pagerState, modifier = modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> TimelineScreen(
                viewModel = viewModel,
                onAddRecord = onNavigateAddTask,
                selectedTaskId = selectedTimelineTaskId,
                onSelectTask = onTimelineTaskSelected,
                selectedEventId = selectedTimelineEventId,
                onSelectEvent = onTimelineEventSelected,
                onSelectBlock = onTimelineBlockSelected
            )

            1 -> DBTaskScreen(viewModel = viewModel, sortMode = sortMode)
            2 -> StatsScreen(viewModel = viewModel)
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
    val ctx = LocalContext.current   // Toast などで使う場合に備え。未使用でもビルドは可。
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var ui by remember { mutableStateOf<TaskCenterUiState>(TaskCenterUiState.List) }
    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newTaskName by rememberSaveable { mutableStateOf("") }
    var addParentError by rememberSaveable { mutableStateOf<String?>(null) }

    var menuForParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddChildDialogFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var newChildName by rememberSaveable { mutableStateOf("") }
    var addChildError by rememberSaveable { mutableStateOf<String?>(null) }

    var menuForChildTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTaskCurrentName by rememberSaveable { mutableStateOf("") }
    var renameTaskText by rememberSaveable { mutableStateOf("") }
    var confirmDeleteTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteTaskName by rememberSaveable { mutableStateOf("") }

    var colorPickForParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteParentName by rememberSaveable { mutableStateOf("") }

    val presetColors =
        listOf(0xFFEF9A9A, 0xFFA5D6A7, 0xFF90CAF9, 0xFFFFF59D, 0xFFCE93D8).map { Color(it) }

    var fabVisible by rememberSaveable { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastInteractionTime) {
        fabVisible = true
        delay(5000)
        fabVisible = false
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { lastInteractionTime = System.currentTimeMillis() }
    }

    var showRgbPicker by rememberSaveable { mutableStateOf(false) }
    var hexInput by rememberSaveable { mutableStateOf("") }

    // 実行中タスクに合わせてハイライトを更新（サブ状態はリセット）
    LaunchedEffect(activeUi, parents) {
        selectedTaskId = null
        ui = TaskCenterUiState.List

        val a = activeUi ?: return@LaunchedEffect

        val directGroup = parents.firstOrNull { group -> group.children.any { it.id == a.taskId } }
        if (directGroup != null) {
            selectedTaskId = a.taskId
            return@LaunchedEffect
        }

        var highlightParentChildId: Long? = null
        outer@ for (group in parents) {
            for (child in group.children) {
                if (viewModel.hasChildren(child.id)) {
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

    val reorderLists = remember(sortMode, parents) {
        mutableStateMapOf<Long, MutableList<Long>>().apply {
            parents.forEach { group ->
                put(group.parent.id, group.children.map { it.id }.toMutableList())
            }
        }
    }

    var draggingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draggingDy by remember { mutableFloatStateOf(0f) }

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

                            val childById =
                                remember(p.children) { p.children.associateBy { it.id } }
                            val order: MutableList<Long> =
                                reorderLists[p.parent.id] ?: p.children.map { it.id }
                                    .toMutableList()
                            reorderLists[p.parent.id] = order

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

                                        val isSelected = selectedTaskId == child.id && !sortMode
                                        val bg =
                                            if (isSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                                        val border =
                                            if (isSelected) Color.Red else MaterialTheme.colorScheme.outline
                                        val textColor =
                                            if (isSelected) Color.Red else MaterialTheme.colorScheme.onSurface

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
                                                        Modifier.clickable {
                                                            selectedTaskId = child.id
                                                            lastInteractionTime =
                                                                System.currentTimeMillis()
                                                            val scopeLocal = this
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
                                                                    // ▼ ここは結果判定せず、ViewModel に任せる
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
                                                Text(
                                                    text = child.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    modifier = Modifier.weight(1f),
                                                    color = textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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
                                                Spacer(Modifier.width(8.dp))
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
                                                            text = { Text("サブタスクを編集") },
                                                            onClick = {
                                                                menuForChildTaskId = null
                                                                ui =
                                                                    TaskCenterUiState.SubtaskSelect(
                                                                        parentTitle = child.name,
                                                                        parentId = child.id,
                                                                        color = parentColor
                                                                    )
                                                                lastInteractionTime =
                                                                    System.currentTimeMillis()
                                                            }
                                                        )

                                                        // サブタスク追加
                                                        DropdownMenuItem(
                                                            text = { Text("サブタスクを追加") },
                                                            onClick = {
                                                                menuForChildTaskId = null
                                                                newChildName = ""
                                                                addChildError = null
                                                                showAddChildDialogFor = child.id
                                                                lastInteractionTime =
                                                                    System.currentTimeMillis()
                                                            }
                                                        )
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

                is TaskCenterUiState.SubtaskSelect -> {
                    // 親タスク（＝サブタスクの親となる子タスク）の最新名と色を parents から都度引く
                    val liveChild = remember(s.parentId, parents) {
                        parents.firstOrNull { g -> g.children.any { it.id == s.parentId } }
                            ?.children?.firstOrNull { it.id == s.parentId }
                    }
                    val liveParentColor = remember(s.parentId, parents) {
                        parents.firstOrNull { g -> g.children.any { it.id == s.parentId } }
                            ?.parent?.colorInt?.let { Color(it) }
                    }
                    val liveTitle = "${liveChild?.name ?: s.parentTitle} のサブタスク"
                    val colorForUi = liveParentColor ?: s.color

                    SubtaskPicker(
                        title = liveTitle,
                        parentId = s.parentId,
                        color = colorForUi,
                        onBack = {
                            ui = TaskCenterUiState.List
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onPick = { pickedId ->
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

        if (colorPickForParentId != null) {
            // 初期色（プロジェクト現在色 or テーマ色）
            val themePrimary = MaterialTheme.colorScheme.primary

            val initColor = remember(colorPickForParentId, parents, themePrimary) {
                parents.firstOrNull { it.parent.id == colorPickForParentId }
                    ?.parent?.colorInt?.let { Color(it) }
                    ?: themePrimary
            }

            LaunchedEffect(colorPickForParentId) {
                hexInput = colorToHex(initColor, withAlpha = false)
            }
            val parsedHex = remember(hexInput) { parseHexColor(hexInput) }
            val hexError = remember(parsedHex, hexInput) {
                if (hexInput.isBlank()) null
                else if (parsedHex == null) "形式: #RRGGBB または #AARRGGBB"
                else null
            }

            AlertDialog(
                onDismissRequest = { colorPickForParentId = null },
                title = { Text("色の変更") },
                text = {
                    Column {
                        // 1) プリセット
                        Text("プリセット", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
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

                        Spacer(Modifier.height(16.dp))

                        // 2) HEX入力
                        Text("HEX 指定", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { hexInput = it.uppercase() },
                                singleLine = true,
                                isError = hexError != null,
                                label = { Text("HEX (#RRGGBB / #AARRGGBB)") },
                                supportingText = { if (hexError != null) Text(hexError!!) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            // プレビュー
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(parsedHex ?: initColor, RoundedCornerShape(6.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(6.dp)
                                    )
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val id = colorPickForParentId ?: return@OutlinedButton
                                    val c = parsedHex ?: return@OutlinedButton
                                    viewModel.changeParentColor(id, c)
                                    colorPickForParentId = null
                                },
                                enabled = parsedHex != null
                            ) { Text("HEXを適用") }

                            FilledTonalButton(onClick = { showRgbPicker = true }) {
                                Text("カラーピッカー…")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { colorPickForParentId = null }) { Text("閉じる") }
                }
            )

            // 3) カラーピッカー（RGB/Alphaスライダ）
            if (showRgbPicker) {
                RgbColorPickerDialog(
                    initial = parsedHex ?: initColor,
                    onDismiss = { showRgbPicker = false },
                    onConfirm = { picked ->
                        val id = colorPickForParentId
                        if (id != null) {
                            viewModel.changeParentColor(id, picked)
                            colorPickForParentId = null
                        }
                        showRgbPicker = false
                    }
                )
            }
        }

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

        if (fabVisible) {
            FloatingActionButton(
                onClick = {
                    when (val s = ui) {
                        is TaskCenterUiState.SubtaskSelect -> {
                            newChildName = ""
                            addChildError = null
                            showAddChildDialogFor = s.parentId
                        }

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
    val subtasks by viewModel.children(parentId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSubId by rememberSaveable { mutableStateOf<Long?>(null) }

    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    LaunchedEffect(activeUi, subtasks) {
        val aid = activeUi?.taskId
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
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(color, RoundedCornerShape(3.dp))
                                .border(1.dp, border, RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(8.dp))

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

@Composable
private fun TimelineScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier,
    onAddRecord: () -> Unit,
    selectedTaskId: Long?,
    onSelectTask: (Long?) -> Unit,
    selectedEventId: Long?,
    onSelectEvent: (Long?) -> Unit,
    onSelectBlock: (Long, Long, Long, Long) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.ensureHistoryFirstPage() }

    val history by viewModel.historyItems.collectAsStateWithLifecycle()
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    val parents by viewModel.parents.collectAsStateWithLifecycle()

    var dayCount by rememberSaveable { mutableIntStateOf(3) }
    var anchorOffset by rememberSaveable { mutableIntStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val startOfToday = remember(nowMs) { midnightOf(nowMs) }
    val startMs = remember(startOfToday, anchorOffset) {
        startOfToday + anchorOffset * 24L * 60L * 60L * 1000L
    }
    val endMs = remember(startMs, dayCount) { startMs + dayCount * 24L * 60L * 60L * 1000L }

    val f = remember { SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault()) }
    val headerLabel = remember(startMs, endMs, dayCount) {
        val first = f.format(Date(startMs))
        val last = f.format(Date(endMs - 1))
        if (dayCount == 1) first else "$first 〜 $last"
    }

    val defaultTaskColor = MaterialTheme.colorScheme.onSurface

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
                OutlinedButton(onClick = {
                    nowMs = System.currentTimeMillis()
                    viewModel.reloadHistory()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "refresh")
                    Spacer(Modifier.width(8.dp)); Text("更新")
                }
            }

            Spacer(Modifier.height(8.dp))

            val blocksByDay: List<List<TimelineBlock>> =
                remember(history, activeUi, parents, startMs, endMs, dayCount, nowMs) {
                    val days = List(dayCount) { mutableListOf<TimelineBlock>() }

                    fun addClampedBlock(
                        eventId: Long,
                        s: Long,
                        e: Long,
                        name: String,
                        color: Color,
                        taskId: Long
                    ) {
                        if (e <= startMs || s >= endMs) return
                        repeat(dayCount) { i ->
                            val dStart = startMs + i * 24L * 60L * 60L * 1000L
                            val dEnd = dStart + 24L * 60L * 60L * 1000L
                            val cs = maxOf(s, dStart)
                            val ce = minOf(e, dEnd)
                            if (ce > cs) days[i].add(
                                TimelineBlock(
                                    eventId,
                                    cs,
                                    ce,
                                    name,
                                    color,
                                    taskId
                                )
                            )
                        }
                    }

                    history.forEach { h ->
                        val itemStart = h.pressedAt
                        val itemEnd = h.pressedAt + max(0L, h.durationMillis)
                        addClampedBlock(
                            eventId = h.id,          // ★ ここでeventIdを渡す
                            s = itemStart,
                            e = itemEnd,
                            name = h.taskName,
                            color = h.color,
                            taskId = h.taskId
                        )
                    }

                    activeUi?.let { a ->
                        val start = a.startEpochMs
                        if (start > 0L) {
                            val color = colorForTask(a.taskId, defaultTaskColor)
                            val end = nowMs
                            addClampedBlock(
                                eventId = -1L, // ★ 分割対象外
                                s = start, e = end,
                                name = a.taskName, color = color, taskId = a.taskId
                            )
                        }
                    }

                    days.map { it.sortedBy { b -> b.startMs } }
                }

            MultiDayTimeline(
                startMs = startMs,
                dayCount = dayCount,
                blocksByDay = blocksByDay,
                selectedTaskId = selectedTaskId,
                selectedEventId = selectedEventId,
                onSelectEvent = onSelectEvent,
                onSelectTask = onSelectTask,
                onSelectBlock = onSelectBlock
            )
        }

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

private data class TimelineBlock(
    val eventId: Long,
    val startMs: Long,
    val endMs: Long,
    val name: String,
    val color: Color,
    val taskId: Long
)

@Composable
private fun MultiDayTimeline(
    startMs: Long,
    dayCount: Int,
    blocksByDay: List<List<TimelineBlock>>,
    modifier: Modifier = Modifier,
    selectedTaskId: Long? = null,
    selectedEventId: Long? = null,
    onSelectEvent: (Long?) -> Unit = {},
    onSelectTask: (Long?) -> Unit = {},
    onSelectBlock: (Long, Long, Long, Long) -> Unit = { _: Long, _: Long, _: Long, _: Long -> }
) {
    val HOUR_HEIGHT = 48.dp
    val GUTTER = 40.dp
    val totalHeight = HOUR_HEIGHT * 24
    val dateFmt = remember { SimpleDateFormat("M/d (E)", Locale.getDefault()) }

    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .height(totalHeight + 0.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        // 左側時間ガター：ここをタップしても選択解除
        Column(
            modifier = Modifier
                .width(GUTTER)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        onSelectEvent(null)
                        onSelectTask(null)
                    })
                }
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
                    selectedEventId = selectedEventId,
                    onSelectEvent = onSelectEvent,
                    onSelectBlock = onSelectBlock,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    label: String,
    hourHeight: Dp,
    blocks: List<TimelineBlock>,
    modifier: Modifier = Modifier,
    onSelectEvent: (Long?) -> Unit = {},
    selectedEventId: Long? = null,
    onSelectBlock: (Long, Long, Long, Long) -> Unit = { _: Long, _: Long, _: Long, _: Long -> }
) {
    val hourHeightPx = with(LocalDensity.current) { hourHeight.toPx() }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val outline40 = outline.copy(alpha = 0.4f)
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            // ▼ タスク以外（背景）をタップしたら選択解除
            .pointerInput(Unit) { detectTapGestures(onTap = { onSelectEvent(null) }) }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val step = h / 24f
            drawRect(
                color = surfaceVariant,
                size = Size(w, hourHeightPx * 0.75f)
            )
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hourHeight * 0.75f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }

        // 各タスクブロック（ここをタップした時は選択）
        blocks.forEach { b ->
            val startMin = minutesOfDay(b.startMs)
            val endMin = minutesOfDay(b.endMs)
            val top = hourHeight * (startMin / 60f)
            val height = hourHeight * ((endMin - startMin).coerceAtLeast(1) / 60f)

            val isSelected = selectedEventId == b.eventId
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else outline
            val borderWidth = if (isSelected) 2.dp else 1.dp

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .offset(y = top)
                    .fillMaxWidth()
                    .height(height)
                    .background(b.color.copy(alpha = 0.9f), RoundedCornerShape(0.dp))
                    .border(borderWidth, borderColor, RoundedCornerShape(0.dp))
                    .clickable {
                        onSelectEvent(b.eventId)
                        onSelectBlock(b.eventId, b.taskId, b.startMs, b.endMs)
                    }
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

private fun minutesOfDay(ms: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

// ---------- Color ヘルパ ----------
private fun parseHexColor(input: String): Color? {
    val s = input.trim().removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    return try {
        val argb = if (s.length == 6) "FF$s" else s
        val a = argb.substring(0, 2).toInt(16)
        val r = argb.substring(2, 4).toInt(16)
        val g = argb.substring(4, 6).toInt(16)
        val b = argb.substring(6, 8).toInt(16)
        Color(r / 255f, g / 255f, b / 255f, a / 255f)
    } catch (_: Exception) {
        null
    }
}

private fun colorToHex(color: Color, withAlpha: Boolean = false): String {
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    val a = (color.alpha * 255).roundToInt().coerceIn(0, 255)
    return if (withAlpha) String.format("#%02X%02X%02X%02X", a, r, g, b)
    else String.format("#%02X%02X%02X", r, g, b)
}

// ---------- カラーピッカー（RGB/Alphaスライダー） ----------
@Composable
private fun RgbColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    var r by rememberSaveable { mutableIntStateOf((initial.red * 255).roundToInt()) }
    var g by rememberSaveable { mutableIntStateOf((initial.green * 255).roundToInt()) }
    var b by rememberSaveable { mutableIntStateOf((initial.blue * 255).roundToInt()) }
    var a by rememberSaveable { mutableIntStateOf((initial.alpha * 255).roundToInt()) }

    val current = Color(r / 255f, g / 255f, b / 255f, a / 255f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("色を選択") },
        text = {
            Column {
                // プレビュー＋HEX
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(current, RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        colorToHex(current, withAlpha = true),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))

                ChannelSlider("R", r) { r = it }
                ChannelSlider("G", g) { g = it }
                ChannelSlider("B", b) { b = it }
                ChannelSlider("A", a) { a = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text("適用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun ChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
                valueRange = 0f..255f,
            )
            Spacer(Modifier.width(8.dp))
            Text(value.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/* =========================
   右ページ：期間集計グラフ
   ========================= */

@Composable
private fun StatsScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { viewModel.ensureHistoryFirstPage() }

    val history by viewModel.historyItems.collectAsStateWithLifecycle()
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    val parents by viewModel.parents.collectAsStateWithLifecycle()

    var days by rememberSaveable { mutableIntStateOf(7) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val onRefresh: () -> Unit = {
        nowMs = System.currentTimeMillis()
        viewModel.reloadHistory()
    }

    val cutoff = remember(nowMs, days) { nowMs - days * 24L * 60L * 60L * 1000L }

    val defaultTaskColor = MaterialTheme.colorScheme.primary

    fun colorForTask(taskId: Long, defaultColor: Color): Color {
        val group = parents.firstOrNull { g -> g.children.any { it.id == taskId } }
        return group?.parent?.colorInt?.let { Color(it) } ?: defaultColor
    }

    data class Agg(val taskId: Long, val name: String, val color: Color, val totalMs: Long)

    val aggregated: List<Agg> = remember(history, activeUi, parents, cutoff, nowMs) {
        val map = LinkedHashMap<Long, Agg>()

        fun addMs(taskId: Long, name: String, color: Color, ms: Long) {
            if (ms <= 0L) return
            val cur = map[taskId]
            if (cur == null) map[taskId] = Agg(taskId, name, color, ms)
            else map[taskId] = cur.copy(totalMs = cur.totalMs + ms)
        }

        history.filter { it.pressedAt >= cutoff }.forEach { row ->
            addMs(row.taskId, row.taskName, row.color, max(0L, row.durationMillis))
        }

        activeUi?.let { a ->
            val s = maxOf(a.startEpochMs, cutoff)
            val e = nowMs
            addMs(a.taskId, a.taskName, colorForTask(a.taskId, defaultTaskColor), max(0L, e - s))
        }

        map.values.sortedByDescending { it.totalMs }.toList()
    }

    val totalAll = aggregated.sumOf { it.totalMs }.coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("集計", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

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

@Composable
private fun BarChart(
    entries: List<Pair<String, Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    barCornerRadius: Float = 16f
) {
    val maxVal = (entries.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
    LocalDensity.current

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(999.dp)
                        )
                ) {
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

@Composable
fun AdaptiveBottomBar(
    currentPage: Int,
    timelineSelected: Boolean = false,
    onActionA: () -> Unit = {},
    onActionB: () -> Unit = {},
    onLeftAction: () -> Unit = {},
    onSortAction: () -> Unit = {},
    onRightAction: () -> Unit = {},
    onDeleteAction: () -> Unit = {}
) {
    when (currentPage) {
        0 -> {
            BottomAppBar {
                if (timelineSelected) {
                    IconButton(onClick = onActionA) {
                        Icon(
                            painter = painterResource(id = R.drawable.content_cut_24px),
                            contentDescription = "content_cut_24"
                        )
                    }
                    IconButton(onClick = onActionB) {
                        Icon(
                            painter = painterResource(id = R.drawable.edit_24px),
                            contentDescription = "edit_24"
                        )
                    }
                    IconButton(onClick = onDeleteAction) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete record")
                    }
                }
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
   タスク追加画面（実装版：過去記録の手動追加）
   ========================= */

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

// ============================================
// Previews for Main Screens (Timeline / Tasks / Stats)
// ============================================

// プレビュー用の簡単ヘルパ
private fun msOf(baseMidnight: Long, hour: Int, minute: Int = 0): Long {
    return baseMidnight + (hour * 60L + minute) * 60L * 1000L
}

// ---------- 左：タイムラインの見た目プレビュー ----------
@androidx.compose.ui.tooling.preview.Preview(
    name = "Timeline (3 days) – Light",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
private fun Preview_TimelineMain() {
    TimeRecorderTheme {
        val start = midnightOf(System.currentTimeMillis())
        val dayCount = 3

        // サンプルブロック（3日分）
        val blocksByDay = listOf(
            listOf(
                TimelineBlock(
                    eventId = 1L,
                    startMs = msOf(start, 9, 0),
                    endMs = msOf(start, 10, 30),
                    name = "Email & Plan",
                    color = Color(0xFF90CAF9),
                    taskId = 101L
                ),
                TimelineBlock(
                    eventId = 2L,
                    startMs = msOf(start, 11, 0),
                    endMs = msOf(start, 12, 0),
                    name = "Design Review",
                    color = Color(0xFFA5D6A7),
                    taskId = 102L
                ),
                TimelineBlock(
                    eventId = 3L,
                    startMs = msOf(start, 14, 0),
                    endMs = msOf(start, 17, 30),
                    name = "Implementation",
                    color = Color(0xFFFFF59D),
                    taskId = 103L
                ),
            ),
            listOf(
                TimelineBlock(
                    eventId = 4L,
                    startMs = msOf(start + 24L * 60L * 60L * 1000L, 10, 0),
                    endMs = msOf(start + 24L * 60L * 60L * 1000L, 12, 0),
                    name = "1:1 / Mentoring",
                    color = Color(0xFFCE93D8),
                    taskId = 104L
                )
            ),
            listOf(
                TimelineBlock(
                    eventId = 5L,
                    startMs = msOf(start + 2L * 24L * 60L * 60L * 1000L, 13, 0),
                    endMs = msOf(start + 2L * 24L * 60L * 60L * 1000L, 16, 0),
                    name = "Documentation",
                    color = Color(0xFFEF9A9A),
                    taskId = 105L
                )
            )
        )

        MultiDayTimeline(
            startMs = start,
            dayCount = dayCount,
            blocksByDay = blocksByDay,
            selectedTaskId = null,
            selectedEventId = null,
            onSelectEvent = {},
            onSelectTask = {},
            onSelectBlock = { _, _, _, _ -> }
        )
    }
}

// ---------- 中央：タスク一覧の見た目プレビュー（モック） ----------
@androidx.compose.ui.tooling.preview.Preview(
    name = "Tasks – Light",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
private fun Preview_TasksMain() {
    TimeRecorderTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "タスクを選択してください",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            listOf(
                "Project A" to Color(0xFF90CAF9),
                "Project B" to Color(0xFFA5D6A7),
            ).forEach { (title, color) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                        Spacer(Modifier.height(8.dp))
                        (1..4).forEach { i ->
                            val border = MaterialTheme.colorScheme.outline
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .border(1.dp, border, RoundedCornerShape(2.dp)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Task $i",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(color, RoundedCornerShape(3.dp))
                                            .border(
                                                1.dp,
                                                border,
                                                RoundedCornerShape(3.dp)
                                            )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Filled.Menu, contentDescription = null)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ---------- 右：集計の見た目プレビュー（BarChartにダミーデータ） ----------
@androidx.compose.ui.tooling.preview.Preview(
    name = "Stats – Light",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
private fun Preview_StatsMain() {
    TimeRecorderTheme {
        val sampleNames = listOf(
            "Design", "Coding", "Review", "Meeting", "Docs", "Ops", "Support", "Other"
        )
        val sampleVals = listOf(240f, 520f, 180f, 340f, 120f, 90f, 60f, 30f)
        val sampleColors = listOf(
            Color(0xFF90CAF9), Color(0xFFFFF59D), Color(0xFFA5D6A7), Color(0xFFEF9A9A),
            Color(0xFFCE93D8), Color(0xFF80CBC4), Color(0xFFFFAB91), Color(0xFFB39DDB)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("集計", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "期間: 過去7日 / 合計 ${sampleVals.sum().toLong()} 分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            BarChart(
                entries = sampleNames.zip(sampleVals),
                colors = sampleColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
            )

            Spacer(Modifier.height(12.dp))

            sampleNames.zip(sampleVals).zip(sampleColors).forEach { (nv, c) ->
                val (name, value) = nv
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(c, RoundedCornerShape(3.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(3.dp)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${value.toInt()} 分", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
