package com.lukeneedham.poetrybook.ui.bin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.PoetryRepository
import kotlinx.coroutines.launch

/** A deleted book in the bin, to restore or to wipe. */
class BinBookViewModel(application: Application, private val bookId: String) : AndroidViewModel(application) {
    private val poetry = PoetryRepository.get(application)

    /** Every deleted book, or null until they have been read. The page leaves when this one is no longer among them. */
    var binBooks: List<BinBook>? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch { poetry.observeBinBooks().collect { binBooks = it } }
    }

    /** The titles of the deleted book's poems. */
    suspend fun poemTitles(): List<String> = poetry.poemTitles(bookId)

    fun restoreBook() = poetry.restoreBook(bookId)

    fun deleteBookForever() = poetry.deleteBookForever(bookId)
}
