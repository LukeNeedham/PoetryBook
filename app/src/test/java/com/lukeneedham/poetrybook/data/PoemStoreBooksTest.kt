package com.lukeneedham.poetrybook.data

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Creating, editing and counting books on the shelf. */
class PoemStoreBooksTest : DatabaseTestBase() {
    private suspend fun shelf() = store.observeBooks().first()

    @Test
    fun `the shelf starts empty`() = test {
        assertTrue(shelf().isEmpty())
    }

    @Test
    fun `a created book appears on the shelf with its details`() = test {
        clock = 4_000
        val created = store.createBook("Winter", 0x123456)

        assertEquals(Book(created.id, "Winter", poemCount = 0, color = 0x123456, updatedAt = 4_000), shelf().single())
        assertEquals(created, shelf().single())
    }

    @Test
    fun `every created book gets its own id`() = test {
        val ids = List(50) { store.createBook("Same name", 0).id }
        assertEquals(50, ids.toSet().size)
        assertEquals(50, shelf().size)
    }

    @Test
    fun `book ids are random uuid text`() = test {
        val id = store.createBook("A", 0).id
        assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `names are trimmed when a book is created`() = test {
        store.createBook("  Spaced out \n", 0)
        assertEquals("Spaced out", shelf().single().name)
    }

    @Test
    fun `an empty or blank name is stored as given after trimming`() = test {
        store.createBook("   ", 0)
        assertEquals("", shelf().single().name)
    }

    @Test
    fun `books may share a name`() = test {
        store.createBook("Twin", 1)
        store.createBook("Twin", 2)
        assertEquals(listOf("Twin", "Twin"), shelf().map { it.name })
    }

    @Test
    fun `colour is stored as given`() = test {
        val id = store.createBook("A", 0xFFAA5500.toInt()).id
        assertEquals(0xFFAA5500.toInt(), shelf().single { it.id == id }.color)
    }

    @Test
    fun `editing changes the name and colour and nothing else`() = test {
        clock = 2_000
        val id = store.createBook("Old", 1).id
        write(id, "A")
        write(id, "B")
        val before = shelf().single()

        clock = 9_000
        store.editBook(id, "New", 0x0F0F0F)

        val after = shelf().single()
        assertEquals("New", after.name)
        assertEquals(0x0F0F0F, after.color)
        assertEquals(before.poemCount, after.poemCount)
        assertEquals(before.updatedAt, after.updatedAt)
        assertEquals(listOf("A", "B"), titles(id))
    }

    @Test
    fun `editing trims the new name`() = test {
        val id = store.createBook("Old", 1).id
        store.editBook(id, "  Trim me  ", 1)
        assertEquals("Trim me", shelf().single().name)
    }

    @Test
    fun `editing one book leaves the others alone`() = test {
        val a = store.createBook("A", 1).id
        val b = store.createBook("B", 2).id
        store.editBook(a, "A2", 3)
        val byId = shelf().associateBy { it.id }
        assertEquals("A2", byId.getValue(a).name)
        assertEquals("B", byId.getValue(b).name)
        assertEquals(2, byId.getValue(b).color)
    }

    @Test
    fun `editing a book that does not exist does nothing`() = test {
        val id = store.createBook("A", 1).id
        store.editBook("ghost", "X", 9)
        assertEquals("A", shelf().single { it.id == id }.name)
        assertEquals(1, shelf().size)
    }

    @Test
    fun `editing can set the same values again`() = test {
        val id = store.createBook("A", 1).id
        store.editBook(id, "A", 1)
        assertEquals("A", shelf().single().name)
    }

    @Test
    fun `the poem count counts only live poems`() = test {
        val id = store.createBook("A", 0).id
        val p1 = write(id, "1"); write(id, "2"); write(id, "3")
        assertEquals(3, shelf().single().poemCount)
        store.binPoem(id, p1.id)
        assertEquals(2, shelf().single().poemCount)
        store.restorePoem(p1.id)
        assertEquals(3, shelf().single().poemCount)
        store.binPoem(id, p1.id)
        store.deletePoemForever(p1.id)
        assertEquals(2, shelf().single().poemCount)
    }

    @Test
    fun `each book counts its own poems`() = test {
        val a = store.createBook("A", 0).id
        val b = store.createBook("B", 0).id
        val c = store.createBook("C", 0).id
        repeat(3) { write(a, "a$it") }
        repeat(1) { write(b, "b$it") }
        val counts = shelf().associate { it.id to it.poemCount }
        assertEquals(mapOf(a to 3, b to 1, c to 0), counts)
    }

    @Test
    fun `a book updated time is its creation time until a poem changes`() = test {
        clock = 6_000
        val id = store.createBook("A", 0).id
        assertEquals(6_000L, shelf().single().updatedAt)
        clock = 7_000
        write(id, "x")
        assertEquals(7_000L, shelf().single().updatedAt)
    }

    @Test
    fun `binning or reordering poems updates the book time, renaming does not`() = test {
        clock = 1_000
        val id = store.createBook("A", 0).id
        val a = write(id, "a"); val b = write(id, "b")

        clock = 20_000
        store.reorder(id, listOf(b.id, a.id))
        assertEquals(20_000L, shelf().single().updatedAt)

        clock = 30_000
        store.binPoem(id, a.id)
        assertEquals(30_000L, shelf().single().updatedAt)

        clock = 40_000
        store.restorePoem(a.id)
        assertEquals(40_000L, shelf().single().updatedAt)

        clock = 50_000
        store.editBook(id, "Renamed", 5)
        assertEquals(40_000L, shelf().single().updatedAt)
    }

    @Test
    fun `sorting helpers work on what the shelf returns`() = test {
        clock = 1_000
        store.createBook("banana", 0)
        clock = 3_000
        store.createBook("Apple", 0)
        clock = 2_000
        store.createBook("cherry", 0)

        assertEquals(listOf("Apple", "banana", "cherry"), shelf().sortedFor(BookSort.ALPHABETICAL).map { it.name })
        assertEquals(listOf("Apple", "cherry", "banana"), shelf().sortedFor(BookSort.LAST_UPDATED).map { it.name })
    }

    @Test
    fun `thousands of books are fine`() = test {
        repeat(500) { store.createBook("Book $it", it) }
        assertEquals(500, shelf().size)
        assertEquals(500, shelf().map { it.id }.toSet().size)
    }

    @Test
    fun `a new book has no poems and nothing in its bin`() = test {
        val id = store.createBook("A", 0).id
        assertTrue(store.poems(id).isEmpty())
        assertTrue(store.poemTitles(id).isEmpty())
        assertTrue(store.observeBinPoems().first().isEmpty())
    }

    @Test
    fun `the poems of a book that does not exist are empty`() = test {
        assertTrue(store.poems("ghost").isEmpty())
        assertTrue(store.poemTitles("ghost").isEmpty())
    }

    @Test
    fun `books do not see each others poems`() = test {
        val a = store.createBook("A", 0).id
        val b = store.createBook("B", 0).id
        write(a, "in a")
        write(b, "in b")
        assertEquals(listOf("in a"), titles(a))
        assertEquals(listOf("in b"), titles(b))
        assertNotEquals(ids(a), ids(b))
        assertNull(store.observeBooks().first().firstOrNull { it.name == "C" })
    }
}
