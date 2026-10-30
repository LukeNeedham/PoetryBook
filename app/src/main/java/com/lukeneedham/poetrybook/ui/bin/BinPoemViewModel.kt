package com.lukeneedham.poetrybook.ui.bin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.BinPoem
import com.lukeneedham.poetrybook.data.PoetryRepository
import kotlinx.coroutines.launch

/** A deleted poem in the bin, to put back where it was or to wipe. */
class BinPoemViewModel(
    private val poemId: String,
    private val poetry: PoetryRepository,
) : ViewModel() {
    /** Every deleted poem, or null until they have been read. The page leaves when this one is no longer among them. */
    var binPoems: List<BinPoem>? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch { poetry.observeBinPoems().collect { binPoems = it } }
    }

    fun restorePoem() = poetry.restorePoem(poemId)

    fun deletePoemForever() = poetry.deletePoemForever(poemId)
}
