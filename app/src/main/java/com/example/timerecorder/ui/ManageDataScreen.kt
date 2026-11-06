package com.example.timerecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timerecorder.R

@Composable
fun ManageDataScreen(
    onExportTsv: () -> Unit,
    onImportTsv: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.manage_data_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            stringResource(R.string.manage_data_records_title),
            style = MaterialTheme.typography.titleMedium
        )
        DataActionCard(
            title = stringResource(R.string.drawer_export_tsv),
            description = stringResource(R.string.manage_data_export_tsv_description),
            onClick = onExportTsv
        )
        DataActionCard(
            title = stringResource(R.string.drawer_import_tsv),
            description = stringResource(R.string.manage_data_import_tsv_description),
            onClick = onImportTsv
        )

        Text(
            stringResource(R.string.manage_data_tasks_title),
            style = MaterialTheme.typography.titleMedium
        )
        DataActionCard(
            title = stringResource(R.string.drawer_export_json),
            description = stringResource(R.string.manage_data_export_json_description),
            onClick = onExportJson
        )
        DataActionCard(
            title = stringResource(R.string.drawer_import_json),
            description = stringResource(R.string.manage_data_import_json_description),
            onClick = onImportJson
        )
    }
}

@Composable
private fun DataActionCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = title)
        }
    }
}
