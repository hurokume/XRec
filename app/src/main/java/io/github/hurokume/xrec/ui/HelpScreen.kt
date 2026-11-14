package io.github.hurokume.xrec.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.hurokume.xrec.R

@Composable
fun HelpScreen(
    onTemplateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HelpActionCard(
            title = stringResource(R.string.help_privacy_policy_title),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = { openPrivacyPolicy(context) }
        )
        HelpActionCard(
            title = stringResource(R.string.help_template_title),
            icon = Icons.Filled.ChevronRight,
            onClick = onTemplateClick
        )
    }
}

@Composable
private fun HelpActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Icon(icon, contentDescription = null)
        }
    }
}

private fun openPrivacyPolicy(context: Context) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        context.getString(R.string.privacy_policy_url).toUri()
    ).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            R.string.help_privacy_policy_open_failed,
            Toast.LENGTH_LONG
        ).show()
    }
}
