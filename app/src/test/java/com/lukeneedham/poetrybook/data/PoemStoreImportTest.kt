package com.lukeneedham.poetrybook.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Importing a ready-made book (what the one-off folder import writes). */
class PoemStoreImportTest : DatabaseTestBase() {
    private suspend fun shelf() = store.observeBooks().first()

    private fun imported(vararg titles: String, at: Long = 0) = titles.map { ImportedPoem(it, "body of $it", at) }

    @Test
    fun `an import creates a live book with its poems in the given order`() = test {
        val id = store.importBook("Old", color = 5, imported = imported("A", "B", "C"), updatedAt = 0)
        assertEquals(listOf("A", "B", "C"), titles(id))
        assertEquals(listOf("body of A", "body of B", "body of C"), store.poems(id).map { it.body })
        assertEquals(range(3), pages(id))
        assertEquals("Old", shelf().single().name)
        assertEquals(3, shelf().single().poemCount)
    }

    @Test
    fun `an empty import is an empty book`() = test {
        val id = store.importBook("Empty", color = null, imported = emptyList(), updatedAt = 0)
        assertTrue(store.poems(id).isEmpty())
        assertEquals(0, shelf().single().poemCount)
    }

    @Test
    fun `the name is trimmed and the colour kept or left unset`() = test {
        store.importBook("  Padded  ", color = 0x0A0B0C, imported = emptyList(), updatedAt = 0)
        store.importBook("Plain", color = null, imported = emptyList(), updatedAt = 0)
        val byName = shelf().associateBy { it.name }
        assertEquals(0x0A0B0C, byName.getValue("Padded").color)
        assertNull(byName.getValue("Plain").color)
    }

    @Test
    fun `the books updated time is the given time, or now if none`() = test {
        clock = 8_000
        store.importBook("Dated", color = null, imported = emptyList(), updatedAt = 3_000)
        store.importBook("Undated", color = null, imported = emptyList(), updatedAt = 0)
        val byName = shelf().associateBy { it.name }
        assertEquals(3_000L, byName.getValue("Dated").updatedAt)
        assertEquals(8_000L, byName.getValue("Undated").updatedAt)
    }

    @Test
    fun `each poems first version is stamped with its file time, or now if none`() = test {
        clock = 8_000
        val id = store.importBook(
            "B", color = null,
            imported = listOf(ImportedPoem("dated", "x", 2_000), ImportedPoem("undated", "y", 0)),
            updatedAt = 0,
        )
        val (dated, undated) = ids(id)
        assertEquals(2_000L, store.observeHistory(dated).first().single().createdAt)
        assertEquals(8_000L, store.observeHistory(undated).first().single().createdAt)
    }

    @Test
    fun `an imported poem has exactly one version and an empty notepad`() = test {
        val id = store.importBook("B", null, imported("A"), 0)
        val poem = ids(id).single()
        assertEquals(1, store.observeHistory(poem).first().size)
        assertEquals("", store.notes(poem))
    }

    @Test
    fun `every imported poem gets its own id`() = test {
        val id = store.importBook("B", null, imported("A", "A", "A"), 0)
        assertEquals(3, ids(id).toSet().size)
        assertEquals(listOf("A", "A", "A"), titles(id))
    }

    @Test
    fun `imported poems with the same file time do not clash`() = test {
        val id = store.importBook("B", null, List(50) { ImportedPoem("P$it", "x", 5_000) }, 0)
        assertEquals(50, ids(id).size)
        assertEquals(range(50), pages(id))
    }

    @Test
    fun `importing the same thing twice makes two separate books`() = test {
        val one = store.importBook("Same", null, imported("A"), 0)
        val two = store.importBook("Same", null, imported("A"), 0)
        assertTrue(one != two)
        assertEquals(2, shelf().size)
        assertEquals(listOf("A"), titles(one))
        assertEquals(listOf("A"), titles(two))
    }

    @Test
    fun `an import does not disturb existing books`() = test {
        val existing = newBook("Mine")
        write(existing, "keep")
        store.importBook("New", null, imported("A", "B"), 0)
        assertEquals(listOf("keep"), titles(existing))
        assertEquals(listOf(0), pages(existing))
    }

    @Test
    fun `imported poems behave like any other afterwards`() = test {
        val id = store.importBook("B", null, imported("A", "B", "C"), 0)
        val (a, b, c) = ids(id)
        store.binPoem(id, b)
        assertEquals(listOf("A", "C"), titles(id))
        store.restorePoem(b)
        assertEquals(listOf("A", "C", "B"), titles(id))
        store.reorder(id, listOf(c, b, a))
        assertEquals(listOf("C", "B", "A"), titles(id))
        store.save(id, Poem(a, "A", "edited"))
        assertEquals(2, store.observeHistory(a).first().size)
    }

    @Test
    fun `awkward titles and bodies survive an import exactly`() = test {
        val texts = listOf("", "  ", "line\r\nbreak", "🌹", "日本語", "'; DROP TABLE books; --")
        val id = store.importBook("B", null, texts.map { ImportedPoem(it, it, 1) }, 0)
        assertEquals(texts, titles(id))
        assertEquals(texts, store.poems(id).map { it.body })
    }

    @Test
    fun `a big import is fine`() = test {
        val id = store.importBook("Big", null, List(1_000) { ImportedPoem("P$it", "body $it", it.toLong()) }, 0)
        assertEquals(1_000, ids(id).size)
        assertEquals(range(1_000), pages(id))
    }

    @Test
    fun `importing several books at once returns their ids in order and creates them all`() = test {
        val idsBack = store.importBooks(
            listOf(
                ImportedBook("One", 1, imported("a", "b"), 0),
                ImportedBook("Two", null, imported("c"), 0),
                ImportedBook("Three", 3, emptyList(), 0),
            ),
        )
        assertEquals(3, idsBack.size)
        assertEquals(listOf("One", "Two", "Three"), idsBack.map { id -> shelf().single { it.id == id }.name })
        assertEquals(listOf("a", "b"), titles(idsBack[0]))
        assertEquals(listOf("c"), titles(idsBack[1]))
        assertTrue(titles(idsBack[2]).isEmpty())
    }

    @Test
    fun `importing no books at once does nothing`() = test {
        assertTrue(store.importBooks(emptyList()).isEmpty())
        assertTrue(shelf().isEmpty())
    }

    @Test
    fun `an import that fails part way is rolled back completely`() = test {
        try {
            db.withTransaction {
                store.importBook("Inside one", null, imported("a"), 0)
                store.importBook("Inside two", null, imported("b", "c"), 0)
                error("boom")
            }
            fail("the transaction should have failed")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertTrue(shelf().isEmpty())
        assertEquals("0", db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM poems").use { it.moveToFirst(); it.getString(0) })
        assertEquals("0", db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM poem_versions").use { it.moveToFirst(); it.getString(0) })
    }
}
