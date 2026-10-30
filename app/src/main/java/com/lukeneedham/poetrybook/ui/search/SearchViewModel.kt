package com.lukeneedham.poetrybook.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.PoetryRepository
import com.lukeneedham.poetrybook.data.SearchPoem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Search: every poem on the shelf with its current title and text, read again whenever a poem is saved. */
class SearchViewModel(private val poetry: PoetryRepository) : ViewModel() {

    /** The poems to search, or null until they have been read. */
    var all: List<SearchPoem>? by mutableStateOf(null)
        private set

    init {
        // A poem edited in a book opened from the results is saved after this page was last drawn
        viewModelScope.launch { poetry.writes.collectLatest { all = poetry.searchablePoems() } }
    }
}
