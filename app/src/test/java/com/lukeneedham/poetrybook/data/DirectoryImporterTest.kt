package com.lukeneedham.poetrybook.data

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import java.io.IOException

/** The one-off folder import, run against a fake document provider: what becomes a book, a poem, and what is left alone. */
class DirectoryImporterTest : DatabaseTestBase() {
    private lateinit var provider: FakeDocumentsProvider
    private lateinit var context: Application
    private lateinit var settings: AppSettings
    private val treeUri: Uri = Uri.parse("content://fake.docs/tree/root")

    @Before
    fun setUpProvider() {
        context = ApplicationProvider.getApplicationContext()
        provider = Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create("fake.docs").get()
        settings = AppSettings(context)
    }

    private fun importer(miscName: String = "Misc") = DirectoryImporter(context, settings, store, miscName)

    private suspend fun import(miscName: String = "Misc") = importer(miscName).importFrom(treeUri)

    private suspend fun shelfByName() = store.observeBooks().first().associateBy { it.name }

    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // --- Books and poems ---

    @Test
    fun `every subfolder becomes a book and every text file in it a poem`() = test {
        val love = provider.dir(provider.root, "Love")
        provider.file(love, "Rose.txt", "Roses are red")
        provider.file(love, "Violet.txt", "Violets are blue")
        val winter = provider.dir(provider.root, "Winter")
        provider.file(winter, "Snow.txt", "white")

        val result = import()

        assertEquals(ImportResult(books = 2, poems = 3), result)
        val shelf = shelfByName()
        assertEquals(setOf("Love", "Winter"), shelf.keys)
        assertEquals(listOf("Rose", "Violet"), titles(shelf.getValue("Love").id))
        assertEquals(listOf("Roses are red", "Violets are blue"), store.poems(shelf.getValue("Love").id).map { it.body })
        assertEquals(listOf("Snow"), titles(shelf.getValue("Winter").id))
    }

