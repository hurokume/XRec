package com.example.timerecorder.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

enum class TaskAnalysisPeriod {
    Day,
    Week,
    Month,
    Year,
    Custom
}

data class TaskAnalysisRequest(
    val taskId: Long,
    val taskName: String,
    val period: TaskAnalysisPeriod,
    val anchorMs: Long,
    val nowMs: Long = System.currentTimeMillis(),
    val timeZoneId: String = TimeZone.getDefault().id,
    val taskIds: Set<Long> = setOf(taskId),
    val customStartMs: Long? = null,
    val customEndMs: Long? = null
)

data class TaskAnalysisRange(
    val startMs: Long,
    val endMs: Long
)

data class TaskAnalysisBucket(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val totalMs: Long,
    val categoryTotals: Map<Long, Long>
)

data class TaskAnalysisCategory(
    val id: Long,
    val name: String,
    val colorInt: Int?
)

data class TaskAnalysisEntry(
    val eventId: Long,
    val taskId: Long,
    val taskLabel: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
)

data class TaskAnalysisDayGroup(
    val dayStartMs: Long,
    val totalMs: Long,
    val entries: List<TaskAnalysisEntry>
)

data class TaskAnalysisHeatCell(
    val dayOfWeekIndex: Int,
    val hourOfDay: Int,
    val probability: Double
)

data class TaskAnalysisResult(
    val request: TaskAnalysisRequest,
    val range: TaskAnalysisRange,
    val totalMs: Long,
    val averagePerBucketMs: Long,
    val movingAverageMs: List<Long?>,
    val buckets: List<TaskAnalysisBucket>,
    val categories: List<TaskAnalysisCategory>,
    val heatCells: List<TaskAnalysisHeatCell>,
    val dayGroups: List<TaskAnalysisDayGroup>
)

/**
 * タスク別分析の計算を UI から分離する。
 *
 * 選択タスク自身と、その配下に属する全記録を集計対象にする。日をまたぐ記録は
 * 日別明細とヒートマップの境界で分割し、表示期間の外側は切り落とす。
 */
object TaskAnalysisEngine {
    fun movingAverageWindow(period: TaskAnalysisPeriod): Int = when (period) {
        TaskAnalysisPeriod.Day -> 3
        TaskAnalysisPeriod.Week -> 3
        TaskAnalysisPeriod.Month -> 7
        TaskAnalysisPeriod.Year -> 3
        TaskAnalysisPeriod.Custom -> 7
    }

    /** 完了した窓だけを描画する末尾基準の単純移動平均。 */
    fun movingAverage(values: List<Long>, windowSize: Int): List<Long?> {
        require(windowSize > 0) { "windowSize must be positive" }
        var runningTotal = 0L
        return values.mapIndexed { index, value ->
            runningTotal += value
            if (index >= windowSize) runningTotal -= values[index - windowSize]
            if (index + 1 < windowSize) null else runningTotal / windowSize
        }
    }

    /** 最大値を1として0〜1へ正規化する。全要素が0なら0のまま返す。 */
    fun normalizeToMaximum(values: List<Double>): List<Double> {
        val maximum = values.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
        if (maximum <= 0.0) return List(values.size) { 0.0 }
        return values.map { (it / maximum).coerceIn(0.0, 1.0) }
    }

    /** 現在を含むバケットまでを累積し、それより後のバケットは0にする。 */
    fun cumulativeCategoryTotals(
        buckets: List<TaskAnalysisBucket>,
        completedThroughMs: Long
    ): List<Map<Long, Long>> {
        val running = linkedMapOf<Long, Long>()
        return buckets.map { bucket ->
            if (bucket.startMs >= completedThroughMs) {
                emptyMap()
            } else {
                bucket.categoryTotals.forEach { (id, duration) ->
                    running[id] = (running[id] ?: 0L) + duration
                }
                running.toMap()
            }
        }
    }

    fun rangeFor(request: TaskAnalysisRequest): TaskAnalysisRange =
        rangeFor(
            period = request.period,
            anchorMs = request.anchorMs,
            timeZoneId = request.timeZoneId,
            customStartMs = request.customStartMs,
            customEndMs = request.customEndMs
        )

