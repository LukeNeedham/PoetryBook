package com.lukeneedham.poetrybook.ui.bin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.ui.book.formatTimestamp
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.ui.theme.AppTheme
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.BinPoem

@Composable
internal fun BinBookPageContent(
    book: BinBook,
    titles: List<String>,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    BinScaffold(title = book.name, onBack = onBack) {
        Text(
            text = pluralStringResource(R.plurals.poem_count, book.poemCount, book.poemCount) +
                " · " + formatTimestamp(book.deletedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        Button(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp),
        ) { Text(stringResource(R.string.restore)) }
        OutlinedButton(
            onClick = { confirmingDelete = true },
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp),
        ) { Text(stringResource(R.string.delete_forever)) }

        titles.forEach { title ->
            Text(
                text = title.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = PoemFont,
                    fontStyle = if (title.isBlank()) FontStyle.Italic else FontStyle.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp),
            )
        }
        Box(Modifier.height(24.dp))
    }

    if (confirmingDelete) {
        DeleteForeverDialog(
            message = stringResource(R.string.delete_forever_book_message),
            onConfirm = {
                confirmingDelete = false
                onDeleteForever()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun Preview() {
    AppTheme {
        BinBookPageContent(
            book = BinBook("b", "Winter", null, poemCount = 3, deletedAt = 1_700_000_000_000),
            titles = listOf("First frost", "Silver", ""),
            onBack = {},
            onRestore = {},
            onDeleteForever = {},
        )
    }
}