    @Test
    fun `the file name without its extension is the poem title`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "My Poem.txt", "x")
        provider.file(book, "Loud.TXT", "y")
        provider.file(book, "Mixed.TxT", "z")
        provider.file(book, "dots.in.name.txt", "w")
        import()
        val id = shelfByName().getValue("B").id
        assertEquals(setOf("My Poem", "Loud", "Mixed", "dots.in.name"), titles(id).toSet())
    }

    @Test
    fun `only text files are poems`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "poem.txt", "yes")
        provider.file(book, "notes.md", "no")
        provider.file(book, "cover.jpg", "no")
        provider.file(book, "noextension", "no")
        provider.file(book, "poem.txt.bak", "no")
        provider.file(book, "txt", "no")
        import()
        assertEquals(listOf("poem"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `hidden folders are not books`() = test {
        provider.file(provider.dir(provider.root, ".thumbnails"), "a.txt", "x")
        provider.file(provider.dir(provider.root, "Real"), "b.txt", "y")
        val result = import()
        assertEquals(setOf("Real"), shelfByName().keys)
        assertEquals(1, result.books)
    }

    @Test
    fun `only the first level of folders is read, not folders inside books`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "top.txt", "x")
        provider.file(provider.dir(book, "nested"), "deep.txt", "y")
        import()
        assertEquals(setOf("B"), shelfByName().keys)
        assertEquals(listOf("top"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `an empty subfolder becomes an empty book`() = test {
        provider.dir(provider.root, "Empty")
        val result = import()
        assertEquals(ImportResult(1, 0), result)
        assertEquals(0, shelfByName().getValue("Empty").poemCount)
    }

    @Test
    fun `an empty directory imports nothing`() = test {
        assertEquals(ImportResult(0, 0), import())
        assertTrue(store.observeBooks().first().isEmpty())
    }

    @Test
    fun `a directory of only non text files imports nothing`() = test {
        provider.file(provider.root, "readme.md", "x")
        provider.file(provider.dir(provider.root, "Pics"), "a.png", "x")
        val result = import()
        assertEquals(ImportResult(1, 0), result)
    }

    // --- Loose files ---

    @Test
    fun `text files directly in the directory go into a Misc book`() = test {
        provider.file(provider.root, "Loose one.txt", "a")
        provider.file(provider.root, "Loose two.txt", "b")
        provider.file(provider.dir(provider.root, "Real"), "r.txt", "c")

        val result = import()

        assertEquals(ImportResult(2, 3), result)
        assertEquals(listOf("Loose one", "Loose two"), titles(shelfByName().getValue("Misc").id))
    }

    @Test
    fun `no loose files means no Misc book`() = test {
        provider.file(provider.dir(provider.root, "Real"), "r.txt", "c")
        import()
        assertNull(shelfByName()["Misc"])
    }

    @Test
    fun `the Misc book uses the name it is given`() = test {
        provider.file(provider.root, "a.txt", "x")
        import(miscName = "Sonstiges")
        assertEquals(setOf("Sonstiges"), shelfByName().keys)
    }

    @Test
    fun `a real folder called misc keeps its name and the loose book is told apart`() = test {
        provider.file(provider.dir(provider.root, "misc"), "in-folder.txt", "x")
        provider.file(provider.root, "loose.txt", "y")
        import()
        val shelf = shelfByName()
        assertEquals(setOf("misc", "Misc (loose)"), shelf.keys)
        assertEquals(listOf("in-folder"), titles(shelf.getValue("misc").id))
        assertEquals(listOf("loose"), titles(shelf.getValue("Misc (loose)").id))
    }

    // --- Order, colours and times from the old app ---

    @Test
    fun `poems come in alphabetical order, ignoring case, when nothing was saved`() = test {
        val book = provider.dir(provider.root, "B")
        listOf("banana", "Apple", "cherry", "apricot").forEach { provider.file(book, "$it.txt", it) }
        import()
        assertEquals(listOf("Apple", "apricot", "banana", "cherry"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `the page order the old app saved is used, with unlisted poems after it`() = test {
        val book = provider.dir(provider.root, "B")
        listOf("a", "b", "c", "d").forEach { provider.file(book, "$it.txt", it) }
        prefs().edit().putString("order|$treeUri|root/B", JSONArray(listOf("c", "a")).toString()).commit()
        import()
        assertEquals(listOf("c", "a", "b", "d"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `a saved order naming files that no longer exist is harmless`() = test {
        val book = provider.dir(provider.root, "B")
        listOf("a", "b").forEach { provider.file(book, "$it.txt", it) }
        prefs().edit().putString("order|$treeUri|root/B", JSONArray(listOf("gone", "b", "ghost")).toString()).commit()
        import()
        assertEquals(listOf("b", "a"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `a corrupt saved order is ignored`() = test {
        val book = provider.dir(provider.root, "B")
        listOf("b", "a").forEach { provider.file(book, "$it.txt", it) }
        prefs().edit().putString("order|$treeUri|root/B", "not json at all").commit()
        import()
        assertEquals(listOf("a", "b"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `an order saved for another folder is not used`() = test {
        val book = provider.dir(provider.root, "B")
        listOf("a", "b").forEach { provider.file(book, "$it.txt", it) }
        prefs().edit().putString("order|content://other/tree/x|root/B", JSONArray(listOf("b", "a")).toString()).commit()
        import()
        assertEquals(listOf("a", "b"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `the saved order of loose files is used for Misc`() = test {
        listOf("x", "y", "z").forEach { provider.file(provider.root, "$it.txt", it) }
        prefs().edit().putString("order|$treeUri|root", JSONArray(listOf("z", "y")).toString()).commit()
        import()
        assertEquals(listOf("z", "y", "x"), titles(shelfByName().getValue("Misc").id))
    }

    @Test
    fun `the cover colour the old app saved is kept`() = test {
        provider.file(provider.dir(provider.root, "Coloured"), "a.txt", "x")
        provider.file(provider.dir(provider.root, "Plain"), "b.txt", "y")
        prefs().edit().putInt("color|root/Coloured", 0xFF336699.toInt()).commit()
        import()
        assertEquals(0xFF336699.toInt(), shelfByName().getValue("Coloured").color)
        assertNull(shelfByName().getValue("Plain").color)
    }

    @Test
    fun `the colour saved for loose files goes to the Misc book`() = test {
        provider.file(provider.root, "a.txt", "x")
        prefs().edit().putInt("color|root", 12345).commit()
        import()
        assertEquals(12345, shelfByName().getValue("Misc").color)
    }

    @Test
    fun `a book is as new as its newest file`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "old.txt", "x", modified = 1_000)
        provider.file(book, "new.txt", "y", modified = 9_000)
        provider.file(book, "mid.txt", "z", modified = 5_000)
        import()
        assertEquals(9_000L, shelfByName().getValue("B").updatedAt)
    }

    @Test
    fun `the old apps last changed time wins when it is newer than every file`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "a.txt", "x", modified = 1_000)
        prefs().edit().putLong("updated|root/B", 50_000L).commit()
        import()
        assertEquals(50_000L, shelfByName().getValue("B").updatedAt)
    }

    @Test
    fun `files without a time use the time of the import`() = test {
        clock = 6_000
        provider.file(provider.dir(provider.root, "B"), "a.txt", "x", modified = 0)
        import()
        val book = shelfByName().getValue("B")
        assertEquals(6_000L, book.updatedAt)
        assertEquals(6_000L, store.observeHistory(ids(book.id).single()).first().single().createdAt)
    }

    @Test
    fun `each poems first version is stamped with its file time`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "a.txt", "x", modified = 1_234)
        provider.file(book, "b.txt", "y", modified = 5_678)
        import()
        val (a, b) = ids(shelfByName().getValue("B").id)
        assertEquals(1_234L, store.observeHistory(a).first().single().createdAt)
        assertEquals(5_678L, store.observeHistory(b).first().single().createdAt)
    }

    // --- Content ---

    @Test
    fun `text is imported exactly, whatever it contains`() = test {
        val bodies = mapOf(
            "empty" to "",
            "crlf" to "one\r\ntwo\r\n",
            "unicode" to "日本語 🌹 Ünï",
            "trailing" to "end with newline\n",
            "blank lines" to "a\n\n\nb",
            "spaces" to "  indented\n\ttabbed",
            "sql" to "'; DROP TABLE poems; --",
        )
        val book = provider.dir(provider.root, "B")
        bodies.forEach { (name, text) -> provider.file(book, "$name.txt", text) }
        import()
        val imported = store.poems(shelfByName().getValue("B").id).associate { it.title to it.body }
        assertEquals(bodies, imported)
    }

    @Test
    fun `unusual file names become titles unchanged`() = test {
        val names = listOf("Ünï cödé", "日本語", "with  two  spaces", "with 'quote'", "100%", "a & b", "comma, comma")
        val book = provider.dir(provider.root, "B")
        names.forEach { provider.file(book, "$it.txt", "x") }
        import()
        assertEquals(names.toSet(), titles(shelfByName().getValue("B").id).toSet())
    }

    @Test
    fun `a large poem and a large folder import whole`() = test {
        val book = provider.dir(provider.root, "Big")
        val big = "Roses are red\n".repeat(20_000)
        provider.file(book, "big.txt", big)
        repeat(300) { provider.file(book, "p%03d.txt".format(it), "body $it") }
        val result = import()
        assertEquals(ImportResult(1, 301), result)
        val id = shelfByName().getValue("Big").id
        assertEquals(big, store.poems(id).single { it.title == "big" }.body)
        assertEquals(range(301), pages(id))
    }

    // --- Safety ---

    @Test
    fun `the import only reads and never writes, deletes or changes anything in the folder`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "a.txt", "x")
        provider.file(provider.root, "loose.txt", "y")
        import()
        assertTrue("writes: ${provider.writes}", provider.writes.isEmpty())
        assertTrue("open modes: ${provider.openModes}", provider.openModes.isNotEmpty() && provider.openModes.all { it == "r" })
    }

    @Test
    fun `the directory is not remembered anywhere after the import`() = test {
        provider.file(provider.dir(provider.root, "B"), "a.txt", "x")
        val before = prefs().all.toMap()
        import()
        assertEquals(before, prefs().all.toMap())
        assertTrue(prefs().all.keys.none { it.contains("folder", ignoreCase = true) })
    }

    @Test
    fun `importing twice makes the books twice`() = test {
        provider.file(provider.dir(provider.root, "B"), "a.txt", "x")
        import()
        import()
        val books = store.observeBooks().first()
        assertEquals(listOf("B", "B"), books.map { it.name })
        assertEquals(2, books.map { it.id }.toSet().size)
    }

    @Test
    fun `an import does not touch books already there`() = test {
        val mine = newBook("Mine")
        write(mine, "keep")
        provider.file(provider.dir(provider.root, "B"), "a.txt", "x")
        import()
        assertEquals(listOf("keep"), titles(mine))
        assertEquals(listOf(0), pages(mine))
    }

    // --- Failures ---

    @Test
    fun `a file that cannot be read is skipped and the rest are imported`() = test {
        val book = provider.dir(provider.root, "B")
        provider.file(book, "good1.txt", "x")
        provider.file(book, "bad.txt", "y", unreadable = true)
        provider.file(book, "good2.txt", "z")
        val result = import()
        assertEquals(ImportResult(1, 2), result)
        assertEquals(listOf("good1", "good2"), titles(shelfByName().getValue("B").id))
    }

    @Test
    fun `a folder that cannot be listed fails the whole import and leaves nothing behind`() = test {
        provider.file(provider.dir(provider.root, "A-good"), "a.txt", "x")
        provider.dir(provider.root, "B-bad").unlistable = true
        provider.file(provider.dir(provider.root, "C-good"), "c.txt", "y")

        try {
            import()
            fail("the import should fail")
        } catch (_: IOException) {
            // expected
        }
        assertTrue("nothing should be imported", store.observeBooks().first().isEmpty())
    }

    @Test
    fun `a root that cannot be listed fails with an IO error`() = test {
        provider.root.unlistable = true
        try {
            import()
            fail("the import should fail")
        } catch (_: IOException) {
            // expected
        }
        assertTrue(store.observeBooks().first().isEmpty())
    }

    @Test
    fun `a failed import can simply be run again`() = test {
        val bad = provider.dir(provider.root, "B")
        provider.file(bad, "a.txt", "x")
        bad.unlistable = true
        try {
            import()
        } catch (_: IOException) {
        }
        bad.unlistable = false
        val result = import()
        assertEquals(ImportResult(1, 1), result)
        assertEquals(1, store.observeBooks().first().size)
    }
}
