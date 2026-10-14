package com.lukeneedham.poetrybook.data

import kotlinx.serialization.Serializable

/** One page of the book. Poems are plain text; line breaks in [body] are the poem's lines. */
@Serializable
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
