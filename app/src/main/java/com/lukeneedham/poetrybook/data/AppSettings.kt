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

    private companion object {
        const val KEY_FOLDER = "folder"
    }
}
