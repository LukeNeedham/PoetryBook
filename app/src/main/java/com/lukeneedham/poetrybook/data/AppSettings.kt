package com.lukeneedham.poetrybook.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray

/** Small app-private preferences: which folder holds the poems, and the order of the pages. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var folderUri: Uri?
        get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        set(value) {
            prefs.edit().putString(KEY_FOLDER, value?.toString()).apply()
        }

    /**
     * The file names (without extension) in page order, as last saved for the book folder with
     * [folderKey] (see [PoemFolder.key]). Plain text
     * files can't carry an order, so it lives here; after a reinstall poems come back sorted by name.
     */
    fun pageOrder(folderKey: String): List<String> {
        val json = prefs.getString(orderKey(folderKey), null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePageOrder(folderKey: String, names: List<String>) {
        prefs.edit().putString(orderKey(folderKey), JSONArray(names).toString()).apply()
    }

    private fun orderKey(folderKey: String) = "order|$folderKey"

    /** How the bookshelf is sorted. */
    var bookSort: BookSort
        get() = prefs.getString(KEY_SORT, null)
            ?.let { saved -> BookSort.entries.firstOrNull { it.name == saved } }
            ?: BookSort.ALPHABETICAL
        set(value) {
            prefs.edit().putString(KEY_SORT, value.name).apply()
        }

    /** The cover colour (ARGB) the user gave the book with [documentId], or null if it never got one. */
    fun bookColor(documentId: String): Int? =
        if (prefs.contains(colorKey(documentId))) prefs.getInt(colorKey(documentId), 0) else null

    fun saveBookColor(documentId: String, color: Int) {
        prefs.edit().putInt(colorKey(documentId), color).apply()
    }

    /** When the book with [documentId] was last created or changed by this app, or 0 if never. */
    fun bookUpdated(documentId: String): Long = prefs.getLong(updatedKey(documentId), 0L)

    fun saveBookUpdated(documentId: String, millis: Long) {
        prefs.edit().putLong(updatedKey(documentId), millis).apply()
    }

    /**
     * Renaming a book's folder can change its document id and so its [PoemFolder.key]; this carries
     * everything remembered about the book over to the new ones.
     */
    fun moveBook(oldId: String, newId: String, oldFolderKey: String, newFolderKey: String) {
        if (oldId == newId) return
        val editor = prefs.edit()
        prefs.getString(orderKey(oldFolderKey), null)?.let { editor.putString(orderKey(newFolderKey), it) }
        editor.remove(orderKey(oldFolderKey))
        if (prefs.contains(colorKey(oldId))) editor.putInt(colorKey(newId), prefs.getInt(colorKey(oldId), 0))
        editor.remove(colorKey(oldId))
        if (prefs.contains(updatedKey(oldId))) editor.putLong(updatedKey(newId), prefs.getLong(updatedKey(oldId), 0L))
        editor.remove(updatedKey(oldId))
        editor.apply()
    }

    /** Forgets everything remembered about a book that has been deleted. */
    fun forgetBook(documentId: String, folderKey: String) {
        prefs.edit()
            .remove(orderKey(folderKey))
            .remove(colorKey(documentId))
            .remove(updatedKey(documentId))
            .apply()
    }

    private fun colorKey(documentId: String) = "color|$documentId"

    private fun updatedKey(documentId: String) = "updated|$documentId"

    private companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_SORT = "bookSort"
    }
}
