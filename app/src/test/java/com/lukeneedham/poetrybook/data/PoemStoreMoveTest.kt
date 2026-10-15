package com.lukeneedham.poetrybook.data

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test

/** Moving a poem to another book, and finding poems across books. */
class PoemStoreMoveTest : DatabaseTestBase() {
    @Test
    fun `a moved poem goes to the end of the other book and the old book closes the gap`() = test {
        val from = newBook("From"); val to = newBook("To")
        write(from, "A"); val b = write(from, "B"); write(from, "C")
        write(to, "X"); write(to, "Y")

        store.movePoem(b.id, to)

        assertEquals(listOf("A", "C"), titles(from))
        assertEquals(listOf("X", "Y", "B"), titles(to))
    }

    @Test
    fun `a moved poem keeps its history`() = test {
        val from = newBook(); val to = newBook()
        val p = write(from, "v1")
        clock += 1_000
        store.save(from, p.copy(title = "v2"))
        store.movePoem(p.id, to)
        assertEquals(2, store.observeHistoryOnce(p.id))
    }

    @Test
    fun `moving a poem into its own book changes nothing`() = test {
        val book = newBook()
        write(book, "A"); val b = write(book, "B")
        store.movePoem(b.id, book)
        assertEquals(listOf("A", "B"), titles(book))
    }

    @Test
    fun `search sees live poems of live books only`() = test {
        val a = newBook("A"); val gone = newBook("Gone")
        write(a, "Kept"); val binned = write(a, "Binned"); write(gone, "InGoneBook")
        store.binPoem(a, binned.id)
        store.binBook(gone)
        assertEquals(listOf("Kept"), store.searchablePoems().map { it.title })
    }

    private suspend fun PoemStore.observeHistoryOnce(id: String) =
        observeHistory(id).first().size
}
