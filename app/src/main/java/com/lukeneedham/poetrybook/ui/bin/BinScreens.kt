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
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.book.formatTimestamp
import com.lukeneedham.poetrybook.ui.theme.PoemFont

/** A settings subpage with a back button and [title]. */
@Composable
private fun BinPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            content()
        }
    }
}

/**
 * The bin: deleted books, then deleted poems. Tap one to preview it and restore it or delete it
 * for good. There is deliberately no way to empty the whole bin at once.
 */
@Composable
fun BinScreen(
    viewModel: PoemBookViewModel,
    onBack: () -> Unit,
    onOpenBook: (bookId: String) -> Unit,
    onOpenPoem: (poemId: String) -> Unit,
) {
    val books = viewModel.binBooks
    val poems = viewModel.binPoems
    BinPage(title = stringResource(R.string.bin), onBack = onBack) {
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

/** Preview of a deleted poem, with buttons to put it back where it was or to wipe it. */
@Composable
fun BinPoemScreen(viewModel: PoemBookViewModel, poemId: String, onBack: () -> Unit) {
    val poem = viewModel.binPoems.firstOrNull { it.id == poemId }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Once restored or wiped it is no longer here.
    LaunchedEffect(poem == null) { if (poem == null) onBack() }
    if (poem == null) return

    BinPage(title = poem.title.ifBlank { stringResource(R.string.untitled) }, onBack = onBack) {
        Text(
            text = stringResource(R.string.bin_poem_from, poem.bookName) + " · " + formatTimestamp(poem.deletedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        Button(
            onClick = { viewModel.restorePoem(poem.id) },
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp),
        ) { Text(stringResource(R.string.restore)) }
        OutlinedButton(
            onClick = { confirmingDelete = true },
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp),
        ) { Text(stringResource(R.string.delete_forever)) }

        SelectionContainer {
            Text(
                text = poem.body.ifBlank { stringResource(R.string.empty_poem_hint) },
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PoemFont),
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 24.dp, bottom = 24.dp),
            )
        }
    }

    if (confirmingDelete) {
        DeleteForeverDialog(
            message = stringResource(R.string.delete_forever_poem_message),
            onConfirm = {
                confirmingDelete = false
                viewModel.deletePoemForever(poem.id)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** Preview of a deleted book (its poems' titles), with buttons to restore it or wipe it. */
@Composable
fun BinBookScreen(viewModel: PoemBookViewModel, bookId: String, onBack: () -> Unit) {
    val book = viewModel.binBooks.firstOrNull { it.id == bookId }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(book == null) { if (book == null) onBack() }
    if (book == null) return

    val titles by produceState<List<String>>(emptyList(), book.id) { value = viewModel.poemTitles(book.id) }

    BinPage(title = book.name, onBack = onBack) {
        Text(
            text = pluralStringResource(R.plurals.poem_count, book.poemCount, book.poemCount) +
                " · " + formatTimestamp(book.deletedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        Button(
            onClick = { viewModel.restoreBook(book.id) },
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
                viewModel.deleteBookForever(book.id)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun DeleteForeverDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_forever)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_forever)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
