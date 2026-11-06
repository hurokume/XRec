package com.example.timerecorder

import com.example.timerecorder.data.db.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class NextTaskPredictor private constructor(
    private val taskDao: TaskDao
) {
    data class Prediction(val taskId: Long, val score: Double)

    companion object {
        @Volatile
        private var INSTANCE: NextTaskPredictor? = null
        fun getInstance(taskDao: TaskDao): NextTaskPredictor =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NextTaskPredictor(taskDao).also { INSTANCE = it }
            }
    }

    private val lookbackDays = 60

    suspend fun predictTopK(currentTaskId: Long?, nowMs: Long, k: Int = 3): List<Prediction> =
        withContext(Dispatchers.IO) {
            val all = taskDao.getAllEventsForCsv()
            if (all.isEmpty()) return@withContext emptyList()
            val visibleTaskIds = taskDao.getVisibleTaskIds().toSet()
            if (visibleTaskIds.isEmpty()) return@withContext emptyList()

            val since = nowMs - lookbackDays * 24L * 60 * 60 * 1000
            val events = all.map { r ->
                val s = r.pressedAtEpochMs
                val d = max(0L, r.durationMillis)
                EventLite(r.taskId, s, s + d)
            }.filter { it.endMs >= since }
                .sortedBy { it.startMs }
            if (events.isEmpty()) return@withContext emptyList()

            val trans = mutableMapOf<Long, MutableMap<Long, Int>>()    // prev -> (next -> cnt)
            val hourPrior = mutableMapOf<Int, MutableMap<Long, Int>>() // hour -> (task -> cnt)
            val dowPrior = mutableMapOf<Int, MutableMap<Long, Int>>() // dow  -> (task -> cnt)

            for (i in 0 until events.lastIndex) {
                val a = events[i];
                val b = events[i + 1]
                trans.getOrPut(a.taskId) { mutableMapOf() }
                    .merge(b.taskId, 1, Int::plus)
            }
            val cal = java.util.Calendar.getInstance()
            for (e in events) {
                cal.timeInMillis = e.startMs
                val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val d = cal.get(java.util.Calendar.DAY_OF_WEEK)
                hourPrior.getOrPut(h) { mutableMapOf() }.merge(e.taskId, 1, Int::plus)
                dowPrior.getOrPut(d) { mutableMapOf() }.merge(e.taskId, 1, Int::plus)
            }

            fun norm(m: Map<Long, Int>) = m.let { map ->
                val tot = map.values.sum().coerceAtLeast(1)
                map.mapValues { it.value.toDouble() / tot }
            }

            val wTrans = 0.6;
            val wHour = 0.3;
            val wDow = 0.1
            cal.timeInMillis = nowMs
            val curHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val curDow = cal.get(java.util.Calendar.DAY_OF_WEEK)

            val pTrans = norm(trans[currentTaskId] ?: emptyMap())
            val pHour = norm(hourPrior[curHour] ?: emptyMap())
            val pDow = norm(dowPrior[curDow] ?: emptyMap())

            val candidates = (pTrans.keys + pHour.keys + pDow.keys)
                .distinct()
                .filter { it in visibleTaskIds }
            candidates.map { tid ->
                val score = wTrans * (pTrans[tid] ?: 0.0) +
                        wHour * (pHour[tid] ?: 0.0) +
                        wDow * (pDow[tid] ?: 0.0)
                Prediction(tid, score)
            }
                .sortedByDescending { it.score }
                .take(k)
        }

    suspend fun predictNext(currentTaskId: Long?, nowMs: Long): Long? =
        predictTopK(currentTaskId, nowMs, 1).firstOrNull()?.taskId

    private data class EventLite(val taskId: Long, val startMs: Long, val endMs: Long)
}
