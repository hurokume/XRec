package com.example.timerecorder.data

internal enum class TaskSwitchAction {
    NoChange,
    StartFresh,
    MergeShort,
    SwitchNormal
}

internal data class TaskSwitchDecision(
    val action: TaskSwitchAction,
    val previousDurationMs: Long = 0L
)

/** DB操作を伴わないタスク切替判定。 */
internal object TaskSwitchPolicy {
    fun decide(
        currentTaskId: Long?,
        currentStartedAt: Long?,
        newTaskId: Long,
        nowMs: Long,
        mergeThresholdMs: Long
    ): TaskSwitchDecision {
        require(mergeThresholdMs >= 0L) { "merge threshold must not be negative" }

        if (currentTaskId == null) {
            return TaskSwitchDecision(TaskSwitchAction.StartFresh)
        }
        require(currentStartedAt != null) { "active task requires a start time" }

        if (currentTaskId == newTaskId) {
            return TaskSwitchDecision(TaskSwitchAction.NoChange)
        }

        val duration = (nowMs - currentStartedAt).coerceAtLeast(0L)
        val action = if (duration <= mergeThresholdMs) {
            TaskSwitchAction.MergeShort
        } else {
            TaskSwitchAction.SwitchNormal
        }
        return TaskSwitchDecision(action, duration)
    }
}
