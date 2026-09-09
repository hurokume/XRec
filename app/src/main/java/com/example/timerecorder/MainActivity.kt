package com.example.timerecorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.StopCircle
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timerecorder.ui.HistoryItemUi
import com.example.timerecorder.ui.AdvancedAnalysisScreen
import com.example.timerecorder.ui.AddTaskScreen
import com.example.timerecorder.ui.DailyPlanTaskOption
import com.example.timerecorder.ui.ManageDataScreen
import com.example.timerecorder.ui.TimelineRangeControls
import com.example.timerecorder.ui.SettingsScreen
import com.example.timerecorder.ui.StatsFilterSelectionScreen
import com.example.timerecorder.ui.TaskDeletionDialog
import com.example.timerecorder.ui.TaskDeletionUiState
import com.example.timerecorder.ui.TaskAnalysisScreen
import com.example.timerecorder.ui.TaskViewModel
import com.example.timerecorder.ui.UiEvent
import com.example.timerecorder.ui.WeeklyReportScreen
import com.example.timerecorder.ui.ReplacementTaskSelectionScreen
import com.example.timerecorder.data.StatsAggregator
import com.example.timerecorder.data.StatsRecord
import com.example.timerecorder.data.StatsTarget
import com.example.timerecorder.data.DailyPlans
import com.example.timerecorder.data.DailyPlansCodec
import com.example.timerecorder.data.TaskDeletionMode
import com.example.timerecorder.ui.theme.TimeRecorderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** 画面モード（メイン/履歴） */
private enum class AppScreen { Main, History, Analysis, WeeklyReport, Settings, ManageData }

/** 名前ソートモード */
enum class NameSort { None, Asc, Desc }

enum class StatsAggMode { Project, Task, Leaf }

/** Navigation のルート定義 */
private object Routes {
    const val Root = "root"
    const val AddTask = "addTask"
}

private const val PREFS_NAME = "stats_prefs"
private const val KEY_STATS_INCLUDED = "stats_included_leaf_ids" // CSVのLong ID群
private const val SETTINGS_PREFS_NAME = "app_settings"
private const val KEY_SHOW_STOP_BUTTON = "show_stop_button"
private const val KEY_DAILY_PLANS = "daily_plans"

/**
 * エントリポイント Activity。
 * - Compose をセットアップし、NavHost で画面遷移を定義する。
 * - ここでは「Root」（既存 UI）と「AddTask」（タスク記録追加）に遷移可能。
 */
class MainActivity : ComponentActivity() {
    private val taskViewModel: TaskViewModel by viewModels()

