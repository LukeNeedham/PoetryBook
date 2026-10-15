package com.lukeneedham.poetrybook.data

import com.lukeneedham.poetrybook.data.db.PoemEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Runs [PoemStore] against a real, in-memory SQLite database (Room on Robolectric), so the SQL, the
 * foreign keys and the unique page index are exercised for real. See docs/database.md.
 */
class PoemStoreTest : DatabaseTestBase() {

    // --- Versions ---

    @Test
    fun `a new poem is stored with its first version on page 0`() = test {
        val book = newBook()
        val poem = Poem("p1", "Rose", "Roses are red")
        assertTrue(store.save(book, poem))

        assertEquals(listOf(poem), store.poems(book))
        assertEquals(listOf(0), pages(book))
        assertEquals(1, store.observeHistory("p1").first().size)
    }

    @Test
    fun `saving an unchanged poem writes no new version`() = test {
        val book = newBook()
        val poem = Poem("p1", "Rose", "Roses are red")
        store.save(book, poem)
        clock += 1_000

        assertFalse(store.save(book, poem))
        assertEquals(1, store.observeHistory("p1").first().size)
    }

    @Test
    fun `saving a changed title or text adds a version and the newest is current`() = test {
        val book = newBook()
        store.save(book, Poem("p1", "Rose", "Roses are red"))
        clock += 1_000
        assertTrue(store.save(book, Poem("p1", "Roses", "Roses are red")))
        clock += 1_000
        assertTrue(store.save(book, Poem("p1", "Roses", "Roses are red,\nviolets are blue")))

        val history = store.observeHistory("p1").first()
        assertEquals(listOf("Roses", "Roses", "Rose"), history.map { it.title })
        assertEquals(history.map { it.createdAt }.sortedDescending(), history.map { it.createdAt })
        assertEquals(listOf(Poem("p1", "Roses", "Roses are red,\nviolets are blue")), store.poems(book))
    }

    @Test
    fun `version timestamps stay strictly increasing even if the clock does not move`() = test {
        val book = newBook()
        store.save(book, Poem("p1", "A"))
        store.save(book, Poem("p1", "B"))
        store.save(book, Poem("p1", "C"))

        val times = store.observeHistory("p1").first().map { it.createdAt }
        assertEquals(3, times.toSet().size)
        assertEquals(times.sortedDescending(), times)
    }

    @Test
    fun `an old version can be read back as it was`() = test {
        val book = newBook()
        store.save(book, Poem("p1", "First", "one"))
        val first = store.observeHistory("p1").first().single()
        clock += 1_000
        store.save(book, Poem("p1", "Second", "two"))

        assertEquals(PoemVersion(first.createdAt, "First", "one"), store.version("p1", first.createdAt))
        assertNull(store.version("p1", 123_456_789L))
    }

    @Test
    fun `saving a poem updates the books last updated time`() = test {
        val book = newBook()
        clock = 5_000
        store.save(book, Poem("p1", "Rose"))

        assertEquals(5_000L, store.observeBooks().first().single().updatedAt)
    }

    // --- Notes ---

    @Test
    fun `the notepad is shared by every version and not versioned`() = test {
        val book = newBook()
        store.save(book, Poem("p1", "Rose", "one"))
        store.setNotes("p1", "rhymes: rose / close")
        clock += 1_000
        store.save(book, Poem("p1", "Rose", "two"))

        assertEquals("rhymes: rose / close", store.notes("p1"))
        assertEquals(2, store.observeHistory("p1").first().size)
    }

    @Test
    fun `each poem has its own notepad`() = test {
        val book = newBook()
        val a = write(book, "A")
        val b = write(book, "B")
        store.setNotes(a.id, "for a")

        assertEquals("for a", store.notes(a.id))
        assertEquals("", store.notes(b.id))
    }

    // --- Page order ---

    @Test
    fun `poems are added on consecutive pages`() = test {
        val book = newBook()
        write(book, "A"); write(book, "B"); write(book, "C")

        assertEquals(listOf("A", "B", "C"), titles(book))
        assertEquals(range(3), pages(book))
    }

