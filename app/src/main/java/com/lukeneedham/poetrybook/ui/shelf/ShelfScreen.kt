package com.lukeneedham.poetrybook.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * The bookshelf: every book in the chosen folder as a cover. Tap one to read it.
 *
 * @param onCreateBook creates a book with the given name; reports whether that worked.
 */
@Composable
fun ShelfScreen(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onCreateBook: (name: String, onDone: (Boolean) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    var creating by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.bookshelf),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = PoemFont),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings), color = colors.accent)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(books, key = { it.documentId }) { book ->
                BookCover(book = book, onClick = { onOpenBook(book) })
            }
            item(key = "new book") {
                NewBookTile(onClick = { creating = true })
            }
        }
    }

    if (creating) {
        NewBookDialog(
            onCreate = onCreateBook,
            onDismiss = { creating = false },
        )
    }
}

// Muted cloth colours for the covers; a book keeps its colour because it comes from its name.
private val CoverColors = listOf(
    Color(0xFF6B3F36),
    Color(0xFF3F5B52),
    Color(0xFF35506E),
    Color(0xFF6E5A35),
    Color(0xFF5B3F6B),
    Color(0xFF4B4B4B),
)

private fun coverColor(name: String): Color = CoverColors[Math.floorMod(name.lowercase().hashCode(), CoverColors.size)]

@Composable
private fun BookCover(book: Book, onClick: () -> Unit) {
    val colors = paperColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .background(coverColor(book.name), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp))
                .clickable(onClick = onClick),
        ) {
            // The spine.
            Box(
                Modifier
                    .width(14.dp)
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)),
            )
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PoemFont, fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF4EBD8),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 26.dp, end = 14.dp),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.poem_count, book.poemCount, book.poemCount),
            style = MaterialTheme.typography.bodySmall,
            color = colors.faintInk,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NewBookTile(onClick: () -> Unit) {
    val colors = paperColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .border(2.dp, colors.faintInk.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.new_book),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PoemFont),
                color = colors.faintInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun NewBookDialog(
    onCreate: (name: String, onDone: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState()
    var failed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_book)) },
        text = {
            Column {
                OutlinedTextField(
                    state = name,
                    label = { Text(stringResource(R.string.book_name)) },
                    isError = failed,
                    supportingText = if (failed) {
                        { Text(stringResource(R.string.new_book_failed)) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.text.isNotBlank(),
                onClick = {
                    failed = false
                    onCreate(name.text.toString()) { ok -> if (ok) onDismiss() else failed = true }
                },
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ShelfPreview() {
    PoetryBookTheme {
        ShelfScreen(
            books = listOf(
                Book("a", "Love poems", false, 12),
                Book("b", "Winter", false, 1),
                Book("c", "Misc", true, 0),
            ),
            onOpenBook = {},
            onCreateBook = { _, _ -> },
            onOpenSettings = {},
        )
    }
}
