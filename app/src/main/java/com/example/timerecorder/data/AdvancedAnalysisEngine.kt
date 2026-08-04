package com.example.timerecorder.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ln

enum class AnalysisLevel { Large, Middle, Small }

enum class AnalysisDayType { All, Weekday, Weekend }

data class AnalysisRequest(
    val nowMs: Long,
    val days: Int = 30,
    val dayBoundaryMinutes: Int = 240,
    val slotMinutes: Int = 30,
    val level: AnalysisLevel = AnalysisLevel.Large,
    val dayType: AnalysisDayType = AnalysisDayType.All,
    val timeZoneId: String = TimeZone.getDefault().id
)

data class AnalysisRange(val startMs: Long, val endMs: Long)

data class AnalysisCategoryNode(val id: Long, val name: String)

data class AnalysisEvent(
    val eventId: Long,
    val taskId: Long,
    val path: List<AnalysisCategoryNode>,
    val startMs: Long,
    val endMs: Long
)

data class AnalysisQuality(
    val calendarDays: Int,
    val analyzedDays: Int,
    val expectedMs: Long,
    val recordedMs: Long,
    val missingMs: Long,
    val overlapMs: Long,
    val unspecifiedMs: Long,
    val shortEventCount: Int,
    val daysAtLeast95Percent: Int
) {
    val coverageRate: Double
        get() = if (expectedMs > 0L) recordedMs.toDouble() / expectedMs else 0.0
}

data class AnalysisCategoryMetric(
    val id: Long,
    val name: String,
    val pathLabel: String,
    val totalMs: Long,
    val averagePerDayMs: Long,
    val medianPerDayMs: Long,
    val firstQuartileMs: Long,
    val thirdQuartileMs: Long,
    val share: Double,
    val parentShare: Double,
    val sessionCount: Int,
    val occurrencesPerDay: Double,
    val averageSessionMs: Long,
    val maxSessionMs: Long,
    val activeDayRate: Double,
    val fragmentationPerHour: Double,
    val cumulativeShare: Double
)

data class AnalysisSlotCategory(
    val id: Long,
    val name: String,
    val pathLabel: String,
    val probabilities: List<Double>
)

data class AnalysisTimeProfile(
    val slotMinutes: Int,
    val boundaryMinutes: Int,
    val categories: List<AnalysisSlotCategory>,
    val modalLabels: List<String?>,
    val regularity: Double,
    val averageEntropy: Double
)

data class AnalysisRepresentativeDay(
    val dayStartMs: Long,
    val meanDistance: Double,
    val coverageRate: Double
)

data class AnalysisTypicalPattern(
    val medoidDayStartMs: Long,
    val dayCount: Int,
    val share: Double,
    val weekdayCount: Int,
    val weekendCount: Int
)

data class AnalysisTransition(
    val fromId: Long,
    val fromName: String,
    val toId: Long,
    val toName: String,
    val count: Int,
    val probability: Double
)

data class AdvancedAnalysisResult(
    val range: AnalysisRange,
    val quality: AnalysisQuality,
    val categoryMetrics: List<AnalysisCategoryMetric>,
    val timeProfile: AnalysisTimeProfile,
    val representativeDay: AnalysisRepresentativeDay?,
    val typicalPatterns: List<AnalysisTypicalPattern>,
    val transitions: List<AnalysisTransition>
)

object AdvancedAnalysisEngine {
    private const val SHORT_EVENT_MS = 60_000L
    private const val CONTIGUOUS_GAP_MS = 60_000L

    private data class DayWindow(
        val startMs: Long,
        val endMs: Long,
        val isWeekend: Boolean
    )

    private data class Target(
        val id: Long,
        val name: String,
        val pathLabel: String,
        val parentId: Long?
    )

