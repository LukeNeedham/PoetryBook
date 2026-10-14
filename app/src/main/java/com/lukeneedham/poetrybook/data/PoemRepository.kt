package com.lukeneedham.poetrybook.data

import java.io.IOException
import java.util.UUID

/**
 * Loads poems from a [PoemFolder] and writes changes back: one text file per poem, named after
 * the poem's title, containing its text.
 *
 * Remembers which file belongs to which poem so renaming a poem renames its file instead of
 * leaving a copy behind. Not thread-safe; callers serialise access.
 */
class PoemRepository(
    private val folder: PoemFolder,
    private val settings: AppSettings,
) {
    private class Tracked(
        var file: PoemFolder.File,
        /** The text last written to (or read from) the file. */
        var body: String,
        /** The name we last asked for, so a provider that changes names can't make us retry forever. */
        var requestedName: String = file.name,
    )

    private val tracked = mutableMapOf<String, Tracked>()

    /** Reads every poem in the folder, in saved page order. Throws [IOException] if it can't be read. */
    fun load(): List<Poem> {
        tracked.clear()
        val order = settings.pageOrder(folder.treeUri)
        val files = folder.list().sortedWith(
            compareBy<PoemFolder.File> { file ->
                order.indexOf(file.name).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
        return files.mapNotNull { file ->
            val body = try {
                folder.read(file)
            } catch (_: IOException) {
                return@mapNotNull null
            }
            val id = UUID.randomUUID().toString()
            tracked[id] = Tracked(file, body)
            Poem(id = id, title = file.name, body = body)
        }
    }

    /**
     * Makes the folder match [poems]. Returns true if everything is in sync, false if some change
     * failed and a later call should try again.
     */
    fun save(poems: List<Poem>): Boolean {
        var inSync = true
        val ids = poems.mapTo(HashSet()) { it.id }

        tracked.keys.filter { it !in ids }.forEach { id ->
            val removed = tracked[id] ?: return@forEach
            try {
                folder.delete(removed.file)
                tracked.remove(id)
            } catch (_: IOException) {
                inSync = false
            }
        }

        val names = uniqueFileNames(poems.map { it.title })

        poems.forEachIndexed { index, poem ->
            if (poem.id in tracked) return@forEachIndexed
            try {
                // The body is written below; start from nothing so an empty poem needs no write.
                tracked[poem.id] = Tracked(folder.create(names[index]), body = "").also {
                    it.requestedName = names[index]
                }
            } catch (_: IOException) {
                inSync = false
            }
        }

        // A rename can be blocked by another poem still holding the name, so keep going while
        // renames make progress (e.g. two poems swapping titles).
        var progressed = true
        while (progressed) {
            progressed = false
            poems.forEachIndexed { index, poem ->
                val entry = tracked[poem.id] ?: return@forEachIndexed
                val wanted = names[index]
                if (entry.file.name == wanted || entry.requestedName == wanted) return@forEachIndexed
                val taken = tracked.values.any { it !== entry && it.file.name.equals(wanted, ignoreCase = true) }
                if (taken) return@forEachIndexed
                try {
                    entry.file = folder.rename(entry.file, wanted)
                    entry.requestedName = wanted
                    progressed = true
                } catch (_: IOException) {
                    // Try again on the next save.
                }
            }
        }

        poems.forEachIndexed { index, poem ->
            val entry = tracked[poem.id] ?: return@forEachIndexed
            if (entry.file.name != names[index] && entry.requestedName != names[index]) inSync = false
            if (entry.body == poem.body) return@forEachIndexed
            try {
                folder.write(entry.file, poem.body)
                entry.body = poem.body
            } catch (_: IOException) {
                inSync = false
            }
        }

        settings.savePageOrder(folder.treeUri, poems.mapNotNull { tracked[it.id]?.file?.name })
        return inSync
    }
}
