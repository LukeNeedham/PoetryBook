package com.lukeneedham.poetrybook.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookSortTest {
    private fun book(name: String, updatedAt: Long = 0) =
        Book(id = name, name = name, poemCount = 0, updatedAt = updatedAt)

    private fun names(books: List<Book>) = books.map { it.name }

    @Test
    fun `alphabetical ignores case`() {
        val books = listOf(book("winter"), book("Autumn"), book("Love"))
        assertEquals(listOf("Autumn", "Love", "winter"), names(books.sortedFor(BookSort.ALPHABETICAL)))
    }

    @Test
    fun `last updated puts the newest first and breaks ties by name`() {
        val books = listOf(book("B", 5), book("A", 5), book("Old", 1), book("Misc", 9))
        assertEquals(listOf("Misc", "A", "B", "Old"), names(books.sortedFor(BookSort.LAST_UPDATED)))
    }
}
