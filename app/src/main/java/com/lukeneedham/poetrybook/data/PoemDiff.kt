package com.lukeneedham.poetrybook.data

import kotlin.math.abs

/** What happened to a line of an older version, compared with the current poem. */
enum class LineChange {
    /** Still there, in the same place relative to the lines around it. */
    SAME,

    /** Still there word for word, but in another place. */
    MOVED,

    /** Still there, with some words changed. */
    EDITED,

    /** Not in the current poem any more. */
    REMOVED,
}

/** Where a line is in the current poem: stanza and line within it, both counted from 1. */
data class LinePlace(val stanza: Int, val line: Int)

/**
 * One line of the older version. [edited] are the parts of [text] (`start until end`) that the
 * current line doesn't have, for an [LineChange.EDITED] line. [movedTo] is where a
 * [LineChange.MOVED] line is now. [addedAfter] is how many lines the current poem has just after
 * this line's place that the older version didn't.
 */
data class DiffLine(
    val text: String,
    val change: LineChange,
    val edited: List<IntRange> = emptyList(),
    val movedTo: LinePlace? = null,
    val addedAfter: Int = 0,
)

/** A stanza of the older version. [movedTo] is its stanza number (from 1) in the current poem if the whole stanza moved. */
data class DiffStanza(val lines: List<DiffLine>, val movedTo: Int? = null)

/**
 * An older version's text laid out in stanzas and lines, each marked with what became of it in the
 * current poem. [addedAtStart] is how many lines of the current poem come before anything the
 * older version still has.
 */
data class VersionDiff(
    val stanzas: List<DiffStanza>,
    val addedAtStart: Int,
    val currentStanzaCount: Int,
) {
    /** True if the two texts have the same lines in the same stanzas and order. */
    val isSame: Boolean
        get() = addedAtStart == 0 && stanzas.all { stanza ->
            stanza.movedTo == null && stanza.lines.all { it.change == LineChange.SAME && it.addedAfter == 0 }
        }
}

/** A line has to share at least this much of its words with another to count as an edit of it. */
private const val MinSimilarity = 0.5f

/**
 * Compares the text of an older version with the current text of the poem, by stanza (separated by
 * blank lines), then by line, then by word:
 *
 * 1. Stanzas are paired up by how many of their lines they share. Stanzas that are still in order
 *    are left alone; the others have moved.
 * 2. Inside a pair of stanzas, lines that are the same and in order stay; the rest are moved
 *    within the stanza, edited (they share most of their words) or left over.
 * 3. Leftover lines anywhere are matched the same way: moved if word for word, edited if similar.
 * 4. What's still unmatched is removed (older version) or added (current poem).
 *
 * Only edited lines get a word-by-word comparison, so words are marked just where a line was tweaked.
 */
