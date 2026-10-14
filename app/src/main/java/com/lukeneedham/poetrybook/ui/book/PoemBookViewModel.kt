package com.lukeneedham.poetrybook.ui.book

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.data.PoemStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Owns the poems in the book and saves them whenever they change. */
class PoemBookViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = PoemStorage(application)
    private var saveJob: Job? = null

    /** The book is never empty: the last poem can't be removed, only replaced by a blank one. */
    val poems = mutableStateListOf<Poem>().apply {
        addAll(storage.load()?.takeIf { it.isNotEmpty() } ?: listOf(welcomePoem()))
    }

    fun updateTitle(id: String, title: String) = update(id) { it.copy(title = title) }

    fun updateBody(id: String, body: String) = update(id) { it.copy(body = body) }

    /** Adds a blank poem at the end of the book and returns its index. */
    fun addPoem(): Int {
        poems.add(Poem(id = newId()))
        scheduleSave()
        return poems.lastIndex
    }

    /** Removes the poem and returns the index of the page to show instead. */
    fun deletePoem(id: String): Int {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return 0
        poems.removeAt(index)
        if (poems.isEmpty()) poems.add(Poem(id = newId()))
        scheduleSave()
        return index.coerceAtMost(poems.lastIndex)
    }

    /** Writes any pending changes immediately, e.g. when the app goes to the background. */
    fun flush() {
        saveJob?.cancel()
        saveJob = null
        storage.save(poems.toList())
    }

    private fun update(id: String, change: (Poem) -> Poem) {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = change(poems[index])
        if (updated == poems[index]) return
        poems[index] = updated
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            storage.save(poems.toList())
        }
    }

    override fun onCleared() {
        flush()
    }

    private fun welcomePoem() = Poem(
        id = newId(),
        title = getApplication<Application>().getString(R.string.welcome_title),
        body = getApplication<Application>().getString(R.string.welcome_body),
    )

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
