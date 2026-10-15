package com.lukeneedham.poetrybook.data

/** One page of the book: the current title and text of a poem. Line breaks in [body] are the poem's lines. */
data class Poem(
    val id: String,
    val title: String = "",
    val body: String = "",
) {
    /** Nothing written yet: such a poem is not saved, and the book keeps exactly one at its end. */
    val isBlank: Boolean
        get() = title.isBlank() && body.isBlank()
}

/**
 * One entry in a poem's history: the title and text the poem had from [createdAt] (epoch millis)
 * until the next version. The notepad isn't part of a version; it belongs to the whole poem.
 */
data class PoemVersion(
    val createdAt: Long,
    val title: String,
    val body: String,
)

/** A deleted poem waiting in the bin, as it was when it was deleted. */
data class BinPoem(
    val id: String,
    val title: String,
    val body: String,
    val bookName: String,
    val deletedAt: Long,
)

/** A deleted book waiting in the bin. [poemCount] counts its poems that weren't deleted on their own first. */
data class BinBook(
    val id: String,
    val name: String,
    val color: Int?,
    val poemCount: Int,
    val deletedAt: Long,
)

/** True if saving [title] and [body] would change a poem whose latest version is [latest]. */
fun isNewVersion(latest: PoemVersion?, title: String, body: String): Boolean =
    latest == null || latest.title != title || latest.body != body

/**
 * [poems] with exactly one blank poem at the end: one is added if the last poem has been written
 * in, and surplus blanks at the end are dropped. Of several trailing blanks the first is kept,
 * since it may be the page being edited right now. Only the end of the list ever changes.
 */
fun withOneBlankLast(poems: List<Poem>, newBlankPoem: () -> Poem): List<Poem> {
    var firstTrailingBlank = poems.size
    while (firstTrailingBlank > 0 && poems[firstTrailingBlank - 1].isBlank) firstTrailingBlank--
    return if (firstTrailingBlank == poems.size) {
        poems + newBlankPoem()
    } else {
        poems.take(firstTrailingBlank + 1)
    }
}