fun diffPoems(version: String, current: String): VersionDiff {
    val a = stanzasOf(version)
    val b = stanzasOf(current)
    val aKeys = a.map { stanza -> stanza.map(::lineKey) }
    val bKeys = b.map { stanza -> stanza.map(::lineKey) }

    val result = a.map { arrayOfNulls<DiffLine>(it.size) }
    val bKind = b.map { arrayOfNulls<LineChange>(it.size) }
    val bFrom = b.map { arrayOfNulls<Pair<Int, Int>>(it.size) }

    fun claim(i: Int, k: Int, j: Int, l: Int, change: LineChange, edited: List<IntRange> = emptyList()) {
        val place = if (change == LineChange.MOVED) LinePlace(j + 1, l + 1) else null
        result[i][k] = DiffLine(a[i][k], change, edited, place)
        bKind[j][l] = change
        bFrom[j][l] = i to k
    }

    /** Matches the lines nobody has claimed yet, among [aLines] and [bLines] (as stanza to line pairs). */
    fun matchLeftovers(aLines: List<Pair<Int, Int>>, bLines: List<Pair<Int, Int>>) {
        val freeA = aLines.filter { (i, k) -> result[i][k] == null }
        // Word for word, somewhere else.
        for ((i, k) in freeA) {
            val target = bLines.firstOrNull { (j, l) -> bKind[j][l] == null && bKeys[j][l] == aKeys[i][k] }
            if (target != null) claim(i, k, target.first, target.second, LineChange.MOVED)
        }
        // Most of the words.
        val candidates = mutableListOf<Triple<Float, Pair<Int, Int>, Pair<Int, Int>>>()
        for (from in freeA.filter { (i, k) -> result[i][k] == null }) {
            for (to in bLines.filter { (j, l) -> bKind[j][l] == null }) {
                val similarity = similarity(a[from.first][from.second], b[to.first][to.second])
                if (similarity >= MinSimilarity) candidates += Triple(similarity, from, to)
            }
        }
        candidates.sortWith(
            compareByDescending<Triple<Float, Pair<Int, Int>, Pair<Int, Int>>> { it.first }
                .thenBy { abs(it.second.first - it.third.first) * 1000 + abs(it.second.second - it.third.second) },
        )
        for ((_, from, to) in candidates) {
            val (i, k) = from
            val (j, l) = to
            if (result[i][k] != null || bKind[j][l] != null) continue
            claim(i, k, j, l, LineChange.EDITED, differingRanges(a[i][k], b[j][l]))
        }
    }

    // 1. Pair up stanzas, and see which kept their order.
    val stanzaCandidates = mutableListOf<Triple<Float, Int, Int>>()
    for (i in a.indices) {
        for (j in b.indices) {
            val shared = sharedCount(aKeys[i], bKeys[j])
            // Lines added to or taken out of a stanza don't make it a different stanza, so it's
            // enough for half of the smaller one to be shared; the bigger the share, the better the pair.
            if (shared > 0 && shared.toFloat() / minOf(a[i].size, b[j].size) >= MinSimilarity) {
                stanzaCandidates += Triple(shared.toFloat() / maxOf(a[i].size, b[j].size), i, j)
            }
        }
    }
    stanzaCandidates.sortWith(
        compareByDescending<Triple<Float, Int, Int>> { it.first }.thenBy { abs(it.second - it.third) },
    )
    val pairOf = IntArray(a.size) { -1 }
    val bPaired = BooleanArray(b.size)
    for ((_, i, j) in stanzaCandidates) {
        if (pairOf[i] < 0 && !bPaired[j]) {
            pairOf[i] = j
            bPaired[j] = true
        }
    }
    val stayed = longestInOrder(a.indices.filter { pairOf[it] >= 0 }.map { pairOf[it] })
        .map { pairIndex -> a.indices.filter { pairOf[it] >= 0 }[pairIndex] }
        .toSet()

    // 2. Inside each pair of stanzas.
    for (i in a.indices) {
        val j = pairOf[i]
        if (j < 0) continue
        val x = aKeys[i]
        val y = bKeys[j]
        val table = Array(x.size + 1) { IntArray(y.size + 1) }
        for (p in x.size - 1 downTo 0) {
            for (q in y.size - 1 downTo 0) {
                table[p][q] = if (x[p] == y[q]) table[p + 1][q + 1] + 1 else maxOf(table[p + 1][q], table[p][q + 1])
            }
        }
        var p = 0
        var q = 0
        while (p < x.size && q < y.size) {
            when {
                x[p] == y[q] -> {
                    claim(i, p, j, q, LineChange.SAME)
                    p++
                    q++
                }
                table[p + 1][q] >= table[p][q + 1] -> p++
                else -> q++
            }
        }
        matchLeftovers(a[i].indices.map { i to it }, b[j].indices.map { j to it })
    }

    // 3. Whatever is left, anywhere.
    matchLeftovers(
        a.indices.flatMap { i -> a[i].indices.map { i to it } },
        b.indices.flatMap { j -> b[j].indices.map { j to it } },
    )

    // 4. The rest: removed, and added. Added lines are shown after the line of the older version
    // that the line before them in the current poem came from.
    val addedAfter = HashMap<Pair<Int, Int>, Int>()
    var addedAtStart = 0
    val bOrder = b.indices.flatMap { j -> b[j].indices.map { j to it } }
    for ((position, spot) in bOrder.withIndex()) {
        if (bKind[spot.first][spot.second] != null) continue
        val before = bOrder.subList(0, position).asReversed()
        val anchor = before.firstOrNull { (j, l) -> bKind[j][l] == LineChange.SAME || bKind[j][l] == LineChange.EDITED }
            ?: before.firstOrNull { (j, l) -> bKind[j][l] != null }
        val from = anchor?.let { (j, l) -> bFrom[j][l] }
        if (from == null) addedAtStart++ else addedAfter.merge(from, 1, Int::plus)
    }

    val stanzas = a.indices.map { i ->
        DiffStanza(
            lines = a[i].indices.map { k ->
                val line = result[i][k] ?: DiffLine(a[i][k], LineChange.REMOVED)
                line.copy(addedAfter = addedAfter[i to k] ?: 0)
            },
            movedTo = if (pairOf[i] >= 0 && i !in stayed) pairOf[i] + 1 else null,
        )
    }
    return VersionDiff(stanzas, addedAtStart, b.size)
}

