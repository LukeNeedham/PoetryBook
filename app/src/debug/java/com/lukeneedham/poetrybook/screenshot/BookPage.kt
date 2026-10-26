package com.lukeneedham.poetrybook.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.ui.book.BookPageContent
import com.lukeneedham.poetrybook.ui.theme.AppTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-BookPage.png: a book open on its contents page. */
@Preview
@Composable
private fun BookPageScreenshot() {
    AppTheme {
        BookPageContent(
            poems = listOf(
                Poem("1", "Evening", "The light goes slowly\nover the roofs,\nand the street lets go of the day."),
                Poem("2", "Frost", "Silver on the grass\nbefore anyone is awake."),
                Poem("3"),
            ),
            currentBookId = "a",
            shelfBooks = listOf(Book("a", "Love poems", 2), Book("b", "Winter", 1)),
            poemToShow = null,
            onPoemShown = {},
            isStored = { true },
            loadedHistory = { null },
            loadedNotes = { null },
            trackHistory = {},
            loadNotes = {},
            onLoadMoreHistory = { _, _ -> },
            onSaveNotes = { _, _ -> },
            onCommit = {},
            onTitleChange = { _, _ -> },
            onBodyChange = { _, _ -> },
            onMovePoem = { _, _ -> },
            onMovePoemToBook = { _, _ -> 0 },
            onDeletePoem = { 0 },
            onOpenShelf = {},
            onOpenVersion = { _, _ -> },
        )
    }
}
