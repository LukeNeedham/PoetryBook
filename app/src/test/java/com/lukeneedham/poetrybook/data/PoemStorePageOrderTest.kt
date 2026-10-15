package com.lukeneedham.poetrybook.data

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Page order: appending, reordering, and keeping pages 0..n-1 whatever happens. */
class PoemStorePageOrderTest : DatabaseTestBase() {
    private suspend fun writeAll(book: String, n: Int) = List(n) { write(book, "P$it") }

    private fun idsOf(poems: List<Poem>) = poems.map { it.id }

    // --- Appending ---

    @Test
    fun `new poems take consecutive pages from zero`() = test {
        val book = newBook()
        repeat(15) { i ->
            write(book, "P$i")
            assertEquals(range(i + 1), pages(book))
        }
    }

    @Test
    fun `the page a poem is saved on is the count of live poems`() = test {
        val book = newBook()
        writeAll(book, 3)
        val p = write(book, "next")
        assertEquals(3, db.poemDao().poem(p.id)!!.position)
    }

    @Test
    fun `saving an existing poem again never changes its page`() = test {
        val book = newBook()
        val ps = writeAll(book, 4)
        repeat(3) {
            clock += 1_000
            store.save(book, ps[1].copy(body = "edit $it"))
        }
        assertEquals(1, db.poemDao().poem(ps[1].id)!!.position)
        assertEquals(range(4), pages(book))
    }

    @Test
    fun `books number their pages independently`() = test {
        val a = newBook("A"); val b = newBook("B")
        writeAll(a, 3); writeAll(b, 5)
        assertEquals(range(3), pages(a))
        assertEquals(range(5), pages(b))
    }

    // --- Reordering ---

    @Test
    fun `reordering to the same order changes nothing`() = test {
        val book = newBook()
        val ps = writeAll(book, 5)
        store.reorder(book, idsOf(ps))
        assertEquals(idsOf(ps), ids(book))
        assertEquals(range(5), pages(book))
    }

    @Test
    fun `reversing a book`() = test {
        val book = newBook()
        val ps = writeAll(book, 6)
        store.reorder(book, idsOf(ps).reversed())
        assertEquals(idsOf(ps).reversed(), ids(book))
        assertEquals(range(6), pages(book))
    }

    @Test
    fun `moving one poem to the front, to the back, and to the middle`() = test {
        val book = newBook()
        val ps = writeAll(book, 5)
        val base = idsOf(ps)

        store.reorder(book, listOf(base[3]) + base.filter { it != base[3] })
        assertEquals(listOf("P3", "P0", "P1", "P2", "P4"), titles(book))

        store.reorder(book, ids(book).let { current -> current.filter { it != base[3] } + base[3] })
        assertEquals(listOf("P0", "P1", "P2", "P4", "P3"), titles(book))

        val now = ids(book)
        store.reorder(book, now.filter { it != base[0] }.toMutableList().also { it.add(2, base[0]) })
        assertEquals(listOf("P1", "P2", "P0", "P4", "P3"), titles(book))
        assertEquals(range(5), pages(book))
    }

    @Test
    fun `swapping two neighbours and two far apart poems`() = test {
        val book = newBook()
        val ps = writeAll(book, 6)
        val ids = idsOf(ps).toMutableList()
        ids.swap(1, 2)
        store.reorder(book, ids)
        assertEquals(listOf("P0", "P2", "P1", "P3", "P4", "P5"), titles(book))
        ids.swap(0, 5)
        store.reorder(book, ids)
        assertEquals(listOf("P5", "P2", "P1", "P3", "P4", "P0"), titles(book))
    }

    private fun MutableList<String>.swap(i: Int, j: Int) {
        val t = this[i]; this[i] = this[j]; this[j] = t
    }

    @Test
    fun `a single poem book reorders to itself`() = test {
        val book = newBook()
        val p = write(book, "only")
        store.reorder(book, listOf(p.id))
        assertEquals(listOf("only"), titles(book))
        assertEquals(listOf(0), pages(book))
    }

    @Test
    fun `reordering an empty book or with an empty list is harmless`() = test {
        val empty = newBook("empty")
        store.reorder(empty, emptyList())
        assertTrue(store.poems(empty).isEmpty())

        val book = newBook()
        writeAll(book, 3)
        store.reorder(book, emptyList())
        assertEquals(listOf("P0", "P1", "P2"), titles(book))
        assertEquals(range(3), pages(book))
    }

    @Test
    fun `a partial list puts the listed poems first, then the rest in their old order`() = test {
        val book = newBook()
        val ps = writeAll(book, 6)
        store.reorder(book, listOf(ps[4].id, ps[2].id))
        assertEquals(listOf("P4", "P2", "P0", "P1", "P3", "P5"), titles(book))
        assertEquals(range(6), pages(book))
    }

    @Test
    fun `duplicates in the list count once, at their first place`() = test {
        val book = newBook()
        val ps = writeAll(book, 4)
        store.reorder(book, listOf(ps[3].id, ps[1].id, ps[3].id, ps[1].id))
        assertEquals(listOf("P3", "P1", "P0", "P2"), titles(book))
    }

    @Test
    fun `unknown ids and ids from other books are ignored`() = test {
        val book = newBook(); val other = newBook("other")
        val ps = writeAll(book, 3)
        val foreign = write(other, "foreign")
        store.reorder(book, listOf("ghost", foreign.id, ps[2].id, ps[0].id))
        assertEquals(listOf("P2", "P0", "P1"), titles(book))
        assertEquals(listOf("foreign"), titles(other))
        assertEquals(listOf(0), pages(other))
    }

