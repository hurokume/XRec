package com.example.timerecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timerecorder.R
import com.example.timerecorder.data.WorkHistoryAction
import com.example.timerecorder.data.db.WorkHistoryItem
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkHistoryScreen(
    entries: List<WorkHistoryItem>,
    restoring: Boolean,
    onRestore: (id: Long, expectedLatestId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Freeze both selection and latest revision while the confirmation is open.
    // A concurrent change causes restoration to fail safely and asks for a refresh.
    var selection by remember { mutableStateOf<Triple<WorkHistoryItem, Long, Int>?>(null) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.work_history_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.work_history_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (restoring) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (entries.isEmpty()) {
            item {
                Text(stringResource(R.string.work_history_empty), Modifier.padding(vertical = 16.dp))
            }
        } else {
            item {
                Text(stringResource(R.string.work_history_count, entries.size), style = MaterialTheme.typography.labelMedium)
            }
        }
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(WorkHistoryAction.fromStored(entry.action)?.label ?: R.string.work_history_change),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(formatter.format(Date(entry.createdAt)), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.work_history_affected, entry.taskCount, entry.recordCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var expanded by remember(entry.id) { mutableStateOf(false) }
                    if (entry.details.isNotBlank()) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(stringResource(if (expanded) R.string.work_history_hide_details else R.string.work_history_details))
                        }
                        if (expanded) Text(entry.details, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        enabled = !restoring,
                        onClick = { selection = Triple(entry, entries.first().id, index + 1) }
                    ) {
                        Text(stringResource(R.string.work_history_restore_before))
                    }
                }
            }
        }
    }
    selection?.let { (entry, latestId, count) ->
        AlertDialog(
            onDismissRequest = { selection = null },
            title = { Text(stringResource(R.string.work_history_confirm_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatter.format(Date(entry.createdAt)))
                    Text(stringResource(WorkHistoryAction.fromStored(entry.action)?.label ?: R.string.work_history_change))
                    Text(stringResource(R.string.work_history_confirm_message, count))
                }
            },
            confirmButton = {
                TextButton(enabled = !restoring, onClick = {
                    selection = null
                    onRestore(entry.id, latestId)
                }) { Text(stringResource(R.string.work_history_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { selection = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
