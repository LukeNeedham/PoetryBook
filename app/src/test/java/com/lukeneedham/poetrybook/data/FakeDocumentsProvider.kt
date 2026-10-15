package com.lukeneedham.poetrybook.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException

/**
 * A stand-in for a Storage Access Framework document provider, serving a folder tree held in memory,
 * so the importer can be tested without a real file system or picker. It records every attempt to
 * change anything, because the importer must only ever read.
 *
 * Document ids are paths: the tree root is `root`, a book `root/Love`, a poem `root/Love/rose.txt`.
 */
class FakeDocumentsProvider : ContentProvider() {
    class Node(
        val name: String,
        val isDir: Boolean,
        val text: String = "",
        val modified: Long = 0,
        val children: MutableList<Node> = mutableListOf(),
        /** Reading this file's content fails (a file the provider can't open). */
        var unreadable: Boolean = false,
        /** Listing this folder fails. */
        var unlistable: Boolean = false,
    )

    val root = Node("root", isDir = true)

    /** Every write-ish call that reached the provider. Must stay empty. */
    val writes = mutableListOf<String>()

    /** The modes files were opened with. All must be "r". */
    val openModes = mutableListOf<String>()

    fun dir(parent: Node, name: String): Node = Node(name, isDir = true).also { parent.children += it }

    fun file(parent: Node, name: String, text: String = "", modified: Long = 0, unreadable: Boolean = false): Node =
        Node(name, isDir = false, text = text, modified = modified, unreadable = unreadable).also { parent.children += it }

    private fun find(id: String): Node? {
        val parts = id.split('/')
        if (parts.first() != "root") return null
        var node = root
        for (part in parts.drop(1)) node = node.children.firstOrNull { it.name == part } ?: return null
        return node
    }

    private fun documentId(uri: Uri): String {
        // content://authority/tree/<treeId>/document/<docId>[/children]; path segments are already decoded.
        val segments = uri.pathSegments
        val i = segments.indexOf("document")
        return if (i >= 0) segments[i + 1] else "root"
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val id = documentId(uri)
        val node = find(id) ?: return null
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID)
        if (node.unlistable) throw IllegalStateException("provider failed to list $id")
        val cursor = MatrixCursor(columns)
        for (child in node.children) {
            val childId = "$id/${child.name}"
            cursor.addRow(
                columns.map { column ->
                    when (column) {
                        Document.COLUMN_DOCUMENT_ID -> childId
                        Document.COLUMN_DISPLAY_NAME -> child.name
                        Document.COLUMN_MIME_TYPE -> if (child.isDir) Document.MIME_TYPE_DIR else "text/plain"
                        Document.COLUMN_LAST_MODIFIED -> if (child.modified == 0L) null else child.modified
                        else -> null
                    }
                },
            )
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        openModes += mode
        val node = find(documentId(uri)) ?: throw FileNotFoundException(uri.toString())
        if (node.unreadable) throw FileNotFoundException("cannot open ${node.name}")
        val temp = File.createTempFile("doc", ".txt").apply { deleteOnExit() }
        temp.writeText(node.text)
        return ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        writes += "insert $uri"
        return null
    }

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
        writes += "delete $uri"
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
        writes += "update $uri"
        return 0
    }
}
