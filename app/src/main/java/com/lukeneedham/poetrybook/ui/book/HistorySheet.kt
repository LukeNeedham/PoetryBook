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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.HistoryEntry
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.data.VersionChange

/**
 * The poem's history in a bottom sheet: every saved version, newest first, each with when it was
 * saved and a summary of what it changed from the version before. The [entries] come ready-made
 * from [PoemBookViewModel.trackHistory], so nothing is compared when the sheet opens. Picking one
 * calls [onSelect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    onSelect: (PoemVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            itemsIndexed(entries, key = { _, entry -> entry.version.createdAt }) { index, entry ->
                if (index > 0) HorizontalDivider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry.version) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    VersionSummary(entry, current = index == 0)
                }
            }
        }
    }
}

@Composable
private fun VersionSummary(entry: HistoryEntry, current: Boolean) {
    Column {
        Text(
            text = formatTimestamp(entry.version.createdAt),
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
            text = changeDescription(entry.change),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [change] in words, e.g. "5 words added, 2 words removed"; null is the first version. */
@Composable
private fun changeDescription(change: VersionChange?): String {
    if (change == null) return stringResource(R.string.history_first_version)
    val resources = LocalContext.current.resources
    val words = change.words
    val parts = buildList {
        if (words.added > 0) add(resources.getQuantityString(R.plurals.history_words_added, words.added, words.added))
        if (words.removed > 0) add(resources.getQuantityString(R.plurals.history_words_removed, words.removed, words.removed))
        if (words.moved > 0) add(resources.getQuantityString(R.plurals.history_words_moved, words.moved, words.moved))
        if (change.formatting) add(stringResource(R.string.history_formatting_changed))
        if (change.titleChanged) add(stringResource(R.string.history_title_changed))
    }
    return if (parts.isEmpty()) stringResource(R.string.history_no_text_changes) else parts.joinToString(", ")
}