    private data class Slice(
        val event: AnalysisEvent,
        val target: Target,
        val dayStartMs: Long,
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    private data class Interval(val startMs: Long, val endMs: Long)

    private data class SlotAssignment(
        val target: Target,
        val path: List<AnalysisCategoryNode>
    )

    fun rangeFor(request: AnalysisRequest): AnalysisRange {
        require(request.days > 0) { "days must be positive" }
        require(request.dayBoundaryMinutes in 0 until 24 * 60) {
            "day boundary must be within a day"
        }
        require(request.slotMinutes > 0 && 1440 % request.slotMinutes == 0) {
            "slot minutes must divide 1440"
        }

        val timeZone = TimeZone.getTimeZone(request.timeZoneId)
        val end = analysisDayStart(request.nowMs, request.dayBoundaryMinutes, timeZone)
        val start = addCalendarDays(end, -request.days, timeZone)
        return AnalysisRange(start, end)
    }

    fun analyze(
        events: List<AnalysisEvent>,
        request: AnalysisRequest
    ): AdvancedAnalysisResult {
        val range = rangeFor(request)
        val timeZone = TimeZone.getTimeZone(request.timeZoneId)
        val allWindows = buildDayWindows(range, timeZone)
        val windows = allWindows.filter { window ->
            when (request.dayType) {
                AnalysisDayType.All -> true
                AnalysisDayType.Weekday -> !window.isWeekend
                AnalysisDayType.Weekend -> window.isWeekend
            }
        }
        val windowByStart = windows.associateBy { it.startMs }
        val targetCache = mutableMapOf<Long, Target>()
        fun targetFor(event: AnalysisEvent): Target = targetCache.getOrPut(event.taskId) {
            resolveTarget(event.path, request.level)
        }

        val validEvents = events
            .asSequence()
            .filter { it.path.isNotEmpty() && it.endMs > it.startMs }
            .filter { it.endMs > range.startMs && it.startMs < range.endMs }
            .toList()

        val slices = buildList {
            validEvents.forEach { event ->
                windows.forEach { day ->
                    val start = maxOf(event.startMs, day.startMs)
                    val end = minOf(event.endMs, day.endMs)
                    if (end > start) {
                        add(Slice(event, targetFor(event), day.startMs, start, end))
                    }
                }
            }
        }

        val quality = calculateQuality(
            events = validEvents,
            slices = slices,
            windows = windows,
            calendarDays = allWindows.size,
            level = request.level
        )
        val categoryMetrics = calculateCategoryMetrics(slices, windows)
        val slotProfiles = buildSlotProfiles(slices, windows, request.slotMinutes)
        val timeProfile = calculateTimeProfile(
            slotProfiles = slotProfiles,
            categoryMetrics = categoryMetrics,
            slotMinutes = request.slotMinutes,
            boundaryMinutes = request.dayBoundaryMinutes
        )
        // 完全欠損日は「代表的な生活日」の候補にしない。
        val validProfileIndices = slotProfiles.indices.filter { dayIndex ->
            slotProfiles[dayIndex].any { it != null }
        }
        val validProfiles = validProfileIndices.map(slotProfiles::get)
        val validProfileWindows = validProfileIndices.map(windows::get)
        val distanceMatrix = buildDistanceMatrix(validProfiles)
        val representativeIndex = findMedoidIndex(distanceMatrix)
        val slicesByDay = slices.groupBy { it.dayStartMs }
        val coverageByDay = windows.associate { window ->
            val union = mergeIntervals(
                slicesByDay[window.startMs].orEmpty()
                    .map { Interval(it.startMs, it.endMs) }
            ).sumOf { it.endMs - it.startMs }
            window.startMs to if (window.endMs > window.startMs) {
                union.toDouble() / (window.endMs - window.startMs)
            } else {
                0.0
            }
        }
        val representativeDay = representativeIndex?.let { index ->
            val meanDistance = if (distanceMatrix.size <= 1) 0.0 else {
                distanceMatrix[index].sum() / (distanceMatrix.size - 1)
            }
            AnalysisRepresentativeDay(
                dayStartMs = validProfileWindows[index].startMs,
                meanDistance = meanDistance,
                coverageRate = coverageByDay[validProfileWindows[index].startMs] ?: 0.0
            )
        }
        val patterns = buildTypicalPatterns(distanceMatrix, validProfileWindows, representativeIndex)
        val transitions = calculateTransitions(validEvents, windows, ::targetFor)

        // Referencing the map here also guarantees that only selected analysis days contribute.
        check(slices.all { it.dayStartMs in windowByStart })

        return AdvancedAnalysisResult(
            range = range,
            quality = quality,
            categoryMetrics = categoryMetrics,
            timeProfile = timeProfile,
            representativeDay = representativeDay,
            typicalPatterns = patterns,
            transitions = transitions
        )
    }

    private fun buildDayWindows(range: AnalysisRange, timeZone: TimeZone): List<DayWindow> {
        val result = mutableListOf<DayWindow>()
        var start = range.startMs
        while (start < range.endMs) {
            val end = minOf(addCalendarDays(start, 1, timeZone), range.endMs)
            val cal = Calendar.getInstance(timeZone).apply { timeInMillis = start }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            result += DayWindow(
                startMs = start,
                endMs = end,
                isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            )
            start = end
        }
        return result
    }

    private fun calculateQuality(
        events: List<AnalysisEvent>,
        slices: List<Slice>,
        windows: List<DayWindow>,
        calendarDays: Int,
        level: AnalysisLevel
    ): AnalysisQuality {
        var expectedMs = 0L
        var recordedMs = 0L
        var overlapMs = 0L
        var daysAtLeast95 = 0
        val slicesByDay = slices.groupBy { it.dayStartMs }

        windows.forEach { day ->
            val intervals = slicesByDay[day.startMs].orEmpty()
                .map { Interval(it.startMs, it.endMs) }
            val raw = intervals.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
            val union = mergeIntervals(intervals).sumOf { it.endMs - it.startMs }
            val expected = (day.endMs - day.startMs).coerceAtLeast(0L)
            expectedMs += expected
            recordedMs += union
            overlapMs += (raw - union).coerceAtLeast(0L)
            if (expected > 0L && union.toDouble() / expected >= 0.95) daysAtLeast95++
        }

        val requiredDepth = when (level) {
            AnalysisLevel.Large -> 1
            AnalysisLevel.Middle -> 2
            AnalysisLevel.Small -> 3
        }
        val unspecifiedMs = slices
            .filter { it.event.path.size < requiredDepth }
            .sumOf { it.durationMs }
        val selectedEventIds = slices.map { it.event.eventId }.toSet()
        val shortCount = events.count { event ->
            event.eventId in selectedEventIds && event.endMs - event.startMs < SHORT_EVENT_MS
        }

        return AnalysisQuality(
            calendarDays = calendarDays,
            analyzedDays = windows.size,
            expectedMs = expectedMs,
            recordedMs = recordedMs,
            missingMs = (expectedMs - recordedMs).coerceAtLeast(0L),
            overlapMs = overlapMs,
            unspecifiedMs = unspecifiedMs,
            shortEventCount = shortCount,
            daysAtLeast95Percent = daysAtLeast95
        )
    }

    private fun calculateCategoryMetrics(
        slices: List<Slice>,
        windows: List<DayWindow>
    ): List<AnalysisCategoryMetric> {
        if (windows.isEmpty()) return emptyList()

        data class Draft(
            val target: Target,
            val totalMs: Long,
            val averagePerDayMs: Long,
            val medianMs: Long,
            val q1Ms: Long,
            val q3Ms: Long,
            val sessions: Int,
            val averageSessionMs: Long,
            val maxSessionMs: Long,
            val activeDayRate: Double
        )

        val drafts = slices.groupBy { it.target.id }.values.map { group ->
            val target = group.first().target
            val dailyTotals = windows.map { day ->
                mergeIntervals(
                    group.filter { it.dayStartMs == day.startMs }
                        .map { Interval(it.startMs, it.endMs) }
                ).sumOf { it.endMs - it.startMs }
            }
            val total = dailyTotals.sum()
            val sessions = mergeIntervals(group.map { Interval(it.startMs, it.endMs) })
            Draft(
                target = target,
                totalMs = total,
                averagePerDayMs = total / windows.size,
                medianMs = quantile(dailyTotals, 0.5),
                q1Ms = quantile(dailyTotals, 0.25),
                q3Ms = quantile(dailyTotals, 0.75),
                sessions = sessions.size,
                averageSessionMs = if (sessions.isNotEmpty()) total / sessions.size else 0L,
                maxSessionMs = sessions.maxOfOrNull { it.endMs - it.startMs } ?: 0L,
                activeDayRate = dailyTotals.count { it > 0L }.toDouble() / windows.size
            )
        }

        val totalAcrossCategories = drafts.sumOf { it.totalMs }.coerceAtLeast(1L)
        val totalByParent = drafts.groupBy { it.target.parentId }
            .mapValues { (_, values) -> values.sumOf { it.totalMs }.coerceAtLeast(1L) }
        val sorted = drafts.sortedByDescending { it.totalMs }
        var cumulative = 0L

        return sorted.map { draft ->
            cumulative += draft.totalMs
            val hours = draft.totalMs.toDouble() / 3_600_000.0
            AnalysisCategoryMetric(
                id = draft.target.id,
                name = draft.target.name,
                pathLabel = draft.target.pathLabel,
                totalMs = draft.totalMs,
                averagePerDayMs = draft.averagePerDayMs,
                medianPerDayMs = draft.medianMs,
                firstQuartileMs = draft.q1Ms,
                thirdQuartileMs = draft.q3Ms,
                share = draft.totalMs.toDouble() / totalAcrossCategories,
                parentShare = draft.totalMs.toDouble() /
                        (totalByParent[draft.target.parentId] ?: totalAcrossCategories),
                sessionCount = draft.sessions,
                occurrencesPerDay = draft.sessions.toDouble() / windows.size,
                averageSessionMs = draft.averageSessionMs,
                maxSessionMs = draft.maxSessionMs,
                activeDayRate = draft.activeDayRate,
                fragmentationPerHour = if (hours > 0.0) draft.sessions / hours else 0.0,
                cumulativeShare = cumulative.toDouble() / totalAcrossCategories
            )
        }
    }

    private fun buildSlotProfiles(
        slices: List<Slice>,
        windows: List<DayWindow>,
        slotMinutes: Int
    ): List<List<SlotAssignment?>> {
        val slotCount = 1440 / slotMinutes
        val slotMs = slotMinutes * 60_000L
        val slicesByDay = slices.groupBy { it.dayStartMs }
        return windows.map { day ->
            val daySlices = slicesByDay[day.startMs].orEmpty()
            List(slotCount) { slotIndex ->
                val slotStart = day.startMs + slotIndex * slotMs
                val slotEnd = minOf(slotStart + slotMs, day.endMs)
                val overlaps = daySlices.filter {
                    it.endMs > slotStart && it.startMs < slotEnd
                }
                if (overlaps.isEmpty()) {
                    null
                } else {
                    val durationByTarget = overlaps.groupBy { it.target.id }.mapValues { (_, values) ->
                        values.sumOf {
                            (minOf(it.endMs, slotEnd) - maxOf(it.startMs, slotStart)).coerceAtLeast(0L)
                        }
                    }
                    val winningId = durationByTarget.maxByOrNull { it.value }!!.key
                    val winningSlice = overlaps.first { it.target.id == winningId }
                    SlotAssignment(winningSlice.target, winningSlice.event.path)
                }
            }
        }
    }

    private fun calculateTimeProfile(
        slotProfiles: List<List<SlotAssignment?>>,
        categoryMetrics: List<AnalysisCategoryMetric>,
        slotMinutes: Int,
        boundaryMinutes: Int
    ): AnalysisTimeProfile {
        val slotCount = 1440 / slotMinutes
        val probabilitiesById = mutableMapOf<Long, DoubleArray>()
        val modal = MutableList<String?>(slotCount) { null }
        var regularitySum = 0.0
        var entropySum = 0.0
        var validSlots = 0

        repeat(slotCount) { slot ->
            val assignments = slotProfiles.mapNotNull { it.getOrNull(slot) }
            if (assignments.isEmpty()) return@repeat

            val counts = assignments.groupingBy { it.target.id }.eachCount()
            val denominator = assignments.size.toDouble()
            counts.forEach { (id, count) ->
                probabilitiesById.getOrPut(id) { DoubleArray(slotCount) }[slot] = count / denominator
            }
            val winner = counts.maxByOrNull { it.value }!!.key
            modal[slot] = assignments.first { it.target.id == winner }.target.pathLabel

            val probabilities = counts.values.map { it / denominator }
            regularitySum += probabilities.sumOf { it * it }
            entropySum += -probabilities.filter { it > 0.0 }.sumOf { it * ln(it) }
            validSlots++
        }

        val metricById = categoryMetrics.associateBy { it.id }
        val categories = categoryMetrics.mapNotNull { metric ->
            probabilitiesById[metric.id]?.let { probabilities ->
                AnalysisSlotCategory(
                    id = metric.id,
                    name = metric.name,
                    pathLabel = metric.pathLabel,
                    probabilities = probabilities.toList()
                )
            }
        }

        return AnalysisTimeProfile(
            slotMinutes = slotMinutes,
            boundaryMinutes = boundaryMinutes,
            categories = categories,
            modalLabels = modal,
            regularity = if (validSlots > 0) regularitySum / validSlots else 0.0,
            averageEntropy = if (validSlots > 0) entropySum / validSlots else 0.0
        )
    }

    private fun buildDistanceMatrix(
        profiles: List<List<SlotAssignment?>>
    ): List<DoubleArray> {
        val matrix = List(profiles.size) { DoubleArray(profiles.size) }
        for (left in profiles.indices) {
            for (right in left + 1 until profiles.size) {
                val distance = profileDistance(profiles[left], profiles[right])
                matrix[left][right] = distance
                matrix[right][left] = distance
            }
        }
        return matrix
    }

    private fun profileDistance(
        left: List<SlotAssignment?>,
        right: List<SlotAssignment?>
    ): Double {
        if (left.isEmpty()) return 0.0
        return left.indices.sumOf { index ->
            hierarchyDistance(left[index]?.path, right.getOrNull(index)?.path)
        } / left.size
    }

    private fun hierarchyDistance(
        left: List<AnalysisCategoryNode>?,
        right: List<AnalysisCategoryNode>?
    ): Double {
        if (left == null && right == null) return 0.0
        if (left == null || right == null) return 1.0
        val weights = doubleArrayOf(4.0, 2.0, 1.0)
        var mismatch = 0.0
        weights.indices.forEach { level ->
            if (left.getOrNull(level)?.id != right.getOrNull(level)?.id) {
                mismatch += weights[level]
            }
        }
        return mismatch / weights.sum()
    }

    private fun findMedoidIndex(distanceMatrix: List<DoubleArray>): Int? {
        if (distanceMatrix.isEmpty()) return null
        return distanceMatrix.indices.minByOrNull { index -> distanceMatrix[index].sum() }
    }

    private fun buildTypicalPatterns(
        distanceMatrix: List<DoubleArray>,
        windows: List<DayWindow>,
        representativeIndex: Int?
    ): List<AnalysisTypicalPattern> {
        if (distanceMatrix.isEmpty() || representativeIndex == null) return emptyList()
        val requestedK = when {
            distanceMatrix.size >= 14 -> 3
            distanceMatrix.size >= 4 -> 2
            else -> 1
        }
        val medoids = mutableListOf(representativeIndex)
        while (medoids.size < requestedK) {
            val next = distanceMatrix.indices
                .filter { it !in medoids }
                .maxByOrNull { candidate -> medoids.minOf { distanceMatrix[candidate][it] } }
                ?: break
            medoids += next
        }

        var assignments = IntArray(distanceMatrix.size)
        repeat(10) {
            assignments = IntArray(distanceMatrix.size) { day ->
                medoids.indices.minByOrNull { cluster -> distanceMatrix[day][medoids[cluster]] } ?: 0
            }
            var changed = false
            medoids.indices.forEach { cluster ->
                val members = distanceMatrix.indices.filter { assignments[it] == cluster }
                if (members.isEmpty()) return@forEach
                val nextMedoid = members.minByOrNull { candidate ->
                    members.sumOf { other -> distanceMatrix[candidate][other] }
                } ?: medoids[cluster]
                if (nextMedoid != medoids[cluster]) {
                    medoids[cluster] = nextMedoid
                    changed = true
                }
            }
            if (!changed) return@repeat
        }
        assignments = IntArray(distanceMatrix.size) { day ->
            medoids.indices.minByOrNull { cluster -> distanceMatrix[day][medoids[cluster]] } ?: 0
        }

        return medoids.indices.mapNotNull { cluster ->
            val members = distanceMatrix.indices.filter { assignments[it] == cluster }
            if (members.isEmpty()) return@mapNotNull null
            AnalysisTypicalPattern(
                medoidDayStartMs = windows[medoids[cluster]].startMs,
                dayCount = members.size,
                share = members.size.toDouble() / distanceMatrix.size,
                weekdayCount = members.count { !windows[it].isWeekend },
                weekendCount = members.count { windows[it].isWeekend }
            )
        }.sortedByDescending { it.dayCount }
    }

    private fun calculateTransitions(
        events: List<AnalysisEvent>,
        windows: List<DayWindow>,
        targetFor: (AnalysisEvent) -> Target
    ): List<AnalysisTransition> {
        data class Span(val startMs: Long, val endMs: Long, val target: Target)
        data class Key(val fromId: Long, val toId: Long)

        val targets = mutableMapOf<Long, Target>()
        val counts = mutableMapOf<Key, Int>()

        windows.forEach { day ->
            val spans = events.mapNotNull { event ->
                val start = maxOf(event.startMs, day.startMs)
                val end = minOf(event.endMs, day.endMs)
                if (end > start) Span(start, end, targetFor(event)) else null
            }.sortedBy { it.startMs }

            var previous: Span? = null
            spans.forEach { current ->
                targets[current.target.id] = current.target
                val prev = previous
                if (prev == null) {
                    previous = current
                    return@forEach
                }
                val gap = current.startMs - prev.endMs
                if (current.target.id == prev.target.id && gap <= CONTIGUOUS_GAP_MS) {
                    previous = Span(prev.startMs, maxOf(prev.endMs, current.endMs), prev.target)
                } else {
                    if (gap in 0..CONTIGUOUS_GAP_MS) {
                        val key = Key(prev.target.id, current.target.id)
                        counts[key] = (counts[key] ?: 0) + 1
                    }
                    previous = current
                }
            }
        }

        val outgoing = counts.entries.groupBy { it.key.fromId }
            .mapValues { (_, values) -> values.sumOf { it.value }.coerceAtLeast(1) }
        return counts.entries.map { (key, count) ->
            AnalysisTransition(
                fromId = key.fromId,
                fromName = targets[key.fromId]?.pathLabel ?: key.fromId.toString(),
                toId = key.toId,
                toName = targets[key.toId]?.pathLabel ?: key.toId.toString(),
                count = count,
                probability = count.toDouble() / (outgoing[key.fromId] ?: 1)
            )
        }.sortedWith(compareByDescending<AnalysisTransition> { it.count }.thenByDescending { it.probability })
    }

    private fun resolveTarget(
        path: List<AnalysisCategoryNode>,
        level: AnalysisLevel
    ): Target {
        require(path.isNotEmpty()) { "category path must not be empty" }
        return when (level) {
            AnalysisLevel.Large -> Target(
                id = path.first().id,
                name = path.first().name,
                pathLabel = path.first().name,
                parentId = null
            )

            AnalysisLevel.Middle -> {
                val parent = path.first()
                val node = path.getOrNull(1)
                Target(
                    id = node?.id ?: syntheticId(parent.id, 2),
                    name = node?.name ?: "未指定",
                    pathLabel = listOf(parent.name, node?.name ?: "未指定").joinToString(" / "),
                    parentId = parent.id
                )
            }

            AnalysisLevel.Small -> {
                val parent = path.getOrNull(1) ?: path.first()
                val node = path.getOrNull(2)
                val names = buildList {
                    add(path.first().name)
                    add(path.getOrNull(1)?.name ?: "未指定")
                    add(node?.name ?: "未指定")
                }
                Target(
                    id = node?.id ?: syntheticId(parent.id, 3),
                    name = node?.name ?: "未指定",
                    pathLabel = names.joinToString(" / "),
                    parentId = parent.id
                )
            }
        }
    }

    private fun syntheticId(parentId: Long, level: Int): Long =
        -1_000_000_000_000L - parentId * 10L - level

    private fun mergeIntervals(intervals: List<Interval>): List<Interval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sorted.isEmpty()) return emptyList()

        val merged = mutableListOf<Interval>()
        var current = sorted.first()
        sorted.drop(1).forEach { next ->
            if (next.startMs <= current.endMs) {
                current = Interval(current.startMs, maxOf(current.endMs, next.endMs))
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun quantile(values: List<Long>, probability: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val position = (sorted.size - 1) * probability
        val lower = position.toInt()
        val upper = kotlin.math.ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val fraction = position - lower
        return (sorted[lower] * (1.0 - fraction) + sorted[upper] * fraction).toLong()
    }

    private fun analysisDayStart(
        timeMs: Long,
        boundaryMinutes: Int,
        timeZone: TimeZone
    ): Long {
        val boundary = Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, boundaryMinutes / 60)
            set(Calendar.MINUTE, boundaryMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (timeMs < boundary.timeInMillis) boundary.add(Calendar.DAY_OF_MONTH, -1)
        return boundary.timeInMillis
    }

    private fun addCalendarDays(timeMs: Long, days: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMs
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
}
