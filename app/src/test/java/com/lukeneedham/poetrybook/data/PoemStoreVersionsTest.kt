package com.lukeneedham.poetrybook.data

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** History: when a version is written, what it holds, how versions are ordered and identified. */
class PoemStoreVersionsTest : DatabaseTestBase() {
    private suspend fun history(poemId: String) = store.observeHistory(poemId).first()

    private suspend fun updatedAt(): Long = store.observeBooks().first().single().updatedAt

    // --- First version ---

    @Test
    fun `the first version holds exactly the title and text saved`() = test {
        val book = newBook()
        clock = 4_000
        store.save(book, Poem("p", "Title", "Body\nlines"))

        assertEquals(listOf(PoemVersion(4_000, "Title", "Body\nlines")), history("p"))
    }

    @Test
    fun `a blank poem can be saved and is a real poem with one version`() = test {
        val book = newBook()
        assertTrue(store.save(book, Poem("p", "", "")))
        assertEquals(listOf(Poem("p", "", "")), store.poems(book))
        assertEquals(1, history("p").size)
    }

    @Test
    fun `a poem with only a title or only a body saves`() = test {
        val book = newBook()
        assertTrue(store.save(book, Poem("a", "Only title", "")))
        assertTrue(store.save(book, Poem("b", "", "Only body")))
        assertEquals(listOf("Only title", ""), titles(book))
    }

    // --- When a new version is written ---

    @Test
    fun `an identical save returns false and writes nothing, however often`() = test {
        val book = newBook()
        val poem = Poem("p", "Rose", "red")
        store.save(book, poem)
        repeat(20) {
            clock += 1_000
            assertFalse(store.save(book, poem))
        }
        assertEquals(1, history("p").size)
    }

    @Test
    fun `an identical save does not move the books last updated time`() = test {
        val book = newBook()
        clock = 2_000
        store.save(book, Poem("p", "Rose", "red"))
        clock = 9_000
        store.save(book, Poem("p", "Rose", "red"))
        assertEquals(2_000L, updatedAt())
    }

    @Test
    fun `each kind of small change is a new version`() = test {
        val book = newBook()
        store.save(book, Poem("p", "Rose", "red"))
        val changes = listOf(
            Poem("p", "Rose2", "red"), // title text
            Poem("p", "Rose2", "red2"), // body text
            Poem("p", "rose2", "red2"), // title case
            Poem("p", "rose2", "Red2"), // body case
            Poem("p", "rose2 ", "Red2"), // trailing space in title
            Poem("p", "rose2 ", "Red2\n"), // trailing newline in body
            Poem("p", "rose2 ", "Red2\r\n"), // newline kind
            Poem("p", "", "Red2\r\n"), // title cleared
            Poem("p", "", ""), // everything cleared
        )
        changes.forEach { change ->
            clock += 1_000
            assertTrue("$change should be new", store.save(book, change))
        }
        assertEquals(changes.size + 1, history("p").size)
        assertEquals(changes.last(), store.poems(book).single())
    }

    @Test
    fun `going back to earlier text is a new version, not a reuse of the old one`() = test {
        val book = newBook()
        store.save(book, Poem("p", "A", "one"))
        clock += 1_000
        store.save(book, Poem("p", "A", "two"))
        clock += 1_000
        assertTrue(store.save(book, Poem("p", "A", "one")))

        assertEquals(listOf("one", "two", "one"), history("p").map { it.body })
    }

    @Test
    fun `only the title changing, and only the body changing, each count`() = test {
        val book = newBook()
        store.save(book, Poem("p", "T", "B"))
        clock += 1
        assertTrue(store.save(book, Poem("p", "T2", "B")))
        clock += 1
        assertTrue(store.save(book, Poem("p", "T2", "B2")))
        assertFalse(store.save(book, Poem("p", "T2", "B2")))
    }

    @Test
    fun `a hundred edits give a hundred and one versions, newest first, each exactly as saved`() = test {
        val book = newBook()
        store.save(book, Poem("p", "T0", "B0"))
        for (i in 1..100) {
            clock += 1_000
            store.save(book, Poem("p", "T$i", "B$i"))
        }
        val history = history("p")
        assertEquals(101, history.size)
        assertEquals((100 downTo 0).map { "T$it" }, history.map { it.title })
        assertEquals((100 downTo 0).map { "B$it" }, history.map { it.body })
        assertEquals(history.map { it.createdAt }.sortedDescending(), history.map { it.createdAt })
        assertEquals(Poem("p", "T100", "B100"), store.poems(book).single())
    }

    // --- Timestamps ---

