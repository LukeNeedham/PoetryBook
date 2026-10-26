package com.lukeneedham.poetrybook.ui.bin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.BinPoem
import com.lukeneedham.poetrybook.data.PoetryRepository
import kotlinx.coroutines.launch

/** The bin: deleted books, then deleted poems. */
class BinViewModel(application: Application) : AndroidViewModel(application) {
    private val poetry = PoetryRepository.get(application)

    var binBooks: List<BinBook> by mutableStateOf(emptyList())
        private set
    var binPoems: List<BinPoem> by mutableStateOf(emptyList())
        private set

    init {
        viewModelScope.launch { poetry.observeBinBooks().collect { binBooks = it } }
        viewModelScope.launch { poetry.observeBinPoems().collect { binPoems = it } }
    }
}
