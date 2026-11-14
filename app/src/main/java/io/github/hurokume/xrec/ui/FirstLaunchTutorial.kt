package io.github.hurokume.xrec.ui

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hurokume.xrec.R
import io.github.hurokume.xrec.data.TaskTemplateType

private const val KEY_COMPLETED = "completed"

@Composable
fun FirstLaunchTutorialHost(
    preferences: SharedPreferences,
    content: @Composable (initialTemplate: TaskTemplateType?) -> Unit
) {
    var showingTutorial by rememberSaveable {
        mutableStateOf(!preferences.getBoolean(KEY_COMPLETED, false))
    }
    var initialTemplate by rememberSaveable { mutableStateOf<TaskTemplateType?>(null) }

    fun finish(template: TaskTemplateType?) {
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
        initialTemplate = template
        showingTutorial = false
    }

    if (showingTutorial) {
        FirstLaunchTutorialScreen(
            onOpenTemplate = { finish(it) },
            onSkip = { finish(null) }
        )
    } else {
        content(initialTemplate)
    }
}

@Composable
fun FirstLaunchTutorialScreen(
    onOpenTemplate: (TaskTemplateType) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedTemplate by rememberSaveable { mutableStateOf<TaskTemplateType?>(null) }
    val title = when (step) {
        0 -> R.string.tutorial_record_title
        1 -> R.string.tutorial_organize_title
        else -> R.string.tutorial_templates_title
    }
    val description = when (step) {
        0 -> R.string.tutorial_record_description
        1 -> R.string.tutorial_organize_description
        else -> R.string.tutorial_templates_description
    }
    val icon = when (step) {
        0 -> Icons.Filled.Timer
        1 -> Icons.Filled.AccountTree
        else -> Icons.Filled.Dashboard
    }

    BackHandler {
        if (step > 0) step-- else onSkip()
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.tutorial_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.tutorial_skip)) }
                }
                Text(
                    stringResource(R.string.tutorial_progress, step + 1, 3),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())

                key(step) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (step == 2) 16.dp else 24.dp)
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = if (step == 2) Modifier.padding(12.dp).size(32.dp)
                                else Modifier.padding(20.dp).size(48.dp)
                            )
                        }
                        Text(
                            stringResource(title),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(stringResource(description), style = MaterialTheme.typography.bodyLarge)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            if (step == 2) {
                                Column(modifier = Modifier.selectableGroup().padding(vertical = 8.dp)) {
                                    TaskTemplateType.entries.forEach { template ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                                .selectable(
                                                    selected = selectedTemplate == template,
                                                    onClick = { selectedTemplate = template },
                                                    role = Role.RadioButton
                                                )
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(selected = selectedTemplate == template, onClick = null)
                                            Text(stringResource(template.titleRes), modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (step == 0) {
                                        Text(stringResource(R.string.tutorial_record_hint))
                                    } else {
                                        Text(stringResource(R.string.tutorial_project_example), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.tutorial_task_example), modifier = Modifier.padding(start = 24.dp))
                                    }
                                }
                            }
                        }
                        if (step == 2) {
                            Text(
                                stringResource(R.string.tutorial_templates_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                    Button(
                        onClick = { if (step == 2) selectedTemplate?.let(onOpenTemplate) else step++ },
                        enabled = step != 2 || selectedTemplate != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(if (step == 2) R.string.tutorial_preview_template else R.string.action_next))
                    }
                }
            }
        }
    }
}
