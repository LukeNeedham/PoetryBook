package com.lukeneedham.poetrybook.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.BookSort
import com.lukeneedham.poetrybook.ui.shelf.ShelfPageContent
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-ShelfPage.png: the bookshelf with a few books. */
@Preview
@Composable
private fun ShelfPageScreenshot() {
    PoetryBookTheme {
        ShelfPageContent(
            books = listOf(
                Book("a", "Love poems", 12, 0xFF8E3B46.toInt()),
                Book("b", "Winter", 7, 0xFF2F4858.toInt()),
                Book("c", "Letters", 3, 0xFF5B7B4A.toInt()),
                Book("d", "Misc", 1),
            ),
            sort = BookSort.ALPHABETICAL,
            columns = 2,
            onColumnsChange = {},
            onSortChange = {},
            onOpenBook = {},
            onCreateBook = { _, _, _ -> },
            onEditBook = { _, _, _, _ -> },
            onDeleteBook = { _, _ -> },
            onOpenSettings = {},
            onOpenSearch = {},
        )
    }
}