    @Test
    fun `a version is stamped with the clock when the clock is ahead`() = test {
        val book = newBook()
        clock = 10_000
        store.save(book, Poem("p", "A"))
        clock = 20_000
        store.save(book, Poem("p", "B"))
        assertEquals(listOf(20_000L, 10_000L), history("p").map { it.createdAt })
    }

    @Test
    fun `a version is one millisecond after the last when the clock stands still`() = test {
        val book = newBook()
        clock = 10_000
        store.save(book, Poem("p", "A"))
        store.save(book, Poem("p", "B"))
        store.save(book, Poem("p", "C"))
        assertEquals(listOf(10_002L, 10_001L, 10_000L), history("p").map { it.createdAt })
    }

    @Test
    fun `a version is one millisecond after the last when the clock goes backwards`() = test {
        val book = newBook()
        clock = 50_000
        store.save(book, Poem("p", "A"))
        clock = 1_000 // e.g. the user changed the date
        store.save(book, Poem("p", "B"))
        assertEquals(listOf(50_001L, 50_000L), history("p").map { it.createdAt })
        assertEquals("B", store.poems(book).single().title)
    }

    @Test
    fun `the newest version is current even after the clock went backwards`() = test {
        val book = newBook()
        clock = 50_000
        store.save(book, Poem("p", "first"))
        clock = 10
        store.save(book, Poem("p", "second"))
        clock = 5
        store.save(book, Poem("p", "third"))
        assertEquals("third", store.poems(book).single().title)
    }

    @Test
    fun `different poems keep independent timelines`() = test {
        val book = newBook()
        clock = 1_000
        store.save(book, Poem("a", "a1"))
        clock = 900
        store.save(book, Poem("b", "b1"))
        assertEquals(listOf(1_000L), history("a").map { it.createdAt })
        assertEquals(listOf(900L), history("b").map { it.createdAt })
    }

    @Test
    fun `two poems saved at the same instant both keep that timestamp`() = test {
        val book = newBook()
        clock = 7_000
        store.save(book, Poem("a", "a"))
        store.save(book, Poem("b", "b"))
        assertEquals(7_000L, history("a").single().createdAt)
        assertEquals(7_000L, history("b").single().createdAt)
    }

    @Test
    fun `negative and zero clocks still work`() = test {
        val book = newBook()
        clock = 0
        store.save(book, Poem("p", "A"))
        clock = -5
        store.save(book, Poem("p", "B"))
        assertEquals(listOf(1L, 0L), history("p").map { it.createdAt })
    }

    // --- Reading versions ---

    @Test
    fun `reading a version returns exactly that version`() = test {
        val book = newBook()
        clock = 1_000
        store.save(book, Poem("p", "A", "a"))
        clock = 2_000
        store.save(book, Poem("p", "B", "b"))
        clock = 3_000
        store.save(book, Poem("p", "C", "c"))

        assertEquals(PoemVersion(1_000, "A", "a"), store.version("p", 1_000))
        assertEquals(PoemVersion(2_000, "B", "b"), store.version("p", 2_000))
        assertEquals(PoemVersion(3_000, "C", "c"), store.version("p", 3_000))
    }

    @Test
    fun `reading a version that is not there gives null`() = test {
        val book = newBook()
        clock = 1_000
        store.save(book, Poem("p", "A"))
        assertNull(store.version("p", 999))
        assertNull(store.version("p", 1_001))
        assertNull(store.version("nobody", 1_000))
    }

    @Test
    fun `a version is not found under another poems id`() = test {
        val book = newBook()
        clock = 1_000
        store.save(book, Poem("a", "A"))
        clock = 2_000
        store.save(book, Poem("b", "B"))
        assertNull(store.version("b", 1_000))
        assertNull(store.version("a", 2_000))
    }

    @Test
    fun `the history of a poem that does not exist is empty`() = test {
        assertTrue(history("nobody").isEmpty())
    }

    @Test
    fun `old versions never change when the poem is edited again`() = test {
        val book = newBook()
        clock = 1_000
        store.save(book, Poem("p", "A", "a"))
        val first = store.version("p", 1_000)
        repeat(5) {
            clock += 1_000
            store.save(book, Poem("p", "A$it", "a$it"))
        }
        assertEquals(first, store.version("p", 1_000))
    }

    @Test
    fun `history entries carry no notes`() = test {
        val book = newBook()
        store.save(book, Poem("p", "A", "a"))
        store.setNotes("p", "ideas")
        assertEquals(listOf("A"), history("p").map { it.title })
    }

    // --- Which book, which poem ---

