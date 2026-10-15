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
    val position: Int,
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
        "SELECT p.id, p.position, v.title, v.body FROM poems p " +
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

    @Query("UPDATE poems SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("UPDATE poems SET deletedAt = :at WHERE id = :id")
    suspend fun setDeletedAt(id: String, at: Long?)

    /** Makes room for a poem arriving at [from]. */
    @Query("UPDATE poems SET position = position + 1 WHERE bookId = :bookId AND deletedAt IS NULL AND position >= :from")
    suspend fun shiftUp(bookId: String, from: Int)

    /** Closes the gap left by a poem that was at [after]. */
    @Query("UPDATE poems SET position = position - 1 WHERE bookId = :bookId AND deletedAt IS NULL AND position > :after")
    suspend fun shiftDown(bookId: String, after: Int)

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
