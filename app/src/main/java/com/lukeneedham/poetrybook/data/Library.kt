package com.lukeneedham.poetrybook.data

import android.content.Context
import android.net.Uri

/**
 * A book on the shelf: a folder of poems.
 *
 * @property documentId the folder's document id inside the picked tree.
 * @property isLooseFiles true for the Misc book, which is the picked folder itself: it holds the
 * poem files that sit directly in it rather than in a subfolder.
 */
data class Book(
    val documentId: String,
    val name: String,
    val isLooseFiles: Boolean,
    val poemCount: Int,
)

/**
 * Everything under the folder the user picked. Each subfolder is a book, and the `.txt` files
 * directly in the folder are gathered into one more book, Misc.
 *
 * Calls are blocking and must run off the main thread. Failures throw [java.io.IOException].
 */
class Library(context: Context, val treeUri: Uri, private val miscName: String) {
    private val root = PoemFolder(context, treeUri)

    /** The books, alphabetically, then Misc. Misc is always there so there's somewhere to write. */
    fun books(): List<Book> {
        val subFolders = root.listSubFolders().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val books = subFolders.map { subFolder ->
            Book(
                documentId = subFolder.documentId,
                name = subFolder.name,
                isLooseFiles = false,
                poemCount = root.subFolder(subFolder).list().size,
            )
        }
        // A real folder called Misc would be confusable with the loose files; tell them apart.
        val looseName = if (books.any { it.name.equals(miscName, ignoreCase = true) }) "$miscName (loose)" else miscName
        return books + Book(root.directoryDocumentId, looseName, isLooseFiles = true, poemCount = root.list().size)
    }

    /** The folder holding [book]'s poems. */
    fun folderOf(book: Book): PoemFolder = PoemFolder(root.appContext, treeUri, book.documentId)

    /** Creates a new, empty book called [name]. */
    fun createBook(name: String): Book {
        val subFolder = root.createSubFolder(name.trim())
        return Book(subFolder.documentId, subFolder.name, isLooseFiles = false, poemCount = 0)
    }
}
