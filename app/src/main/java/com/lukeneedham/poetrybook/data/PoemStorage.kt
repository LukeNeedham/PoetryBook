package com.lukeneedham.poetrybook.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Keeps the whole book in one JSON file in the app's private storage. */
class PoemStorage(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the saved poems, or null if nothing has been saved yet (or the file is unreadable). */
    fun load(): List<Poem>? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Book>(file.readText()).poems
        } catch (_: Exception) {
            null
        }
    }

    fun save(poems: List<Poem>) {
        // Write to a temp file first so a crash mid-write can't corrupt the existing book.
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(Book.serializer(), Book(poems)))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    @Serializable
    private data class Book(val poems: List<Poem>)

    private companion object {
        const val FILE_NAME = "poems.json"
    }
}