/** The non-blank lines of [text], grouped into stanzas by the blank lines between them. */
private fun stanzasOf(text: String): List<List<String>> {
    val stanzas = mutableListOf<MutableList<String>>()
    var open: MutableList<String>? = null
    for (raw in text.split('\n')) {
        val line = raw.trimEnd()
        if (line.isBlank()) {
            open = null
            continue
        }
        val stanza = open ?: mutableListOf<String>().also {
            stanzas += it
            open = it
        }
        stanza.add(line)
    }
    return stanzas
}

/** What a line is compared by: its text without the spaces around it or doubled up inside it. */
private fun lineKey(line: String) = line.trim().replace(Regex("\\s+"), " ")

private val WordPattern = Regex("[\\p{L}\\p{N}_']+")

private fun wordsOf(line: String) = WordPattern.findAll(line).map { it.value.lowercase() }.toList()

/** How much of two lines' words they share, 0 to 1 (0 if either has no words). */
private fun similarity(first: String, second: String): Float {
    val x = wordsOf(first)
    val y = wordsOf(second)
    if (x.isEmpty() || y.isEmpty()) return 0f
    val left = HashMap<String, Int>()
    y.forEach { left.merge(it, 1, Int::plus) }
    var shared = 0
    for (word in x) {
        val count = left[word] ?: 0
        if (count > 0) {
            shared++
            left[word] = count - 1
        }
    }
    return 2f * shared / (x.size + y.size)
}

/** How many lines the two stanzas have in common, counting repeats separately. */
private fun sharedCount(x: List<String>, y: List<String>): Int {
    val left = HashMap<String, Int>()
    y.forEach { left.merge(it, 1, Int::plus) }
    var shared = 0
    for (line in x) {
        val count = left[line] ?: 0
        if (count > 0) {
            shared++
            left[line] = count - 1
        }
    }
    return shared
}

/** The indices into [values] of a longest run of strictly increasing values. */
private fun longestInOrder(values: List<Int>): List<Int> {
    if (values.isEmpty()) return emptyList()
    val length = IntArray(values.size) { 1 }
    val previous = IntArray(values.size) { -1 }
    for (x in values.indices) {
        for (y in 0 until x) {
            if (values[y] < values[x] && length[y] + 1 > length[x]) {
                length[x] = length[y] + 1
                previous[x] = y
            }
        }
    }
    var at = length.indices.maxByOrNull { length[it] } ?: return emptyList()
    val run = mutableListOf<Int>()
    while (at >= 0) {
        run += at
        at = previous[at]
    }
    return run.reversed()
}