    @Test
    fun `two live poems cannot share a page in a book`() = test {
        val book = newBook()
        db.poemDao().insert(PoemEntity("a", book, position = 0))
        try {
            db.poemDao().insert(PoemEntity("b", book, position = 0))
            fail("the unique index should have refused a second poem on page 0")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test
    fun `any number of binned poems fit because their page is null`() = test {
        val book = newBook()
        db.poemDao().insert(PoemEntity("a", book, position = null, deletedAt = 1))
        db.poemDao().insert(PoemEntity("b", book, position = null, deletedAt = 2))
        db.poemDao().insert(PoemEntity("c", book, position = 0))
    }

    @Test
    fun `different books can both have a poem on page 0`() = test {
        val one = newBook("One")
        val two = newBook("Two")
        write(one, "A")
        write(two, "B")

        assertEquals(listOf(0), pages(one))
        assertEquals(listOf(0), pages(two))
    }

    @Test
    fun `reordering rewrites the pages`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B"); val c = write(book, "C"); val d = write(book, "D")

        store.reorder(book, listOf(d.id, b.id, a.id, c.id))

        assertEquals(listOf("D", "B", "A", "C"), titles(book))
        assertEquals(range(4), pages(book))
    }

    @Test
    fun `reordering with a partial or unknown list keeps every poem on a complete set of pages`() = test {
        val book = newBook()
        val a = write(book, "A"); write(book, "B"); val c = write(book, "C"); write(book, "D")
        // C is listed; unknown ids are ignored; the rest keep their old order after it.
        store.reorder(book, listOf("nope", c.id, c.id))

        assertEquals(listOf("C", "A", "B", "D"), titles(book))
        assertEquals(range(4), pages(book))
        assertEquals(a.id, db.poemDao().liveIds(book)[1])
    }

    @Test
    fun `reordering ignores poems in the bin`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B"); val c = write(book, "C")
        store.binPoem(book, b.id)

        store.reorder(book, listOf(c.id, b.id, a.id))

        assertEquals(listOf("C", "A"), titles(book))
        assertEquals(range(2), pages(book))
        assertNull(db.poemDao().poem(b.id)!!.position)
    }

    // --- The bin: poems ---

    @Test
    fun `binning a poem closes the gap and keeps its history`() = test {
        val book = newBook()
        write(book, "A"); val b = write(book, "B"); write(book, "C"); write(book, "D")

        store.binPoem(book, b.id)

        assertEquals(listOf("A", "C", "D"), titles(book))
        assertEquals(range(3), pages(book))
        val binned = db.poemDao().poem(b.id)!!
        assertNull(binned.position)
        assertNotNull(binned.deletedAt)
        assertEquals(1, store.observeHistory(b.id).first().size)
    }

    @Test
    fun `binning the first and the last poem both leave consecutive pages`() = test {
        val book = newBook()
        val a = write(book, "A"); write(book, "B"); val c = write(book, "C")

        store.binPoem(book, a.id)
        assertEquals(listOf("B", "C"), titles(book))
        assertEquals(range(2), pages(book))

        store.binPoem(book, c.id)
        assertEquals(listOf("B"), titles(book))
        assertEquals(range(1), pages(book))
    }

    @Test
    fun `binning a poem twice changes nothing the second time`() = test {
        val book = newBook()
        write(book, "A"); val b = write(book, "B"); write(book, "C")

        store.binPoem(book, b.id)
        store.binPoem(book, b.id)

        assertEquals(listOf("A", "C"), titles(book))
        assertEquals(range(2), pages(book))
    }

    @Test
    fun `a restored poem goes to the end of the book with its history and notes`() = test {
        val book = newBook()
        write(book, "A"); val b = write(book, "B"); write(book, "C"); write(book, "D")
        store.setNotes(b.id, "keep me")
        store.binPoem(book, b.id)

        store.restorePoem(b.id)

        assertEquals(listOf("A", "C", "D", "B"), titles(book))
        assertEquals(range(4), pages(book))
        assertNull(db.poemDao().poem(b.id)!!.deletedAt)
        assertEquals("keep me", store.notes(b.id))
        assertEquals(1, store.observeHistory(b.id).first().size)
    }

    @Test
    fun `restoring several poems puts them at the end in the order restored`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B"); write(book, "C")
        store.binPoem(book, a.id)
        store.binPoem(book, b.id)

        store.restorePoem(b.id)
        store.restorePoem(a.id)

