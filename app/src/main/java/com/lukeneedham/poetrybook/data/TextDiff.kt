package com.lukeneedham.poetrybook.data

private class Token(val start: Int, val end: Int, val text: String) {
    val isSpace get() = text.first().isWhitespace()
}

private val TokenPattern = Regex("[\\p{L}\\p{N}_']+|\\s+|[^\\p{L}\\p{N}_'\\s]")

private fun tokenize(text: String) =
    TokenPattern.findAll(text).map { Token(it.range.first, it.range.last + 1, it.value) }.toList()

/** Beyond this many token pairs the exact comparison is skipped and the whole changed middle counts as different. */
private const val MaxCells = 4_000_000

/**
 * The parts of [version] (as `start until end` ranges) that differ from [current]: the words and
 * punctuation that [current] doesn't have in the same place. Comparing is by words, so changing one
 * word of a line marks that word and not the line. Spaces and line breaks are only marked when
 * they sit between two marked words.
 */
fun differingRanges(version: String, current: String): List<IntRange> {
    val a = tokenize(version)
    val b = tokenize(current)
    val differs = BooleanArray(a.size) { true }

    var prefix = 0
    while (prefix < a.size && prefix < b.size && a[prefix].text == b[prefix].text) {
        differs[prefix] = false
        prefix++
    }
    var suffix = 0
    while (suffix < a.size - prefix && suffix < b.size - prefix &&
        a[a.size - 1 - suffix].text == b[b.size - 1 - suffix].text
    ) {
        differs[a.size - 1 - suffix] = false
        suffix++
    }

    val n = a.size - prefix - suffix
    val m = b.size - prefix - suffix
    if (n > 0 && m > 0 && n.toLong() * m <= MaxCells) {
        // Longest common subsequence of the middles; what it keeps is unchanged.
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (a[prefix + i].text == b[prefix + j].text) {
                    lcs[i + 1][j + 1] + 1
                } else {
                    maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
        }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (a[prefix + i].text == b[prefix + j].text) {
                differs[prefix + i] = false
                i++
                j++
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                i++
            } else {
                j++
            }
        }
    }

    val marked = BooleanArray(a.size) { index ->
        if (a[index].isSpace) {
            index > 0 && index < a.size - 1 && differs[index - 1] && differs[index + 1]
        } else {
            differs[index]
        }
    }
    val ranges = mutableListOf<IntRange>()
    var index = 0
    while (index < a.size) {
        if (!marked[index]) {
            index++
            continue
        }
        val first = index
        while (index + 1 < a.size && marked[index + 1]) index++
        ranges += a[first].start until a[index].end
        index++
    }
    return ranges
}
