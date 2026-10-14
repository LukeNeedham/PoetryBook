package com.lukeneedham.poetrybook.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/**
 * The folder the user picked with the system folder picker, holding one `.txt` file per poem.
 *
 * Goes through the Storage Access Framework, so it works for any folder the user granted
 * (internal storage, SD card, cloud providers…) and survives the app being reinstalled.
 * All calls are blocking and must run off the main thread. Failures throw [IOException].
 */
class PoemFolder(context: Context, val treeUri: Uri) {
    private val resolver = context.contentResolver
    private val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    private val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)

    /** A poem file. [name] is the file name without the `.txt` extension. */
    data class File(val uri: Uri, val name: String)

    fun list(): List<File> = io {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(children, projection, null, null, null)
            ?: throw IOException("Folder can't be listed")
        cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val name = it.getString(1) ?: continue
                    if (it.getString(2) == Document.MIME_TYPE_DIR) continue
                    if (!name.endsWith(PoemFileExtension, ignoreCase = true)) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, it.getString(0))
                    add(File(uri, name.dropLast(PoemFileExtension.length)))
                }
            }
        }
    }

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
        val uri = DocumentsContract.createDocument(resolver, rootUri, "text/plain", name + PoemFileExtension)
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
