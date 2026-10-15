package com.lukeneedham.poetrybook.data

/** A poem as the search sees it: its current title and text, and the book it is in. */
data class SearchPoem(
    val id: String,
    val bookId: String,
    val bookName: String,
    val title: String,
    val body: String,
)

/**
 * The poems that match [query], best match first. A poem matches if every word of the query
 * appears in its title or text, ignoring case. A word in the title counts for far more than one in
 * the text, and the whole query as a phrase counts for more still. Ties, and an empty query,
 * are ordered alphabetically by title (untitled poems last).
 */
fun searchPoems(poems: List<SearchPoem>, query: String): List<SearchPoem> {
    val phrase = query.trim().lowercase()
    val alphabetical = compareBy<SearchPoem>({ it.title.isBlank() }, { it.title.lowercase() }, { it.id })
    if (phrase.isEmpty()) return poems.sortedWith(alphabetical)

    val words = phrase.split(Regex("\\s+"))
    return poems
        .mapNotNull { poem ->
            val score = matchScore(poem, phrase, words)
            if (score > 0) poem to score else null
        }
        .sortedWith(compareByDescending<Pair<SearchPoem, Int>> { it.second }.thenBy(alphabetical) { it.first })
        .map { it.first }
}

private fun matchScore(poem: SearchPoem, phrase: String, words: List<String>): Int {
    val title = poem.title.lowercase()
    val body = poem.body.lowercase()
    var score = 0
    for (word in words) {
        val inTitle = title.indexOf(word)
        val inBody = body.indexOf(word)
        if (inTitle < 0 && inBody < 0) return 0
        if (inTitle >= 0) {
            score += 100
            if (startsAWord(title, inTitle)) score += 50
        }
        if (inBody >= 0) {
            score += 10
            if (startsAWord(body, inBody)) score += 5
        }
    }
    if (title == phrase) score += 1000
    else if (title.startsWith(phrase)) score += 500
    else if (title.contains(phrase)) score += 200
    if (body.contains(phrase)) score += 20
    return score
}

private fun startsAWord(text: String, index: Int) = index == 0 || !text[index - 1].isLetterOrDigit()
