package com.lukeneedham.poetrybook.data

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/**
 * Property-style test: a long random series of operations is applied both to the real database
 * (through [PoemStore]) and to a deliberately simple in-memory model, and after every step the whole
 * visible state is compared. Any seed that fails is reproducible: the message names the seed, the
 * step and the operations so far.
 */
class PoemStoreModelBasedTest : DatabaseTestBase() {
    private class MPoem(val id: String, var title: String, var body: String) {
        val versions = mutableListOf(title to body) // oldest first
        var notes = ""
    }

    private class MBook(val id: String, var name: String, var color: Int) {
        var binned = false
        val live = mutableListOf<MPoem>()
        val bin = mutableListOf<MPoem>() // in the order they were binned, oldest first
        var poemCountAtBinOrder = 0
    }

    private inner class Run(val seed: Int, val steps: Int, val weights: Map<String, Int>) {
        val random = Random(seed)
        val books = mutableListOf<MBook>()
        val log = mutableListOf<String>()
        var counter = 0

        fun say(what: String) {
            log += what
        }

        fun fail(step: Int, what: String): String =
            "seed=$seed step=$step: $what\nlast operations:\n" + log.takeLast(8).joinToString("\n")

        fun pick(): String {
            val total = weights.values.sum()
            var n = random.nextInt(total)
            for ((name, w) in weights) {
                if (n < w) return name
                n -= w
            }
            error("unreachable")
        }

        suspend fun step(step: Int) {
            clock += 1_000
            val book = if (books.isEmpty()) null else books[random.nextInt(books.size)]
            when (pick()) {
                "createBook" -> {
                    val name = "B${counter++}"
                    val color = random.nextInt()
                    val created = store.createBook(name, color)
                    books += MBook(created.id, name, color)
                    say("createBook $name")
                }
                "newPoem" -> if (book != null) {
                    val poem = MPoem(newId(), "T${counter++}", "body ${random.nextInt(1000)}")
                    store.save(book.id, Poem(poem.id, poem.title, poem.body))
                    book.live += poem
                    say("newPoem ${poem.title} in ${book.name}")
                }
                "editPoem" -> if (book != null && book.live.isNotEmpty()) {
                    val poem = book.live[random.nextInt(book.live.size)]
                    val (title, body) = when (random.nextInt(4)) {
                        0 -> poem.title to poem.body // unchanged
                        1 -> "T${counter++}" to poem.body
                        2 -> poem.title to "body ${random.nextInt(1000)}"
                        else -> "T${counter++}" to "body ${random.nextInt(1000)}"
                    }
                    val wrote = store.save(book.id, Poem(poem.id, title, body))
                    val last = poem.versions.last()
                    val shouldWrite = last != (title to body)
                    assertEquals(fail(step, "save() result for ${poem.title}"), shouldWrite, wrote)
                    if (shouldWrite) {
                        poem.versions += title to body
                        poem.title = title
                        poem.body = body
                    }
                    say("editPoem ${poem.id.take(4)} -> $title / unchanged=${!shouldWrite}")
                }
                "binPoem" -> if (book != null && book.live.isNotEmpty()) {
                    val poem = book.live.removeAt(random.nextInt(book.live.size))
                    book.bin += poem
                    store.binPoem(book.id, poem.id)
                    say("binPoem ${poem.title} from ${book.name}")
                }
                "restorePoem" -> if (book != null && book.bin.isNotEmpty()) {
                    val poem = book.bin.removeAt(random.nextInt(book.bin.size))
                    book.live += poem
                    store.restorePoem(poem.id)
                    say("restorePoem ${poem.title} in ${book.name}")
                }
                "deletePoemForever" -> if (book != null && book.bin.isNotEmpty()) {
                    val poem = book.bin.removeAt(random.nextInt(book.bin.size))
                    store.deletePoemForever(poem.id)
                    say("deletePoemForever ${poem.title}")
                    assertNull(fail(step, "poem should be gone"), db.poemDao().poem(poem.id))
                    assertEquals(fail(step, "versions should be gone"), 0, store.observeHistory(poem.id).first().size)
                }
                "reorder" -> if (book != null && book.live.isNotEmpty()) {
                    val subset = book.live.shuffled(random).take(random.nextInt(0, book.live.size + 1)).toMutableList()
                    val request = subset.map { it.id } + listOf("ghost-${random.nextInt(3)}") +
                        book.bin.take(1).map { it.id } // junk the store must ignore
                    store.reorder(book.id, request.shuffled(random).let { shuffled ->
                        // keep the subset's relative order meaningful: subset first, then junk
                        subset.map { it.id } + shuffled.filter { it !in subset.map { s -> s.id } }
                    })
                    val rest = book.live.filter { it !in subset }
                    book.live.clear()
                    book.live += subset + rest
                    say("reorder ${book.name} to ${book.live.map { it.title }}")
                }
                "setNotes" -> if (book != null && (book.live.isNotEmpty() || book.bin.isNotEmpty())) {
                    val all = book.live + book.bin
                    val poem = all[random.nextInt(all.size)]
                    val notes = if (random.nextInt(5) == 0) "" else "notes ${random.nextInt(1000)}"
                    store.setNotes(poem.id, notes)
                    poem.notes = notes
                    say("setNotes ${poem.title}")
                }
                "editBook" -> if (book != null) {
                    val name = "  E${counter++}  "
                    val color = random.nextInt()
                    store.editBook(book.id, name, color)
                    book.name = name.trim()
                    book.color = color
                    say("editBook -> ${book.name}")
                }
                "binBook" -> if (book != null && !book.binned) {
                    store.binBook(book.id)
                    book.binned = true
                    say("binBook ${book.name}")
                }
                "restoreBook" -> if (book != null && book.binned) {
                    store.restoreBook(book.id)
                    book.binned = false
                    say("restoreBook ${book.name}")
                }
                "deleteBookForever" -> if (book != null && book.binned) {
                    store.deleteBookForever(book.id)
                    books -= book
                    say("deleteBookForever ${book.name}")
                    (book.live + book.bin).forEach {
                        assertNull(fail(step, "poem ${it.title} should be gone with its book"), db.poemDao().poem(it.id))
                    }
                }
                "importBook" -> {
                    val n = random.nextInt(0, 5)
                    val name = "I${counter++}"
                    val imported = List(n) { ImportedPoem("IP${counter++}", "ib $it", random.nextLong(0, 5_000)) }
                    val id = store.importBook(name, random.nextInt(), imported, 0)
                    val mb = MBook(id, name, 0)
                    val storedColor = store.observeBooks().first().single { it.id == id }.color!!
                    mb.color = storedColor
                    store.poems(id).forEach { mb.live += MPoem(it.id, it.title, it.body) }
                    books += mb
                    say("importBook $name ($n)")
                }
            }
        }

        suspend fun verify(step: Int) {
            val shelf = store.observeBooks().first().associateBy { it.id }
            val binBooks = store.observeBinBooks().first().associateBy { it.id }
            val binPoems = store.observeBinPoems().first().associateBy { it.id }

            assertEquals(fail(step, "shelf books"), books.filter { !it.binned }.map { it.id }.toSet(), shelf.keys)
            assertEquals(fail(step, "bin books"), books.filter { it.binned }.map { it.id }.toSet(), binBooks.keys)
            assertEquals(
                fail(step, "bin poems"),
                books.filter { !it.binned }.flatMap { b -> b.bin.map { it.id } }.toSet(),
                binPoems.keys,
            )

            for (b in books) {
                val shown = if (b.binned) binBooks.getValue(b.id) else shelf.getValue(b.id)
                if (b.binned) {
                    assertEquals(fail(step, "bin name ${b.name}"), b.name, (shown as BinBook).name)
                    assertEquals(fail(step, "bin poem count ${b.name}"), b.live.size, shown.poemCount)
                    assertEquals(fail(step, "bin colour ${b.name}"), b.color, shown.color)
                } else {
                    shown as Book
                    assertEquals(fail(step, "name ${b.name}"), b.name, shown.name)
                    assertEquals(fail(step, "colour ${b.name}"), b.color, shown.color)
                    assertEquals(fail(step, "poem count ${b.name}"), b.live.size, shown.poemCount)
                }

                assertEquals(
                    fail(step, "poems of ${b.name}"),
                    b.live.map { Poem(it.id, it.title, it.body) },
                    store.poems(b.id),
                )
                assertEquals(fail(step, "pages of ${b.name}"), range(b.live.size), pages(b.id))
                assertEquals(fail(step, "titles of ${b.name}"), b.live.map { it.title }, store.poemTitles(b.id))

                for (p in b.live + b.bin) {
                    val history = store.observeHistory(p.id).first()
                    assertEquals(
                        fail(step, "history of ${p.title}"),
                        p.versions.reversed(),
                        history.map { it.title to it.body },
                    )
                    assertEquals(
                        fail(step, "history order of ${p.title}"),
                        history.map { it.createdAt }.sortedDescending(),
                        history.map { it.createdAt },
                    )
                    assertEquals(fail(step, "history times unique ${p.title}"), history.size, history.map { it.createdAt }.toSet().size)
                    assertEquals(fail(step, "notes of ${p.title}"), p.notes, store.notes(p.id))
                }
                for (p in b.bin) {
                    assertNull(fail(step, "binned ${p.title} must have no page"), db.poemDao().poem(p.id)!!.position)
                }
            }
        }

        suspend fun execute() {
            repeat(steps) { step ->
                step(step)
                verify(step)
            }
        }
    }