    fun rangeFor(
        period: TaskAnalysisPeriod,
        anchorMs: Long,
        timeZoneId: String = TimeZone.getDefault().id,
        customStartMs: Long? = null,
        customEndMs: Long? = null
    ): TaskAnalysisRange {
        if (period == TaskAnalysisPeriod.Custom) {
            val startMs = requireNotNull(customStartMs)
            val endMs = requireNotNull(customEndMs)
            require(endMs > startMs)
            return TaskAnalysisRange(startMs, endMs)
        }
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = anchorMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (period) {
                TaskAnalysisPeriod.Day -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                }

                TaskAnalysisPeriod.Week -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    val daysFromMonday =
                        (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                    add(Calendar.DAY_OF_MONTH, -daysFromMonday)
                }

                TaskAnalysisPeriod.Month -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                }

                TaskAnalysisPeriod.Year -> {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                }

                TaskAnalysisPeriod.Custom -> Unit
            }
        }
        val end = start.clone() as Calendar
        when (period) {
            TaskAnalysisPeriod.Day -> end.add(Calendar.DAY_OF_MONTH, 1)
            TaskAnalysisPeriod.Week -> end.add(Calendar.WEEK_OF_YEAR, 1)
            TaskAnalysisPeriod.Month -> end.add(Calendar.MONTH, 1)
            TaskAnalysisPeriod.Year -> end.add(Calendar.YEAR, 1)
            TaskAnalysisPeriod.Custom -> Unit
        }
        return TaskAnalysisRange(start.timeInMillis, end.timeInMillis)
    }

    fun movingAverageLookbackStart(request: TaskAnalysisRequest): Long {
        val range = rangeFor(request)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(request.timeZoneId)).apply {
            timeInMillis = range.startMs
        }
        val lookbackBuckets = movingAverageWindow(request.period) - 1
        when (request.period) {
            TaskAnalysisPeriod.Day -> calendar.add(Calendar.HOUR_OF_DAY, -lookbackBuckets)
            TaskAnalysisPeriod.Week,
            TaskAnalysisPeriod.Month,
            TaskAnalysisPeriod.Custom -> calendar.add(Calendar.DAY_OF_MONTH, -lookbackBuckets)

            TaskAnalysisPeriod.Year -> calendar.add(Calendar.MONTH, -lookbackBuckets)
        }
        return calendar.timeInMillis
    }

    fun shiftAnchor(request: TaskAnalysisRequest, amount: Int): Long {
        val timeZone = TimeZone.getTimeZone(request.timeZoneId)
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = request.anchorMs
            when (request.period) {
                TaskAnalysisPeriod.Day -> add(Calendar.DAY_OF_MONTH, amount)
                TaskAnalysisPeriod.Week -> add(Calendar.WEEK_OF_YEAR, amount)
                TaskAnalysisPeriod.Month -> add(Calendar.MONTH, amount)
                TaskAnalysisPeriod.Year -> add(Calendar.YEAR, amount)
                TaskAnalysisPeriod.Custom -> Unit
            }
        }.timeInMillis
    }

    fun analyze(
        events: List<AnalysisEvent>,
        request: TaskAnalysisRequest
    ): TaskAnalysisResult {
        val range = rangeFor(request)
        val completedThroughMs = min(request.nowMs, range.endMs)
        val lookbackStartMs = movingAverageLookbackStart(request)
        val selectedEvents = events.mapNotNull { event ->
            // 複数選択で祖先と子孫がともに含まれる場合、より具体的な子孫側を採用する。
            val selectedIndex = event.path.indexOfLast { it.id in request.taskIds }
            if (selectedIndex < 0) return@mapNotNull null
            val start = max(event.startMs, lookbackStartMs)
            val end = min(event.endMs, completedThroughMs)
            if (end <= start) return@mapNotNull null
            SelectedEvent(event, selectedIndex, start, end)
        }
        val clippedEvents = selectedEvents.mapNotNull { selected ->
            val start = max(selected.startMs, range.startMs)
            val end = min(selected.endMs, range.endMs)
            if (end <= start) null else selected.copy(startMs = start, endMs = end)
        }

        val categoriesById = linkedMapOf<Long, TaskAnalysisCategory>()
        clippedEvents.forEach { selected ->
            val node = selected.categoryNode
            categoriesById.putIfAbsent(
                node.id,
                TaskAnalysisCategory(node.id, node.name, node.colorInt)
            )
        }

        val boundaries = bucketBoundaries(request.period, range, request.timeZoneId)
        val buckets = boundaries.zipWithNext().mapIndexed { index, (start, end) ->
            val categoryTotals = linkedMapOf<Long, Long>()
            clippedEvents.forEach { selected ->
                val overlap = overlapMs(selected.startMs, selected.endMs, start, end)
                if (overlap > 0L) {
                    categoryTotals[selected.categoryNode.id] =
                        (categoryTotals[selected.categoryNode.id] ?: 0L) + overlap
                }
            }
            TaskAnalysisBucket(
                startMs = start,
                endMs = end,
                label = bucketLabel(request.period, index, start, request.timeZoneId),
                totalMs = categoryTotals.values.sum(),
                categoryTotals = categoryTotals
            )
        }

        val dayGroups = buildDayGroups(clippedEvents, request.timeZoneId)
        val heatCells = buildHeatCells(clippedEvents, range, request.timeZoneId)
        val totalMs = buckets.sumOf { it.totalMs }
        // Average calendar totals, including empty buckets and the current partial bucket.
        // Future hours/days/months have not contributed an observation yet.
        val elapsedBuckets = buckets.filter { it.startMs < completedThroughMs }
        val averagePerBucketMs = if (elapsedBuckets.isEmpty()) {
            0L
        } else {
            elapsedBuckets.sumOf { it.totalMs } / elapsedBuckets.size
        }
        val movingBoundaries = bucketBoundaries(
            period = request.period,
            range = TaskAnalysisRange(lookbackStartMs, range.endMs),
            timeZoneId = request.timeZoneId
        )
        val movingTotals = movingBoundaries.zipWithNext().map { (start, end) ->
            selectedEvents.sumOf { selected ->
                overlapMs(
                    selected.startMs,
                    min(selected.endMs, completedThroughMs),
                    start,
                    end
                )
            }
        }
        val movingAverage = movingAverage(
            values = movingTotals,
            windowSize = movingAverageWindow(request.period)
        ).takeLast(buckets.size).mapIndexed { index, value ->
            value.takeIf { buckets[index].startMs < completedThroughMs }
        }
        val categoryTotals = buckets
            .flatMap { it.categoryTotals.entries }
            .groupingBy { it.key }
            .fold(0L) { total, entry -> total + entry.value }
        val sortedCategories = categoriesById.values.sortedWith(
            compareByDescending<TaskAnalysisCategory> { categoryTotals[it.id] ?: 0L }
                .thenBy { it.name }
                .thenBy { it.id }
        )

        return TaskAnalysisResult(
            request = request,
            range = range,
            totalMs = totalMs,
            averagePerBucketMs = averagePerBucketMs,
            movingAverageMs = movingAverage,
            buckets = buckets,
            categories = sortedCategories,
            heatCells = heatCells,
            dayGroups = dayGroups
        )
    }

    private data class SelectedEvent(
        val source: AnalysisEvent,
        val selectedPathIndex: Int,
        val startMs: Long,
        val endMs: Long
    ) {
        val categoryNode: AnalysisCategoryNode
            get() = source.path.getOrElse(selectedPathIndex + 1) {
                source.path[selectedPathIndex]
            }

        val detailLabel: String
            get() {
                val descendants = source.path.drop(selectedPathIndex + 1)
                return if (descendants.isEmpty()) {
                    source.path[selectedPathIndex].name
                } else {
                    descendants.joinToString(" / ") { it.name }
                }
            }
    }

    private fun bucketBoundaries(
        period: TaskAnalysisPeriod,
        range: TaskAnalysisRange,
        timeZoneId: String
    ): List<Long> {
        val field = when (period) {
            TaskAnalysisPeriod.Day -> Calendar.HOUR_OF_DAY
            TaskAnalysisPeriod.Week,
            TaskAnalysisPeriod.Month,
            TaskAnalysisPeriod.Custom -> Calendar.DAY_OF_MONTH

            TaskAnalysisPeriod.Year -> Calendar.MONTH
        }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId)).apply {
            timeInMillis = range.startMs
        }
        return buildList {
            add(calendar.timeInMillis)
            while (calendar.timeInMillis < range.endMs) {
                calendar.add(field, 1)
                add(min(calendar.timeInMillis, range.endMs))
            }
        }.distinct()
    }

    private fun bucketLabel(
        period: TaskAnalysisPeriod,
        index: Int,
        startMs: Long,
        timeZoneId: String
    ): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId)).apply {
            timeInMillis = startMs
        }
        return when (period) {
            TaskAnalysisPeriod.Day -> String.format(java.util.Locale.getDefault(), "%02d", calendar.get(Calendar.HOUR_OF_DAY))
            TaskAnalysisPeriod.Week -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).apply {
                timeZone = calendar.timeZone
            }.format(calendar.time)
            TaskAnalysisPeriod.Month -> calendar.get(Calendar.DAY_OF_MONTH).toString()
            TaskAnalysisPeriod.Year -> java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).apply {
                timeZone = calendar.timeZone
            }.format(calendar.time)
            TaskAnalysisPeriod.Custom ->
                "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
        }
    }

    private fun buildDayGroups(
        events: List<SelectedEvent>,
        timeZoneId: String
    ): List<TaskAnalysisDayGroup> {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val groups = linkedMapOf<Long, MutableList<TaskAnalysisEntry>>()
        events.forEach { selected ->
            val dayStart = startOfDay(selected.startMs, timeZone)
            groups.getOrPut(dayStart) { mutableListOf() } += TaskAnalysisEntry(
                eventId = selected.source.eventId,
                taskId = selected.source.taskId,
                taskLabel = selected.detailLabel,
                startMs = selected.startMs,
                endMs = selected.endMs,
                durationMs = (selected.endMs - selected.startMs).coerceAtLeast(0L)
            )
        }
        return groups.entries
            .sortedByDescending { it.key }
            .map { (dayStart, entries) ->
                val sorted = entries.filter { it.durationMs > 0L }.sortedBy { it.startMs }
                TaskAnalysisDayGroup(dayStart, sorted.sumOf { it.durationMs }, sorted)
            }
            .filter { it.entries.isNotEmpty() }
    }

    private fun buildHeatCells(
        events: List<SelectedEvent>,
        range: TaskAnalysisRange,
        timeZoneId: String
    ): List<TaskAnalysisHeatCell> {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val observed = Array(7) { IntArray(24) }
        val denominator = IntArray(7)
        var dayStart = startOfDay(range.startMs, timeZone)

        while (dayStart < range.endMs) {
            val dayCalendar = Calendar.getInstance(timeZone).apply { timeInMillis = dayStart }
            val weekday = mondayBasedWeekday(dayCalendar.get(Calendar.DAY_OF_WEEK))
            denominator[weekday] += 1
            for (hour in 0 until 24) {
                val hourStartCalendar = dayCalendar.clone() as Calendar
                hourStartCalendar.set(Calendar.HOUR_OF_DAY, hour)
                val hourStart = max(hourStartCalendar.timeInMillis, range.startMs)
                val hourEndCalendar = hourStartCalendar.clone() as Calendar
                hourEndCalendar.add(Calendar.HOUR_OF_DAY, 1)
                val hourEnd = min(hourEndCalendar.timeInMillis, range.endMs)
                if (hourEnd > hourStart && events.any {
                        overlapMs(it.startMs, it.endMs, hourStart, hourEnd) > 0L
                    }
                ) {
                    observed[weekday][hour] += 1
                }
            }
            dayCalendar.add(Calendar.DAY_OF_MONTH, 1)
            dayStart = dayCalendar.timeInMillis
        }

        return buildList {
            for (weekday in 0 until 7) {
                for (hour in 0 until 24) {
                    add(
                        TaskAnalysisHeatCell(
                            dayOfWeekIndex = weekday,
                            hourOfDay = hour,
                            probability = if (denominator[weekday] == 0) 0.0 else
                                observed[weekday][hour].toDouble() / denominator[weekday]
                        )
                    )
                }
            }
        }
    }

    private fun startOfDay(valueMs: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = valueMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun mondayBasedWeekday(calendarDay: Int): Int =
        (calendarDay - Calendar.MONDAY + 7) % 7

    private fun overlapMs(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long =
        (min(aEnd, bEnd) - max(aStart, bStart)).coerceAtLeast(0L)
}
