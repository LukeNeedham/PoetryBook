package com.lukeneedham.poetrybook.data

import androidx.room.withTransaction
import com.lukeneedham.poetrybook.data.db.BookEntity
import com.lukeneedham.poetrybook.data.db.BookRow
import com.lukeneedham.poetrybook.data.db.PoemEntity
import com.lukeneedham.poetrybook.data.db.PoemVersionEntity
import com.lukeneedham.poetrybook.data.db.PoetryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** A poem read from a directory, on its way into the database. */
data class ImportedPoem(val title: String, val body: String, val modifiedAt: Long)

/**
 * Everything the app does with books, poems, their history and the bin. A poem's current title
 * and text are its latest version. Each call is one transaction.
 */
class PoemStore(
    private val db: PoetryDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val books = db.bookDao()
    private val poems = db.poemDao()

    // --- Books ---

    fun observeBooks(): Flow<List<Book>> = books.observeShelf().map { rows -> rows.map { it.toBook() } }

    suspend fun createBook(name: String, color: Int): Book = db.withTransaction {
        val at = now()
        val entity = BookEntity(newId(), name.trim(), color, updatedAt = at)
        books.insert(entity)
        Book(entity.id, entity.name, poemCount = 0, color = color, updatedAt = at)
    }

    suspend fun editBook(id: String, name: String, color: Int) {
        books.edit(id, name.trim(), color)
    }

    // --- Poems ---

    /** The live poems of the book, in page order. */
    suspend fun poems(bookId: String): List<Poem> =
        poems.poems(bookId).map { Poem(it.id, it.title, it.body) }

    /**
     * Saves [poem] as the poem's newest version, if that differs from the version it has now.
     * A poem that isn't stored yet is added at the end of the book. Returns true if anything was written.
     */
    suspend fun save(bookId: String, poem: Poem): Boolean = db.withTransaction {
        val latest = poems.latestVersion(poem.id)
        if (!isNewVersion(latest?.toVersion(), poem.title, poem.body)) return@withTransaction false
        val at = now()
        if (latest == null) {
            poems.insert(PoemEntity(poem.id, bookId, position = poems.count(bookId)))
        }
        // Versions of one poem are keyed by time, so keep them strictly increasing.
        val createdAt = maxOf(at, (latest?.createdAt ?: Long.MIN_VALUE) + 1)
        poems.insert(PoemVersionEntity(poem.id, createdAt, poem.title, poem.body))
        books.touch(bookId, at)
        true
    }

    /**
     * Stores the page order: [orderedIds] are the book's stored poems, first page first. Any live
     * poem missing from the list keeps its relative order after the listed ones, so the pages stay
     * a complete 0..n-1 whatever is passed in.
     */
    suspend fun reorder(bookId: String, orderedIds: List<String>) = db.withTransaction {
        val live = poems.liveIds(bookId)
        val liveSet = live.toSet()
        val wanted = orderedIds.filter { it in liveSet }.distinct()
        val order = wanted + live.filter { it !in wanted.toSet() }
        poems.parkPositions(bookId)
        order.forEachIndexed { index, id -> poems.setPosition(id, index) }
        books.touch(bookId, now())
    }

    suspend fun notes(poemId: String): String = poems.notes(poemId).orEmpty()

    suspend fun setNotes(poemId: String, notes: String) = poems.setNotes(poemId, notes)

    /** The poem's versions, newest first. */
    fun observeHistory(poemId: String): Flow<List<PoemVersion>> =
        poems.observeVersions(poemId).map { rows -> rows.map { it.toVersion() } }

    suspend fun version(poemId: String, createdAt: Long): PoemVersion? =
        poems.version(poemId, createdAt)?.toVersion()

    // --- Bin ---

    /** Moves the poem to the bin. It loses its page, and the poems after it move up one. */
    suspend fun binPoem(bookId: String, poemId: String) = db.withTransaction {
        val poem = poems.poem(poemId) ?: return@withTransaction
        val position = poem.position ?: return@withTransaction // already in the bin
        val at = now()
        poems.bin(poemId, at)
        poems.parkAfter(poem.bookId, position)
        poems.settleParkedDown(poem.bookId)
        books.touch(bookId, at)
    }

    suspend fun binBook(bookId: String) = books.setDeletedAt(bookId, now())

    fun observeBinBooks(): Flow<List<BinBook>> = books.observeBin().map { rows ->
        rows.map { BinBook(it.id, it.name, it.color, it.poemCount, it.deletedAt ?: 0L) }
    }

    fun observeBinPoems(): Flow<List<BinPoem>> = poems.observeBin().map { rows ->
        rows.map { BinPoem(it.id, it.title, it.body, it.bookName, it.deletedAt) }
    }

    /** Titles of the poems in the book, in page order, for previewing a deleted book. */
    suspend fun poemTitles(bookId: String): List<String> = poems.poems(bookId).map { it.title }

    suspend fun restoreBook(bookId: String) {
        books.setDeletedAt(bookId, null)
    }

    /** Wipes the book, its poems and all their history. */
    suspend fun deleteBookForever(bookId: String) = books.delete(bookId)

    /** Puts the poem back in its book, as the last page. It doesn't get its old page back. */
    suspend fun restorePoem(poemId: String) = db.withTransaction {
        val poem = poems.poem(poemId) ?: return@withTransaction
        if (poem.deletedAt == null) return@withTransaction // not in the bin
        poems.restore(poemId, poems.count(poem.bookId))
        books.touch(poem.bookId, now())
    }

    /** Wipes the poem and all its history. */
    suspend fun deletePoemForever(poemId: String) = poems.delete(poemId)

    // --- Import ---

    /** Adds a book with these poems, each with a first version. Returns the new book's id. */
    suspend fun importBook(name: String, color: Int?, imported: List<ImportedPoem>, updatedAt: Long): String =
        db.withTransaction {
            val at = now()
            val book = BookEntity(newId(), name.trim(), color, updatedAt = if (updatedAt > 0) updatedAt else at)
            books.insert(book)
            imported.forEachIndexed { index, poem ->
                val id = newId()
                poems.insert(PoemEntity(id, book.id, position = index))
                poems.insert(
                    PoemVersionEntity(id, if (poem.modifiedAt > 0) poem.modifiedAt else at, poem.title, poem.body),
                )
            }
            book.id
        }

    private fun BookRow.toBook() = Book(id, name, poemCount, color, updatedAt)

    private fun PoemVersionEntity.toVersion() = PoemVersion(createdAt, title, body)

    private fun newId() = UUID.randomUUID().toString()
}
