package com.lukeneedham.poetrybook.ui.book

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.AppSettings
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.BinPoem
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.BookSort
import com.lukeneedham.poetrybook.data.DirectoryImporter
import com.lukeneedham.poetrybook.data.ImportResult
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.data.PoemStore
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.data.db.PoetryDatabase
import com.lukeneedham.poetrybook.data.sortedFor
import com.lukeneedham.poetrybook.data.withOneBlankLast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Owns the shelf and the book being read. Books and poems live in the database; this keeps the
 * open book's poems in memory so typing is instant, and writes them as a new version of the poem
 * when an edit is finished (see [commit]).
 */
class PoemBookViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val store = PoemStore(PoetryDatabase.get(application))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Database work runs one job at a time, in the order it was asked for. */
    private val storeLock = Mutex()

    /** Every book on the shelf, from the database. */
    var books: List<Book> by mutableStateOf(emptyList())
        private set

    /** How the bookshelf is ordered. */
    var bookSort by mutableStateOf(settings.bookSort)
        private set

    /** [books] in the order the bookshelf shows them. */
    val shelfBooks: List<Book>
        get() = books.sortedFor(bookSort)

    /** The book being read, or null while on the bookshelf. */
    var currentBook: Book? by mutableStateOf(null)
        private set

    /**
     * The open book's poems in page order, as they are being edited. The last one is always a
     * blank, unstarted poem, and only the last one is: it's where the next poem gets written.
     */
    val poems = mutableStateListOf<Poem>()

    /** What each poem looked like when it was last written to the database. Missing: not stored yet. */
    private val stored = mutableStateMapOf<String, Poem>()

    /** Deleted books and poems, for the bin. */
    var binBooks: List<BinBook> by mutableStateOf(emptyList())
        private set
    var binPoems: List<BinPoem> by mutableStateOf(emptyList())
        private set

    init {
        scope.launch { store.observeBooks().collect { books = it } }
        scope.launch { store.observeBinBooks().collect { binBooks = it } }
        scope.launch { store.observeBinPoems().collect { binPoems = it } }
    }

    fun sortBooks(sort: BookSort) {
        bookSort = sort
        settings.bookSort = sort
    }

    // --- Books ---

    /** Opens [book] and calls [onDone] with whether it could be read. */
    fun openBook(book: Book, onDone: (Boolean) -> Unit) {
        scope.launch {
            val loaded = runCatching { locked { store.poems(book.id) } }.getOrNull()
            if (loaded == null) {
                onDone(false)
                return@launch
            }
            stored.clear()
            loaded.forEach { stored[it.id] = it }
            poems.clear()
            poems.addAll(loaded)
            keepOneBlankPoemLast()
            currentBook = book
            onDone(true)
        }
    }

    /** Puts the open book back on the shelf, saving any finished edits first. */
    fun closeBook() {
        if (currentBook == null) return
        commitAll()
        stored.clear()
        currentBook = null
        poems.clear()
    }

    /** Creates a book called [name] with the cover [color] (ARGB) and calls [onDone] with whether that worked. */
    fun createBook(name: String, color: Int, onDone: (Boolean) -> Unit) {
        scope.launch {
            val ok = runCatching { locked { store.createBook(name, color) } }.isSuccess
            onDone(ok)
        }
    }

    /** Renames [book] and gives it the cover [color] (ARGB), and calls [onDone] with whether that worked. */
    fun editBook(book: Book, name: String, color: Int, onDone: (Boolean) -> Unit) {
        scope.launch {
            val ok = runCatching { locked { store.editBook(book.id, name, color) } }.isSuccess
            onDone(ok)
        }
    }

    /** Moves [book] to the bin and calls [onDone] with whether that worked. */
    fun deleteBook(book: Book, onDone: (Boolean) -> Unit) {
        scope.launch {
            val ok = runCatching { locked { store.binBook(book.id) } }.isSuccess
            onDone(ok)
        }
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
        val book = currentBook ?: return
        val poem = poems.firstOrNull { it.id == id } ?: return
        save(book, poem)
    }

    /** Commits every poem with unsaved changes, e.g. when the app goes to the background. */
    fun commitAll() {
        val book = currentBook ?: return
        poems.toList().forEach { save(book, it) }
    }

    private fun save(book: Book, poem: Poem) {
        val before = stored[poem.id]
        if (before == null && poem.isBlank) return
        if (before == poem) return
        stored[poem.id] = poem
        scope.launch { runCatching { locked { store.save(book.id, poem) } } }
    }

    /** Moves the poem to the bin and returns the index of the page to show instead. */
    fun deletePoem(id: String): Int {
        val book = currentBook
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return 0
        poems.removeAt(index)
        keepOneBlankPoemLast()
        if (book != null && stored.remove(id) != null) {
            scope.launch { runCatching { locked { store.binPoem(book.id, id) } } }
        }
        return index.coerceAtMost(poems.lastIndex)
    }

    /**
     * Moves the poem at page [from] to page [to]. The blank poem always stays last, so neither
     * index may be that page.
     */
    fun movePoem(from: Int, to: Int) {
        val book = currentBook ?: return
        val movable = 0 until poems.lastIndex
        if (from == to || from !in movable || to !in movable) return
        poems.add(to, poems.removeAt(from))
        val order = poems.map { it.id }.filter { it in stored }
        scope.launch { runCatching { locked { store.reorder(book.id, order) } } }
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

    // --- Notepad and history ---

    suspend fun notes(poemId: String): String = locked { store.notes(poemId) }

    /** Saves the poem's notepad. This is not an edit of the poem, so it makes no new version. */
    fun saveNotes(poemId: String, notes: String) {
        scope.launch { runCatching { locked { store.setNotes(poemId, notes) } } }
    }

    fun history(poemId: String): Flow<List<PoemVersion>> = store.observeHistory(poemId)

    suspend fun version(poemId: String, createdAt: Long): PoemVersion? = locked { store.version(poemId, createdAt) }

    // --- Bin ---

    fun restorePoem(id: String) = inStore { store.restorePoem(id) }

    fun deletePoemForever(id: String) = inStore { store.deletePoemForever(id) }

    fun restoreBook(id: String) = inStore { store.restoreBook(id) }

    fun deleteBookForever(id: String) = inStore { store.deleteBookForever(id) }

    suspend fun poemTitles(bookId: String): List<String> = locked { store.poemTitles(bookId) }

    private fun inStore(block: suspend () -> Unit) {
        scope.launch { runCatching { locked { block() } } }
    }

    // --- Import ---

    /** Copies the books and poems in the directory [treeUri] into the app, once, and calls [onDone] with what was imported, or null if it failed. */
    fun importDirectory(treeUri: Uri, onDone: (ImportResult?) -> Unit) {
        val app = getApplication<Application>()
        scope.launch {
            val importer = DirectoryImporter(app, settings, store, app.getString(R.string.misc_book))
            val result = runCatching {
                locked { withContext(Dispatchers.IO) { importer.importFrom(treeUri) } }
            }.getOrNull()
            onDone(result)
        }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = storeLock.withLock { block() }

    override fun onCleared() {
        scope.cancel()
    }

    private fun newId() = UUID.randomUUID().toString()
}
