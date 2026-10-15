package com.lukeneedham.poetrybook.data

import android.content.Context
import android.net.Uri

/**
 * A book on the shelf: a folder of poems.
 *
 * @property documentId the folder's document id inside the picked tree.
 * @property color the cover colour (ARGB) the user chose, or null if they never did.
 * @property updatedAt when a poem in the book was last changed, or the book made (epoch millis).
 * @property isLooseFiles true for the Misc book, which is the picked folder itself: it holds the
 * poem files that sit directly in it rather than in a subfolder.
 */
data class Book(
    val documentId: String,
    val name: String,
    val isLooseFiles: Boolean,
    val poemCount: Int,
    val color: Int? = null,
    val updatedAt: Long = 0L,
)

/**
 * Everything under the folder the user picked. Each subfolder is a book, and the `.txt` files
 * directly in the folder are gathered into one more book, Misc.
 *
 * Calls are blocking and must run off the main thread. Failures throw [java.io.IOException].
 */
class Library(
    context: Context,
    val treeUri: Uri,
    private val miscName: String,
    private val settings: AppSettings,
) {
    private val root = PoemFolder(context, treeUri)

    /** The books, alphabetically, then Misc. Misc is always there so there's somewhere to write. */
    fun books(): List<Book> {
        val subFolders = root.listSubFolders().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val books = subFolders.map { subFolder ->
            val stats = root.subFolder(subFolder).stats()
            Book(
                documentId = subFolder.documentId,
                name = subFolder.name,
                isLooseFiles = false,
                poemCount = stats.poemCount,
                color = settings.bookColor(subFolder.documentId),
                updatedAt = maxOf(stats.lastModified, settings.bookUpdated(subFolder.documentId)),
            )
        }
        // A real folder called Misc would be confusable with the loose files; tell them apart.
        val looseName = if (books.any { it.name.equals(miscName, ignoreCase = true) }) "$miscName (loose)" else miscName
        val looseStats = root.stats()
        val loose = Book(
            documentId = root.directoryDocumentId,
            name = looseName,
            isLooseFiles = true,
            poemCount = looseStats.poemCount,
            color = settings.bookColor(root.directoryDocumentId),
            updatedAt = maxOf(looseStats.lastModified, settings.bookUpdated(root.directoryDocumentId)),
        )
        return books + loose
    }

    /** The folder holding [book]'s poems. */
    fun folderOf(book: Book): PoemFolder = PoemFolder(root.appContext, treeUri, book.documentId)

    /** Creates a new, empty book called [name] with the cover [color] (ARGB). */
    fun createBook(name: String, color: Int): Book {
        val subFolder = root.createSubFolder(name.trim())
        val now = System.currentTimeMillis()
        settings.saveBookColor(subFolder.documentId, color)
        settings.saveBookUpdated(subFolder.documentId, now)
        return Book(subFolder.documentId, subFolder.name, isLooseFiles = false, poemCount = 0, color = color, updatedAt = now)
    }

    /**
     * Gives [book] the cover [color] (ARGB) and, unless it is the Misc book (which is the picked
     * folder itself and keeps its name), renames it to [name]. Renaming is not an update: only
     * writing poems is. Returns the book as it is now.
     */
    fun editBook(book: Book, name: String, color: Int): Book {
        var edited = book
        val newName = name.trim()
        if (!book.isLooseFiles && newName.isNotEmpty() && newName != book.name) {
            val oldKey = folderOf(book).key
            val renamed = root.renameSubFolder(PoemFolder.SubFolder(book.documentId, book.name), newName)
            settings.moveBook(book.documentId, renamed.documentId, oldKey, PoemFolder(root.appContext, treeUri, renamed.documentId).key)
            edited = book.copy(documentId = renamed.documentId, name = renamed.name)
        }
        settings.saveBookColor(edited.documentId, color)
        return edited.copy(color = color)
    }
}

/** How the bookshelf orders its books. */
enum class BookSort {
    /** By name, with Misc last. */
    ALPHABETICAL,

    /** The book with the most recently created or changed poems first. */
    LAST_UPDATED,
}

/** These books in the order [sort] asks for. */
fun List<Book>.sortedFor(sort: BookSort): List<Book> {
    val byName = compareBy<Book>({ it.name.lowercase() }, { it.name })
    return when (sort) {
        BookSort.ALPHABETICAL -> sortedWith(compareBy<Book> { it.isLooseFiles }.then(byName))
        BookSort.LAST_UPDATED -> sortedWith(compareByDescending<Book> { it.updatedAt }.then(byName))
    }
}
