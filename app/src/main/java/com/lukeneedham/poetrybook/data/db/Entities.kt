package com.lukeneedham.poetrybook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A book. [deletedAt] is set while it sits in the bin; its poems stay attached to it. */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A poem. Its title and text live in [PoemVersionEntity]; the latest version is the current one.
 * The [notes] are the poem's notepad, shared by all its versions.
 *
 * [position] is the page number (from 0) among the book's poems that aren't in the bin. A poem
 * in the bin keeps the position it had so it can go back to the same page.
 */
@Entity(
    tableName = "poems",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class PoemEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val position: Int,
    val notes: String = "",
    val deletedAt: Long? = null,
)

/** The poem as it was from [createdAt] (epoch millis), when that edit was saved. */
@Entity(
    tableName = "poem_versions",
    primaryKeys = ["poemId", "createdAt"],
    foreignKeys = [
        ForeignKey(
            entity = PoemEntity::class,
            parentColumns = ["id"],
            childColumns = ["poemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PoemVersionEntity(
    val poemId: String,
    val createdAt: Long,
    val title: String,
    val body: String,
)
