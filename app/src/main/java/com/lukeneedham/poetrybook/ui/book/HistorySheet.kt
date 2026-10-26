package com.lukeneedham.poetrybook.ui.book

import com.lukeneedham.poetrybook.data.HistoryChunkSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.HistoryEntry
import com.lukeneedham.poetrybook.data.HistoryList
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.data.VersionChange

/** The list asks for more when its last visible row is this close to the end of what is ready. */
private const val PrefetchDistance = 3

/**
 * The poem's history in a bottom sheet: every saved version, newest first, each with when it was
 * saved and a summary of what it changed from the version before. The [history] comes from
 * [HistoryRepository.trackHistory] with only the newest versions compared; a spinner shows until
 * the first of them are ready, and [onLoadMore] asks for more (a total row count to have ready)
 * when the list is scrolled near the end of what is ready. Picking one calls [onSelect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    history: HistoryList?,
    onLoadMore: (count: Int) -> Unit,
    onSelect: (PoemVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (history == null || (history.entries.isEmpty() && history.total > 0)) {
            LoadingRow(Modifier.padding(vertical = 32.dp).navigationBarsPadding())
            return@ModalBottomSheet
        }
        val entries = history.entries
        val listState = rememberLazyListState()
        LaunchedEffect(listState, entries.size, history.total) {
            if (entries.size >= history.total) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (last >= entries.size - PrefetchDistance) onLoadMore(entries.size + HistoryChunkSize)
                }
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), state = listState) {
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
            if (entries.size < history.total) {
                item(key = "loading") { LoadingRow(Modifier.padding(vertical = 16.dp)) }
            }
        }
    }
}

@Composable
private fun LoadingRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
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