    private fun getSystemService(name: Any) {}

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeRecorderTheme {
                com.example.timerecorder.ui.TaskEditDialogs(taskViewModel)
                Surface(color = MaterialTheme.colorScheme.background) {
                    // NavController を用意し、アプリ内の画面遷移を管理
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Routes.Root
                    ) {
                        composable(Routes.Root) {
                            AppRoot(
                                taskVm = taskViewModel,
                                onNavigateAddTask = { navController.navigate(Routes.AddTask) },
                                onNavigateEditTask = { eventId, taskId, startMs, endMs ->
                                    navController.navigate(
                                        "${Routes.AddTask}?initialEventId=$eventId" +
                                            "&initialTaskId=$taskId" +
                                            "&initialStartMs=$startMs" +
                                            "&initialEndMs=$endMs"
                                    )
                                }
                            )
                        }

                        composable(
                            route = "${Routes.AddTask}?initialEventId={initialEventId}" +
                                "&initialTaskId={initialTaskId}" +
                                "&initialStartMs={initialStartMs}" +
                                "&initialEndMs={initialEndMs}",
                            arguments = listOf(
                                navArgument("initialEventId") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
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
                            val initEvent = backStackEntry.arguments?.getLong("initialEventId")
                                ?.takeIf { it != 0L }
                            val initTask = backStackEntry.arguments?.getLong("initialTaskId")
                                ?.takeIf { it >= 0L }
                            val initStart = backStackEntry.arguments?.getLong("initialStartMs")
                                ?.takeIf { it >= 0L }
                            val initEnd = backStackEntry.arguments?.getLong("initialEndMs")
                                ?.takeIf { it >= 0L }
                            AddTaskScreen(
                                viewModel = taskViewModel,
                                onBack = { navController.popBackStack() },
                                initialEventId = initEvent,
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
    taskVm: TaskViewModel,
    onNavigateAddTask: () -> Unit,
    onNavigateEditTask: (Long, Long, Long, Long) -> Unit
) {

    val snackbar = remember { SnackbarHostState() }

    // ドロワーの開閉状態
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // タスク木を参照して “タスク / サブタスク” を解決するために購読
    val parents by taskVm.parents.collectAsStateWithLifecycle()

    // 中央ページ（タスク一覧）を起点に、左右へページング
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 4 })

    val ctx = LocalContext.current
    val active by taskVm.active.collectAsStateWithLifecycle()
    val settingsPreferences = remember(ctx) {
        ctx.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var showStopButton by rememberSaveable {
        mutableStateOf(
            settingsPreferences.getBoolean(KEY_SHOW_STOP_BUTTON, true)
        )
    }
    var dailyPlans by remember {
        mutableStateOf(DailyPlansCodec.decode(settingsPreferences.getString(KEY_DAILY_PLANS, null)))
    }
    val dailyPlanTaskOptions = remember(parents) {
        parents.flatMap { group ->
            listOf(
                DailyPlanTaskOption(
                    id = group.parent.id,
                    label = group.parent.name,
                    colorInt = group.parent.colorInt
                )
            ) +
                    group.children.map { child ->
                        DailyPlanTaskOption(
                            id = child.id,
                            label = "${group.parent.name} / ${child.name}",
                            colorInt = child.colorInt ?: group.parent.colorInt
                        )
                    }
        }
    }

    fun updateShowStopButton(show: Boolean) {
        showStopButton = show
        settingsPreferences.edit()
            .putBoolean(KEY_SHOW_STOP_BUTTON, show)
            .apply()
    }

    fun updateDailyPlans(updated: DailyPlans) {
        dailyPlans = updated
        settingsPreferences.edit()
            .putString(KEY_DAILY_PLANS, DailyPlansCodec.encode(updated))
            .apply()
    }

    // 通知許可のランチャ
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ActiveTaskService.start(ctx)
        } // 拒否時は何もしない（必要ならダイアログ or 設定遷移を案内）
    }

    LaunchedEffect(active) {
        if (active != null) {
            if (Build.VERSION.SDK_INT >= 33) {
                val has = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (has) {
                    ActiveTaskService.start(ctx)
                } else {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                ActiveTaskService.start(ctx)
            }
        } else {
            ActiveTaskService.stop(ctx)
        }
    }

    // タイムラインで選択中の taskId を保持（null で未選択）
    var selectedTimelineTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTimelineStartMs by rememberSaveable { mutableLongStateOf(-1L) }
    var selectedTimelineEndMs by rememberSaveable { mutableLongStateOf(-1L) }
    var selectedTimelineEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    // 追加：タイトルの「タスク / サブタスク」文字列
    var selectedTimelineTitleCore by rememberSaveable { mutableStateOf<String?>(null) }
    var activeTitlePath by rememberSaveable { mutableStateOf<String?>(null) }

    var showDeleteEventDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteEventId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showImportTsvDialog by rememberSaveable { mutableStateOf(false) }
    var clearExistingEvents by rememberSaveable { mutableStateOf(false) }
    var createMissing by rememberSaveable { mutableStateOf(true) }

    var showExportTsvDialog by rememberSaveable { mutableStateOf(false) }

    var statsGroupByProject by rememberSaveable { mutableStateOf(false) }

    val activeUi by taskVm.activeUi.collectAsStateWithLifecycle()

    var showStatsFilterScreen by rememberSaveable { mutableStateOf(false) }
    var statsIncludedTaskIds by remember { mutableStateOf<Set<Long>?>(null) }

    var statsAggMode by rememberSaveable { mutableStateOf(StatsAggMode.Leaf) }

    // 3モード巡回
    fun cycleStatsAgg() {
        statsAggMode = when (statsAggMode) {
            StatsAggMode.Project -> StatsAggMode.Task
            StatsAggMode.Task -> StatsAggMode.Leaf
            StatsAggMode.Leaf -> StatsAggMode.Project
        }
    }

    // 起動時に永続値を読み出し
    LaunchedEffect(Unit) {
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val csv = sp.getString(KEY_STATS_INCLUDED, null)
        statsIncludedTaskIds = csv
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
    }

    // 保存ヘルパ
    fun saveStatsFilter(ids: Set<Long>) {
        statsIncludedTaskIds = ids
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_STATS_INCLUDED, ids.joinToString(",")).apply()
    }


    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    var runningPath by remember { mutableStateOf<String?>(null) }

    // 共通ヘルパ
    fun hhmm(start: Long, end: Long): String {
        val d = (end - start).coerceAtLeast(0L)
        val h = (d / 3_600_000L).toInt()
        val m = ((d / 60_000L) % 60).toInt()
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m)
    }

    fun hhmmssFromDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val hh = s / 3600
        val mm = (s % 3600) / 60
        val ss = s % 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hh, mm, ss)
    }

    fun hhmmClock(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(
            Locale.getDefault(), "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    val spanText =
        if (selectedTimelineTaskId != null &&
            selectedTimelineStartMs >= 0L &&
            selectedTimelineEndMs > selectedTimelineStartMs
        ) {
            "${hhmmClock(selectedTimelineStartMs)} - ${hhmmClock(selectedTimelineEndMs)}"
        } else null

    LaunchedEffect(selectedTimelineTaskId, parents) {
        val tid = selectedTimelineTaskId ?: run {
            selectedTimelineTitleCore = null
            return@LaunchedEffect
        }
        // 1) まず“子タスク”として一致するか
        val directGroup = parents.firstOrNull { g -> g.children.any { it.id == tid } }
        if (directGroup != null) {
            val child = directGroup.children.first { it.id == tid }
            selectedTimelineTitleCore = child.name
            return@LaunchedEffect
        }
        // 2) 見つからなければ“サブタスク”探索（子配下をロード）
        var resolved: String? = null
        outer@ for (g in parents) {
            for (child in g.children) {
                val subs = taskVm.children(child.id).first() // suspend OK
                val sub = subs.firstOrNull { it.id == tid }
                if (sub != null) {
                    resolved = "${child.name} / ${sub.name}"
                    break@outer
                }
            }
        }
        selectedTimelineTitleCore = resolved
    }

    LaunchedEffect(activeUi, parents) {
        val a = activeUi ?: run { activeTitlePath = null; return@LaunchedEffect }
        val tid = a.taskId

        // 1) 子タスク一致（Project / Task）
        parents.firstOrNull { g -> g.children.any { it.id == tid } }?.let { g ->
            val child = g.children.first { it.id == tid }
            activeTitlePath = "${g.parent.name} / ${child.name}"
            return@LaunchedEffect
        }
        // 2) サブタスク探索（Project / Task / SubTask）
        outer@ for (g in parents) {
            for (child in g.children) {
                val subs = taskVm.children(child.id).first() // suspend OK
                for (sub in subs) {
                    if (sub.id == tid) {
                        activeTitlePath = "${g.parent.name} / ${child.name} / ${sub.name}"
                        break@outer
                    }
                }
            }
        }
        if (activeTitlePath == null) activeTitlePath = activeUi?.taskName // フォールバック
    }

    // 追加：選択していない時にだけ1秒ごとにリフレッシュ（HH:MM:SS用）
    var tickNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeUi, selectedTimelineTaskId) {
        if (activeUi != null && selectedTimelineTaskId == null) {
            while (true) {
                tickNow = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // ViewModel のワンショット UI イベントを収集して Snackbar を表示
    LaunchedEffect(Unit) {
        taskVm.events.collectLatest { ev ->
            when (ev) {
                UiEvent.TasksMerged -> {
                    dailyPlans = DailyPlansCodec.decode(settingsPreferences.getString(KEY_DAILY_PLANS, null))
                    statsIncludedTaskIds = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_STATS_INCLUDED, null)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet()
                }
                is UiEvent.ShowMessage -> snackbar.showSnackbar(ev.message)
            }
        }
    }

    var timelineRefreshTick by remember { mutableLongStateOf(0L) }

    // Import ダイアログの状態
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var replaceAll by rememberSaveable { mutableStateOf(false) }

    val importJsonFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = readImportFile(ctx, uri)
                    taskVm.importTasksFromJson(json, replaceAll)
                    replaceAll = false
                    showImportDialog = false
                    snackbar.showSnackbar(ctx.getString(R.string.import_complete))
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.import_failed, e.message.orEmpty()),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val importTsvFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val tsv = readImportFile(ctx, uri)
                    val count = taskVm.importEventsTsv(
                        tsv = tsv,
                        clearExisting = clearExistingEvents,
                        createMissing = createMissing
                    )
                    clearExistingEvents = false
                    createMissing = true
                    showImportTsvDialog = false
                    taskVm.reloadHistory()
                    timelineRefreshTick = System.currentTimeMillis()
                    snackbar.showSnackbar(ctx.getString(R.string.import_tsv_complete, count))
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.import_failed, e.message.orEmpty()),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // 現在表示中のページインデックス（BottomBar の表示切替に利用）
    var currentPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState) {
        // ページが変わったら currentPage を追従
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collectLatest { page ->
                currentPage = page
                when (page) {
                    0 -> { // タイムライン
                        taskVm.reloadHistory()                   // ★追加
                        timelineRefreshTick = System.currentTimeMillis()
                    }

                    1 -> { /* 何もしない */
                    }

                    2 -> { // 統計
                        taskVm.reloadHistory()                   // ★追加
                    }

                    3 -> { /* 分析画面は選択した期間を都度読み込む */ }
                }
            }
    }

    // 画面モード（メイン / 履歴）
    var screen by remember { mutableStateOf(AppScreen.Main) }

    // Manage Data は Settings の子画面として一段ずつ戻る。
    BackHandler(enabled = screen == AppScreen.ManageData) {
        screen = AppScreen.Settings
    }
    // その他の Home 以外の画面では、Activity を終了せず Home に戻す。
    BackHandler(enabled = screen != AppScreen.Main && screen != AppScreen.ManageData) {
        screen = AppScreen.Main
    }
    BackHandler(enabled = screen == AppScreen.Main && showStatsFilterScreen) {
        showStatsFilterScreen = false
    }
    // 並び替えモード（タスクの上下入れ替え）
    var sortMode by rememberSaveable { mutableStateOf(false) }

    // ★ 追加：名前ソート（None -> Asc -> Desc -> Asc...）
    var nameSort by rememberSaveable { mutableStateOf(NameSort.None) }

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
                                    taskVm.ensureHistoryFirstPage()
                                }

                                "analysis" -> screen = AppScreen.Analysis

                                "weekly_report" -> screen = AppScreen.WeeklyReport

                                "settings" -> screen = AppScreen.Settings

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
                            navigationIcon = {
                                if (screen == AppScreen.ManageData) {
                                    IconButton(onClick = { screen = AppScreen.Settings }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(
                                                R.string.action_back
                                            )
                                        )
                                    }
                                }
                            },
                            title = {
                                val showTimelineTitle =
                                    screen == AppScreen.Main &&
                                            currentPage == 0 &&
                                            selectedTimelineTaskId != null &&
                                            selectedTimelineStartMs >= 0L &&
                                            selectedTimelineEndMs > selectedTimelineStartMs

                                val titleText =
                                    if (showStatsFilterScreen) {
                                        stringResource(R.string.stats_filter_title)
                                    } else if (showTimelineTitle) {
                                        // ① 選択中ブロックを最優先（タスク / サブタスク + HH:MM）
                                        val core = selectedTimelineTitleCore
                                            ?: stringResource(R.string.main_selected_item)
                                        val dur =
                                            hhmm(selectedTimelineStartMs, selectedTimelineEndMs)
                                        "$core ($dur)"
                                    } else if (activeUi != null) {
                                        // ② 未選択なら “現在実行中” を表示（Project / Task (/ SubTask) + HH:MM:SS）
                                        val core = activeTitlePath ?: activeUi!!.taskName
                                        val dur =
                                            hhmmssFromDuration(tickNow - activeUi!!.startEpochMs)
                                        "$core ($dur)"
                                    } else {
                                        // ③ どちらも無ければ既存タイトル
                                        when (screen) {
                                            AppScreen.Main -> if (sortMode) {
                                                stringResource(R.string.main_sort_title)
                                            } else {
                                                stringResource(R.string.header_title)
                                            }

                                            AppScreen.History -> stringResource(R.string.drawer_history)
                                            AppScreen.Analysis -> stringResource(R.string.drawer_analysis)
                                            AppScreen.WeeklyReport -> stringResource(
                                                R.string.weekly_report_title
                                            )
                                            AppScreen.Settings -> stringResource(R.string.drawer_settings)
                                            AppScreen.ManageData -> stringResource(
                                                R.string.manage_data_title
                                            )
                                        }
                                    }

                                Text(
                                    titleText,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                if (!showStatsFilterScreen) {
                                    if (activeUi != null && showStopButton) {
                                        IconButton(onClick = { taskVm.onStopPressed() }) {
                                            Icon(
                                                Icons.Filled.StopCircle,
                                                contentDescription = stringResource(
                                                    R.string.main_stop_active_task
                                                )
                                            )
                                        }
                                    }
                                    if (screen == AppScreen.Main && sortMode) {
                                        IconButton(onClick = { sortMode = false }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = stringResource(
                                                    R.string.main_exit_sort_mode
                                                )
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(
                                                imageVector = Icons.Filled.Menu,
                                                contentDescription = stringResource(
                                                    R.string.drawer_title
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (screen == AppScreen.Main && !showStatsFilterScreen) {
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
                                            scope.launch {
                                                snackbar.showSnackbar(
                                                    ctx.getString(R.string.main_split_unavailable)
                                                )
                                            }
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
                                                    scope.launch {
                                                        snackbar.showSnackbar(
                                                            ctx.getString(
                                                                R.string.main_split_out_of_range
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    taskVm.splitEventAt(eid, base)
                                                    // 選択は一旦クリア
                                                    selectedTimelineEventId = null
                                                    selectedTimelineTaskId = null
                                                }
                                            },
                                            initHour, initMin, true
                                        ).show()
                                    },
                                    onActionB = {
                                        val eventId = selectedTimelineEventId
                                        val taskId = selectedTimelineTaskId
                                        val startMs = selectedTimelineStartMs
                                        val endMs = selectedTimelineEndMs
                                        if (
                                            eventId != null && eventId != 0L &&
                                            taskId != null &&
                                            startMs >= 0L && endMs > startMs
                                        ) {
                                            scope.launch {
                                                val event = if (eventId > 0) taskVm.recordForEdit(eventId) else null
                                                val running = taskVm.activeUi.value
                                                if (event != null) {
                                                    onNavigateEditTask(eventId, event.taskId,
                                                        event.pressedAtEpochMs, event.pressedAtEpochMs + event.durationMillis)
                                                } else if (eventId == -1L && running != null) {
                                                    onNavigateEditTask(-1L, running.taskId,
                                                        running.startEpochMs, System.currentTimeMillis())
                                                }
                                            }
                                            selectedTimelineEventId = null
                                            selectedTimelineTaskId = null
                                            selectedTimelineStartMs = -1L
                                            selectedTimelineEndMs = -1L
                                        } else {
                                            scope.launch {
                                                snackbar.showSnackbar(
                                                    ctx.getString(R.string.main_edit_unavailable)
                                                )
                                            }
                                        }
                                    },
                                    onLeftAction = { /* 予備 */ },
                                    onSortAction = { sortMode = true },
                                    onRightAction = { showStatsFilterScreen = true },
                                    onDeleteAction = {
                                        val eid = selectedTimelineEventId
                                        val s = selectedTimelineStartMs
                                        val e = selectedTimelineEndMs
                                        if (eid == null || eid <= 0L || s < 0L || e <= s) {
                                            scope.launch {
                                                snackbar.showSnackbar(
                                                    ctx.getString(R.string.main_delete_unavailable)
                                                )
                                            }
                                            return@AdaptiveBottomBar
                                        }
                                        pendingDeleteEventId = eid
                                        showDeleteEventDialog = true
                                    },
                                    showExtraIcon = sortMode,
                                    onExtraAction = {
                                        nameSort = when (nameSort) {
                                            NameSort.None -> NameSort.Asc
                                            NameSort.Asc -> NameSort.Desc
                                            NameSort.Desc -> NameSort.Asc
                                        }
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                when (nameSort) {
                                                    NameSort.Asc -> ctx.getString(
                                                        R.string.main_sort_ascending
                                                    )
                                                    NameSort.Desc -> ctx.getString(
                                                        R.string.main_sort_descending
                                                    )
                                                    else -> ctx.getString(R.string.main_sort_manual)
                                                }
                                            )
                                        }
                                    },
                                    timelineSpanText = spanText,
                                    onFilterAction = {
                                        if (currentPage == 2) showStatsFilterScreen = true
                                    },
                                    onAggCycleAction = { if (currentPage == 2) cycleStatsAgg() },
                                    aggMode = statsAggMode
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    // ページ本体
                    when (screen) {
                        AppScreen.Main -> {
                            if (showStatsFilterScreen) {
                                StatsFilterSelectionScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    viewModel = taskVm,
                                    selectedTaskIds = statsIncludedTaskIds,
                                    onSelectionChange = ::saveStatsFilter,
                                    onBack = { showStatsFilterScreen = false }
                                )
                            } else {
                                MainPager(
                                    modifier = Modifier.padding(innerPadding),
                                    pagerState = pagerState,
                                    taskVm = taskVm,
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
                                    onTimelineEventSelected = { eid ->
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
                                    },
                                    nameSort = nameSort,
                                    timelineRefreshTick = timelineRefreshTick,
                                    statsIncludedTaskIds = statsIncludedTaskIds,
                                    aggMode = statsAggMode
                                )
                            }
                        }

                        AppScreen.History -> HistoryScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            viewModel = taskVm
                        )

                        AppScreen.Analysis -> AdvancedAnalysisScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            viewModel = taskVm,
                            dailyPlans = dailyPlans
                        )

                        AppScreen.WeeklyReport -> WeeklyReportScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            viewModel = taskVm,
                            dailyPlans = dailyPlans
                        )

                        AppScreen.Settings -> SettingsScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            showStopButton = showStopButton,
                            onShowStopButtonChange = ::updateShowStopButton,
                            dailyPlans = dailyPlans,
                            taskOptions = dailyPlanTaskOptions,
                            onDailyPlansChange = ::updateDailyPlans,
                            onManageDataClick = { screen = AppScreen.ManageData }
                        )

                        AppScreen.ManageData -> ManageDataScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            onExportTsv = { showExportTsvDialog = true },
                            onImportTsv = { showImportTsvDialog = true },
                            onExportJson = {
                                scope.launch {
                                    try {
                                        val text = taskVm.exportTasksJson()
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                ctx.getString(R.string.export_tasks_subject)
                                            )
                                        }
                                        ctx.startActivity(
                                            Intent.createChooser(
                                                send,
                                                ctx.getString(R.string.export_tasks_chooser)
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            ctx,
                                            ctx.getString(
                                                R.string.export_failed,
                                                e.message.orEmpty()
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            onImportJson = { showImportDialog = true }
                        )
                    }

                    // ===== Import ダイアログ =====
                    if (showImportDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportDialog = false },
                            title = { Text(stringResource(R.string.import_json_title)) },
                            text = {
                                Column {
                                    Text(
                                        stringResource(R.string.import_json_description),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    // 既存を全削除して置換するかどうか
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = replaceAll,
                                            onCheckedChange = { replaceAll = it }
                                        )
                                        Text(stringResource(R.string.import_json_replace_all))
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    importJsonFileLauncher.launch(
                                        arrayOf("application/json", "text/json", "text/plain")
                                    )
                                }) { Text(stringResource(R.string.import_select_file)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showImportDialog = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        )
                    }
                    if (showImportTsvDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportTsvDialog = false },
                            title = { Text(stringResource(R.string.import_tsv_title)) },
                            text = {
                                Column {
                                    Text(
                                        stringResource(R.string.import_tsv_description),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = clearExistingEvents,
                                            onCheckedChange = { clearExistingEvents = it })
                                        Text(stringResource(R.string.import_tsv_clear_existing))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = createMissing,
                                            onCheckedChange = { createMissing = it })
                                        Text(stringResource(R.string.import_tsv_create_missing))
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    importTsvFileLauncher.launch(
                                        arrayOf(
                                            "text/tab-separated-values",
                                            "text/tsv",
                                            "text/plain"
                                        )
                                    )
                                }) { Text(stringResource(R.string.import_select_file)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showImportTsvDialog = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        )
                    }
                    if (showExportTsvDialog) {
                        ExportTsvRangeDialog(
                            taskVm = taskVm,
                            onDismiss = { showExportTsvDialog = false },
                            onExport = { startMs, endExclusiveMs ->
                                scope.launch {
                                    try {
                                        val text = taskVm.exportEventsTsv(startMs, endExclusiveMs)
                                        val uri = writeTsvToCacheAndGetUri(
                                            context = ctx,
                                            content = text,
                                            startMs = startMs,
                                            endExclusiveMs = endExclusiveMs
                                        )
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/tab-separated-values"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                ctx.getString(R.string.export_tsv_subject)
                                            )
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            clipData = android.content.ClipData.newUri(
                                                ctx.contentResolver,
                                                ctx.getString(R.string.export_tsv_clip_label),
                                                uri
                                            )
                                        }
                                        ctx.startActivity(
                                            Intent.createChooser(
                                                send,
                                                ctx.getString(R.string.export_tsv_chooser)
                                            )
                                        )
                                        showExportTsvDialog = false
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            ctx,
                                            ctx.getString(
                                                R.string.export_tsv_failed,
                                                e.message.orEmpty()
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
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
                            title = { Text(stringResource(R.string.delete_record_title)) },
                            text = {
                                Text(stringResource(R.string.delete_record_confirmation))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingDeleteEventId?.let { eid ->
                                        // ViewModel 側の削除 API（名称はプロジェクトに合わせて）
                                        taskVm.deleteEvent(eid)
                                    }
                                    // 選択解除
                                    selectedTimelineEventId = null
                                    selectedTimelineTaskId = null
                                    selectedTimelineStartMs = -1L
                                    selectedTimelineEndMs = -1L
                                    pendingDeleteEventId = null
                                    showDeleteEventDialog = false
                                }) { Text(stringResource(R.string.action_delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    pendingDeleteEventId = null
                                    showDeleteEventDialog = false
                                }) { Text(stringResource(R.string.action_cancel)) }
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
            label = { Text(stringResource(R.string.drawer_history)) },
            selected = false,
            onClick = { onItemSelected("history") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_analysis)) },
            selected = false,
            onClick = { onItemSelected("analysis") },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_weekly_report)) },
            selected = false,
            onClick = { onItemSelected("weekly_report") },
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
 * 4ページ（タイムライン / タスク / 集計 / 分析）を横スワイプで切替。
 */
@Composable
fun MainPager(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    taskVm: TaskViewModel,
    sortMode: Boolean,
    onNavigateAddTask: () -> Unit,
    selectedTimelineTaskId: Long?,
    selectedTimelineEventId: Long?,
    onTimelineTaskSelected: (Long?) -> Unit,
    onTimelineEventSelected: (Long?) -> Unit,
    onTimelineBlockSelected: (Long, Long, Long, Long) -> Unit,
    nameSort: NameSort,
    timelineRefreshTick: Long,
    statsIncludedTaskIds: Set<Long>?,
    aggMode: StatsAggMode
) {
    HorizontalPager(state = pagerState, modifier = modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> TimelineScreen(
                viewModel = taskVm,
                onAddRecord = onNavigateAddTask,
                selectedTaskId = selectedTimelineTaskId,
                onSelectTask = onTimelineTaskSelected,
                selectedEventId = selectedTimelineEventId,
                onSelectEvent = onTimelineEventSelected,
                onSelectBlock = onTimelineBlockSelected,
                externalRefreshTick = timelineRefreshTick
            )

            1 -> DBTaskScreen(taskVm = taskVm, sortMode = sortMode, nameSort = nameSort)
            2 -> StatsScreen(
                viewModel = taskVm,
                includedTaskIds = statsIncludedTaskIds,
                aggMode = aggMode
            )

            3 -> TaskAnalysisScreen(viewModel = taskVm)
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
    taskVm: TaskViewModel,
    modifier: Modifier = Modifier,
    sortMode: Boolean,
    nameSort: NameSort
) {
    val parents by taskVm.parents.collectAsStateWithLifecycle()
    val activeUi by taskVm.activeUi.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val duplicateParentErrorText = stringResource(R.string.task_duplicate_parent_error)
    val duplicateChildErrorText = stringResource(R.string.task_duplicate_child_error)
    val emptyNameErrorText = stringResource(R.string.task_empty_name_error)

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
    var taskDeletion by remember { mutableStateOf<TaskDeletionUiState?>(null) }

    var colorPickForParentId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun requestTaskDeletion(id: Long, name: String, isRoot: Boolean) {
        taskDeletion = TaskDeletionUiState(
            taskId = id,
            taskName = name,
            isRootTask = isRoot
        )
    }

    LaunchedEffect(taskDeletion?.taskId) {
        val taskId = taskDeletion?.taskId ?: return@LaunchedEffect
        val excludedIds = taskVm.taskTreeIds(taskId)
        taskDeletion = taskDeletion
            ?.takeIf { it.taskId == taskId }
            ?.copy(excludedTaskIds = excludedIds)
    }

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

    // 追加：表示用の実効ソート（平常時は任意＝NameSort.None）
    val effectiveNameSort = if (sortMode) nameSort else NameSort.None

    val parentsForView = remember(parents, effectiveNameSort) {
        when (effectiveNameSort) {
            NameSort.None -> parents
            NameSort.Asc -> parents.sortedBy { it.parent.name.lowercase() }
            NameSort.Desc -> parents.sortedByDescending { it.parent.name.lowercase() }
        }
    }

    var renameIsParent by rememberSaveable { mutableStateOf(false) }
    var renameError by rememberSaveable { mutableStateOf<String?>(null) }

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
                if (taskVm.hasChildren(child.id)) {
                    val subs = taskVm.children(child.id).first()
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
            taskVm.reorderChildren(parentId, ordered)
        }
    }


    LaunchedEffect(sortMode) {
        if (!sortMode && nameSort != NameSort.None) {
            val parentIdsSorted = when (nameSort) {
                NameSort.Asc -> parents.sortedBy { it.parent.name.lowercase() }.map { it.parent.id }
                NameSort.Desc -> parents.sortedByDescending { it.parent.name.lowercase() }
                    .map { it.parent.id }

                else -> emptyList()
            }

            // ★ これを追加（TaskViewModel に用意してある前提）
            if (parentIdsSorted.isNotEmpty()) {
                taskVm.reorderParents(parentIdsSorted)
            }

            // 子・サブタスクのコミットは既にOK（そのまま）
            parents.forEach { g ->
                val childIdsSorted = when (nameSort) {
                    NameSort.Asc -> g.children.sortedBy { it.name.lowercase() }.map { it.id }
                    NameSort.Desc -> g.children.sortedByDescending { it.name.lowercase() }
                        .map { it.id }

                    else -> g.children.map { it.id }
                }
                taskVm.reorderChildren(g.parent.id, childIdsSorted)
            }

            // 2) ★ サブタスクも全親子について一括コミット
            parents.forEach { g ->
                g.children.forEach { child ->
                    val subs = taskVm.children(child.id).first()
                    if (subs.isNotEmpty()) {
                        val subIdsSorted = when (nameSort) {
                            NameSort.Asc -> subs.sortedBy { it.name.lowercase() }.map { it.id }
                            NameSort.Desc -> subs.sortedByDescending { it.name.lowercase() }
                                .map { it.id }

                            else -> subs.map { it.id }
                        }
                        taskVm.reorderChildren(child.id, subIdsSorted)
                    }
                }
            }
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
                        text = if (sortMode) stringResource(R.string.task_reorder_description)
                        else stringResource(R.string.label_select_task),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = if (sortMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = true),
                        state = listState
                    ) {
                        items(parentsForView.size) { idx ->
                            val p = parentsForView[idx]
                            val parentColor = p.parent.colorInt?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.primary

                            val childById =
                                remember(p.children) { p.children.associateBy { it.id } }
                            val baseOrder: List<Long> = when (effectiveNameSort) {
                                NameSort.None -> {
                                    val o = reorderLists[p.parent.id] ?: p.children.map { it.id }
                                        .toMutableList()
                                    reorderLists[p.parent.id] = o
                                    o
                                }

                                NameSort.Asc -> p.children.sortedBy { it.name.lowercase() }
                                    .map { it.id }

                                NameSort.Desc -> p.children.sortedByDescending { it.name.lowercase() }
                                    .map { it.id }
                            }
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
                                        RoundedCornerShape(2.dp)
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
                                            modifier = Modifier.weight(1f).combinedClickable(
                                                onClick = {}, onLongClick = { menuForParentId = p.parent.id }
                                            )
                                        )
                                        IconButton(onClick = {
                                            menuForParentId = p.parent.id
                                            lastInteractionTime = System.currentTimeMillis()
                                        }) {
                                            Icon(
                                                Icons.Filled.Menu,
                                                contentDescription = stringResource(
                                                    R.string.task_parent_menu
                                                )
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
                                                text = { Text(stringResource(R.string.task_add)) },
                                                onClick = {
                                                    menuForParentId = null
                                                    newChildName = ""
                                                    addChildError = null
                                                    showAddChildDialogFor = p.parent.id
                                                    lastInteractionTime = System.currentTimeMillis()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.task_rename)) },
                                                onClick = {
                                                    menuForParentId = null
                                                    renameIsParent = true           // ★親の改名フラグをON
                                                    renameTaskId = p.parent.id
                                                    renameTaskCurrentName = p.parent.name
                                                    renameTaskText = p.parent.name
                                                    renameError = null
                                                    lastInteractionTime = System.currentTimeMillis()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.task_delete_parent)
                                                    )
                                                },
                                                onClick = {
                                                    menuForParentId = null
                                                    requestTaskDeletion(
                                                        p.parent.id,
                                                        p.parent.name,
                                                        isRoot = true
                                                    )
                                                    lastInteractionTime = System.currentTimeMillis()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.task_change_color))
                                                },
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
                                    baseOrder.forEach { childId ->
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
                                                .border(1.dp, border, RoundedCornerShape(2.dp))
                                                .graphicsLayer {
                                                    translationY =
                                                        if (isDragging) draggingDy else 0f
                                                }
                                                .zIndex(z)
                                                .then(
                                                    if (sortMode && effectiveNameSort == NameSort.None)
                                                        Modifier
                                                            .onGloballyPositioned { coords ->
                                                                val c =
                                                                    coords.boundsInParent().center.y
                                                                centers[child.id] = c
                                                            }
                                                            .pointerInput(sortMode, p.parent.id) {
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
                                                                                order.removeAt(
                                                                                    idx
                                                                                )
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
                                                                                order.removeAt(
                                                                                    idx
                                                                                )
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
                                                        Modifier.combinedClickable(
                                                            onLongClick = { menuForChildTaskId = child.id },
                                                            onClick = {
                                                            selectedTaskId = child.id
                                                            lastInteractionTime =
                                                                System.currentTimeMillis()
                                                            scope.launch {
                                                                val has =
                                                                    taskVm.hasChildren(child.id)
                                                                if (has) {
                                                                    ui =
                                                                        TaskCenterUiState.SubtaskSelect(
                                                                            parentTitle = child.name,
                                                                            parentId = child.id,
                                                                            color = parentColor
                                                                        )
                                                                } else {
                                                                    // ▼ ここは結果判定せず、ViewModel に任せる
                                                                    taskVm.onTaskPressed(child.id)
                                                                }
                                                            }
                                                        })
                                                ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else bg
                                            ),
                                            shape = RoundedCornerShape(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 0.dp),
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
                                                            contentDescription = stringResource(
                                                                R.string.task_menu
                                                            )
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
                                                            text = {
                                                                Text(
                                                                    stringResource(
                                                                        R.string.task_edit_subtasks
                                                                    )
                                                                )
                                                            },
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
                                                            text = {
                                                                Text(
                                                                    stringResource(
                                                                        R.string.task_add_subtask
                                                                    )
                                                                )
                                                            },
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
                                                            text = {
                                                                Text(stringResource(R.string.task_rename))
                                                            },
                                                            onClick = {
                                                                menuForChildTaskId = null
                                                                renameIsParent = false
                                                                renameTaskId = child.id
                                                                renameTaskCurrentName = child.name
                                                                renameTaskText = child.name
                                                                renameError = null
                                                                lastInteractionTime =
                                                                    System.currentTimeMillis()
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(stringResource(R.string.task_delete))
                                                            },
                                                            onClick = {
                                                                menuForChildTaskId = null
                                                                requestTaskDeletion(
                                                                    child.id,
                                                                    child.name,
                                                                    isRoot = false
                                                                )
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
                    val liveTitle = stringResource(
                        R.string.task_subtask_title,
                        liveChild?.name ?: s.parentTitle
                    )
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
                            taskVm.onTaskPressed(pickedId)
                            ui = TaskCenterUiState.List
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onRenameRequest = { taskId, currentName ->
                            renameIsParent = false
                            renameTaskId = taskId
                            renameTaskCurrentName = currentName
                            renameTaskText = currentName
                            renameError = null
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onDeleteRequest = { taskId, name ->
                            requestTaskDeletion(taskId, name, isRoot = false)
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        taskVm = taskVm,
                        nameSort = effectiveNameSort
                    )
                }

            }
        }

        taskDeletion?.takeIf { it.isSelectingReplacement }?.let { deletion ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
                color = MaterialTheme.colorScheme.background
            ) {
                ReplacementTaskSelectionScreen(
                    viewModel = taskVm,
                    parents = parents,
                    excludedTaskIds = deletion.excludedTaskIds,
                    nameSort = effectiveNameSort,
                    onBack = {
                        taskDeletion = taskDeletion?.copy(isSelectingReplacement = false)
                    },
                    onPick = { taskId, label ->
                        taskDeletion = taskDeletion?.copy(
                            replacementTaskId = taskId,
                            replacementTaskLabel = label,
                            isSelectingReplacement = false
                        )
                    }
                )
            }
        }

        // ====== 各種ダイアログ ======
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.task_add)) },
                text = {
                    OutlinedTextField(
                        value = newTaskName,
                        onValueChange = {
                            if (it.length <= 128) {
                                newTaskName = it
                                val name = it.trim()
                                addParentError = when {
                                    name.isEmpty() -> null
                                    else -> null
                                }
                            }
                        },
                        singleLine = true,
                        isError = addParentError != null,
                        label = { Text(stringResource(R.string.task_name_label)) },
                        supportingText = { if (addParentError != null) Text(addParentError!!) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = newTaskName.trim().take(128)
                        when {
                            name.isEmpty() -> addParentError = emptyNameErrorText

                            else -> {
                                taskVm.addParentTask(name)
                                showAddDialog = false
                                addParentError = null
                            }
                        }
                    }) { Text(stringResource(R.string.action_add)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showAddChildDialogFor != null) {
            AlertDialog(
                onDismissRequest = { showAddChildDialogFor = null },
                title = { Text(stringResource(R.string.task_add_child)) },
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

                                    else -> null
                                }
                            }
                        },
                        singleLine = true,
                        isError = addChildError != null,
                        label = { Text(stringResource(R.string.task_name_label)) },
                        supportingText = { if (addChildError != null) Text(addChildError!!) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val pid = showAddChildDialogFor
                        val name = newChildName.trim().take(128)
                        when {
                            pid == null -> {}
                            name.isEmpty() -> addChildError = emptyNameErrorText

                            else -> {
                                taskVm.addChildTask(pid, name)
                                showAddChildDialogFor = null
                                addChildError = null
                            }
                        }
                    }) { Text(stringResource(R.string.action_add)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddChildDialogFor = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
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
            val hexFormatError = stringResource(R.string.task_color_hex_error)
            val hexError = remember(parsedHex, hexInput, hexFormatError) {
                if (hexInput.isBlank()) null
                else if (parsedHex == null) hexFormatError
                else null
            }

            AlertDialog(
                onDismissRequest = { colorPickForParentId = null },
                title = { Text(stringResource(R.string.task_change_color)) },
                text = {
                    Column {
                        // 1) プリセット
                        Text(
                            stringResource(R.string.task_color_preset),
                            style = MaterialTheme.typography.labelMedium
                        )
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
                                                taskVm.changeParentColor(id, c)
                                                colorPickForParentId = null
                                            }
                                        }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 2) HEX入力
                        Text(
                            stringResource(R.string.task_color_hex_title),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { hexInput = it.uppercase() },
                                singleLine = true,
                                isError = hexError != null,
                                label = {
                                    Text(stringResource(R.string.task_color_hex_label))
                                },
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
                                    taskVm.changeParentColor(id, c)
                                    colorPickForParentId = null
                                },
                                enabled = parsedHex != null
                            ) { Text(stringResource(R.string.task_color_apply_hex)) }

                            FilledTonalButton(onClick = { showRgbPicker = true }) {
                                Text(stringResource(R.string.task_color_picker))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { colorPickForParentId = null }) {
                        Text(stringResource(R.string.action_close))
                    }
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
                            taskVm.changeParentColor(id, picked)
                            colorPickForParentId = null
                        }
                        showRgbPicker = false
                    }
                )
            }
        }

        if (renameTaskId != null) {
            AlertDialog(
                onDismissRequest = { renameTaskId = null },
                title = { Text(stringResource(R.string.task_rename)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.task_current_name, renameTaskCurrentName),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameTaskText,
                            onValueChange = {
                                if (it.length <= 128) {
                                    renameTaskText = it
                                    renameError = null
                                }
                            },
                            singleLine = true,
                            isError = renameError != null,   // ★エラー表示
                            label = { Text(stringResource(R.string.task_new_name_label)) },
                            supportingText = { if (renameError != null) Text(renameError!!) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = renameTaskId
                        val name = renameTaskText.trim().take(128)
                        when {
                            id == null -> Unit
                            name.isEmpty() -> renameError = emptyNameErrorText
                            else -> {
                                taskVm.renameTask(id, name)
                                renameTaskId = null
                                renameError = null
                            }
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTaskId = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }


        taskDeletion?.takeIf { !it.isSelectingReplacement }?.let { deletion ->
            TaskDeletionDialog(
                taskName = deletion.taskName,
                isRootTask = deletion.isRootTask,
                mode = deletion.mode,
                replacementTaskId = deletion.replacementTaskId,
                replacementTaskLabel = deletion.replacementTaskLabel,
                onModeChange = { newMode ->
                    taskDeletion = taskDeletion?.copy(
                        mode = newMode,
                        replacementTaskId = null,
                        replacementTaskLabel = null,
                        isSelectingReplacement = newMode == TaskDeletionMode.Replace
                    )
                },
                onSelectReplacement = {
                    taskDeletion = taskDeletion?.copy(isSelectingReplacement = true)
                },
                onConfirm = {
                    taskVm.deleteTask(
                        deletion.taskId,
                        deletion.mode,
                        deletion.replacementTaskId
                    )
                    taskDeletion = null
                },
                onDismiss = {
                    taskDeletion = null
                }
            )
        }

        if (fabVisible && taskDeletion?.isSelectingReplacement != true) {
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
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = stringResource(R.string.task_add_accessibility)
                )
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
    taskVm: TaskViewModel,
    nameSort: NameSort
) {

    val subtasks by taskVm.children(parentId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSubId by rememberSaveable { mutableStateOf<Long?>(null) }

    val activeUi by taskVm.activeUi.collectAsStateWithLifecycle()
    LaunchedEffect(activeUi, subtasks) {
        val aid = activeUi?.taskId
        if (aid != null && subtasks.any { it.id == aid }) {
            selectedSubId = aid
        }
    }

    val subtasksForView = remember(subtasks, nameSort) {
        when (nameSort) {
            NameSort.None -> subtasks
            NameSort.Asc -> subtasks.sortedBy { it.name.lowercase() }
            NameSort.Desc -> subtasks.sortedByDescending { it.name.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back_with_marker))
            }
            Spacer(Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp)
        ) {
            if (subtasksForView.isNotEmpty()) {
                item {
                    TextButton(onClick = { selectedSubId = null; onPick(parentId) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.subtask_none))
                    }
                }
            }
            items(subtasksForView.size) { i ->
                val t = subtasksForView[i]
                val isSelected = selectedSubId == t.id
                val bg = if (isSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                val border = if (isSelected) Color.Red else MaterialTheme.colorScheme.outline
                val textColor = if (isSelected) Color.Red else MaterialTheme.colorScheme.onSurface

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, border, RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onLongClick = { onRenameRequest(t.id, t.name) },
                            onClick = { selectedSubId = t.id; onPick(t.id) }
                        ),
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
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.task_subtask_menu)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.task_rename)) },
                                onClick = {
                                    menuExpanded = false
                                    onRenameRequest(t.id, t.name)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
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
    onSelectBlock: (Long, Long, Long, Long) -> Unit,
    externalRefreshTick: Long = 0L
) {
    LaunchedEffect(Unit) { viewModel.ensureHistoryFirstPage() }

    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val history by viewModel.historyItems.collectAsStateWithLifecycle()
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    val isLoading by viewModel.historyLoading.collectAsStateWithLifecycle()
    val endReached by viewModel.historyEndReached.collectAsStateWithLifecycle()

    val defaultProjectColor = MaterialTheme.colorScheme.onSurface

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(externalRefreshTick) {
        while (true) { nowMs = System.currentTimeMillis(); delay(30_000) }
    }
    val range = com.example.timerecorder.ui.rememberTimelineRange(nowMs)
    val startMs = range.startMs
    val endMs = range.endMs
    val dayCount = range.dayCount

    LaunchedEffect(startMs, endMs, history, isLoading, endReached) {
        // いま読み込めている最古の pressedAt（なければ MAX）
        val earliestLoaded = history.minOfOrNull { it.pressedAt } ?: Long.MAX_VALUE

        // 表示開始よりも「新しい」＝まだ古い履歴が足りない
        if (earliestLoaded > startMs && !isLoading && !endReached) {
            // 1ページずつ追加ロード（Flow更新で再度この Effect が走り、必要分だけ繰り返す）
            viewModel.loadNextHistoryPage()
        }
    }

    // taskId -> 現在のプロジェクト色
    val colorByTaskId by produceState<Map<Long, Color>>(
        initialValue = emptyMap(),
        parents, defaultProjectColor
    ) {
        val base = mutableMapOf<Long, Color>()
        val projColorByChild = mutableMapOf<Long, Color>()
        parents.forEach { g ->
            val pc = g.parent.colorInt?.let { Color(it) } ?: defaultProjectColor
            g.children.forEach { ch ->
                base[ch.id] = pc
                projColorByChild[ch.id] = pc
            }
        }
        value = base
        val subIdsByChild = mutableMapOf<Long, Set<Long>>()
        parents.forEach { g ->
            g.children.forEach { ch ->
                launch {
                    viewModel.children(ch.id).collect { subs ->
                        subIdsByChild[ch.id] = subs.map { it.id }.toSet()
                        val subMap = mutableMapOf<Long, Color>()
                        subIdsByChild.forEach { (childId, ids) ->
                            val pc = projColorByChild[childId] ?: defaultProjectColor
                            ids.forEach { sid -> subMap[sid] = pc }
                        }
                        value = buildMap { putAll(base); putAll(subMap) }
                    }
                }
            }
        }
    }

    fun colorFor(taskId: Long) = colorByTaskId[taskId] ?: defaultProjectColor

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            TimelineRangeControls(range)
            Spacer(Modifier.height(8.dp))

            // 表示するブロックを分割生成
            val blocksByDay: List<List<TimelineBlock>> =
                remember(
                    history,
                    activeUi,
                    parents,
                    startMs,
                    endMs,
                    dayCount,
                    nowMs,
                    colorByTaskId
                ) {
                    val days = List(dayCount) { mutableListOf<TimelineBlock>() }
                    fun addClampedBlock(
                        eventId: Long,
                        s: Long,
                        e: Long,
                        name: String,
                        taskId: Long
                    ) {
                        if (e <= startMs || s >= endMs) return
                        repeat(dayCount) { i ->
                            val dStart = range.dayStart(i)
                            val dEnd = range.dayStart(i + 1)
                            val cs = maxOf(s, dStart)
                            val ce = minOf(e, dEnd)
                            if (ce > cs) days[i].add(
                                TimelineBlock(
                                    eventId = eventId,
                                    startMs = cs,
                                    endMs = ce,
                                    recordStartMs = s,
                                    recordEndMs = e,
                                    name = name,
                                    color = colorFor(taskId),
                                    taskId = taskId
                                )
                            )
                        }
                    }
                    history.forEach { h ->
                        val s = h.pressedAt
                        val e = h.pressedAt + max(0L, h.durationMillis)
                        addClampedBlock(h.id, s, e, h.taskName, h.taskId)
                    }
                    activeUi?.let { a ->
                        val s = a.startEpochMs
                        if (s > 0L) addClampedBlock(-1L, s, nowMs, a.taskName, a.taskId)
                    }
                    days.map { it.sortedBy { b -> b.startMs } }
                }

            var hourHeightDp by rememberSaveable { mutableFloatStateOf(48f) }
            val zoomSensitivity = 1.9f
            fun onScale(rawZoom: Float) {
                if (kotlin.math.abs(rawZoom - 1f) < 0.01f) return
                val adjusted = rawZoom.pow(zoomSensitivity)
                hourHeightDp = (hourHeightDp * adjusted).coerceIn(24f, 200f)
            }

            MultiDayTimeline(
                startMs = startMs,
                dayCount = dayCount,
                blocksByDay = blocksByDay,
                selectedTaskId = selectedTaskId,
                selectedEventId = selectedEventId,
                onSelectEvent = onSelectEvent,
                onSelectTask = onSelectTask,
                onSelectBlock = onSelectBlock,
                hourHeight = hourHeightDp.dp,
                onScale = ::onScale
            )
        }

        FloatingActionButton(
            onClick = onAddRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Filled.AddCircle,
                contentDescription = stringResource(R.string.timeline_add_record)
            )
        }
    }
}


