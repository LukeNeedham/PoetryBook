package com.lukeneedham.poetrybook.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/**
 * A directory inside the folder the user picked with the system folder picker, holding one `.txt`
 * file per poem. With the default [directoryDocumentId] this is the picked folder itself; a
 * subfolder of it is a book.
 *
 * Goes through the Storage Access Framework, so it works for any folder the user granted
 * (internal storage, SD card, cloud providers…) and survives the app being reinstalled.
 * All calls are blocking and must run off the main thread. Failures throw [IOException].
 */
class PoemFolder(
    context: Context,
    val treeUri: Uri,
    val directoryDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri),
) {
    internal val appContext: Context = context.applicationContext
    private val resolver = appContext.contentResolver
    private val directoryUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, directoryDocumentId)

    /** Identifies this directory in app settings, e.g. for the saved page order. */
    val key: String = "$treeUri|$directoryDocumentId"

    /** A subfolder; in the library, a book. */
    data class SubFolder(val documentId: String, val name: String)

    /** A poem file. [name] is the file name without the `.txt` extension. */
    data class File(val uri: Uri, val name: String)

    private class Child(val documentId: String, val name: String, val isDirectory: Boolean, val lastModified: Long)

    private fun children(): List<Child> = io {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryDocumentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("Folder can't be listed")
        cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val name = it.getString(1) ?: continue
                    val modified = if (it.isNull(3)) 0L else it.getLong(3)
                    add(Child(it.getString(0), name, it.getString(2) == Document.MIME_TYPE_DIR, modified))
                }
            }
        }
    }

    /** The poem files directly in this directory. */
    fun list(): List<File> = children()
        .filter { !it.isDirectory && it.name.endsWith(PoemFileExtension, ignoreCase = true) }
        .map {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId)
            File(uri, it.name.dropLast(PoemFileExtension.length))
        }

    /** How many poems are directly in this directory, and when the most recently changed one was. */
    fun stats(): Stats {
        val poems = children().filter { !it.isDirectory && it.name.endsWith(PoemFileExtension, ignoreCase = true) }
        return Stats(poems.size, poems.maxOfOrNull { it.lastModified } ?: 0L)
    }

    /** See [stats]. */
    data class Stats(val poemCount: Int, val lastModified: Long)

    /** The folders directly in this directory, ignoring hidden ones such as `.thumbnails`. */
    fun listSubFolders(): List<SubFolder> = children()
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .map { SubFolder(it.documentId, it.name) }

    /** Creates a folder called [name] here. The provider may adjust the name, so use the result. */
    fun createSubFolder(name: String): SubFolder = io {
        val uri = DocumentsContract.createDocument(resolver, directoryUri, Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Can't create $name")
        SubFolder(DocumentsContract.getDocumentId(uri), displayName(uri) ?: name)
    }

    /** Renames [subFolder]. The document id can change with the name, so use the result. */
    fun renameSubFolder(subFolder: SubFolder, newName: String): SubFolder = io {
        val oldUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, subFolder.documentId)
        val uri = DocumentsContract.renameDocument(resolver, oldUri, newName)
            ?: throw IOException("Can't rename ${subFolder.name}")
        SubFolder(DocumentsContract.getDocumentId(uri), displayName(uri) ?: newName)
    }

    /** The poem folder for [subFolder]. */
    fun subFolder(subFolder: SubFolder): PoemFolder =
        PoemFolder(appContext, treeUri, subFolder.documentId)

    fun read(file: File): String = io {
        resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("Can't read ${file.name}")
    }

    fun write(file: File, text: String) = io {
        // "wt" truncates, so a shorter poem doesn't leave the end of the old one behind.
        resolver.openOutputStream(file.uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw IOException("Can't write ${file.name}")
    }

    /** Creates an empty file called [name]. The provider may adjust the name, so use the result. */
    fun create(name: String): File = io {
        val uri = DocumentsContract.createDocument(resolver, directoryUri, "text/plain", name + PoemFileExtension)
            ?: throw IOException("Can't create $name")
        File(uri, displayName(uri)?.removeExtension() ?: name)
    }

    fun rename(file: File, newName: String): File = io {
        val uri = DocumentsContract.renameDocument(resolver, file.uri, newName + PoemFileExtension)
            ?: throw IOException("Can't rename ${file.name}")
        File(uri, displayName(uri)?.removeExtension() ?: newName)
    }

    fun delete(file: File) = io {
        if (!DocumentsContract.deleteDocument(resolver, file.uri)) {
            throw IOException("Can't delete ${file.name}")
        }
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun String.removeExtension() =
        if (endsWith(PoemFileExtension, ignoreCase = true)) dropLast(PoemFileExtension.length) else this

    /** Providers report problems in many ways; callers only care that it failed. */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e)
    }
}
