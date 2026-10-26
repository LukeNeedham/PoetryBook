package com.lukeneedham.poetrybook.ui.book

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.HistoryList
import com.lukeneedham.poetrybook.data.HistoryRepository
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.data.PoetryRepository
import com.lukeneedham.poetrybook.data.sortedFor
import com.lukeneedham.poetrybook.data.withOneBlankLast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One book being read. The poems live in the database; this keeps them in memory so typing is
 * instant, and writes a poem as a new version when an edit is finished (see [commit]). The book is
 * the one in the page's route, so this view model is its own and is not shared with any other page.
 */
class BookViewModel(
    application: Application,
    private val bookId: String,
    poemToShow: String?,
) : AndroidViewModel(application) {
    private val poetry = PoetryRepository.get(application)
    private val history = HistoryRepository.get(application)

    /** True once the book's poems have been read. */
    var isLoaded by mutableStateOf(false)
        private set

    /** True if the book could not be read, e.g. it was deleted: there is nothing to show. */
    var loadFailed by mutableStateOf(false)
        private set

    /**
     * The book's poems in page order, as they are being edited. The last one is always a blank,
     * unstarted poem, and only the last one is: it's where the next poem gets written.
     */
    val poems = mutableStateListOf<Poem>()

    /** What each poem looked like when it was last written to the database. Missing: not stored yet. */
    private val stored = mutableStateMapOf<String, Poem>()

    /** Every book on the shelf, from the database: the books a poem can be moved to. */
    private var books: List<Book> by mutableStateOf(emptyList())

    /** [books] in the order the bookshelf shows them. */
    val shelfBooks: List<Book>
        get() = books.sortedFor(poetry.settings.bookSort)

    /** A poem a search result wants shown as soon as the book has opened, once. */
    var poemToShow: String? by mutableStateOf(poemToShow)
        private set

    init {
        viewModelScope.launch { poetry.observeBooks().collect { books = it } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val exists = poetry.observeBooks().first().any { it.id == bookId }
        val loaded = if (exists) poetry.poems(bookId) else null
        if (loaded == null) {
            loadFailed = true
            return
        }
        loaded.forEach { stored[it.id] = it }
        poems.addAll(loaded)
        keepOneBlankPoemLast()
        isLoaded = true
    }

    /** The poem to show has been shown. */
    fun poemShown() {
        poemToShow = null
    }

    // --- Editing poems ---

    fun updateTitle(id: String, title: String) = update(id) { it.copy(title = title) }

    fun updateBody(id: String, body: String) = update(id) { it.copy(body = body) }

    private fun update(id: String, change: (Poem) -> Poem) {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = change(poems[index])
        if (updated == poems[index]) return
        poems[index] = updated
        keepOneBlankPoemLast()
    }

    /**
     * Finishes the edit of the poem [id]: if it now differs from the version stored, saves it as a
     * new version. A poem written in the book's blank last page is stored as a new poem.
     */
    fun commit(id: String) {
        val poem = poems.firstOrNull { it.id == id } ?: return
        save(poem)
    }

    /** Commits every poem with unsaved changes, e.g. when the app goes to the background. */
    fun commitAll() {
        poems.toList().forEach { save(it) }
    }

    private fun save(poem: Poem) {
        val before = stored[poem.id]
        if (before == null && poem.isBlank) return
        if (before == poem) return
        stored[poem.id] = poem
        poetry.savePoem(bookId, poem)
    }

    /** Moves the poem to the bin and returns the index of the page to show instead. */
    fun deletePoem(id: String): Int {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return 0
        poems.removeAt(index)
        keepOneBlankPoemLast()
        if (stored.remove(id) != null) poetry.binPoem(bookId, id)
        return index.coerceAtMost(poems.lastIndex)
    }

    /**
     * Moves the poem to the end of the book [toBookId] and returns the index of the page to show
     * instead. The poem must already be stored: finish its edit with [commit] first.
     */
    fun movePoemToBook(id: String, toBookId: String): Int {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0 || bookId == toBookId || id !in stored) return index.coerceAtLeast(0)
        poems.removeAt(index)
        stored.remove(id)
        keepOneBlankPoemLast()
        poetry.movePoem(id, toBookId)
        return index.coerceAtMost(poems.lastIndex)
    }

    /**
     * Moves the poem at page [from] to page [to]. The blank poem always stays last, so neither
     * index may be that page.
     */
    fun movePoem(from: Int, to: Int) {
        val movable = 0 until poems.lastIndex
        if (from == to || from !in movable || to !in movable) return
        poems.add(to, poems.removeAt(from))
        poetry.reorder(bookId, poems.map { it.id }.filter { it in stored })
    }

    /** True if the poem has been written to the database, so it has a history and a notepad. */
    fun isStored(id: String) = id in stored

    /**
     * Makes sure the book ends with exactly one blank poem: starting to write in it adds a fresh
     * one after it, and emptying a poem at the end doesn't leave two blanks behind.
     */
    private fun keepOneBlankPoemLast() {
        val fixed = withOneBlankLast(poems) { Poem(id = newId()) }
        // Only the end changes, so touch only the end and leave the other pages alone.
        if (fixed.size > poems.size) poems.add(fixed.last())
        while (poems.size > fixed.size) poems.removeAt(poems.lastIndex)
    }

    // --- Notepad and history: kept in memory by the repository, which the history page shares ---

    /** The poem's notes if [loadNotes] has read them, else null. */
    fun loadedNotes(poemId: String): String? = history.loadedNotes(poemId)

    /** Reads the poem's notes into memory, unless that has been done already. */
    suspend fun loadNotes(poemId: String) = history.loadNotes(poemId)

    /** Saves the poem's notepad. This is not an edit of the poem, so it makes no new version. */
    fun saveNotes(poemId: String, notes: String) = history.saveNotes(poemId, notes)

    /** The poem's history if [trackHistory] has read it, else null. */
    fun loadedHistory(poemId: String): HistoryList? = history.loadedHistory(poemId)

    /** Keeps the poem's history up to date in memory until cancelled. */
    suspend fun trackHistory(poemId: String) = history.trackHistory(poemId)

    /** Works out the changes of the newest [count] versions of the poem, if that hasn't been done. */
    fun loadMoreHistory(poemId: String, count: Int) = history.loadMoreHistory(poemId, count)

    /** The finished edits are saved when the book is left, however it is left. */
    override fun onCleared() {
        commitAll()
    }

    private fun newId() = UUID.randomUUID().toString()
}
