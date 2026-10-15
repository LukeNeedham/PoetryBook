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
                    VersionSummary(version, current = index == 0)
                }
            }
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
