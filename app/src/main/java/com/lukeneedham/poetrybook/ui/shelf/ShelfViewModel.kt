package com.lukeneedham.poetrybook.ui.shelf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.AppSettings
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.BookSort
import com.lukeneedham.poetrybook.data.PoetryRepository
import com.lukeneedham.poetrybook.data.sortedFor
import kotlinx.coroutines.launch

/** The bookshelf: the books, how they are ordered and how many fit in a row. */
class ShelfViewModel(application: Application) : AndroidViewModel(application) {
    private val poetry = PoetryRepository.get(application)
    private val settings = poetry.settings

    /** Every book on the shelf, from the database. */
    var books: List<Book> by mutableStateOf(emptyList())
        private set

    /** How the bookshelf is ordered. */
    var bookSort by mutableStateOf(settings.bookSort)
        private set

    /** How many books the bookshelf shows in a row; pinching the shelf changes it. */
    var shelfColumns by mutableStateOf(settings.shelfColumns)
        private set

    /** [books] in the order the bookshelf shows them. */
    val shelfBooks: List<Book>
        get() = books.sortedFor(bookSort)

    init {
        viewModelScope.launch { poetry.observeBooks().collect { books = it } }
    }

    fun sortBooks(sort: BookSort) {
        bookSort = sort
        settings.bookSort = sort
    }

    fun changeShelfColumns(columns: Int) {
        shelfColumns = columns.coerceIn(AppSettings.MIN_SHELF_COLUMNS, AppSettings.MAX_SHELF_COLUMNS)
        settings.shelfColumns = shelfColumns
    }

    /** Creates a book called [name] with the cover [color] (ARGB) and calls [onDone] with whether that worked. */
    fun createBook(name: String, color: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(poetry.createBook(name, color)) }
    }

    /** Renames [book] and gives it the cover [color] (ARGB), and calls [onDone] with whether that worked. */
    fun editBook(book: Book, name: String, color: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(poetry.editBook(book.id, name, color)) }
    }

    /** Moves [book] to the bin and calls [onDone] with whether that worked. */
    fun deleteBook(book: Book, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(poetry.binBook(book.id)) }
    }
}
