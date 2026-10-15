package com.lukeneedham.poetrybook.data.db

import android.database.Cursor
import com.lukeneedham.poetrybook.data.DatabaseTestBase
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The rules the database itself enforces, whatever the app code does: keys, foreign keys, the
 * unique page index, NOT NULL, cascading deletes, and that any text survives a round trip.
 * These go straight to the DAOs, bypassing [com.lukeneedham.poetrybook.data.PoemStore].
 */
class SchemaConstraintsTest : DatabaseTestBase() {
    private val books get() = db.bookDao()
    private val poems get() = db.poemDao()

    private fun <T> Cursor.use(block: (Cursor) -> T): T = try { block(this) } finally { close() }

    private fun query(sql: String): List<Map<String, String?>> =
        db.openHelper.writableDatabase.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.columnNames.associateWith { name -> c.getString(c.getColumnIndexOrThrow(name)) })
                }
            }
        }

    private fun book(id: String = newId(), name: String = "Book") =
        BookEntity(id = id, name = name, color = null, updatedAt = 1)

    private suspend fun expectRefused(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            return
        }
        fail("the database should have refused: $what")
    }

    // --- Pragmas and structure ---

    @Test
    fun `foreign keys are switched on`() {
        assertEquals("1", query("PRAGMA foreign_keys").single().values.single())
    }

    @Test
    fun `there are exactly the three app tables`() {
        val tables = query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .map { it.getValue("name")!! }
            .filterNot { it.startsWith("sqlite_") || it == "android_metadata" || it == "room_master_table" }
        assertEquals(listOf("books", "poem_versions", "poems"), tables.sorted())
    }

    @Test
    fun `books has the documented columns`() {
        val columns = query("PRAGMA table_info(books)").associate { it["name"]!! to it }
        assertEquals(setOf("id", "name", "color", "updatedAt", "deletedAt"), columns.keys)
        assertEquals("1", columns.getValue("id")["pk"])
        assertEquals("1", columns.getValue("name")["notnull"])
        assertEquals("0", columns.getValue("color")["notnull"])
        assertEquals("1", columns.getValue("updatedAt")["notnull"])
        assertEquals("0", columns.getValue("deletedAt")["notnull"])
        assertEquals("TEXT", columns.getValue("id")["type"])
        assertEquals("INTEGER", columns.getValue("color")["type"])
    }

    @Test
    fun `poems has the documented columns`() {
        val columns = query("PRAGMA table_info(poems)").associate { it["name"]!! to it }
        assertEquals(setOf("id", "bookId", "position", "notes", "deletedAt"), columns.keys)
        assertEquals("1", columns.getValue("id")["pk"])
        assertEquals("1", columns.getValue("bookId")["notnull"])
        assertEquals("0", columns.getValue("position")["notnull"])
        assertEquals("1", columns.getValue("notes")["notnull"])
        assertEquals("0", columns.getValue("deletedAt")["notnull"])
    }

    @Test
    fun `poem_versions has the documented columns and a two column key`() {
        val columns = query("PRAGMA table_info(poem_versions)").associate { it["name"]!! to it }
        assertEquals(setOf("poemId", "createdAt", "title", "body"), columns.keys)
        assertEquals("1", columns.getValue("poemId")["pk"])
        assertEquals("2", columns.getValue("createdAt")["pk"])
        assertEquals("0", columns.getValue("title")["pk"])
        listOf("poemId", "createdAt", "title", "body").forEach {
            assertEquals("$it should be NOT NULL", "1", columns.getValue(it)["notnull"])
        }
    }

    @Test
    fun `poems has a unique index on book and position`() {
        val indexes = query("PRAGMA index_list(poems)")
        val index = indexes.single { it["name"] == "index_poems_bookId_position" }
        assertEquals("1", index["unique"])
        val columns = query("PRAGMA index_info(index_poems_bookId_position)").sortedBy { it["seqno"]!!.toInt() }
        assertEquals(listOf("bookId", "position"), columns.map { it["name"] })
    }

    @Test
    fun `the foreign keys cascade`() {
        val poemsFk = query("PRAGMA foreign_key_list(poems)").single()
        assertEquals("books", poemsFk["table"])
        assertEquals("bookId", poemsFk["from"])
        assertEquals("id", poemsFk["to"])
        assertEquals("CASCADE", poemsFk["on_delete"])

        val versionsFk = query("PRAGMA foreign_key_list(poem_versions)").single()
        assertEquals("poems", versionsFk["table"])
        assertEquals("poemId", versionsFk["from"])
        assertEquals("id", versionsFk["to"])
        assertEquals("CASCADE", versionsFk["on_delete"])
    }

    @Test
    fun `books has no foreign keys and no extra indexes`() {
        assertTrue(query("PRAGMA foreign_key_list(books)").isEmpty())
        assertTrue(query("PRAGMA index_list(books)").none { it["origin"] == "c" })
    }

    // --- Primary keys ---

    @Test
    fun `a book id cannot be used twice`() = test {
        books.insert(book("b"))
        expectRefused("a second book with the same id") { books.insert(book("b", "Other")) }
    }

    @Test
    fun `a poem id cannot be used twice`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p", "b", position = 0))
        expectRefused("a second poem with the same id") { poems.insert(PoemEntity("p", "b", position = 1)) }
    }

    @Test
    fun `a poem cannot have two versions with the same timestamp`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p", "b", position = 0))
        poems.insert(PoemVersionEntity("p", 5, "t", "x"))
        expectRefused("a second version at the same time") { poems.insert(PoemVersionEntity("p", 5, "t2", "y")) }
    }

    @Test
    fun `different poems can have versions at the same timestamp`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p1", "b", position = 0))
        poems.insert(PoemEntity("p2", "b", position = 1))
        poems.insert(PoemVersionEntity("p1", 5, "a", "x"))
        poems.insert(PoemVersionEntity("p2", 5, "b", "y"))
        assertEquals("a", poems.version("p1", 5)!!.title)
        assertEquals("b", poems.version("p2", 5)!!.title)
    }

    @Test
    fun `book names do not have to be unique`() = test {
        books.insert(book("b1", "Same"))
        books.insert(book("b2", "Same"))
        assertEquals(2, books.observeShelf().first().size)
    }

    // --- Foreign keys ---

    @Test
    fun `a poem needs an existing book`() = test {
        expectRefused("a poem in a book that does not exist") { poems.insert(PoemEntity("p", "ghost", position = 0)) }
        assertNull(poems.poem("p"))
    }

    @Test
    fun `a version needs an existing poem`() = test {
        expectRefused("a version of a poem that does not exist") {
            poems.insert(PoemVersionEntity("ghost", 1, "t", "b"))
        }
    }

    @Test
    fun `saving into a missing book fails and stores nothing`() = test {
        try {
            store.save("ghost", com.lukeneedham.poetrybook.data.Poem("p", "Rose", "red"))
            fail("saving into a missing book should fail")
        } catch (_: Exception) {
            // expected
        }
        assertNull(poems.poem("p"))
        assertNull(poems.latestVersion("p"))
    }

    // --- Cascades ---

    @Test
    fun `deleting a book removes its poems and their versions`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p1", "b", position = 0))
        poems.insert(PoemEntity("p2", "b", position = null, deletedAt = 3))
        poems.insert(PoemVersionEntity("p1", 1, "a", "x"))
        poems.insert(PoemVersionEntity("p1", 2, "a2", "x"))
        poems.insert(PoemVersionEntity("p2", 1, "b", "y"))

        books.delete("b")

        assertNull(poems.poem("p1"))
        assertNull(poems.poem("p2"))
        assertEquals("poem_versions should be empty", "0", query("SELECT COUNT(*) AS n FROM poem_versions").single()["n"])
        assertEquals("poems should be empty", "0", query("SELECT COUNT(*) AS n FROM poems").single()["n"])
    }

    @Test
    fun `deleting a book leaves other books alone`() = test {
        books.insert(book("b1"))
        books.insert(book("b2"))
        poems.insert(PoemEntity("p1", "b1", position = 0))
        poems.insert(PoemEntity("p2", "b2", position = 0))
        poems.insert(PoemVersionEntity("p1", 1, "a", "x"))
        poems.insert(PoemVersionEntity("p2", 1, "b", "y"))

        books.delete("b1")

        assertNull(poems.poem("p1"))
        assertNotNull(poems.poem("p2"))
        assertNotNull(poems.version("p2", 1))
    }

    @Test
    fun `deleting a poem removes its versions but not its book or siblings`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p1", "b", position = 0))
        poems.insert(PoemEntity("p2", "b", position = 1))
        poems.insert(PoemVersionEntity("p1", 1, "a", "x"))
        poems.insert(PoemVersionEntity("p1", 2, "a2", "x"))
        poems.insert(PoemVersionEntity("p2", 1, "b", "y"))

        poems.delete("p1")

        assertNull(poems.poem("p1"))
        assertNull(poems.version("p1", 1))
        assertNull(poems.version("p1", 2))
        assertNotNull(poems.poem("p2"))
        assertNotNull(poems.version("p2", 1))
        assertEquals(1, books.observeShelf().first().size)
    }

    @Test
    fun `deleting something that is not there changes nothing`() = test {
        books.insert(book("b"))
        books.delete("ghost")
        poems.delete("ghost")
        assertEquals(1, books.observeShelf().first().size)
    }

    // --- The unique page index ---

    @Test
    fun `two live poems cannot share a page`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p1", "b", position = 0))
        expectRefused("a second live poem on page 0") { poems.insert(PoemEntity("p2", "b", position = 0)) }
    }

    @Test
    fun `moving a poem onto a taken page is refused`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p1", "b", position = 0))
        poems.insert(PoemEntity("p2", "b", position = 1))
        expectRefused("moving p2 onto page 0") { poems.setPosition("p2", 0) }
        assertEquals(1, poems.poem("p2")!!.position)
    }

    @Test
    fun `binned poems have no page so any number can coexist`() = test {
        books.insert(book("b"))
        repeat(25) { poems.insert(PoemEntity("bin$it", "b", position = null, deletedAt = it.toLong() + 1)) }
        poems.insert(PoemEntity("live", "b", position = 0))
        assertEquals(1, poems.count("b"))
    }

    @Test
    fun `different books may each use page 0`() = test {
        books.insert(book("b1"))
        books.insert(book("b2"))
        poems.insert(PoemEntity("p1", "b1", position = 0))
        poems.insert(PoemEntity("p2", "b2", position = 0))
    }

    @Test
    fun `negative pages are allowed so poems can be parked`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p1", "b", position = -1))
        poems.insert(PoemEntity("p2", "b", position = -2))
    }

    @Test
    fun `a page freed by binning can be reused`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p1", "b", position = 0))
        poems.bin("p1", at = 10)
        poems.insert(PoemEntity("p2", "b", position = 0))
        assertEquals(listOf("p2"), poems.liveIds("b"))
    }

    // --- Defaults and nullability ---

    @Test
    fun `notes default to empty text`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p", "b", position = 0))
        assertEquals("", poems.notes("p"))
    }

    @Test
    fun `notes of a poem that does not exist are null`() = test {
        assertNull(poems.notes("ghost"))
    }

    @Test
    fun `colour and deletion time may be null and are read back as null`() = test {
        books.insert(BookEntity("b", "Book", color = null, updatedAt = 1, deletedAt = null))
        val row = books.observeShelf().first().single()
        assertNull(row.color)
        assertNull(row.deletedAt)
    }

    @Test
    fun `colour is stored as the full ARGB integer`() = test {
        listOf(0, 1, -1, 0xFF112233.toInt(), Int.MIN_VALUE, Int.MAX_VALUE).forEach { color ->
            val id = newId()
            books.insert(BookEntity(id, "c$color", color = color, updatedAt = 1))
            assertEquals(color, books.observeShelf().first().single { it.id == id }.color)
        }
    }

    @Test
    fun `timestamps keep their full 64 bit range`() = test {
        books.insert(BookEntity("b", "Book", color = null, updatedAt = Long.MAX_VALUE))
        poems.insert(PoemEntity("p", "b", position = 0))
        poems.insert(PoemVersionEntity("p", Long.MIN_VALUE, "t", "x"))
        poems.insert(PoemVersionEntity("p", Long.MAX_VALUE, "t2", "y"))
        assertEquals(Long.MAX_VALUE, books.observeShelf().first().single().updatedAt)
        assertEquals("t2", poems.latestVersion("p")!!.title)
        assertEquals("t", poems.version("p", Long.MIN_VALUE)!!.title)
    }

    // --- Any text survives ---

    private val awkwardTexts = listOf(
        "",
        " ",
        "\n",
        "\r\n",
        "line one\nline two\n\nline four",
        "windows\r\nline endings\r\n",
        "tabs\tand non-breaking spaces",
        "trailing space ",
        "Ünïcödé — “quotes” ‘single’ … ½ ©",
        "日本語の詩\n春の海",
        "العربية: قصيدة",
        "עברית",
        "emoji 🌹🔥 and ZWJ 👩‍👩‍👧‍👦 and flags 🇬🇧",
        "combining é vs precomposed é",
        "'; DROP TABLE poems; --",
        "\" OR 1=1 --",
        "100% sure_ish [brackets] {braces} \\ backslash",
        "%s %d \$name \${x}",
        "NULL",
        "<html>&amp;</html>",
        "a".repeat(10_000),
        "line\n".repeat(5_000),
    )

    @Test
    fun `titles and bodies of every awkward kind survive unchanged`() = test {
        val book = newBook()
        awkwardTexts.forEachIndexed { index, text ->
            val poem = com.lukeneedham.poetrybook.data.Poem(newId(), title = text, body = text)
            store.save(book, poem)
            val back = store.poems(book)[index]
            assertEquals("title #$index", text, back.title)
            assertEquals("body #$index", text, back.body)
        }
    }

    @Test
    fun `book names and notes of every awkward kind survive`() = test {
        awkwardTexts.forEach { text ->
            val id = store.createBook(text, color = 0).id
            val expected = text.trim()
            assertEquals(expected, store.observeBooks().first().single { it.id == id }.name)
        }
        val book = newBook()
        val poem = write(book, "P")
        awkwardTexts.forEach { text ->
            store.setNotes(poem.id, text)
            assertEquals(text, store.notes(poem.id))
        }
    }

    @Test
    fun `a very large poem is stored and read back`() = test {
        val book = newBook()
        val big = "Roses are red\n".repeat(100_000) // about 1.4 MB
        store.save(book, com.lukeneedham.poetrybook.data.Poem("big", "Big", big))
        assertEquals(big, store.poems(book).single().body)
    }

    @Test
    fun `a title that looks like another titles prefix is not confused with it`() = test {
        val book = newBook()
        write(book, "Rose")
        write(book, "Rose ")
        write(book, "rose")
        write(book, "Rose\n")
        assertEquals(listOf("Rose", "Rose ", "rose", "Rose\n"), titles(book))
    }

    // --- Raw bin statements ---

    @Test
    fun `bin and restore statements set and clear the deletion time and page`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("p", "b", position = 0))
        poems.bin("p", at = 77)
        poems.poem("p")!!.let {
            assertNull(it.position)
            assertEquals(77L, it.deletedAt)
        }
        poems.restore("p", position = 4)
        poems.poem("p")!!.let {
            assertEquals(4, it.position)
            assertNull(it.deletedAt)
        }
    }

    @Test
    fun `the park and settle statements close a gap without clashing`() = test {
        books.insert(book("b"))
        listOf("a", "b2", "c", "d", "e").forEachIndexed { i, id -> poems.insert(PoemEntity(id, "b", position = i)) }
        poems.bin("b2", at = 1) // page 1 is now free

        poems.parkAfter("b", after = 1)
        assertEquals("c,d,e should be parked on negative pages", listOf(-2, -3, -4), listOf("c", "d", "e").map { poems.poem(it)!!.position })
        poems.settleParkedDown("b")

        assertEquals(listOf("a", "c", "d", "e"), poems.liveIds("b"))
        assertEquals(listOf(0, 1, 2, 3), listOf("a", "c", "d", "e").map { poems.poem(it)!!.position })
    }

    @Test
    fun `parking all positions then placing them again works for any order`() = test {
        books.insert(book("b"))
        listOf("a", "b2", "c").forEachIndexed { i, id -> poems.insert(PoemEntity(id, "b", position = i)) }
        poems.parkPositions("b")
        assertEquals(listOf(-1, -2, -3), listOf("a", "b2", "c").map { poems.poem(it)!!.position })
        listOf("c", "a", "b2").forEachIndexed { i, id -> poems.setPosition(id, i) }
        assertEquals(listOf("c", "a", "b2"), poems.liveIds("b"))
    }

    @Test
    fun `parking leaves other books and binned poems alone`() = test {
        books.insert(book("b1"))
        books.insert(book("b2"))
        poems.insert(PoemEntity("x", "b1", position = 0))
        poems.insert(PoemEntity("y", "b2", position = 0))
        poems.insert(PoemEntity("z", "b1", position = null, deletedAt = 5))

        poems.parkPositions("b1")

        assertEquals(-1, poems.poem("x")!!.position)
        assertEquals(0, poems.poem("y")!!.position)
        assertNull(poems.poem("z")!!.position)
    }

    @Test
    fun `settling with nothing parked changes nothing`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("a", "b", position = 0))
        poems.insert(PoemEntity("c", "b", position = 1))
        poems.settleParkedDown("b")
        assertEquals(listOf("a", "c"), poems.liveIds("b"))
        assertEquals(listOf(0, 1), listOf("a", "c").map { poems.poem(it)!!.position })
    }

    @Test
    fun `live ids come back in page order whatever order they were inserted in`() = test {
        books.insert(book("b"))
        poems.insert(PoemEntity("third", "b", position = 2))
        poems.insert(PoemEntity("first", "b", position = 0))
        poems.insert(PoemEntity("second", "b", position = 1))
        assertEquals(listOf("first", "second", "third"), poems.liveIds("b"))
    }

    @Test
    fun `live ids leave out binned poems and other books`() = test {
        books.insert(book("b1"))
        books.insert(book("b2"))
        poems.insert(PoemEntity("a", "b1", position = 0))
        poems.insert(PoemEntity("gone", "b1", position = null, deletedAt = 1))
        poems.insert(PoemEntity("other", "b2", position = 0))
        assertEquals(listOf("a"), poems.liveIds("b1"))
        assertFalse(poems.liveIds("b1").contains("gone"))
    }
}
