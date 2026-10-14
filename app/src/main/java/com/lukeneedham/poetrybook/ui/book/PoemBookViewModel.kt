package com.lukeneedham.poetrybook.ui.book

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.AppSettings
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.Library
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.data.PoemRepository
import com.lukeneedham.poetrybook.data.withOneBlankLast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class BookStatus {
    /** Looking at the poems folder. */
    LOADING,

    /** There's no usable poems folder: first launch, or access to it was lost. */
    NEEDS_FOLDER,

    READY,
}

/**
 * Owns the library and the book being read. Poems live as text files in the folder the user
 * chose, one subfolder per book; this keeps the open book's poems in memory and writes changes
 * back shortly after each edit.
 */
class PoemBookViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storageLock = Mutex()
    private var library: Library? = null
    private var repository: PoemRepository? = null
    private var saveJob: Job? = null

    var status by mutableStateOf(BookStatus.LOADING)
        private set

    /** True when a folder was chosen before but can no longer be read. */
    var folderLost by mutableStateOf(false)
        private set

    var folderUri: Uri? by mutableStateOf(null)
        private set

    /** Every book in the chosen folder, for the bookshelf. */
    var books: List<Book> by mutableStateOf(emptyList())
        private set

    /** The book being read, or null while on the bookshelf. */
    var currentBook: Book? by mutableStateOf(null)
        private set

    /** True while the bookshelf is looking at the folder. */
    var loadingBooks by mutableStateOf(false)
        private set

    /**
     * The open book's poems in page order. The last one is always a blank, unstarted poem, and
     * only the last one is: it's where the next poem gets written.
     */
    val poems = mutableStateListOf<Poem>()

    init {
        scope.launch { restore() }
    }

    private suspend fun restore() {
        val uri = settings.folderUri
        if (uri == null) {
            status = BookStatus.NEEDS_FOLDER
            return
        }
        val lib = if (hasAccess(uri)) openLibrary(uri) else null
        val found = lib?.let { listBooks(it) }
        if (lib == null || found == null) {
            folderLost = true
            status = BookStatus.NEEDS_FOLDER
            return
        }
        adopt(uri, lib, found)
        status = BookStatus.READY
    }

    /**
     * Starts using [uri], a folder just picked with the system picker, and calls [onDone] with
     * whether it worked. The shelf then shows the books in that folder. Poems from the previous
     * folder stay there, untouched.
     */
    fun chooseFolder(uri: Uri, onDone: (Boolean) -> Unit) {
        scope.launch {
            val granted = runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.isSuccess
            val lib = if (granted) openLibrary(uri) else null
            val found = lib?.let { listBooks(it) }
            if (lib == null || found == null) {
                onDone(false)
                return@launch
            }

            // Changes made just before switching belong to the folder they were made in.
            saveJob?.cancel()
            saveNow()

            val previous = folderUri
            adopt(uri, lib, found)
            if (previous != null && previous != uri) releaseAccess(previous)
            status = BookStatus.READY
            onDone(true)
        }
    }

    private fun openLibrary(uri: Uri) =
        Library(getApplication(), uri, getApplication<Application>().getString(R.string.misc_book))

    private suspend fun listBooks(lib: Library): List<Book>? =
        withContext(Dispatchers.IO) { runCatching { lib.books() }.getOrNull() }

    private fun adopt(uri: Uri, lib: Library, found: List<Book>) {
        library = lib
        repository = null
        currentBook = null
        poems.clear()
        books = found
        folderUri = uri
        folderLost = false
        settings.folderUri = uri
    }

    /** Looks at the folder again, e.g. when the bookshelf is shown, to pick up new books and counts. */
    fun refreshBooks() {
        val lib = library ?: return
        scope.launch {
            loadingBooks = true
            listBooks(lib)?.let { books = it }
            loadingBooks = false
        }
    }

    /** Opens [book] and calls [onDone] with whether it could be read. */
    fun openBook(book: Book, onDone: (Boolean) -> Unit) {
        val lib = library ?: return onDone(false)
        scope.launch {
            val repo = PoemRepository(lib.folderOf(book), settings)
            val loaded = withContext(Dispatchers.IO) { runCatching { repo.load() }.getOrNull() }
            if (loaded == null) {
                onDone(false)
                return@launch
            }
            saveJob?.cancel()
            saveNow()
            repository = repo
            poems.clear()
            poems.addAll(loaded)
            keepOneBlankPoemLast()
            currentBook = book
            onDone(true)
        }
    }

    /** Puts the open book back on the shelf, saving it first. */
    fun closeBook() {
        if (currentBook == null) return
        saveJob?.cancel()
        scope.launch {
            saveNow()
            repository = null
            currentBook = null
            poems.clear()
            refreshBooks()
        }
    }

    /** Creates a book called [name] and calls [onDone] with it, or null if that failed. */
    fun createBook(name: String, onDone: (Book?) -> Unit) {
        val lib = library ?: return onDone(null)
        scope.launch {
            val created = withContext(Dispatchers.IO) { runCatching { lib.createBook(name) }.getOrNull() }
            if (created != null) refreshBooks()
            onDone(created)
        }
    }

    private fun hasAccess(uri: Uri): Boolean =
        getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    private fun releaseAccess(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun updateTitle(id: String, title: String) = update(id) { it.copy(title = title) }

    fun updateBody(id: String, body: String) = update(id) { it.copy(body = body) }

    /** Removes the poem (and its file) and returns the index of the page to show instead. */
    fun deletePoem(id: String): Int {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return 0
        poems.removeAt(index)
        keepOneBlankPoemLast()
        scheduleSave()
        return index.coerceAtMost(poems.lastIndex)
    }

    /** Writes any pending changes now, e.g. when the app goes to the background. */
    fun flush() {
        if (repository == null) return
        saveJob?.cancel()
        scope.launch { saveNow() }
    }

    private fun update(id: String, change: (Poem) -> Poem) {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = change(poems[index])
        if (updated == poems[index]) return
        poems[index] = updated
        keepOneBlankPoemLast()
        scheduleSave()
    }

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

    private fun scheduleSave() {
        if (repository == null) return
        saveJob?.cancel()
        saveJob = scope.launch {
            // Wait for a pause in typing so a title isn't renamed on every keystroke.
            delay(SAVE_DEBOUNCE_MS)
            var attempts = 0
            while (!saveNow() && ++attempts <= MAX_SAVE_RETRIES) delay(RETRY_DELAY_MS)
        }
    }

    /** Returns true if the folder now matches the open book. */
    private suspend fun saveNow(): Boolean {
        val repo = repository ?: return true
        val snapshot = poems.toList()
        return storageLock.withLock {
            // Finish what was started even if the app is going away: a half-saved poem is worse.
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching { repo.save(snapshot) }.getOrDefault(false)
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        const val SAVE_DEBOUNCE_MS = 800L
        const val RETRY_DELAY_MS = 2_000L
        const val MAX_SAVE_RETRIES = 3
    }
}
