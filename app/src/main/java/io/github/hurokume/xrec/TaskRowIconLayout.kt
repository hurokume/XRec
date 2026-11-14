package io.github.hurokume.xrec

import android.content.SharedPreferences
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object TaskIconSpacing {
    const val Min = 32
    const val Max = 64
    const val Default = 48
    private const val KEY = "task_icon_width_dp"

    fun read(prefs: SharedPreferences): Int = prefs.getInt(KEY, Default).coerceIn(Min, Max)

    fun write(prefs: SharedPreferences, width: Int) {
        prefs.edit().putInt(KEY, width.coerceIn(Min, Max)).apply()
    }
}

internal val LocalTaskIconWidth = staticCompositionLocalOf { TaskIconSpacing.Default.dp }

@Composable
internal fun TaskIconRow(
    iconWidthDp: Int,
    iconCount: Int,
    modifier: Modifier = Modifier,
    reservedWidth: Dp = 72.dp,
    content: @Composable RowScope.() -> Unit
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        // Keep room for the task name and color chip when many icons are enabled.
        val availableWidth = ((maxWidth - reservedWidth) / iconCount.coerceAtLeast(1))
            .coerceAtLeast(24.dp)
        val iconWidth = minOf(iconWidthDp.coerceIn(TaskIconSpacing.Min, TaskIconSpacing.Max).dp, availableWidth)
        CompositionLocalProvider(LocalTaskIconWidth provides iconWidth) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
internal fun TaskIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.width(LocalTaskIconWidth.current).height(48.dp),
            content = content
        )
    }
}
