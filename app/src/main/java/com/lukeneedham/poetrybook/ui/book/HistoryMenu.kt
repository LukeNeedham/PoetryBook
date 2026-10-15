package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import kotlinx.coroutines.flow.Flow

/**
 * The poem's history as a drop-down: every saved version, newest first, each with when it was
 * saved, its title and the first two lines of its text. Picking one calls [onSelect].
 */
@Composable
fun HistoryMenu(
    expanded: Boolean,
    versions: Flow<List<PoemVersion>>,
    onSelect: (PoemVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 260.dp, max = 340.dp),
    ) {
        val list by versions.collectAsState(initial = emptyList())
        list.forEachIndexed { index, version ->
            DropdownMenuItem(
                onClick = { onSelect(version) },
                text = { VersionSummary(version, current = index == 0) },
            )
        }
    }
}

@Composable
private fun VersionSummary(version: PoemVersion, current: Boolean) {
    Column {
        Text(
            text = formatTimestamp(version.createdAt) +
                if (current) " · " + stringResource(R.string.history_current) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = version.title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = PoemFont,
                fontWeight = FontWeight.SemiBold,
                fontStyle = if (version.title.isBlank()) FontStyle.Italic else FontStyle.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (version.body.isNotBlank()) {
            Text(
                text = version.body.trim(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PoemFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
