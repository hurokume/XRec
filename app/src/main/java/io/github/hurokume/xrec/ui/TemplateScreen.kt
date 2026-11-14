package io.github.hurokume.xrec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hurokume.xrec.R
import io.github.hurokume.xrec.data.TaskTemplateType
import io.github.hurokume.xrec.data.TemplateApplyMode

private enum class TemplateDialog { None, ChooseMode, ConfirmReplace }

@Composable
fun TemplateScreen(
    applying: Boolean,
    onApply: (TaskTemplateType, TemplateApplyMode) -> Unit,
    modifier: Modifier = Modifier,
    initialTemplate: TaskTemplateType? = null,
    onInitialTemplateConsumed: () -> Unit = {}
) {
    var dialog by rememberSaveable {
        mutableStateOf(if (initialTemplate != null) TemplateDialog.ChooseMode else TemplateDialog.None)
    }
    var selectedTemplate by rememberSaveable {
        mutableStateOf(initialTemplate ?: TaskTemplateType.SecondaryStudents)
    }
    LaunchedEffect(Unit) {
        if (initialTemplate != null) onInitialTemplateConsumed()
    }
    val context = LocalContext.current
    val projects = selectedTemplate.projects { context.getString(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.help_template_pending),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.help_template_search))
        }
        Text(
            stringResource(R.string.help_template_list),
            style = MaterialTheme.typography.titleMedium
        )
        if (applying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.help_template_applying))
        }
        TaskTemplateType.entries.forEach { template ->
            OutlinedButton(
                onClick = {
                    selectedTemplate = template
                    dialog = TemplateDialog.ChooseMode
                },
                enabled = !applying,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(template.titleRes))
            }
        }
    }

    when (dialog) {
        TemplateDialog.None -> Unit
        TemplateDialog.ChooseMode -> AlertDialog(
            onDismissRequest = { dialog = TemplateDialog.None },
            title = { Text(stringResource(selectedTemplate.titleRes)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.help_template_choose_mode))
                    Text(
                        stringResource(
                            R.string.help_template_summary,
                            projects.size,
                            projects.sumOf { it.tasks.size }
                        )
                    )
                    projects.forEach { project ->
                        Text(project.name, style = MaterialTheme.typography.titleSmall)
                        project.tasks.forEach { task ->
                            Text("• $task", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = !applying,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            dialog = TemplateDialog.None
                            onApply(selectedTemplate, TemplateApplyMode.Add)
                        }
                    ) { Text(stringResource(R.string.help_template_add)) }
                    OutlinedButton(
                        enabled = !applying,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { dialog = TemplateDialog.ConfirmReplace }
                    ) { Text(stringResource(R.string.help_template_replace)) }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { dialog = TemplateDialog.None }
                    ) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
        TemplateDialog.ConfirmReplace -> AlertDialog(
            onDismissRequest = { dialog = TemplateDialog.ChooseMode },
            icon = {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(stringResource(R.string.help_template_replace_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.help_template_replace_warning,
                        stringResource(selectedTemplate.titleRes)
                    ),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                Button(
                    enabled = !applying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        dialog = TemplateDialog.None
                        onApply(selectedTemplate, TemplateApplyMode.ReplaceAll)
                    }
                ) { Text(stringResource(R.string.help_template_replace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = TemplateDialog.ChooseMode }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