private data class TimelineBlock(
    val eventId: Long,
    val startMs: Long,
    val endMs: Long,
    val name: String,
    val color: Color,
    val taskId: Long,
    // Keep the full record bounds separate from the day-clipped drawing bounds.
    val recordStartMs: Long = startMs,
    val recordEndMs: Long = endMs
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
    onSelectBlock: (Long, Long, Long, Long) -> Unit = { _, _, _, _ -> },
    hourHeight: Dp = 48.dp,
    onScale: (Float) -> Unit = {}
) {
    val GUTTER = 40.dp
    val totalHeight = hourHeight * 24
    val dateFmt = remember { SimpleDateFormat("M/d (E)", Locale.getDefault()) }

    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .height(totalHeight)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        // ★ 外側の pointerInput は削除（単指横スワイプを親へ渡す）
    ) {
        // 左ガター：タップで選択解除
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
                        .height(hourHeight)
                        .width(GUTTER),
                    contentAlignment = Alignment.TopEnd
                ) {
                    if (h in 0..23) {
                        Text(
                            text = String.format(java.util.Locale.getDefault(), "%02d", h),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        }

        // 日カラム側（ここにだけピンチ検出を付ける）
        Row(
            Modifier
                .weight(1f)
                .then(if (dayCount > 7) Modifier.horizontalScroll(rememberScrollState()).width(110.dp * dayCount) else Modifier)
                .fillMaxHeight()
                .pointerInput(dayCount, hourHeight) {
                    awaitEachGesture {
                        // 1) まずは「2本指になるまで」待つ（単指は親のPagerへ）
                        var scaling = false
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.changes.size >= 2) {
                                scaling = true; break
                            }
                            // 1本指のまま指が離れたらスケールなしで終了（イベントを消費しない）
                            if (e.changes.all { !it.pressed }) return@awaitEachGesture
                        }

                        // 2) 2本指になったので以降はズームだけ処理（ここからは消費してOK）
                        var lastDist: Float? = null
                        while (true) {
                            val e = awaitPointerEvent()
                            val changes = e.changes
                            if (changes.size < 2) break  // 2本指でなくなったら終了

                            val p1 = changes[0].position
                            val p2 = changes[1].position
                            val prev1 = changes[0].previousPosition
                            val prev2 = changes[1].previousPosition

                            val dist = (p1 - p2).getDistance()
                            val prevDist = (prev1 - prev2).getDistance()
                            if (prevDist > 0f) {
                                val zoom = dist / prevDist
                                if (kotlin.math.abs(zoom - 1f) > 0.01f) {
                                    onScale(zoom) // ← 親で hourHeight を更新する
                                }
                                // ピンチ中は消費（Pagerに渡さない）
                                changes.forEach { it.consume() }
                            }
                            if (changes.any { !it.pressed }) break
                        }
                    }
                }
        ) {
            repeat(dayCount) { i ->
                val dayStart = com.example.timerecorder.ui.calendarDay(startMs, i)
                val label = dateFmt.format(Date(dayStart))
                DayColumn(
                    label = label,
                    dayStartMs = dayStart,
                    hourHeight = hourHeight,
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
    dayStartMs: Long,
    hourHeight: Dp,
    blocks: List<TimelineBlock>,
    modifier: Modifier = Modifier,
    onSelectEvent: (Long?) -> Unit = {},
    selectedEventId: Long? = null,
    onSelectBlock: (Long, Long, Long, Long) -> Unit = { _: Long, _: Long, _: Long, _: Long -> },
    blockAlpha: Float = 0.6f
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

        fun minutesWithinDay(ms: Long): Int {
            // その日の 00:00 からの経過分を 0..1440 にクランプ
            val m = ((ms - dayStartMs) / 60_000L).toInt()
            return m.coerceIn(0, 24 * 60)
        }

        // 各タスクブロック（ここをタップした時は選択）
        blocks.forEach { b ->
            val startMin = minutesWithinDay(b.startMs)
            val endMin = minutesWithinDay(b.endMs)
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
                    .background(b.color.copy(alpha = blockAlpha), RoundedCornerShape(0.dp))
                    .border(borderWidth, borderColor, RoundedCornerShape(0.dp))
                    .clickable {
                        onSelectEvent(b.eventId)
                        onSelectBlock(b.eventId, b.taskId, b.recordStartMs, b.recordEndMs)
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
    return if (withAlpha) String.format(java.util.Locale.getDefault(), "#%02X%02X%02X%02X", a, r, g, b)
    else String.format(java.util.Locale.getDefault(), "#%02X%02X%02X", r, g, b)
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
        title = { Text(stringResource(R.string.task_color_select)) },
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
            TextButton(onClick = { onConfirm(current) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
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

@SuppressLint("DefaultLocale")
@Composable
private fun StatsScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier,
    includedTaskIds: Set<Long>? = null,
    aggMode: StatsAggMode = StatsAggMode.Leaf
) {
    val statsItems by viewModel.statsItems.collectAsStateWithLifecycle()
    val activeUi by viewModel.activeUi.collectAsStateWithLifecycle()
    val parents by viewModel.parents.collectAsStateWithLifecycle()

    var period by rememberSaveable { mutableStateOf(com.example.timerecorder.ui.StatisticsPeriod.SevenDays) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(period) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val (windowStart, windowEnd) = com.example.timerecorder.ui.statisticsBounds(period, nowMs)

    LaunchedEffect(windowStart, windowEnd) {
        viewModel.loadStatsRange(windowStart, windowEnd)
    }

    val defaultTaskColor = MaterialTheme.colorScheme.primary

    // 親プロジェクト色の解決（子・サブタスクは親プロジェクト色で表示）
    val projectDefault = MaterialTheme.colorScheme.primary
    val colorByTaskId by produceState<Map<Long, Color>>(
        initialValue = emptyMap(),
        parents
    ) {
        val base = mutableMapOf<Long, Color>()
        val projColorByChild = mutableMapOf<Long, Color>()
        parents.forEach { g ->
            val pc = g.parent.colorInt?.let { Color(it) } ?: projectDefault
            g.children.forEach { ch ->
                base[ch.id] = pc
                projColorByChild[ch.id] = pc
            }
        }
        value = base
        val subIdsByChild = mutableMapOf<Long, Set<Long>>()
        parents.forEach { g ->
            g.children.forEach { ch ->
                launch {
                    viewModel.children(ch.id).collect { subs ->
                        subIdsByChild[ch.id] = subs.map { it.id }.toSet()
                        val subMap = mutableMapOf<Long, Color>()
                        subIdsByChild.forEach { (childId, ids) ->
                            val pc = projColorByChild[childId] ?: projectDefault
                            ids.forEach { sid -> subMap[sid] = pc }
                        }
                        value = buildMap {
                            putAll(base)
                            putAll(subMap)
                        }
                    }
                }
            }
        }
    }

    // leaf(子/サブ) → プロジェクト情報
    data class ProjInfo(val id: Long, val name: String, val color: Color)

    val projByTaskId by produceState<Map<Long, ProjInfo>>(initialValue = emptyMap(), parents) {
        val base = mutableMapOf<Long, ProjInfo>()
        parents.forEach { g ->
            val pc = g.parent.colorInt?.let { Color(it) } ?: projectDefault
            val pInfo = ProjInfo(g.parent.id, g.parent.name, pc)
            g.children.forEach { ch ->
                base[ch.id] = pInfo
                launch {
                    viewModel.children(ch.id).collect { subs ->
                        subs.forEach { sub -> base[sub.id] = pInfo }
                        value = base.toMap()
                    }
                }
            }
        }
        value = base.toMap()
    }

    // leaf(子/サブ) → 子タスク情報（タスク集計用）
    data class ChildInfo(val id: Long, val name: String, val projColor: Color)

    val childByLeafId by produceState<Map<Long, ChildInfo>>(initialValue = emptyMap(), parents) {
        val base = mutableMapOf<Long, ChildInfo>()
        parents.forEach { g ->
            val pc = g.parent.colorInt?.let { Color(it) } ?: projectDefault
            g.children.forEach { ch ->
                val cInfo = ChildInfo(ch.id, ch.name, pc)
                base[ch.id] = cInfo
                launch {
                    viewModel.children(ch.id).collect { subs ->
                        subs.forEach { sub -> base[sub.id] = cInfo }
                        value = base.toMap()
                    }
                }
            }
        }
        value = base.toMap()
    }

    fun colorFor(taskId: Long, fallback: Color): Color = colorByTaskId[taskId] ?: fallback

    data class Agg(val taskId: Long, val name: String, val color: Color, val totalMs: Long)

    val aggregated: List<Agg> = remember(
        statsItems,
        activeUi,
        parents,
        windowStart,
        windowEnd,
        includedTaskIds,
        aggMode,
        projByTaskId,
        childByLeafId,
        colorByTaskId
    ) {
        val records = buildList {
            statsItems.forEach { row ->
                add(
                    StatsRecord(
                        taskId = row.taskId,
                        taskName = row.taskName,
                        startMs = row.pressedAt,
                        endMs = row.pressedAt + max(0L, row.durationMillis)
                    )
                )
            }
            activeUi?.let { active ->
                add(
                    StatsRecord(
                        taskId = active.taskId,
                        taskName = active.taskName,
                        startMs = active.startEpochMs,
                        endMs = windowEnd
                    )
                )
            }
        }

        StatsAggregator.aggregate(
            records = records,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            includedTaskIds = includedTaskIds
        ) { record ->
            when (aggMode) {
                StatsAggMode.Project -> projByTaskId[record.taskId]
                    ?.let { StatsTarget(it.id, it.name) }
                    ?: StatsTarget(record.taskId, record.taskName)

                StatsAggMode.Task -> childByLeafId[record.taskId]
                    ?.let { StatsTarget(it.id, it.name) }
                    ?: StatsTarget(record.taskId, record.taskName)

                StatsAggMode.Leaf -> StatsTarget(record.taskId, record.taskName)
            }
        }.map { total ->
            val color = when (aggMode) {
                StatsAggMode.Project -> parents
                    .firstOrNull { it.parent.id == total.id }
                    ?.parent?.colorInt?.let { Color(it) }
                    ?: colorFor(total.id, defaultTaskColor)

                StatsAggMode.Task -> parents
                    .firstOrNull { group -> group.children.any { it.id == total.id } }
                    ?.parent?.colorInt?.let { Color(it) }
                    ?: colorFor(total.id, defaultTaskColor)

                StatsAggMode.Leaf -> colorFor(total.id, defaultTaskColor)
            }
            Agg(total.id, total.name, color, total.totalMs)
        }
    }

    val totalAll = aggregated.sumOf { it.totalMs }.coerceAtLeast(1L)
    val scroll = rememberScrollState()

    fun formatHhMm(ms: Long): String {
        val totalMin = (ms / 60_000L).toInt()
        val hh = totalMin / 60
        val mm = totalMin % 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", hh, mm)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scroll)
    ) {
        Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.example.timerecorder.ui.StatisticsPeriod.entries.forEach { option ->
                SegButton(stringResource(option.label), active = period == option) { period = option }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.stats_selected_period_summary,
                stringResource(period.label),
                totalAll / 1_000L / 60L
            ),
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
                    Text(stringResource(R.string.stats_no_data))
                }
            }
        } else {
            val items = aggregated

            BarChart(
                entries = items.map { it.name to it.totalMs.toFloat() },
                colors = items.map { it.color },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)   // 余白いっぱいに確保（中でスクロール）
            )
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
    val totalVal = entries.sumOf { it.second.toDouble() }.coerceAtLeast(1.0)
    val scroll = rememberScrollState()

    fun hhmm(ms: Float): String {
        val totalSec = (ms / 1000f).toLong().coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m)
    }

    Column(modifier = modifier.verticalScroll(scroll)) {
        entries.forEachIndexed { index, (label, value) ->
            val ratio = (value / maxVal).coerceIn(0f, 1f)
            val barColor = colors.getOrNull(index) ?: MaterialTheme.colorScheme.primary
            val pct = ((value / totalVal) * 100.0).roundToInt()
            val displayLabel = stringResource(
                R.string.stats_bar_label,
                label,
                hhmm(value),
                pct
            )

            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = displayLabel,
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

    LaunchedEffect(listState, isLoading, endReached) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 5
        }
            .distinctUntilChanged()
            .collectLatest { nearEnd ->
                if (nearEnd && !isLoading && !endReached) {
                    viewModel.loadNextHistoryPage()
                }
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
                        ) { Text(stringResource(R.string.history_end)) }
                    }
                }
            }
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
                String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds),
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
    onDeleteAction: () -> Unit = {},
    showExtraIcon: Boolean = false,
    onExtraAction: () -> Unit = {},
    timelineSpanText: String? = null,
    onFilterAction: () -> Unit = {},
    onAggCycleAction: () -> Unit = {},
    aggMode: StatsAggMode = StatsAggMode.Leaf
) {
    when (currentPage) {
        0 -> {
            BottomAppBar {
                if (timelineSelected) {
                    IconButton(onClick = onActionA) {
                        Icon(
                            painter = painterResource(id = R.drawable.content_cut_24px),
                            contentDescription = stringResource(R.string.bottom_split_record)
                        )
                    }
                    IconButton(onClick = onActionB) {
                        Icon(
                            painter = painterResource(id = R.drawable.edit_24px),
                            contentDescription = stringResource(R.string.bottom_edit_record)
                        )
                    }
                    IconButton(onClick = onDeleteAction) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.bottom_delete_record)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (timelineSpanText != null) {
                        Text(
                            timelineSpanText,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            }
        }

        1 -> {
            BottomAppBar(
                actions = {
                    IconButton(onClick = onSortAction) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.bottom_sort)
                        )
                    }
                    if (showExtraIcon) {
                        IconButton(onClick = onExtraAction) {
                            // ★ 仮アイコン：設定アイコン（後でカスタムに差し替え）
                            Icon(
                                painter = painterResource(id = R.drawable.sort_by_alpha_24px),
                                contentDescription = stringResource(R.string.bottom_sort_by_name)
                            )
                        }
                    }
                }
            )
        }

        2 -> {
            BottomAppBar {
                IconButton(onClick = onFilterAction) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = stringResource(R.string.bottom_filter)
                    )
                }
                Spacer(Modifier.weight(1f))
                // 3モード巡回（アイコンは共通、CD に現在モード名）
                IconButton(onClick = onAggCycleAction) {
                    val cd = when (aggMode) {
                        StatsAggMode.Project -> stringResource(
                            R.string.bottom_aggregate_project
                        )
                        StatsAggMode.Task -> stringResource(R.string.bottom_aggregate_task)
                        StatsAggMode.Leaf -> stringResource(R.string.bottom_aggregate_leaf)
                    }
                    Icon(Icons.Filled.Dashboard, contentDescription = cd)
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
                text = stringResource(R.string.label_select_task),
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

private suspend fun readImportFile(
    context: Context,
    uri: android.net.Uri
): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?.removePrefix("\uFEFF")
        ?: error(context.getString(R.string.import_file_open_failed))
}

private fun writeTsvToCacheAndGetUri(
    context: Context,
    content: String,
    startMs: Long,
    endExclusiveMs: Long
): android.net.Uri {
    val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
    val endForName = (endExclusiveMs - 1L).coerceAtLeast(startMs)
    val file = java.io.File(
        dir,
        "timerecorder_${fmt.format(java.util.Date(startMs))}_${fmt.format(java.util.Date(endForName))}.tsv"
    )
    file.writeText(content, Charsets.UTF_8)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

private fun monthStartMillis(offsetMonths: Int): Long {
    return java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.MONTH, offsetMonths)
    }.timeInMillis
}

private fun monthLabel(monthStartMs: Long): String {
    return java.text.SimpleDateFormat("yyyy/MM", java.util.Locale.getDefault())
        .format(java.util.Date(monthStartMs))
}

private fun dayStartMillis(ms: Long): Long {
    return java.util.Calendar.getInstance().apply {
        timeInMillis = ms
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
private fun ExportTsvRangeDialog(
    taskVm: TaskViewModel,
    onDismiss: () -> Unit,
    onExport: (Long, Long) -> Unit
) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEnd by rememberSaveable { mutableStateOf<Long?>(null) }

    val monthStart = remember(monthOffset) { monthStartMillis(monthOffset) }
    val monthEndExclusive = remember(monthStart) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = monthStart
            add(java.util.Calendar.MONTH, 1)
        }.timeInMillis
    }

    val markedDays by produceState<Set<Long>>(initialValue = emptySet(), monthStart) {
        value = taskVm.getDaysWithData(monthStart, monthEndExclusive)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_tsv_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_tsv_range_description))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { monthOffset -= 1 }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(R.string.calendar_previous_month)
                        )
                    }
                    Text(monthLabel(monthStart), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { monthOffset += 1 }) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.calendar_next_month)
                        )
                    }
                }
                ExportCalendarMonth(
                    monthStartMs = monthStart,
                    markedDays = markedDays,
                    selectedStart = selectedStart,
                    selectedEnd = selectedEnd,
                    onDayClick = { clicked ->
                        when {
                            selectedStart == null || (selectedStart != null && selectedEnd != null) -> {
                                selectedStart = clicked
                                selectedEnd = null
                            }

                            clicked < selectedStart!! -> {
                                selectedStart = clicked
                            }

                            else -> {
                                selectedEnd = clicked
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedStart != null && selectedEnd != null,
                onClick = {
                    val start = dayStartMillis(selectedStart!!)
                    val endExclusive = java.util.Calendar.getInstance().apply {
                        timeInMillis = dayStartMillis(selectedEnd!!)
                        add(java.util.Calendar.DAY_OF_MONTH, 1)
                    }.timeInMillis
                    onExport(start, endExclusive)
                }
            ) { Text(stringResource(R.string.action_export)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ExportCalendarMonth(
    monthStartMs: Long,
    markedDays: Set<Long>,
    selectedStart: Long?,
    selectedEnd: Long?,
    onDayClick: (Long) -> Unit
) {
    val monthCal = remember(monthStartMs) {
        java.util.Calendar.getInstance().apply { timeInMillis = monthStartMs }
    }
    val firstDayOfWeek = monthCal.get(java.util.Calendar.DAY_OF_WEEK)
    val offset = (firstDayOfWeek + 5) % 7
    val daysInMonth = monthCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val weekLabels = stringArrayResource(R.array.weekday_short_labels)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val totalCells = ((offset + daysInMonth + 6) / 7) * 7
        for (row in 0 until totalCells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val day = index - offset + 1
                    if (day !in 1..daysInMonth) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                        )
                    } else {
                        val cal = java.util.Calendar.getInstance().apply {
                            timeInMillis = monthStartMs
                            set(java.util.Calendar.DAY_OF_MONTH, day)
                        }
                        val dayMs = dayStartMillis(cal.timeInMillis)
                        val start = selectedStart?.let { dayStartMillis(it) }
                        val end = selectedEnd?.let { dayStartMillis(it) }
                        val isBoundary = dayMs == start || dayMs == end
                        val inRange = start != null && end != null && dayMs in start..end
                        val hasData = markedDays.contains(dayMs)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .background(
                                    color = when {
                                        isBoundary -> MaterialTheme.colorScheme.primary
                                        inRange -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (hasData) 1.dp else 0.dp,
                                    color = if (hasData) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onDayClick(dayMs) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.toString(),
                                    color = if (isBoundary) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (hasData) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(6.dp)
                                            .background(
                                                color = if (isBoundary) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.tertiary
                                                },
                                                shape = RoundedCornerShape(50)
                                            )
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
            Text(
                stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    R.string.stats_period_summary,
                    7,
                    sampleVals.sum().toLong()
                ),
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
                    Text(
                        stringResource(R.string.label_minutes, value.toInt()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
