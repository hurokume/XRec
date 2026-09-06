package com.example.timerecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timerecorder.R
import com.example.timerecorder.data.TaskNameDecision

@Composable
fun TaskEditDialogs(viewModel: TaskViewModel) {
    val editing by viewModel.editingTask.collectAsStateWithLifecycle()
    val conflict by viewModel.nameConflict.collectAsStateWithLifecycle()
    editing?.let { (id, current) ->
        var name by rememberSaveable(id) { mutableStateOf(current) }
        AlertDialog(onDismissRequest = { viewModel.editingTask.value = null },
            title = { Text(stringResource(R.string.task_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(128) },
                label = { Text(stringResource(R.string.task_new_name_label)) }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                viewModel.renameTask(id, name); viewModel.editingTask.value = null
            }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { viewModel.editingTask.value = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    conflict?.let { duplicate ->
        var name by rememberSaveable(duplicate.key) { mutableStateOf("") }
        var all by rememberSaveable(duplicate.key) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { viewModel.answerNameConflict(null) },
            title = { Text(stringResource(R.string.duplicate_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.duplicate_description,
                    duplicate.parentName.ifBlank { stringResource(R.string.duplicate_root) }, duplicate.taskName))
                OutlinedTextField(value = name, onValueChange = { name = it.take(128) },
                    label = { Text(stringResource(R.string.duplicate_new_name)) }, singleLine = true)
                Text(stringResource(R.string.duplicate_auto_name), style = MaterialTheme.typography.bodySmall)
                Row { Checkbox(checked = all, onCheckedChange = { all = it }); Text(stringResource(R.string.duplicate_apply_all)) }
            } },
            confirmButton = { Column {
                TextButton(onClick = { viewModel.answerNameConflict(TaskNameDecision(true, applyAll = all)) }) { Text(stringResource(R.string.duplicate_merge)) }
                TextButton(onClick = { viewModel.answerNameConflict(TaskNameDecision(false, name, all)) }) { Text(stringResource(R.string.duplicate_keep_separate)) }
            } },
            dismissButton = { TextButton(onClick = { viewModel.answerNameConflict(null) }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