    @Test
    fun `binned poems in the list are ignored and stay binned without a page`() = test {
        val book = newBook()
        val ps = writeAll(book, 4)
        store.binPoem(book, ps[1].id)
        store.reorder(book, listOf(ps[3].id, ps[1].id, ps[0].id))
        assertEquals(listOf("P3", "P0", "P2"), titles(book))
        assertNull(db.poemDao().poem(ps[1].id)!!.position)
        assertEquals(range(3), pages(book))
    }

    @Test
    fun `reordering one book does not touch another`() = test {
        val a = newBook("A"); val b = newBook("B")
        val pa = writeAll(a, 3); val pb = writeAll(b, 3)
        store.reorder(a, idsOf(pa).reversed())
        assertEquals(listOf("P0", "P1", "P2"), titles(b))
        assertEquals(idsOf(pb), ids(b))
    }

    @Test
    fun `reordering does not change any poem or its history`() = test {
        val book = newBook()
        val ps = writeAll(book, 4)
        store.reorder(book, idsOf(ps).reversed())
        ps.forEach { assertEquals(1, store.observeHistory(it.id).first().size) }
        assertEquals(ps.map { it.body }.toSet(), store.poems(book).map { it.body }.toSet())
    }

    @Test
    fun `reordering keeps notes with their poems`() = test {
        val book = newBook()
        val ps = writeAll(book, 3)
        ps.forEachIndexed { i, p -> store.setNotes(p.id, "note $i") }
        store.reorder(book, idsOf(ps).reversed())
        assertEquals(listOf("note 2", "note 1", "note 0"), ids(book).map { store.notes(it) })
    }

    @Test
    fun `reordering a book that does not exist is harmless`() = test {
        store.reorder("ghost", listOf("a", "b"))
        assertTrue(store.poems("ghost").isEmpty())
    }

    @Test
    fun `a large book can be reversed in one go`() = test {
        val book = newBook()
        val ps = writeAll(book, 200)
        store.reorder(book, idsOf(ps).reversed())
        assertEquals((199 downTo 0).map { "P$it" }, titles(book))
        assertEquals(range(200), pages(book))
    }

    @Test
    fun `every permutation of five poems reorders correctly`() = test {
        val book = newBook()
        val ps = writeAll(book, 5)
        val base = idsOf(ps)
        fun permutations(items: List<String>): List<List<String>> =
            if (items.size <= 1) listOf(items)
            else items.flatMap { head -> permutations(items - head).map { listOf(head) + it } }
        val all = permutations(base)
        assertEquals(120, all.size)
        all.forEach { order ->
            store.reorder(book, order)
            assertEquals(order, ids(book))
            assertEquals(range(5), pages(book))
        }
    }

    @Test
    fun `many random reorderings, partial and full, keep a complete set of pages`() = test {
        val book = newBook()
        val ps = writeAll(book, 9)
        val random = Random(1234)
        repeat(300) { step ->
            val subset = idsOf(ps).shuffled(random).take(random.nextInt(0, 10))
            store.reorder(book, subset)
            val now = ids(book)
            assertEquals("step $step", 9, now.size)
            assertEquals("step $step", subset, now.take(subset.size))
            assertEquals("step $step", range(9), pages(book))
        }
    }

    // --- Interplay with the bin ---

    @Test
    fun `the page order survives a bin and restore cycle for the others`() = test {
        val book = newBook()
        val ps = writeAll(book, 5)
        store.reorder(book, listOf(ps[4].id, ps[3].id, ps[2].id, ps[1].id, ps[0].id))
        store.binPoem(book, ps[2].id)
        assertEquals(listOf("P4", "P3", "P1", "P0"), titles(book))
        store.restorePoem(ps[2].id)
        assertEquals(listOf("P4", "P3", "P1", "P0", "P2"), titles(book))
        assertEquals(range(5), pages(book))
    }

    @Test
    fun `reordering after a restore treats the restored poem like any other`() = test {
        val book = newBook()
        val ps = writeAll(book, 4)
        store.binPoem(book, ps[0].id)
        store.restorePoem(ps[0].id)
        store.reorder(book, listOf(ps[0].id))
        assertEquals(listOf("P0", "P1", "P2", "P3"), titles(book))
        assertEquals(range(4), pages(book))
    }

    @Test
    fun `adding a poem after binning reuses the freed page range`() = test {
        val book = newBook()
        val ps = writeAll(book, 3)
        store.binPoem(book, ps[0].id)
        store.binPoem(book, ps[1].id)
        val p = write(book, "new")
        assertEquals(listOf("P2", "new"), titles(book))
        assertEquals(1, db.poemDao().poem(p.id)!!.position)
    }

    @Test
    fun `interleaved adds, bins, restores and reorders always keep pages complete`() = test {
        val book = newBook()
        val random = Random(99)
        val live = mutableListOf<Poem>()
        val binned = mutableListOf<Poem>()
        var counter = 0
        repeat(250) { step ->
            when (random.nextInt(5)) {
                0 -> live += write(book, "N${counter++}")
                1 -> if (live.isNotEmpty()) {
                    val p = live.removeAt(random.nextInt(live.size))
                    store.binPoem(book, p.id)
                    binned += p
                }
                2 -> if (binned.isNotEmpty()) {
                    val p = binned.removeAt(random.nextInt(binned.size))
                    store.restorePoem(p.id)
                    live += p
                }
                3 -> if (live.isNotEmpty()) {
                    live.shuffle(random)
                    store.reorder(book, live.map { it.id })
                }
                else -> if (binned.isNotEmpty()) {
                    val p = binned.removeAt(random.nextInt(binned.size))
                    store.deletePoemForever(p.id)
                }
            }
            assertEquals("step $step", live.map { it.id }, ids(book))
            assertEquals("step $step", range(live.size), pages(book))
        }
    }
}
