package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.data.wordChanges
import kotlinx.coroutines.flow.Flow

/**
 * The poem's history in a bottom sheet: every saved version, newest first, each with when it was
 * saved, its title and the first two lines of its text. Picking one calls [onSelect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    versions: Flow<List<PoemVersion>>,
    onSelect: (PoemVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val list by versions.collectAsState(initial = emptyList())
        Text(
            text = stringResource(R.string.history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            itemsIndexed(list, key = { _, version -> version.createdAt }) { index, version ->
                if (index > 0) HorizontalDivider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(version) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    VersionSummary(version, previous = list.getOrNull(index + 1), current = index == 0)
                }
            }
        }
    }
}

@Composable
private fun VersionSummary(version: PoemVersion, previous: PoemVersion?, current: Boolean) {
    val description = changeDescription(version, previous)
    Column {
        Text(
            text = formatTimestamp(version.createdAt),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (current) {
            Text(
                text = stringResource(R.string.history_current),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What [version] changed compared with the [previous] (older) one, e.g. "5 words added, 2 words removed". */
@Composable
private fun changeDescription(version: PoemVersion, previous: PoemVersion?): String {
    if (previous == null) return stringResource(R.string.history_first_version)
    val changes = remember(version.body, previous.body) { wordChanges(previous.body, version.body) }
    val resources = LocalContext.current.resources
    val parts = buildList {
        if (changes.added > 0) add(resources.getQuantityString(R.plurals.history_words_added, changes.added, changes.added))
        if (changes.removed > 0) add(resources.getQuantityString(R.plurals.history_words_removed, changes.removed, changes.removed))
        if (changes.moved > 0) add(resources.getQuantityString(R.plurals.history_words_moved, changes.moved, changes.moved))
        if (version.title != previous.title) add(stringResource(R.string.history_title_changed))
    }
    return if (parts.isEmpty()) stringResource(R.string.history_no_text_changes) else parts.joinToString(", ")
}
