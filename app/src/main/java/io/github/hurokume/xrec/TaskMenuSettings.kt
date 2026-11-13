package io.github.hurokume.xrec

import android.content.SharedPreferences
import androidx.annotation.StringRes

enum class TaskMenuAction(val preferenceKey: String, @StringRes val label: Int) {
    AddChild("add_child", R.string.settings_task_menu_add),
    EditSubtasks("edit_subtasks", R.string.task_edit_subtasks),
    Rename("rename", R.string.task_rename),
    Delete("delete", R.string.action_delete),
    ChangeColor("change_color", R.string.task_change_color)
}

data class TaskMenuSettings(
    val showButtons: Boolean = false,
    val actions: Set<TaskMenuAction> = TaskMenuAction.entries.toSet()
) {
    fun hasAny(vararg availableActions: TaskMenuAction): Boolean =
        availableActions.any { it in actions }
}

internal object TaskMenuPreferences {
    private const val SHOW_BUTTONS = "show_task_menu_buttons"
    private const val ACTIONS = "task_menu_actions"

    fun read(prefs: SharedPreferences): TaskMenuSettings {
        val savedActions = prefs.getStringSet(ACTIONS, null)
        return TaskMenuSettings(
            showButtons = prefs.getBoolean(SHOW_BUTTONS, false),
            actions = TaskMenuAction.entries.filter {
                savedActions == null || it.preferenceKey in savedActions
            }.toSet()
        )
    }

    fun write(prefs: SharedPreferences, settings: TaskMenuSettings) {
        prefs.edit()
            .putBoolean(SHOW_BUTTONS, settings.showButtons)
            .putStringSet(ACTIONS, settings.actions.map { it.preferenceKey }.toSet())
            .apply()
    }
}
