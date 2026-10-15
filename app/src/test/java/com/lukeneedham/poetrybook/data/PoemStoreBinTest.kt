package com.lukeneedham.poetrybook.data

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bin: binning, restoring and deleting for good, for poems and for books. */
class PoemStoreBinTest : DatabaseTestBase() {
    private suspend fun binPoems() = store.observeBinPoems().first()
    private suspend fun binBooks() = store.observeBinBooks().first()
    private suspend fun shelf() = store.observeBooks().first()

    // --- Poems: binning ---

    @Test
    fun `binning a poem puts it in the bin with its text, book name and time`() = test {
        val book = newBook("Spring")
        val p = write(book, "Blossom", "pink")
        clock = 77_000
        store.binPoem(book, p.id)

        assertEquals(listOf(BinPoem(p.id, "Blossom", "pink", "Spring", 77_000)), binPoems())
    }

    @Test
    fun `the bin shows the newest version of a binned poem`() = test {
        val book = newBook()
        val p = write(book, "v1", "one")
        clock += 1_000
        store.save(book, p.copy(title = "v2", body = "two"))
        store.binPoem(book, p.id)
        assertEquals("v2", binPoems().single().title)
        assertEquals("two", binPoems().single().body)
    }

    @Test
    fun `the bin lists newest deletion first`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B"); val c = write(book, "C")
        clock = 10_000; store.binPoem(book, b.id)
        clock = 30_000; store.binPoem(book, a.id)
        clock = 20_000; store.binPoem(book, c.id)

