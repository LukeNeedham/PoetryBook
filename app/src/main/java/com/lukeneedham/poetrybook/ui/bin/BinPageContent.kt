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
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.BinPoem

@Composable
internal fun BinPageContent(
    books: List<BinBook>,
    poems: List<BinPoem>,
    onBack: () -> Unit,
    onOpenBook: (bookId: String) -> Unit,
    onOpenPoem: (poemId: String) -> Unit,
) {
    BinScaffold(title = stringResource(R.string.bin), onBack = onBack) {
        Text(
            text = stringResource(R.string.bin_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        )
        if (books.isEmpty() && poems.isEmpty()) {
            Text(
                text = stringResource(R.string.bin_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp, top = 24.dp),
            )
        }
        if (books.isNotEmpty()) {
            SectionTitle(stringResource(R.string.bin_books))
            books.forEach { book ->
                BinRow(
                    title = book.name,
                    subtitle = pluralStringResource(R.plurals.poem_count, book.poemCount, book.poemCount) +
                        " · " + formatTimestamp(book.deletedAt),
                    onClick = { onOpenBook(book.id) },
                )
            }
        }
        if (poems.isNotEmpty()) {
            SectionTitle(stringResource(R.string.bin_poems))
            poems.forEach { poem ->
                BinRow(
                    title = poem.title.ifBlank { stringResource(R.string.untitled) },
                    subtitle = stringResource(R.string.bin_poem_from, poem.bookName) +
                        " · " + formatTimestamp(poem.deletedAt),
                    onClick = { onOpenPoem(poem.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun BinRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun Preview() {
    PoetryBookTheme {
        BinPageContent(
            books = listOf(BinBook("b", "Winter", null, poemCount = 4, deletedAt = 1_700_000_000_000)),
            poems = listOf(BinPoem("p", "Evening", "The light goes slowly", "Love poems", deletedAt = 1_700_100_000_000)),
            onBack = {},
            onOpenBook = {},
            onOpenPoem = {},
        )
    }
}
