package com.lukeneedham.poetrybook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lukeneedham.poetrybook.data.db.PoetryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Shared set-up for tests that need the real database: a fresh, empty, in-memory Room database
 * (real SQLite, via Robolectric) and a [PoemStore] on top of it with a clock the test controls.
 */
@RunWith(RobolectricTestRunner::class)
abstract class DatabaseTestBase {
    protected lateinit var db: PoetryDatabase
    protected lateinit var store: PoemStore

    /** The store's clock. Tests move it by hand so timestamps are predictable. */
    protected var clock = 1_000L

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PoetryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PoemStore(db) { clock }
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    protected fun test(block: suspend () -> Unit) = runBlocking { block() }

    protected fun newId(): String = UUID.randomUUID().toString()

    protected suspend fun newBook(name: String = "Book", color: Int = 0): String = store.createBook(name, color).id

    /** Saves a new poem at the end of the book and moves the clock on. */
    protected suspend fun write(bookId: String, title: String, body: String = "text"): Poem {
        val poem = Poem(newId(), title, body)
        store.save(bookId, poem)
        clock += 1_000
        return poem
    }

    protected suspend fun titles(bookId: String): List<String> = store.poems(bookId).map { it.title }

    protected suspend fun ids(bookId: String): List<String> = store.poems(bookId).map { it.id }

    /** The page number of each live poem, in page order: a healthy book gives 0, 1, 2… */
    protected suspend fun pages(bookId: String): List<Int> =
        db.poemDao().liveIds(bookId).map { id -> db.poemDao().poem(id)!!.position!! }

    protected fun range(n: Int): List<Int> = (0 until n).toList()
}
