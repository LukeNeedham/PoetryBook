package com.lukeneedham.poetrybook.data

import android.content.Context
import android.net.Uri
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.db.PoetryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What the pages of the app share: the books, poems and bin in the database, and the settings. Every
 * page has a view model of its own and none of them is shared; whatever two pages both need lives
 * here, below the view models.
 *
 * Database work runs one job at a time, in the order it was asked for. The writes that a page does
 * not wait for ([savePoem], [binPoem], ...) are done on this repository's own scope, so they still
 * finish when the page that asked for them, and its view model, is gone.
 */
class PoetryRepository(private val context: Context) {
    /** How the shelf is sorted and the other small preferences. */
    val settings = AppSettings(context)

    private val store = PoemStore(PoetryDatabase.get(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storeLock = Mutex()
    private val writeCount = MutableStateFlow(0)

    /** Changes each time a write to the books or poems is done, so what shows them can read them again. */
    val writes: StateFlow<Int> = writeCount

    // --- Reading ---

    /** Every book on the shelf, from the database. */
    fun observeBooks(): Flow<List<Book>> = store.observeBooks()

    /** Deleted books, for the bin. */
    fun observeBinBooks(): Flow<List<BinBook>> = store.observeBinBooks()

    /** Deleted poems, for the bin. */
    fun observeBinPoems(): Flow<List<BinPoem>> = store.observeBinPoems()

    /** Every version of the poem, newest first, kept up to date. */
    fun observeHistory(poemId: String): Flow<List<PoemVersion>> = store.observeHistory(poemId)

    /** The poems of the book in page order, or null if they could not be read. */
    suspend fun poems(bookId: String): List<Poem>? = runCatching { locked { store.poems(bookId) } }.getOrNull()

    /** Every poem on the shelf with its current title and text, as of the last saved edit. */
    suspend fun searchablePoems(): List<SearchPoem> = locked { store.searchablePoems() }

    /** The titles of the poems of the (deleted) book. */
    suspend fun poemTitles(bookId: String): List<String> = locked { store.poemTitles(bookId) }

    /** The notepad of the poem. */
    suspend fun notes(poemId: String): String = locked { store.notes(poemId) }

    // --- Books: each says whether it worked ---

    /** Creates a book called [name] with the cover [color] (ARGB). */
    suspend fun createBook(name: String, color: Int): Boolean =
        runCatching { write { store.createBook(name, color) } }.isSuccess

    /** Renames the book and gives it the cover [color] (ARGB). */
    suspend fun editBook(id: String, name: String, color: Int): Boolean =
        runCatching { write { store.editBook(id, name, color) } }.isSuccess

    /** Moves the book to the bin. */
    suspend fun binBook(id: String): Boolean = runCatching { write { store.binBook(id) } }.isSuccess

    /**
     * Copies the books and poems in the directory [treeUri] into the app, once. Returns what was
     * imported, or null if it failed. It runs to the end even if what asked for it goes away.
     */
    suspend fun importDirectory(treeUri: Uri): ImportResult? = withContext(NonCancellable) {
        val importer = DirectoryImporter(context, settings, store, context.getString(R.string.misc_book))
        runCatching { write { withContext(Dispatchers.IO) { importer.importFrom(treeUri) } } }.getOrNull()
    }

    // --- Poems and the bin: done in the background ---

    /** Saves the poem as a new version, in the book. */
    fun savePoem(bookId: String, poem: Poem) = inBackground { save(bookId, poem) }

    /** Moves the poem to the bin. */
    fun binPoem(bookId: String, poemId: String) = inBackground { binPoem(bookId, poemId) }

    /** Moves the poem to the end of the book [toBookId]. */
    fun movePoem(poemId: String, toBookId: String) = inBackground { movePoem(poemId, toBookId) }

    /** Puts the poems of the book in the order [orderedIds]. */
    fun reorder(bookId: String, orderedIds: List<String>) = inBackground { reorder(bookId, orderedIds) }

    /** Saves the poem's notepad. This is not an edit of the poem, so it makes no new version and is not a write that [writes] counts. */
    fun setNotes(poemId: String, notes: String) = inBackground(countsAsWrite = false) { setNotes(poemId, notes) }

    fun restorePoem(id: String) = inBackground { restorePoem(id) }

    fun deletePoemForever(id: String) = inBackground { deletePoemForever(id) }

    fun restoreBook(id: String) = inBackground { restoreBook(id) }

    fun deleteBookForever(id: String) = inBackground { deleteBookForever(id) }

    private suspend fun <T> locked(block: suspend () -> T): T = storeLock.withLock { block() }

    /** [block] as a write: under the lock, and counted in [writes] when it is done, however it ends. */
    private suspend fun <T> write(block: suspend () -> T): T = try {
        locked(block)
    } finally {
        writeCount.update { it + 1 }
    }

    private fun inBackground(countsAsWrite: Boolean = true, block: suspend PoemStore.() -> Unit) {
        scope.launch {
            runCatching {
                if (countsAsWrite) write { store.block() } else locked { store.block() }
            }
        }
    }
}
