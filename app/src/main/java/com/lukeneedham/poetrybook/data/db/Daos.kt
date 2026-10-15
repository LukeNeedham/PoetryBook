package com.lukeneedham.poetrybook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A book with how many live poems it has. */
data class BookRow(
    val id: String,
    val name: String,
    val color: Int?,
    val updatedAt: Long,
    val deletedAt: Long?,
    val poemCount: Int,
)

/** A poem with its latest version. */
data class PoemRow(
    val id: String,
    val title: String,
    val body: String,
)

/** A live poem with its latest version and the book it is in, for searching. */
data class SearchRow(
    val id: String,
    val bookId: String,
    val bookName: String,
    val title: String,
    val body: String,
)

/** A deleted poem with its latest version and the book it came from. */
data class BinPoemRow(
    val id: String,
    val title: String,
    val body: String,
    val bookName: String,
    val deletedAt: Long,
)

/**
 * Joins a poem `p` to its newest version `v`. The primary key of poem_versions starts with poemId
 * and then createdAt, so the MAX is an index lookup. Every poem has at least one version, because
 * a poem and its first version are always inserted in the same transaction.
 */
private const val LATEST = "v.createdAt = (SELECT MAX(createdAt) FROM poem_versions WHERE poemId = p.id)"

@Dao
interface BookDao {
    @Query(
        "SELECT b.id, b.name, b.color, b.updatedAt, b.deletedAt, " +
            "(SELECT COUNT(*) FROM poems p WHERE p.bookId = b.id AND p.deletedAt IS NULL) AS poemCount " +
            "FROM books b WHERE b.deletedAt IS NULL",
    )
    fun observeShelf(): Flow<List<BookRow>>

    @Query(
        "SELECT b.id, b.name, b.color, b.updatedAt, b.deletedAt, " +
            "(SELECT COUNT(*) FROM poems p WHERE p.bookId = b.id AND p.deletedAt IS NULL) AS poemCount " +
            "FROM books b WHERE b.deletedAt IS NOT NULL ORDER BY b.deletedAt DESC",
    )
    fun observeBin(): Flow<List<BookRow>>

    @Insert
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET name = :name, color = :color WHERE id = :id")
    suspend fun edit(id: String, name: String, color: Int)

    @Query("UPDATE books SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE books SET deletedAt = :at WHERE id = :id")
    suspend fun setDeletedAt(id: String, at: Long?)

    /** Removes the book for good; its poems and their versions go with it. */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PoemDao {
    @Insert
    suspend fun insert(poem: PoemEntity)

    @Insert
    suspend fun insert(version: PoemVersionEntity)

    @Query("SELECT * FROM poems WHERE id = :id")
    suspend fun poem(id: String): PoemEntity?

    @Query(
        "SELECT p.id, v.title, v.body FROM poems p " +
            "JOIN poem_versions v ON v.poemId = p.id AND $LATEST " +
            "WHERE p.bookId = :bookId AND p.deletedAt IS NULL ORDER BY p.position",
    )
    suspend fun poems(bookId: String): List<PoemRow>

    @Query("SELECT COUNT(*) FROM poems WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun count(bookId: String): Int

    @Query("SELECT * FROM poem_versions WHERE poemId = :poemId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestVersion(poemId: String): PoemVersionEntity?

    @Query("SELECT * FROM poem_versions WHERE poemId = :poemId ORDER BY createdAt DESC")
    fun observeVersions(poemId: String): Flow<List<PoemVersionEntity>>

    @Query("SELECT * FROM poem_versions WHERE poemId = :poemId AND createdAt = :createdAt")
    suspend fun version(poemId: String, createdAt: Long): PoemVersionEntity?

    @Query("SELECT notes FROM poems WHERE id = :id")
    suspend fun notes(id: String): String?

    @Query("UPDATE poems SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: String, notes: String)

    /** The ids of the book's live poems, first page first. */
    @Query("SELECT id FROM poems WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY position")
    suspend fun liveIds(bookId: String): List<String>

    /** Puts a live poem on page [position]. The page must be free: see [parkPositions]. */
    @Query("UPDATE poems SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    /** Puts a live poem on page [position] of another book. The page must be free there. */
    @Query("UPDATE poems SET bookId = :bookId, position = :position WHERE id = :id")
    suspend fun setBook(id: String, bookId: String, position: Int)

    /** Every live poem of every book on the shelf, with its latest version. */
    @Query(
        "SELECT p.id, p.bookId, b.name AS bookName, v.title, v.body FROM poems p " +
            "JOIN poem_versions v ON v.poemId = p.id AND $LATEST " +
            "JOIN books b ON b.id = p.bookId " +
            "WHERE p.deletedAt IS NULL AND b.deletedAt IS NULL",
    )
    suspend fun searchable(): List<SearchRow>

    /** Moves the poem to the bin: it gets a deletion time and no page. */
    @Query("UPDATE poems SET deletedAt = :at, position = NULL WHERE id = :id")
    suspend fun bin(id: String, at: Long)

    /** Takes the poem out of the bin, onto page [position]. */
    @Query("UPDATE poems SET deletedAt = NULL, position = :position WHERE id = :id")
    suspend fun restore(id: String, position: Int)

    // Positions are unique per book (a unique index), and SQLite checks that after every row it
    // changes, not at the end of the statement. So adding or subtracting 1 across a range could
    // briefly put two poems on one page and fail. Instead, rows are first parked on negative
    // pages, which can't clash with real ones or each other, and then moved to their final page.

    /** Step 1 of closing a gap: parks the live poems after page [after] on pages -(p). */
    @Query("UPDATE poems SET position = -position WHERE bookId = :bookId AND position > :after")
    suspend fun parkAfter(bookId: String, after: Int)

    /** Step 2 of closing a gap: moves parked poems (page -(p)) to page p - 1. */
    @Query("UPDATE poems SET position = -position - 1 WHERE bookId = :bookId AND position < 0")
    suspend fun settleParkedDown(bookId: String)

    /** Step 1 of reordering: parks all the book's live poems on pages -1, -2… */
    @Query("UPDATE poems SET position = -position - 1 WHERE bookId = :bookId AND position >= 0")
    suspend fun parkPositions(bookId: String)

    /** Removes the poem for good, with all its versions. */
    @Query("DELETE FROM poems WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "SELECT p.id, v.title, v.body, b.name AS bookName, p.deletedAt AS deletedAt FROM poems p " +
            "JOIN poem_versions v ON v.poemId = p.id AND $LATEST " +
            "JOIN books b ON b.id = p.bookId " +
            "WHERE p.deletedAt IS NOT NULL AND b.deletedAt IS NULL ORDER BY p.deletedAt DESC",
    )
    fun observeBin(): Flow<List<BinPoemRow>>
}
