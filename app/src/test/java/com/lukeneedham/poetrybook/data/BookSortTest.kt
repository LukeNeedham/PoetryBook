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

    @Test
    fun `sorting an empty or single list is fine`() {
        assertEquals(emptyList<String>(), names(emptyList<Book>().sortedFor(BookSort.ALPHABETICAL)))
        assertEquals(listOf("A"), names(listOf(book("A")).sortedFor(BookSort.LAST_UPDATED)))
    }

    @Test
    fun `names that differ only by case are ordered the same way every time`() {
        val books = listOf(book("b"), book("B"), book("a"), book("A"))
        val first = names(books.sortedFor(BookSort.ALPHABETICAL))
        assertEquals(first, names(books.reversed().sortedFor(BookSort.ALPHABETICAL)))
        assertEquals(first, names(books.shuffled(kotlin.random.Random(5)).sortedFor(BookSort.ALPHABETICAL)))
        // a and A come before b and B, ignoring case.
        assertEquals(setOf("a", "A"), first.take(2).toSet())
        assertEquals(setOf("b", "B"), first.drop(2).toSet())
    }

    @Test
    fun `last updated breaks ties by name, not by input order`() {
        val books = listOf(book("Z", 5), book("M", 5), book("A", 5))
        assertEquals(listOf("A", "M", "Z"), names(books.sortedFor(BookSort.LAST_UPDATED)))
        assertEquals(listOf("A", "M", "Z"), names(books.reversed().sortedFor(BookSort.LAST_UPDATED)))
    }

    @Test
    fun `numbers sort as text`() {
        val books = listOf(book("Book 10"), book("Book 2"), book("Book 1"))
        assertEquals(listOf("Book 1", "Book 10", "Book 2"), names(books.sortedFor(BookSort.ALPHABETICAL)))
    }

    @Test
    fun `sorting does not change the list it was given`() {
        val books = listOf(book("b"), book("a"))
        books.sortedFor(BookSort.ALPHABETICAL)
        assertEquals(listOf("b", "a"), names(books))
    }

    @Test
    fun `an unset update time sorts last`() {
        val books = listOf(book("A", 0), book("B", 1))
        assertEquals(listOf("B", "A"), names(books.sortedFor(BookSort.LAST_UPDATED)))
    }
}
