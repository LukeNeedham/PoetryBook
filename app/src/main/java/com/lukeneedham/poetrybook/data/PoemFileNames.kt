package com.lukeneedham.poetrybook.data

private val IllegalFileNameChars = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
private val Whitespace = Regex("""\s+""")

private const val MaxNameLength = 100
const val UntitledFileName = "Untitled"
const val PoemFileExtension = ".txt"

/** The file name (without extension) a poem with [title] is stored under. */
fun fileNameFor(title: String): String {
    val cleaned = title
        .replace(IllegalFileNameChars, " ")
        .replace(Whitespace, " ")
        .trim()
        // A leading dot would hide the file; trailing dots and spaces are rejected by some file systems.
        .trimStart('.')
        .take(MaxNameLength)
        .trimEnd('.', ' ')
        .trim()
    return cleaned.ifEmpty { UntitledFileName }
}

/**
 * File names for poems with the given [titles], in order. Poems that share a title get
 * " (2)", " (3)"… so every name is unique, ignoring case like most file systems do.
 */
fun uniqueFileNames(titles: List<String>): List<String> {
    val used = HashSet<String>()
    return titles.map { title ->
        val base = fileNameFor(title)
        var candidate = base
        var n = 1
        while (!used.add(candidate.lowercase())) {
            n++
            candidate = "$base ($n)"
        }
        candidate
    }
}