        assertEquals(listOf("C", "B", "A"), titles(book))
        assertEquals(range(3), pages(book))
    }

    @Test
    fun `restoring a poem that is not in the bin does nothing`() = test {
        val book = newBook()
        val a = write(book, "A"); write(book, "B")

        store.restorePoem(a.id)

        assertEquals(listOf("A", "B"), titles(book))
        assertEquals(range(2), pages(book))
    }

    @Test
    fun `a poem written after another was binned takes the next free page`() = test {
        val book = newBook()
        val a = write(book, "A"); write(book, "B")
        store.binPoem(book, a.id)

        write(book, "C")

        assertEquals(listOf("B", "C"), titles(book))
        assertEquals(range(2), pages(book))
    }

    @Test
    fun `the poem bin lists binned poems with their book and the shelf count excludes them`() = test {
        val book = newBook("Winter")
        write(book, "A"); val b = write(book, "B")
        store.binPoem(book, b.id)

        val binned = store.observeBinPoems().first().single()
        assertEquals(b.id, binned.id)
        assertEquals("Winter", binned.bookName)
        assertEquals(1, store.observeBooks().first().single().poemCount)
    }

    @Test
    fun `deleting a poem for good wipes its versions`() = test {
        val book = newBook()
        val a = write(book, "A")
        store.save(book, a.copy(body = "changed"))
        val version = store.observeHistory(a.id).first().first()
        store.binPoem(book, a.id)

        store.deletePoemForever(a.id)

        assertNull(db.poemDao().poem(a.id))
        assertNull(store.version(a.id, version.createdAt))
        assertTrue(store.observeHistory(a.id).first().isEmpty())
        assertTrue(store.observeBinPoems().first().isEmpty())
    }

    // --- The bin: books ---

    @Test
    fun `binning a book takes it off the shelf and restoring brings it back with its poems`() = test {
        val book = newBook("Autumn")
        write(book, "A"); write(book, "B")

        store.binBook(book)
        assertTrue(store.observeBooks().first().isEmpty())
        val binned = store.observeBinBooks().first().single()
        assertEquals("Autumn", binned.name)
        assertEquals(2, binned.poemCount)

        store.restoreBook(book)
        assertTrue(store.observeBinBooks().first().isEmpty())
        assertEquals(2, store.observeBooks().first().single().poemCount)
        assertEquals(listOf("A", "B"), titles(book))
    }

    @Test
    fun `poems binned on their own are hidden while their book is in the bin and return with it`() = test {
        val book = newBook()
        write(book, "A"); val b = write(book, "B")
        store.binPoem(book, b.id)

        store.binBook(book)
        assertTrue(store.observeBinPoems().first().isEmpty())

        store.restoreBook(book)
        assertEquals(listOf(b.id), store.observeBinPoems().first().map { it.id })
    }

    @Test
    fun `deleting a book for good wipes its poems and their versions`() = test {
        val book = newBook()
        val a = write(book, "A")
        val b = write(book, "B")
        store.binPoem(book, b.id)
        val version = store.observeHistory(a.id).first().single()
        store.binBook(book)

        store.deleteBookForever(book)

        assertTrue(store.observeBinBooks().first().isEmpty())
        assertNull(db.poemDao().poem(a.id))
        assertNull(db.poemDao().poem(b.id))
        assertNull(store.version(a.id, version.createdAt))
        assertTrue(store.observeBinPoems().first().isEmpty())
    }

    @Test
    fun `editing a book changes its name and colour only`() = test {
        val book = newBook("Old")
        write(book, "A")

        store.editBook(book, "  New  ", 0x112233)

        val shown = store.observeBooks().first().single()
        assertEquals("New", shown.name)
        assertEquals(0x112233, shown.color)
        assertEquals(1, shown.poemCount)
    }

    // --- Import ---

    @Test
    fun `an import adds a book whose poems are in the given order with their file times as first versions`() = test {
        val id = store.importBook(
            name = "Old poems",
            color = 0x445566,
            imported = listOf(
                ImportedPoem("First", "one", modifiedAt = 5_000),
                ImportedPoem("Second", "two", modifiedAt = 0),
            ),
            updatedAt = 0,
        )

        assertEquals(listOf("First", "Second"), titles(id))
        assertEquals(range(2), pages(id))
        val firstId = db.poemDao().liveIds(id)[0]
        val secondId = db.poemDao().liveIds(id)[1]
        assertEquals(5_000L, store.observeHistory(firstId).first().single().createdAt)
        // No file time: falls back to now.
        assertEquals(clock, store.observeHistory(secondId).first().single().createdAt)
        val book = store.observeBooks().first().single()
        assertEquals("Old poems", book.name)
        assertEquals(0x445566, book.color)
        assertEquals(clock, book.updatedAt)
    }

    @Test
    fun `importing poems that share a timestamp does not clash`() = test {
        val id = store.importBook(
            name = "Same time",
            color = null,
            imported = List(5) { ImportedPoem("P$it", "x", modifiedAt = 7_000) },
            updatedAt = 0,
        )

        assertEquals(5, titles(id).size)
        assertEquals(range(5), pages(id))
    }
}
