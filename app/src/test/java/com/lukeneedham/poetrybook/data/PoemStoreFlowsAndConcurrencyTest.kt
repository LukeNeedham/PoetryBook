package com.lukeneedham.poetrybook.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live queries ([PoemStore.observeBooks] and friends) must re-emit when the data changes, and
 * the store's multi-step operations must stay correct when many run at once.
 */
class PoemStoreFlowsAndConcurrencyTest : DatabaseTestBase() {
    /** Collects a flow in the background so tests can see what it emits after each change. */
    private class Watch<T>(scope: CoroutineScope, flow: Flow<T>) {
        private val channel = Channel<T>(Channel.UNLIMITED)
        private val job: Job = scope.launch(Dispatchers.Default) { flow.collect { channel.send(it) } }

        /** Waits for an emission satisfying [predicate], ignoring earlier ones. */
        suspend fun await(predicate: (T) -> Boolean): T = withTimeout(10_000) {
            while (true) {
                val value = channel.receive()
                if (predicate(value)) return@withTimeout value
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }

        fun stop() = job.cancel()
    }

    // --- The shelf ---

    @Test
    fun `the shelf emits again when a book is created, edited, binned and restored`() = test {
        coroutineScope {
            val watch = Watch(this, store.observeBooks())
            watch.await { it.isEmpty() }

            val id = store.createBook("A", 1).id
            watch.await { it.map { b -> b.name } == listOf("A") }

            store.editBook(id, "B", 2)
            watch.await { it.singleOrNull()?.let { b -> b.name == "B" && b.color == 2 } == true }

            store.binBook(id)
            watch.await { it.isEmpty() }

            store.restoreBook(id)
            watch.await { it.size == 1 }
            watch.stop()
        }
    }

    @Test
    fun `the shelf poem count follows saving, binning, restoring and deleting`() = test {
        coroutineScope {
            val book = newBook()
            val watch = Watch(this, store.observeBooks())
            watch.await { it.single().poemCount == 0 }

            val p = write(book, "A")
            watch.await { it.single().poemCount == 1 }
            store.binPoem(book, p.id)
            watch.await { it.single().poemCount == 0 }
            store.restorePoem(p.id)
            watch.await { it.single().poemCount == 1 }
            store.binPoem(book, p.id)
            store.deletePoemForever(p.id)
            watch.await { it.single().poemCount == 0 }
            watch.stop()
        }
    }

    @Test
    fun `the shelf updated time follows saves`() = test {
        coroutineScope {
            val book = newBook()
            val watch = Watch(this, store.observeBooks())
            clock = 12_345
            write(book, "A")
            watch.await { it.single().updatedAt == 12_345L }
            watch.stop()
        }
    }

    // --- The bins ---

    @Test
    fun `the book bin follows binning, restoring and deleting for good`() = test {
        coroutineScope {
            val id = store.createBook("A", 0).id
            val watch = Watch(this, store.observeBinBooks())
            watch.await { it.isEmpty() }
            store.binBook(id)
            watch.await { it.map { b -> b.id } == listOf(id) }
            store.restoreBook(id)
            watch.await { it.isEmpty() }
            store.binBook(id)
            watch.await { it.size == 1 }
            store.deleteBookForever(id)
            watch.await { it.isEmpty() }
            watch.stop()
        }
    }

    @Test
    fun `the poem bin follows binning, restoring and deleting for good, and a binned book`() = test {
        coroutineScope {
            val book = newBook()
            val a = write(book, "A"); val b = write(book, "B")
            val watch = Watch(this, store.observeBinPoems())
            watch.await { it.isEmpty() }

            store.binPoem(book, a.id)
            watch.await { it.map { p -> p.id } == listOf(a.id) }
            store.binPoem(book, b.id)
            watch.await { it.size == 2 }
            store.restorePoem(a.id)
            watch.await { it.map { p -> p.id } == listOf(b.id) }
            store.binBook(book)
            watch.await { it.isEmpty() }
            store.restoreBook(book)
            watch.await { it.size == 1 }
            store.deletePoemForever(b.id)
            watch.await { it.isEmpty() }
            watch.stop()
        }
    }

    // --- History ---

    @Test
    fun `the history emits again for each new version and only for its own poem`() = test {
        coroutineScope {
            val book = newBook()
            val a = write(book, "A")
            val other = write(book, "Other")
            val watch = Watch(this, store.observeHistory(a.id))
            watch.await { it.size == 1 }

            clock += 1_000
            store.save(book, a.copy(title = "A2"))
            watch.await { it.size == 2 && it.first().title == "A2" }

            clock += 1_000
            store.save(book, other.copy(title = "Other2")) // must not matter
            clock += 1_000
            store.save(book, a.copy(title = "A3"))
            val latest = watch.await { it.size == 3 }
            assertEquals(listOf("A3", "A2", "A"), latest.map { it.title })
            watch.stop()
        }
    }

