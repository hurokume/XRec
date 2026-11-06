package com.example.timerecorder.ui

internal fun toggleTaskTreeSelection(
    selectedTaskIds: Set<Long>,
    taskTreeIds: Collection<Long>
): Set<Long> {
    val ids = taskTreeIds.toSet()
    if (ids.isEmpty()) return selectedTaskIds
    return if (selectedTaskIds.containsAll(ids)) {
        selectedTaskIds - ids
    } else {
        selectedTaskIds + ids
    }
}
