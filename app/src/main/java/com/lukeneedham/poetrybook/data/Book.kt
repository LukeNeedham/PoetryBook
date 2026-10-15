package com.lukeneedham.poetrybook.data

/**
 * A book on the shelf.
 *
 * @property color the cover colour (ARGB) the user chose, or null if they never did.
 * @property updatedAt when a poem in the book was last changed, or the book made (epoch millis).
 */
data class Book(
    val id: String,
    val name: String,
    val poemCount: Int,
    val color: Int? = null,
    val updatedAt: Long = 0L,
)

/** How the bookshelf orders its books. */
enum class BookSort {
    /** By name. */
    ALPHABETICAL,

    /** The book with the most recently created or changed poems first. */
    LAST_UPDATED,
}

/** These books in the order [sort] asks for. */
fun List<Book>.sortedFor(sort: BookSort): List<Book> {
    val byName = compareBy<Book>({ it.name.lowercase() }, { it.name })
    return when (sort) {
        BookSort.ALPHABETICAL -> sortedWith(byName)
        BookSort.LAST_UPDATED -> sortedWith(compareByDescending<Book> { it.updatedAt }.then(byName))
    }
}