    @Test
    fun `the history empties when the poem is deleted for good`() = test {
        coroutineScope {
            val book = newBook()
            val a = write(book, "A")
            val watch = Watch(this, store.observeHistory(a.id))
            watch.await { it.size == 1 }
            store.binPoem(book, a.id)
            store.deletePoemForever(a.id)
            watch.await { it.isEmpty() }
            watch.stop()
        }
    }

    // --- Concurrency ---

    @Test
    fun `forty poems saved at once land on forty distinct consecutive pages`() = test {
        val book = newBook()
        coroutineScope {
            (0 until 40).map { i ->
                async(Dispatchers.Default) { store.save(book, Poem("p$i", "P$i", "x")) }
            }.awaitAll()
        }
        assertEquals(40, ids(book).size)
        assertEquals(range(40), pages(book))
    }

    @Test
    fun `thirty simultaneous edits of one poem each become a version with a distinct time`() = test {
        val book = newBook()
        store.save(book, Poem("p", "start", "0"))
        coroutineScope {
            (1..30).map { i ->
                async(Dispatchers.Default) { store.save(book, Poem("p", "start", "edit $i")) }
            }.awaitAll()
        }
        val history = store.observeHistory("p").first()
        assertEquals(31, history.size)
        assertEquals(31, history.map { it.createdAt }.toSet().size)
        assertEquals(31, history.map { it.body }.toSet().size)
    }

    @Test
    fun `binning many poems at once leaves complete pages`() = test {
        val book = newBook()
        val ps = List(30) { write(book, "P$it") }
        coroutineScope {
            ps.filterIndexed { i, _ -> i % 2 == 0 }.map { p ->
                async(Dispatchers.Default) { store.binPoem(book, p.id) }
            }.awaitAll()
        }
        assertEquals(15, ids(book).size)
        assertEquals(range(15), pages(book))
        assertEquals(ps.filterIndexed { i, _ -> i % 2 == 1 }.map { it.title }, titles(book))
    }

    @Test
    fun `binning and restoring at the same time keeps pages complete and loses no poem`() = test {
        val book = newBook()
        val ps = List(20) { write(book, "P$it") }
        ps.take(10).forEach { store.binPoem(book, it.id) }
        coroutineScope {
            val restores = ps.take(10).map { p -> async(Dispatchers.Default) { store.restorePoem(p.id) } }
            val bins = ps.drop(10).take(5).map { p -> async(Dispatchers.Default) { store.binPoem(book, p.id) } }
            (restores + bins).awaitAll()
        }
        val live = ids(book)
        assertEquals(15, live.size)
        assertEquals(range(15), pages(book))
        assertEquals(live.toSet().size, live.size)
    }

    @Test
    fun `reordering while poems are being added keeps a complete set of pages`() = test {
        val book = newBook()
        val ps = List(10) { write(book, "P$it") }
        coroutineScope {
            val adds = (0 until 10).map { i ->
                async(Dispatchers.Default) { store.save(book, Poem("new$i", "N$i", "x")) }
            }
            val reorders = (0 until 10).map {
                async(Dispatchers.Default) { store.reorder(book, ps.map { p -> p.id }.shuffled()) }
            }
            (adds + reorders).awaitAll()
        }
        assertEquals(20, ids(book).size)
        assertEquals(range(20), pages(book))
    }

    @Test
    fun `fifty books created at once all exist with their own ids`() = test {
        val created = coroutineScope {
            (0 until 50).map { i -> async(Dispatchers.Default) { store.createBook("B$i", i) } }.awaitAll()
        }
        assertEquals(50, created.map { it.id }.toSet().size)
        assertEquals(50, store.observeBooks().first().size)
    }

    @Test
    fun `notes written at once end up as one of the written values`() = test {
        val book = newBook()
        val p = write(book, "A")
        val values = (0 until 20).map { "note $it" }
        coroutineScope {
            values.map { v -> async(Dispatchers.Default) { store.setNotes(p.id, v) } }.awaitAll()
        }
        assertTrue(store.notes(p.id) in values)
    }

    @Test
    fun `reads while poems are being binned never see a gap or a clash in the pages`() = test {
        val book = newBook()
        val ps = List(30) { write(book, "P$it") }
        coroutineScope {
            val writer = async(Dispatchers.Default) { ps.forEach { store.binPoem(book, it.id) } }
            val reader = async(Dispatchers.Default) {
                repeat(300) {
                    // One query = one snapshot: the live pages must always be exactly 0..count-1.
                    db.openHelper.readableDatabase.query(
                        "SELECT COUNT(*), COUNT(DISTINCT position), COALESCE(MIN(position), 0), COALESCE(MAX(position), -1) " +
                            "FROM poems WHERE deletedAt IS NULL",
                    ).use { c ->
                        c.moveToFirst()
                        val count = c.getInt(0)
                        assertEquals("distinct pages", count, c.getInt(1))
                        assertEquals("lowest page", 0, c.getInt(2))
                        assertEquals("highest page", count - 1, c.getInt(3))
                    }
                }
            }
            listOf(writer, reader).awaitAll()
        }
        assertEquals(0, ids(book).size)
        assertEquals(30, store.observeBinPoems().first().size)
    }
}
