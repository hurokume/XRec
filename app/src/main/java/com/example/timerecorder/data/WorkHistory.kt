package com.example.timerecorder.data

import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.example.timerecorder.R
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.WorkHistoryEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

enum class WorkHistoryAction(@StringRes val label: Int) {
    StartOrSwitch(R.string.work_history_start_switch),
    Stop(R.string.work_history_stop),
    AddTask(R.string.work_history_add_task),
    RenameTask(R.string.work_history_rename_task),
    ChangeColor(R.string.work_history_change_color),
    ReorderTasks(R.string.work_history_reorder_tasks),
    DeleteTask(R.string.work_history_delete_task),
    ChangeRunningTask(R.string.work_history_running_task),
    AddRecord(R.string.work_history_add_record),
    EditRecord(R.string.work_history_edit_record),
    SplitRecord(R.string.work_history_split_record),
    DeleteRecord(R.string.work_history_delete_record),
    ReassignRecords(R.string.work_history_reassign_records),
    ImportTasks(R.string.work_history_import_tasks),
    ImportRecords(R.string.work_history_import_records),
    Restore(R.string.work_history_restore);

    companion object {
        fun fromStored(value: String): WorkHistoryAction? = entries.find { it.name == value }
    }
}

enum class RestoreWorkResult { Restored, HistoryChanged, Missing }

/**
 * A transaction and its undo journal commit together. Nested operations (for example
 * each row in an import) form one history item. Only changed rows are persisted, so
 * retaining 100 edits does not retain 100 copies of the entire database.
 */
