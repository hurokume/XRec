package io.github.hurokume.xrec.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hurokume.xrec.R
import io.github.hurokume.xrec.TaskMenuAction
import io.github.hurokume.xrec.TaskMenuSettings

@Composable
internal fun TaskMenuSettingsEditor(
    settings: TaskMenuSettings,
    onSettingsChange: (TaskMenuSettings) -> Unit
) {
    var showCustomization by rememberSaveable { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        TaskMenuCheckboxRow(
            label = stringResource(R.string.settings_show_task_menu_buttons),
            checked = settings.showButtons,
            onCheckedChange = { onSettingsChange(settings.copy(showButtons = it)) }
        )
        FilledTonalButton(
            onClick = { showCustomization = true },
            enabled = settings.showButtons,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(stringResource(R.string.settings_task_menu_customize))
        }
    }

    if (showCustomization && settings.showButtons) {
        TaskMenuCustomizationDialog(
            settings = settings,
            onDismiss = { showCustomization = false },
            onSave = {
                onSettingsChange(settings.copy(actions = it))
                showCustomization = false
            }
        )
    }
}

@Composable
private fun TaskMenuCustomizationDialog(
    settings: TaskMenuSettings,
    onDismiss: () -> Unit,
    onSave: (Set<TaskMenuAction>) -> Unit
) {
    var selectedKeys by rememberSaveable {
        mutableStateOf(settings.actions.map { it.preferenceKey })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_task_menu_customize)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.settings_task_menu_description),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TaskMenuAction.entries.forEach { action ->
                    TaskMenuCheckboxRow(
                        label = stringResource(action.label),
                        checked = action.preferenceKey in selectedKeys,
                        onCheckedChange = { checked ->
                            selectedKeys = if (checked) selectedKeys + action.preferenceKey
                            else selectedKeys - action.preferenceKey
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(TaskMenuAction.entries.filter { it.preferenceKey in selectedKeys }.toSet())
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun TaskMenuCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