        assertEquals(listOf("A", "C", "B"), binPoems().map { it.title })
    }

    @Test
    fun `a binned poem has no page and a deletion time`() = test {
        val book = newBook()
        val p = write(book, "A")
        clock = 5_000
        store.binPoem(book, p.id)
        val row = db.poemDao().poem(p.id)!!
        assertNull(row.position)
        assertEquals(5_000L, row.deletedAt)
    }

    @Test
    fun `binning the only poem leaves an empty book`() = test {
        val book = newBook()
        val p = write(book, "A")
        store.binPoem(book, p.id)
        assertTrue(store.poems(book).isEmpty())
        assertTrue(pages(book).isEmpty())
    }

    @Test
    fun `binning poems one after another from the front leaves the rest in order`() = test {
        val book = newBook()
        val all = List(8) { write(book, "P$it") }
        all.take(7).forEachIndexed { i, p ->
            store.binPoem(book, p.id)
            assertEquals((i + 1 until 8).map { "P$it" }, titles(book))
            assertEquals(range(7 - i), pages(book))
        }
    }

    @Test
    fun `binning poems one after another from the back leaves the rest in order`() = test {
        val book = newBook()
        val all = List(8) { write(book, "P$it") }
        all.reversed().take(7).forEachIndexed { i, p ->
            store.binPoem(book, p.id)
            assertEquals((0 until 7 - i).map { "P$it" }, titles(book))
            assertEquals(range(7 - i), pages(book))
        }
    }

    @Test
    fun `binning every poem in a random order always leaves consecutive pages`() = test {
        val book = newBook()
        val all = List(12) { write(book, "P$it") }
        val order = all.shuffled(kotlin.random.Random(7))
        val remaining = all.map { it.title }.toMutableList()
        order.forEach { p ->
            store.binPoem(book, p.id)
            remaining.remove(p.title)
            assertEquals(remaining, titles(book))
            assertEquals(range(remaining.size), pages(book))
        }
        assertEquals(12, binPoems().size)
    }

    @Test
    fun `binning a poem in one book does not move poems in another`() = test {
        val one = newBook("One"); val two = newBook("Two")
        val a = write(one, "a1"); write(one, "a2")
        write(two, "b1"); write(two, "b2")
        store.binPoem(one, a.id)
        assertEquals(listOf("b1", "b2"), titles(two))
        assertEquals(listOf(0, 1), pages(two))
    }

    @Test
    fun `binning a poem that does not exist does nothing`() = test {
        val book = newBook()
        write(book, "A")
        store.binPoem(book, "ghost")
        assertEquals(listOf("A"), titles(book))
        assertTrue(binPoems().isEmpty())
    }

    @Test
    fun `binning a poem twice keeps its first deletion time`() = test {
        val book = newBook()
        val p = write(book, "A"); write(book, "B")
        clock = 10_000
        store.binPoem(book, p.id)
        clock = 20_000
        store.binPoem(book, p.id)
        assertEquals(10_000L, binPoems().single().deletedAt)
        assertEquals(listOf(0), pages(book))
    }

    @Test
    fun `binning uses the poems own book to close the gap`() = test {
        val one = newBook("One"); val two = newBook("Two")
        val a = write(one, "a1"); write(one, "a2"); write(one, "a3")
        write(two, "b1"); write(two, "b2")
        // Wrong book id passed in: the gap must still be closed in the poem's real book.
        store.binPoem(two, a.id)
        assertEquals(listOf("a2", "a3"), titles(one))
        assertEquals(listOf(0, 1), pages(one))
        assertEquals(listOf(0, 1), pages(two))
    }

    // --- Poems: restoring ---

    @Test
    fun `a restored poem returns with the same text and goes last`() = test {
        val book = newBook()
        val a = write(book, "A", "aa"); write(book, "B"); write(book, "C")
        store.binPoem(book, a.id)
        store.restorePoem(a.id)

        assertEquals(listOf("B", "C", "A"), titles(book))
        assertEquals("aa", store.poems(book).last().body)
        assertEquals(range(3), pages(book))
        assertTrue(binPoems().isEmpty())
    }

    @Test
    fun `restoring into an empty book puts the poem on page 0`() = test {
        val book = newBook()
        val a = write(book, "A")
        store.binPoem(book, a.id)
        store.restorePoem(a.id)
        assertEquals(listOf(0), pages(book))
    }

    @Test
    fun `restoring a poem that does not exist does nothing`() = test {
        val book = newBook()
        write(book, "A")
        store.restorePoem("ghost")
        assertEquals(listOf("A"), titles(book))
    }

    @Test
    fun `restoring twice does not move the poem again`() = test {
        val book = newBook()
        val a = write(book, "A"); write(book, "B")
        store.binPoem(book, a.id)
        store.restorePoem(a.id)
        write(book, "C")
        store.restorePoem(a.id)
        assertEquals(listOf("B", "A", "C"), titles(book))
        assertEquals(range(3), pages(book))
    }

    @Test
    fun `bin and restore the same poem many times keeps everything consistent`() = test {
        val book = newBook()
        val ps = List(5) { write(book, "P$it") }
        repeat(10) { round ->
            val target = ps[round % 5]
            store.binPoem(book, target.id)
            assertEquals(range(4), pages(book))
            store.restorePoem(target.id)
            assertEquals(range(5), pages(book))
            assertEquals(target.title, titles(book).last())
        }
        assertEquals(1, store.observeHistory(ps[0].id).first().size)
    }

    @Test
    fun `restoring touches the book updated time`() = test {
        val book = newBook()
        val a = write(book, "A")
        store.binPoem(book, a.id)
        clock = 88_000
        store.restorePoem(a.id)
        assertEquals(88_000L, shelf().single().updatedAt)
    }

    @Test
    fun `restoring several poems appends them in the order restored`() = test {
        val book = newBook()
        val ps = List(4) { write(book, "P$it") }
        ps.forEach { store.binPoem(book, it.id) }
        listOf(2, 0, 3, 1).forEach { store.restorePoem(ps[it].id) }
        assertEquals(listOf("P2", "P0", "P3", "P1"), titles(book))
        assertEquals(range(4), pages(book))
    }

    @Test
    fun `a restored poem keeps its whole history`() = test {
        val book = newBook()
        val a = write(book, "v1")
        repeat(4) {
            clock += 1_000
            store.save(book, a.copy(title = "v${it + 2}"))
        }
        store.binPoem(book, a.id)
        store.restorePoem(a.id)
        assertEquals(listOf("v5", "v4", "v3", "v2", "v1"), store.observeHistory(a.id).first().map { it.title })
    }

    // --- Poems: deleting for good ---

    @Test
    fun `deleting a binned poem for good removes it everywhere`() = test {
        val book = newBook()
        val a = write(book, "A"); write(book, "B")
        store.binPoem(book, a.id)
        store.deletePoemForever(a.id)

        assertTrue(binPoems().isEmpty())
        assertNull(db.poemDao().poem(a.id))
        assertEquals(listOf("B"), titles(book))
        assertEquals(listOf(0), pages(book))
    }

    @Test
    fun `deleting a poem for good wipes every version`() = test {
        val book = newBook()
        val a = write(book, "v1")
        val times = mutableListOf(store.observeHistory(a.id).first().single().createdAt)
        repeat(3) {
            clock += 1_000
            store.save(book, a.copy(title = "v${it + 2}"))
        }
        times += store.observeHistory(a.id).first().map { it.createdAt }
        store.binPoem(book, a.id)
        store.deletePoemForever(a.id)
        times.forEach { assertNull(store.version(a.id, it)) }
    }

    @Test
    fun `deleting a poem that is not there does nothing`() = test {
        val book = newBook()
        write(book, "A")
        store.deletePoemForever("ghost")
        assertEquals(listOf("A"), titles(book))
    }

    @Test
    fun `deleting a live poem for good removes it without closing the gap, which binning must have done first`() = test {
        // The app only deletes for good from the bin. Deleting a live poem directly is not a supported path:
        // this pins down what happens so a change to it is deliberate.
        val book = newBook()
        val a = write(book, "A"); write(book, "B")
        store.deletePoemForever(a.id)
        assertEquals(listOf("B"), titles(book))
        assertEquals(listOf(1), pages(book))
    }

    @Test
    fun `deleting one binned poem for good leaves other binned poems`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B")
        store.binPoem(book, a.id); store.binPoem(book, b.id)
        store.deletePoemForever(a.id)
        assertEquals(listOf(b.id), binPoems().map { it.id })
    }

    @Test
    fun `a poem can be restored after others were deleted for good`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B"); val c = write(book, "C")
        store.binPoem(book, a.id); store.binPoem(book, b.id); store.binPoem(book, c.id)
        store.deletePoemForever(b.id)
        store.restorePoem(a.id)
        store.restorePoem(c.id)
        assertEquals(listOf("A", "C"), titles(book))
    }

    // --- Books: binning and restoring ---

    @Test
    fun `binning a book moves it from the shelf to the bin with its details`() = test {
        clock = 2_000
        val id = store.createBook("Autumn", 0x112233).id
        write(id, "A"); write(id, "B")
        clock = 55_000
        store.binBook(id)

        assertTrue(shelf().isEmpty())
        assertEquals(listOf(BinBook(id, "Autumn", 0x112233, poemCount = 2, deletedAt = 55_000)), binBooks())
    }

    @Test
    fun `binning a book leaves its poems and their pages untouched`() = test {
        val id = store.createBook("A", 0).id
        write(id, "1"); write(id, "2"); write(id, "3")
        store.binBook(id)
        assertEquals(listOf("1", "2", "3"), titles(id))
        assertEquals(range(3), pages(id))
    }

    @Test
    fun `restoring a book puts it back on the shelf exactly as it was`() = test {
        clock = 3_000
        val id = store.createBook("A", 7).id
        write(id, "1"); write(id, "2")
        val before = shelf().single()
        store.binBook(id)
        store.restoreBook(id)

        assertEquals(before, shelf().single())
        assertTrue(binBooks().isEmpty())
        assertEquals(listOf("1", "2"), titles(id))
    }

    @Test
    fun `binning does not change the books updated time`() = test {
        clock = 3_000
        val id = store.createBook("A", 7).id
        clock = 9_000
        store.binBook(id)
        store.restoreBook(id)
        assertEquals(3_000L, shelf().single().updatedAt)
    }

    @Test
    fun `the book bin lists newest deletion first`() = test {
        val a = store.createBook("A", 0).id; val b = store.createBook("B", 0).id; val c = store.createBook("C", 0).id
        clock = 10_000; store.binBook(b)
        clock = 30_000; store.binBook(c)
        clock = 20_000; store.binBook(a)
        assertEquals(listOf("C", "A", "B"), binBooks().map { it.name })
    }

    @Test
    fun `binning one book leaves the others on the shelf`() = test {
        val a = store.createBook("A", 0).id
        store.createBook("B", 0)
        store.binBook(a)
        assertEquals(listOf("B"), shelf().map { it.name })
    }

    @Test
    fun `binning or restoring a book that does not exist does nothing`() = test {
        store.createBook("A", 0)
        store.binBook("ghost")
        store.restoreBook("ghost")
        assertEquals(1, shelf().size)
        assertTrue(binBooks().isEmpty())
    }

    @Test
    fun `a binned book can be binned again and restored again`() = test {
        val id = store.createBook("A", 0).id
        repeat(5) {
            store.binBook(id)
            assertTrue(shelf().isEmpty())
            store.restoreBook(id)
            assertEquals(1, shelf().size)
        }
    }

    @Test
    fun `restoring a book that is on the shelf changes nothing`() = test {
        val id = store.createBook("A", 0).id
        val before = shelf()
        store.restoreBook(id)
        assertEquals(before, shelf())
    }

    @Test
    fun `an edited name and colour stay after a trip through the bin`() = test {
        val id = store.createBook("A", 1).id
        store.editBook(id, "B", 2)
        store.binBook(id)
        store.restoreBook(id)
        assertEquals("B", shelf().single().name)
        assertEquals(2, shelf().single().color)
    }

    @Test
    fun `the book bin counts live poems only`() = test {
        val id = store.createBook("A", 0).id
        val a = write(id, "1"); write(id, "2"); write(id, "3")
        store.binPoem(id, a.id)
        store.binBook(id)
        assertEquals(2, binBooks().single().poemCount)
    }

    @Test
    fun `an empty book can be binned and restored`() = test {
        val id = store.createBook("Empty", 0).id
        store.binBook(id)
        assertEquals(0, binBooks().single().poemCount)
        store.restoreBook(id)
        assertEquals("Empty", shelf().single().name)
    }

    // --- Books and the poem bin together ---

    @Test
    fun `poems binned on their own are hidden while their book is binned and return with it`() = test {
        val id = store.createBook("A", 0).id
        val a = write(id, "A"); write(id, "B")
        store.binPoem(id, a.id)
        store.binBook(id)
        assertTrue(binPoems().isEmpty())
        store.restoreBook(id)
        assertEquals(listOf(a.id), binPoems().map { it.id })
    }

    @Test
    fun `a poem from another book stays in the poem bin while a different book is binned`() = test {
        val one = store.createBook("One", 0).id
        val two = store.createBook("Two", 0).id
        val a = write(one, "a"); val b = write(two, "b")
        store.binPoem(one, a.id); store.binPoem(two, b.id)
        store.binBook(one)
        assertEquals(listOf(b.id), binPoems().map { it.id })
    }

    @Test
    fun `a poem restored before its book was binned is live when the book returns`() = test {
        val id = store.createBook("A", 0).id
        val a = write(id, "A")
        store.binPoem(id, a.id)
        store.restorePoem(a.id)
        store.binBook(id)
        store.restoreBook(id)
        assertEquals(listOf("A"), titles(id))
        assertTrue(binPoems().isEmpty())
    }

    // --- Books: deleting for good ---

    @Test
    fun `deleting a binned book for good wipes it, its poems, bin poems and every version`() = test {
        val id = store.createBook("A", 0).id
        val a = write(id, "A"); val b = write(id, "B")
        store.save(id, a.copy(body = "edited"))
        store.binPoem(id, b.id)
        val versionTimes = store.observeHistory(a.id).first().map { it.createdAt }
        store.binBook(id)

        store.deleteBookForever(id)

        assertTrue(binBooks().isEmpty())
        assertTrue(shelf().isEmpty())
        assertNull(db.poemDao().poem(a.id))
        assertNull(db.poemDao().poem(b.id))
        versionTimes.forEach { assertNull(store.version(a.id, it)) }
        assertTrue(binPoems().isEmpty())
    }

    @Test
    fun `deleting one book for good leaves every other book intact`() = test {
        val a = store.createBook("A", 0).id; val b = store.createBook("B", 0).id
        val pa = write(a, "pa"); val pb = write(b, "pb")
        store.setNotes(pb.id, "notes b")
        store.binBook(a)
        store.deleteBookForever(a)

        assertEquals(listOf("pb"), titles(b))
        assertEquals("notes b", store.notes(pb.id))
        assertEquals(1, store.observeHistory(pb.id).first().size)
        assertTrue(store.observeHistory(pa.id).first().isEmpty())
    }

    @Test
    fun `deleting a book that does not exist for good does nothing`() = test {
        store.createBook("A", 0)
        store.deleteBookForever("ghost")
        assertEquals(1, shelf().size)
    }

    @Test
    fun `deleting a live book for good is a real delete too`() = test {
        // The app only offers this from the bin; this documents that the store itself does not check.
        val id = store.createBook("A", 0).id
        write(id, "A")
        store.deleteBookForever(id)
        assertTrue(shelf().isEmpty())
        assertNull(db.bookDao().observeShelf().first().firstOrNull())
    }

    @Test
    fun `a bin entry vanishes from the bin once restored or deleted`() = test {
        val book = newBook()
        val a = write(book, "A"); val b = write(book, "B")
        store.binPoem(book, a.id); store.binPoem(book, b.id)
        store.restorePoem(a.id)
        store.deletePoemForever(b.id)
        assertTrue(binPoems().isEmpty())
        assertNotNull(db.poemDao().poem(a.id))
        assertNull(db.poemDao().poem(b.id))
    }
}
