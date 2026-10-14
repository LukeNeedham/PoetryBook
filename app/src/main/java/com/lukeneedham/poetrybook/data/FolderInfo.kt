package com.lukeneedham.poetrybook.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

private const val ExternalStorageAuthority = "com.android.externalstorage.documents"

/** A readable description of the picked folder, e.g. "Internal storage/Documents/Poems". */
fun folderLabel(context: Context, treeUri: Uri): String {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        ?: return treeUri.toString()
    if (treeUri.authority == ExternalStorageAuthority) {
        val volume = documentId.substringBefore(':')
        val path = documentId.substringAfter(':', missingDelimiterValue = "")
        val root = if (volume == "primary") "Internal storage" else "SD card ($volume)"
        return if (path.isEmpty()) root else "$root/$path"
    }
    // Other providers (cloud storage etc.) have opaque ids; the folder's own name is what's useful.
    val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    val name = runCatching {
        context.contentResolver.query(
            rootUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
    return name ?: treeUri.toString()
}

/** Shows the folder in the system file browser. Returns false if nothing could open it. */
fun openFolderInFileBrowser(context: Context, treeUri: Uri): Boolean {
    val authority = treeUri.authority ?: return false
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        ?: return false
    val folder = DocumentsContract.buildDocumentUri(authority, documentId)
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR),
        Intent(Intent.ACTION_VIEW).setData(treeUri),
    )
    return attempts.any { intent ->
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
