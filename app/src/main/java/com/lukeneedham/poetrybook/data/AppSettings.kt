package com.lukeneedham.poetrybook.data

import android.content.Context
import org.json.JSONArray

/**
 * Small app-private preferences: how the shelf is sorted, plus what earlier versions of the app
 * remembered about books kept as folders, which is read once when such a folder is imported.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How the bookshelf is sorted. */
    var bookSort: BookSort
        get() = prefs.getString(KEY_SORT, null)
            ?.let { saved -> BookSort.entries.firstOrNull { it.name == saved } }
            ?: BookSort.ALPHABETICAL
        set(value) {
            prefs.edit().putString(KEY_SORT, value.name).apply()
        }

    /**
     * The file names (without extension) in page order, as the old folder-based app last saved them
     * for the book folder with [folderKey] (see [PoemFolder.key]).
     */
    fun legacyPageOrder(folderKey: String): List<String> {
        val json = prefs.getString("order|$folderKey", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** The cover colour (ARGB) the old app gave the book folder with [documentId], or null. */
    fun legacyBookColor(documentId: String): Int? =
        if (prefs.contains("color|$documentId")) prefs.getInt("color|$documentId", 0) else null

    /** When the old app last changed the book folder with [documentId], or 0. */
    fun legacyBookUpdated(documentId: String): Long = prefs.getLong("updated|$documentId", 0L)

    private companion object {
        const val KEY_SORT = "bookSort"
    }
}