    @Test
    fun `saving a stored poem under another book id does not move it`() = test {
        val one = newBook("One")
        val two = newBook("Two")
        store.save(one, Poem("p", "A"))
        clock += 1_000
        store.save(two, Poem("p", "B"))

        assertEquals(listOf("B"), titles(one))
        assertTrue(titles(two).isEmpty())
    }

    @Test
    fun `a binned poem that is saved again stays in the bin`() = test {
        val book = newBook()
        val poem = write(book, "A")
        store.binPoem(book, poem.id)
        clock += 1_000
        store.save(book, poem.copy(body = "changed"))

        assertTrue(store.poems(book).isEmpty())
        assertEquals(1, store.observeBinPoems().first().size)
        assertEquals(2, history(poem.id).size)
    }

    @Test
    fun `saving writes to the book given and counts it in the shelf`() = test {
        val one = newBook("One")
        val two = newBook("Two")
        write(one, "A"); write(one, "B"); write(two, "C")
        val shelf = store.observeBooks().first().associate { it.name to it.poemCount }
        assertEquals(mapOf("One" to 2, "Two" to 1), shelf)
    }

    @Test
    fun `saving updates the books last updated time to the clock`() = test {
        val book = newBook()
        clock = 3_000
        store.save(book, Poem("p", "A"))
        assertEquals(3_000L, updatedAt())
        clock = 8_000
        store.save(book, Poem("p", "B"))
        assertEquals(8_000L, updatedAt())
    }

    @Test
    fun `saving into one book does not change another books last updated time`() = test {
        val one = newBook("One")
        val two = newBook("Two")
        clock = 5_000
        store.save(one, Poem("p", "A"))
        val times = store.observeBooks().first().associate { it.name to it.updatedAt }
        assertEquals(5_000L, times["One"])
        assertEquals(1_000L, times["Two"])
        assertEquals(two, store.observeBooks().first().single { it.name == "Two" }.id)
    }

    // --- Notes ---

    @Test
    fun `notes start empty`() = test {
        val book = newBook()
        val p = write(book, "A")
        assertEquals("", store.notes(p.id))
    }

    @Test
    fun `notes can be replaced and cleared`() = test {
        val book = newBook()
        val p = write(book, "A")
        store.setNotes(p.id, "one")
        store.setNotes(p.id, "two")
        assertEquals("two", store.notes(p.id))
        store.setNotes(p.id, "")
        assertEquals("", store.notes(p.id))
    }

    @Test
    fun `notes of an unknown poem are empty and setting them does nothing`() = test {
        assertEquals("", store.notes("nobody"))
        store.setNotes("nobody", "x")
        assertEquals("", store.notes("nobody"))
    }

    @Test
    fun `saving a new version leaves the notes alone`() = test {
        val book = newBook()
        store.save(book, Poem("p", "A", "a"))
        store.setNotes("p", "keep")
        repeat(3) {
            clock += 1_000
            store.save(book, Poem("p", "A$it", "a$it"))
        }
        assertEquals("keep", store.notes("p"))
    }

    @Test
    fun `setting notes writes no version and does not touch the poem`() = test {
        val book = newBook()
        store.save(book, Poem("p", "A", "a"))
        clock = 99_000
        store.setNotes("p", "idea")

        assertEquals(1, history("p").size)
        assertEquals(Poem("p", "A", "a"), store.poems(book).single())
        assertEquals(1_000L, updatedAt())
    }

    @Test
    fun `notes survive the poem being binned and restored`() = test {
        val book = newBook()
        val p = write(book, "A")
        store.setNotes(p.id, "stay")
        store.binPoem(book, p.id)
        assertEquals("stay", store.notes(p.id))
        store.restorePoem(p.id)
        assertEquals("stay", store.notes(p.id))
    }

    @Test
    fun `notes are gone when the poem is deleted for good`() = test {
        val book = newBook()
        val p = write(book, "A")
        store.setNotes(p.id, "bye")
        store.binPoem(book, p.id)
        store.deletePoemForever(p.id)
        assertEquals("", store.notes(p.id))
    }

    @Test
    fun `large notes are kept whole`() = test {
        val book = newBook()
        val p = write(book, "A")
        val big = "idea\n".repeat(50_000)
        store.setNotes(p.id, big)
        assertEquals(big, store.notes(p.id))
    }

    @Test
    fun `poems in the same book have separate notes`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B"); val c = write(book, "C")
        store.setNotes(a.id, "A!")
        store.setNotes(c.id, "C!")
        assertEquals(listOf("A!", "", "C!"), listOf(a, b, c).map { store.notes(it.id) })
    }
}
