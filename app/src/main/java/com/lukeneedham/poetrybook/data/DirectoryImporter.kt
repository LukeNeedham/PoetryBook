package com.lukeneedham.poetrybook.data

import android.content.Context
import android.net.Uri
import java.io.IOException

/** What an import brought in. */
data class ImportResult(val books: Int, val poems: Int)

/**
 * A one-off copy of a directory of poems into the database: every subdirectory is a book and every
 * `.txt` file in it a poem. Text files directly in the directory go in one more book, called
 * [miscName]. Nothing is remembered about the directory afterwards, and it is never written to.
 *
 * Page order and cover colours the old folder-based app kept in its settings are used if they are
 * still there. Blocking: call off the main thread. Throws [IOException] if the directory can't be read.
 */
class DirectoryImporter(
    private val context: Context,
    private val settings: AppSettings,
    private val store: PoemStore,
    private val miscName: String,
) {
    suspend fun importFrom(treeUri: Uri): ImportResult {
        val root = PoemFolder(context, treeUri)
        val subFolders = root.listSubFolders().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        // Everything is read first and written in one transaction, so a folder that can't be read
        // part way through leaves nothing half imported.
        val books = subFolders.map { subFolder ->
            val folder = root.subFolder(subFolder)
            val poems = readPoems(folder)
            ImportedBook(
                name = subFolder.name,
                color = settings.legacyBookColor(subFolder.documentId),
                poems = poems,
                updatedAt = maxOf(poems.maxOfOrNull { it.modifiedAt } ?: 0L, settings.legacyBookUpdated(subFolder.documentId)),
            )
        }.toMutableList()

        val loose = readPoems(root)
        if (loose.isNotEmpty()) {
            val taken = subFolders.any { it.name.equals(miscName, ignoreCase = true) }
            books += ImportedBook(
                name = if (taken) "$miscName (loose)" else miscName,
                color = settings.legacyBookColor(root.directoryDocumentId),
                poems = loose,
                updatedAt = maxOf(loose.maxOf { it.modifiedAt }, settings.legacyBookUpdated(root.directoryDocumentId)),
            )
        }

        store.importBooks(books)
        return ImportResult(books = books.size, poems = books.sumOf { it.poems.size })
    }

    /** The poems in [folder] in the order the old app showed them: saved page order, then by name. */
    private fun readPoems(folder: PoemFolder): List<ImportedPoem> {
        val order = settings.legacyPageOrder(folder.key)
        return folder.list()
            .sortedWith(
                compareBy<PoemFolder.File> { file -> order.indexOf(file.name).takeIf { it >= 0 } ?: Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
            .mapNotNull { file ->
                val body = try {
                    folder.read(file)
                } catch (_: IOException) {
                    return@mapNotNull null
                }
                ImportedPoem(title = file.name, body = body, modifiedAt = file.lastModified)
            }
    }
}