class WorkHistory(
    private val db: AppDatabase,
    private val stringResolver: (Int) -> String = { it.toString() }
) {
    private class Recording(val database: AppDatabase) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Recording>
    }

    private data class Table(val name: String, val columns: List<String>) {
        val key: String get() = columns.first()
    }

    private val tables = listOf(
        Table("tasks", listOf("id", "name", "parentId", "colorInt", "sortOrder", "isArchived")),
        Table("task_events", listOf("id", "taskId", "pressedAtEpochMs", "durationMillis")),
        Table("task_sessions", listOf("id", "taskId", "startedAt", "endedAt")),
        Table("active_task_singleton", listOf("singletonId", "taskId", "startedAtEpochMs"))
    )

    private data class Snapshot(val rows: Map<String, Map<Long, List<Any?>>>)
    private data class Change(val table: String, val id: Long, val before: List<Any?>?, val after: List<Any?>?)

    fun observe() = db.workHistoryDao().observe()

    suspend fun <T> record(action: WorkHistoryAction, block: suspend () -> T): T {
        if (currentCoroutineContext()[Recording]?.database === db) return block()
        return db.withTransaction {
            withContext(Recording(db)) {
                val before = snapshot()
                val result = block()
                save(action, before, snapshot())
                result
            }
        }
    }

    suspend fun restoreBefore(id: Long, expectedLatestId: Long): RestoreWorkResult = db.withTransaction {
        val dao = db.workHistoryDao()
        if (dao.latestId() != expectedLatestId) return@withTransaction RestoreWorkResult.HistoryChanged
        val entries = dao.changesSince(id)
        if (entries.none { it.id == id }) return@withTransaction RestoreWorkResult.Missing
        val before = snapshot()
        entries.forEach { entry -> applyBefore(decode(entry.changesJson)) }
        save(WorkHistoryAction.Restore, before, snapshot())
        RestoreWorkResult.Restored
    }

    private fun snapshot(): Snapshot = Snapshot(tables.associate { table ->
        val rows = linkedMapOf<Long, List<Any?>>()
        db.openHelper.writableDatabase.query("SELECT ${table.columns.joinToString()} FROM ${table.name}").use { cursor ->
            while (cursor.moveToNext()) {
                rows[cursor.getLong(0)] = table.columns.indices.map { index ->
                    when {
                        cursor.isNull(index) -> null
                        table.columns[index] == "name" -> cursor.getString(index)
                        else -> cursor.getLong(index)
                    }
                }
            }
        }
        table.name to rows
    })

    private suspend fun save(action: WorkHistoryAction, before: Snapshot, after: Snapshot) {
        val changes = tables.flatMap { table ->
            val old = before.rows.getValue(table.name)
            val new = after.rows.getValue(table.name)
            (old.keys + new.keys).mapNotNull { id ->
                if (old[id] == new[id]) null else Change(table.name, id, old[id], new[id])
            }
        }
        if (changes.isEmpty()) return
        db.workHistoryDao().insert(
            WorkHistoryEntity(
                createdAt = System.currentTimeMillis(),
                action = action.name,
                details = describe(changes, before, after),
                taskCount = changes.count { it.table == "tasks" },
                recordCount = changes.count { it.table == "task_events" },
                changesJson = encode(changes)
            )
        )
        db.workHistoryDao().trim()
    }

    private fun describe(changes: List<Change>, before: Snapshot, after: Snapshot): String {
        fun path(snapshot: Snapshot, id: Long): String {
            val names = mutableListOf<String>()
            val seen = mutableSetOf<Long>()
            var current: Long? = id
            while (current != null && seen.add(current)) {
                val row = snapshot.rows.getValue("tasks")[current] ?: break
                names += row[1] as String
                current = row[2] as Long?
            }
            return names.asReversed().joinToString(" / ")
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        fun label(table: String, row: List<Any?>?, snapshot: Snapshot): String {
            if (row == null) return "—"
            return when (table) {
                "tasks" -> path(snapshot, row[0] as Long)
                "task_events" -> {
                    val start = row[2] as Long
                    "${path(snapshot, row[1] as Long)}\n${formatter.format(Date(start))} – " +
                        formatter.format(Date(start + (row[3] as Long)))
                }
                else -> "${path(snapshot, row[1] as Long)} (${formatter.format(Date(row[2] as Long))})"
            }
        }
        return changes.asSequence().filter { it.table != "task_sessions" }.take(20).map { change ->
            val old = label(change.table, change.before, before)
            val new = label(change.table, change.after, after)
            val lines = mutableListOf(if (old == new) old else "$old\n→ $new")
            if (change.table == "tasks" && change.before != null && change.after != null) {
                fun color(value: Any?): String = (value as Long?)?.let {
                    "#%08X".format(it.toInt())
                } ?: "—"
                if (change.before[3] != change.after[3]) {
                    lines += "${stringResolver(R.string.work_history_color)}: ${color(change.before[3])} → ${color(change.after[3])}"
                }
                if (change.before[4] != change.after[4]) {
                    lines += "${stringResolver(R.string.work_history_order)}: ${(change.before[4] as Long) + 1} → ${(change.after[4] as Long) + 1}"
                }
            }
            lines.joinToString("\n")
        }.joinToString("\n\n")
    }

    private fun encode(changes: List<Change>): String = JSONObject().apply {
        put("version", 1)
        put("changes", JSONArray().apply {
            changes.forEach { change ->
                put(JSONObject().apply {
                    put("table", change.table)
                    put("id", change.id)
                    put("before", change.before?.let { JSONArray(it) } ?: JSONObject.NULL)
                    put("after", change.after?.let { JSONArray(it) } ?: JSONObject.NULL)
                })
            }
        })
    }.toString()

    private fun decode(json: String): List<Change> {
        val root = JSONObject(json)
        check(root.getInt("version") == 1)
        val array = root.getJSONArray("changes")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val table = tables.single { it.name == item.getString("table") }
            fun row(key: String): List<Any?>? {
                if (item.isNull(key)) return null
                val values = item.getJSONArray(key)
                check(values.length() == table.columns.size)
                return table.columns.indices.map { column ->
                    when {
                        values.isNull(column) -> null
                        table.columns[column] == "name" -> values.getString(column)
                        else -> values.getLong(column)
                    }
                }
            }
            Change(table.name, item.getLong("id"), row("before"), row("after"))
        }
    }

    private fun applyBefore(changes: List<Change>) {
        val sql = db.openHelper.writableDatabase
        // Restore tasks before their references. Never REPLACE tasks: that would
        // cascade-delete records which are unchanged by this operation.
        tables.forEach { table ->
            changes.filter { it.table == table.name && it.before != null }.forEach { change ->
                val row = checkNotNull(change.before)
                sql.execSQL(
                    "INSERT OR IGNORE INTO ${table.name} (${table.columns.joinToString()}) VALUES (${table.columns.joinToString { "?" }})",
                    row.toTypedArray()
                )
                sql.execSQL(
                    "UPDATE ${table.name} SET ${table.columns.drop(1).joinToString { "$it = ?" }} WHERE ${table.key} = ?",
                    (row.drop(1) + change.id).toTypedArray()
                )
            }
        }
        // Remove dependent rows first and tasks last, preserving foreign keys.
        tables.asReversed().forEach { table ->
            changes.filter { it.table == table.name && it.before == null }.forEach { change ->
                sql.execSQL("DELETE FROM ${table.name} WHERE ${table.key} = ?", arrayOf(change.id))
            }
        }
    }
}
