package com.lukeneedham.poetrybook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// The schema, its relations and the reasons for them are written up in docs/database.md.
// Timestamps are epoch milliseconds (UTC). Ids are random UUID strings generated in the app.

/**
 * A book on the shelf.
 *
 * - [color] is the cover colour as ARGB; null means "never chosen", and the UI derives a default from the name.
 * - [updatedAt] is deliberately stored rather than derived from the poems' versions: the shelf sorts
 *   by it, and it must also survive imports (which set it from file times) and moves to the bin.
 * - [deletedAt] is null for a live book. A non-null value means the book is in the bin since then.
 *   Its poems are not touched when it is binned, so restoring the book brings back exactly what was in it.
 *
 * Book names are not unique: two books may share a name.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A poem: the identity that all of its versions hang off, plus the things that belong to the poem
 * as a whole rather than to one version.
 *
 * The title and text are not here. They are in [PoemVersionEntity]; the poem's current title and
 * text are those of its newest version, which is looked up rather than copied here, so there is
 * exactly one place each fact is stored.
 *
 * - [bookId] is the book it is in (many poems to one book). Deleting a book for good deletes its poems.
 * - [position] is the page number, counting from 0, among the book's live poems. It is dense
 *   (0, 1, 2… with no gaps) and unique within a book, which the unique index enforces. It is NULL
 *   exactly when the poem is in the bin: a binned poem has no page, and a restored one goes to the
 *   end. SQLite treats NULLs as distinct, so any number of binned poems fit under the unique index.
 *   Moving a range of poems therefore has to avoid briefly duplicating a page: see the park/settle
 *   queries in [PoemDao].
 * - [notes] is the poem's notepad: one per poem and shared by every version, which is why it is a
 *   column here (one-to-one) and not in the version table.
 * - [deletedAt] is null for a live poem; non-null means it is in the bin since then. A binned poem
 *   keeps its versions and notes. Invariant kept by the app (SQLite cannot declare it here):
 *   [deletedAt] is null if and only if [position] is not null.
 *
 * The unique index enforces the page order, serves "the live poems of a book in page order", the
 * app's hottest query, and covers the foreign key to [BookEntity] (its first column).
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
    indices = [Index(value = ["bookId", "position"], unique = true)],
)
data class PoemEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val position: Int?,
    val notes: String = "",
    val deletedAt: Long? = null,
)

/**
 * One saved state of a poem's title and text: the poem as it was from [createdAt], when that edit
 * was saved, until the next version.
 *
 * Versions are append-only: the app never updates one. [createdAt] is what identifies a version
 * within its poem, so the key is the pair ([poemId], [createdAt]) — a timestamp alone would not be
 * unique across poems, as an import creates many at once. The app keeps the timestamps of one
 * poem strictly increasing, so newest-first order is well defined.
 *
 * Deleting a poem for good deletes all its versions. The key's index also serves the foreign key
 * and "the newest version of a poem".
 */
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