    private val mixed = mapOf(
        "createBook" to 3, "newPoem" to 10, "editPoem" to 8, "binPoem" to 5, "restorePoem" to 4,
        "deletePoemForever" to 2, "reorder" to 5, "setNotes" to 3, "editBook" to 2, "binBook" to 2,
        "restoreBook" to 2, "deleteBookForever" to 1, "importBook" to 1,
    )

    private val binHeavy = mapOf(
        "createBook" to 2, "newPoem" to 8, "binPoem" to 10, "restorePoem" to 9, "deletePoemForever" to 3,
        "reorder" to 3, "binBook" to 3, "restoreBook" to 3,
    )

    private val editHeavy = mapOf(
        "createBook" to 2, "newPoem" to 4, "editPoem" to 20, "setNotes" to 5, "reorder" to 2, "binPoem" to 2, "restorePoem" to 2,
    )

    private val reorderHeavy = mapOf(
        "createBook" to 1, "newPoem" to 8, "reorder" to 20, "binPoem" to 4, "restorePoem" to 4,
    )

    private val bookHeavy = mapOf(
        "createBook" to 6, "newPoem" to 6, "editBook" to 6, "binBook" to 6, "restoreBook" to 6,
        "deleteBookForever" to 4, "importBook" to 3, "binPoem" to 2,
    )

    @Test
    fun `mixed operations match the model over many seeds`() = test {
        for (seed in 1..12) Run(seed, steps = 90, weights = mixed).execute().also { reset() }
    }

    @Test
    fun `bin heavy operations match the model over many seeds`() = test {
        for (seed in 100..109) Run(seed, steps = 90, weights = binHeavy).execute().also { reset() }
    }

    @Test
    fun `edit heavy operations match the model over many seeds`() = test {
        for (seed in 200..207) Run(seed, steps = 90, weights = editHeavy).execute().also { reset() }
    }

    @Test
    fun `reorder heavy operations match the model over many seeds`() = test {
        for (seed in 300..307) Run(seed, steps = 90, weights = reorderHeavy).execute().also { reset() }
    }

    @Test
    fun `book heavy operations match the model over many seeds`() = test {
        for (seed in 400..409) Run(seed, steps = 90, weights = bookHeavy).execute().also { reset() }
    }

    /** Starts the next seed from an empty database. */
    private fun reset() {
        db.clearAllTables()
        clock = 1_000
    }
}
