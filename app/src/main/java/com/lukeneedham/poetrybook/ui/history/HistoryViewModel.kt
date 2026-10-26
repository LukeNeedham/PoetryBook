package com.lukeneedham.poetrybook.ui.history

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.HistoryList
import com.lukeneedham.poetrybook.data.HistoryRepository
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.data.PoetryRepository
import kotlinx.coroutines.launch

/** The versions of one poem, as pages to swipe through, and the notepad and the changes marked beside them. */
class HistoryViewModel(application: Application, private val poemId: String) : AndroidViewModel(application) {
    private val settings = PoetryRepository.get(application).settings
    private val history = HistoryRepository.get(application)

    /** Every version of the poem, newest first, or null while they are still being read. */
    var versions: List<PoemVersion>? by mutableStateOf(null)
        private set

    /** The poem as it is now, which is its newest version: what the versions are compared with. */
    val currentPoem: Poem?
        get() = versions?.firstOrNull()?.let { Poem(id = poemId, title = it.title, body = it.body) }

    /** Whether an older version of a poem is shown with its differences from the current poem marked. */
    var showHistoryChanges by mutableStateOf(settings.showHistoryChanges)
        private set

    init {
        viewModelScope.launch { history.history(poemId).collect { versions = it } }
    }

    fun changeShowHistoryChanges(show: Boolean) {
        showHistoryChanges = show
        settings.showHistoryChanges = show
    }

    /** The poem's notes if [loadNotes] has read them, else null. */
    fun loadedNotes(): String? = history.loadedNotes(poemId)

    /** Reads the poem's notes, unless that has been done already. */
    suspend fun loadNotes() = history.loadNotes(poemId)

    /** Saves the poem's notepad. This is not an edit of the poem, so it makes no new version. */
    fun saveNotes(poemId: String, notes: String) = history.saveNotes(poemId, notes)

    /** The poem's history if [trackHistory] has read it, else null. */
    fun loadedHistory(): HistoryList? = history.loadedHistory(poemId)

    /** Keeps the poem's history up to date in memory until cancelled. */
    suspend fun trackHistory() = history.trackHistory(poemId)

    /** Works out the changes of the newest [count] versions of the poem, if that hasn't been done. */
    fun loadMoreHistory(count: Int) = history.loadMoreHistory(poemId, count)
}
