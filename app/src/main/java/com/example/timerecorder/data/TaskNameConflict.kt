package com.example.timerecorder.data

data class TaskNameDecision(val merge: Boolean, val newName: String = "", val applyAll: Boolean = false)
class TaskNameConflict(val key: String, val taskName: String, val parentName: String) : RuntimeException()

internal fun sameTaskName(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)
internal fun uniqueTaskName(name: String, existing: Collection<String>): String {
    var index = 2
    while (true) {
        val suffix = " ($index)"
        val candidate = name.take(128 - suffix.length) + suffix
        if (existing.none { sameTaskName(it, candidate) }) return candidate
        index++
    }
}
